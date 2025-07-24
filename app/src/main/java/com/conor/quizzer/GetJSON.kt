package com.conor.quizzer // Or your actual package name

import TutorialViewModel
import android.annotation.SuppressLint
import android.app.Application
import java.util.Locale as jLocale
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.text.util.Linkify
import android.view.View
import kotlinx.coroutines.delay
import android.widget.* // Import CheckBox
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.conor.quizzer.databinding.ActivityGetJsonBinding
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlin.text.take
import kotlinx.serialization.json.Json
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.combine
import kotlin.getValue
import androidx.core.view.isVisible

val numberOfThreads = 4 // Example
val customThreadPool: ExecutorService = Executors.newFixedThreadPool(numberOfThreads)
val customDispatcher: CoroutineDispatcher = customThreadPool.asCoroutineDispatcher()

@Serializable
data class GeminiResponse(
    val candidates: List<Candidate>? = null,
    // ... other fields like promptFeedback
)

@Serializable
data class Candidate(
    val content: GeminiContent? = null,
    // ... other fields like finishReason, safetyRatings
)

@Serializable
data class GeminiContent(
    val parts: List<Part>, // This is the list we're interested in
    val role: String? = null
)

@Serializable
data class Part(
    val text: String // The text content of each part
    // Potentially other fields in Part like 'inlineData', but we're focusing on 'text'
)

val jsonParser = Json {
    ignoreUnknownKeys = true // Ignore properties in JSON not present in your data class
    isLenient = true         // Be more tolerant of minor JSON format issues (e.g., trailing commas)
    coerceInputValues = true // Attempt to coerce incorrect types (e.g., "1" to 1) if possible
    prettyPrint = false      // Set to true if you need to generate formatted JSON strings for output
}


private const val DEFAULT_TIMEOUT_SECONDS = 300L
// Custom exception for HTTP errors (can be in its own file or here)
class HttpException(val code: Int, message: String) : IOException("HTTP $code: $message")
// NetworkClient class (can be an inner class, a separate file, or part of GetJSON if simple)
class NetworkClient {
    private val TAG_NC = "NetworkClient" // Different TAG for clarity

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor { message -> Log.d(TAG_NC, message) }
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            .build()
    }

    suspend fun sendPostRequestWithCustomHeader(
        urlString: String,
        jsonBody: String,
        customHeaderName: String,
        customHeaderValue: String
    ): String {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(urlString)
                .post(requestBody)
                .addHeader(customHeaderName, customHeaderValue)
                .build()

            val response: Response = client.newCall(request).execute()

            if (response.isSuccessful) {
                response.body?.string() ?: throw IOException("Successful response but empty body")
            } else {
                val errorBody = response.body?.string() ?: "No error body"
                Log.e(TAG_NC, "Request failed. Code: ${response.code}, Message: ${response.message}, Body: $errorBody")
                throw HttpException(response.code, "Request failed: ${response.code} - $errorBody")
            }
        }
    }
}


class GetJSON : AppCompatActivity() {

    private lateinit var binding: ActivityGetJsonBinding

    private val TAG = "GetJSON_Activity" // TAG for this activity

    private lateinit var apiKeyToggle: ToggleButton
    private lateinit var apiKeyLayout: LinearLayout
    private lateinit var apiKeyText: TextInputEditText
    private lateinit var advancedOptionsToggle: ToggleButton
    private lateinit var advancedOptionsLayout: LinearLayout
    private lateinit var extraPromptText: TextInputEditText
    private lateinit var submitButton: Button
    private lateinit var isExamSwitch: SwitchCompat
    private lateinit var quizOrStudySwitch: SwitchCompat
    private lateinit var notifyText: TextView
    private lateinit var numberOfQuestionsSeekBar: SeekBar
    private lateinit var numberOfQuestionsText: TextView
    private lateinit var numberOfQuestionsLabel: TextView
    private lateinit var numberOfTopicsSwitchLabel: TextView
    private lateinit var studyProgressBar: ProgressBar
    private lateinit var numberOfTopicsSeekBar: SeekBar
    private lateinit var numberOfTopicsText: TextView
    private lateinit var numberOfTopicsLabel: TextView
    private lateinit var numberOfTopicsSwitch: SwitchCompat
    private lateinit var apiKeyNoteLabel: TextView
    private lateinit var languageSpinner: Spinner

    private val tutorialViewModel: TutorialViewModel by viewModels()




    private lateinit var topicText: TextInputEditText

    private val PREFS_NAME = "QuizzerPrefs"
    private val API_KEY_PREF = "gemini_api_key"
    private val HAS_SEEN_NOTICE = "has_seen_notice"
    private val HAS_SEEN_API_TUTORIAL = "has_seen_api_tutorial"
    private lateinit var sharedPreferences: SharedPreferences

    private val networkClient = NetworkClient() // Instantiate NetworkClient here

    // For SAF (Storage Access Framework)
    private var currentJsonToSave: String? = null
    private val createFileLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    currentJsonToSave?.let { jsonString ->
                        writeJsonToUri(jsonString, uri) // Implement this function
                    }
                }
            }
            currentJsonToSave = null
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityGetJsonBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Initialize Views
        apiKeyToggle = findViewById(R.id.apiKeyToggle)
        apiKeyLayout = findViewById(R.id.apiKeyLayout)
        apiKeyText = findViewById(R.id.apiKeyText) // Use the corrected ID
        advancedOptionsToggle = findViewById(R.id.advancedToggle)
        advancedOptionsLayout = findViewById(R.id.advancedOptionsLayout)
        extraPromptText = findViewById(R.id.extraPromptText)
        submitButton = findViewById(R.id.submit)
        isExamSwitch = findViewById(R.id.isExamSwitch)
        quizOrStudySwitch = findViewById(R.id.quizOrStudySwitch)
        notifyText = findViewById(R.id.notifyText)
        numberOfQuestionsLabel = findViewById(R.id.numberOfQuestionsLabel)
        numberOfQuestionsText = findViewById(R.id.numberOfQuestionsText)
        numberOfQuestionsSeekBar = findViewById(R.id.numberOfQuestionsSeekBar)
        numberOfTopicsLabel = findViewById(R.id.numberOfTopicsLabel)
        numberOfTopicsSwitchLabel = findViewById(R.id.numberOfTopicsSwitchLabel)
        numberOfTopicsText = findViewById(R.id.numberOfTopicsText)
        numberOfTopicsSeekBar = findViewById(R.id.numberOfTopicsSeekBar)
        numberOfTopicsSwitch = findViewById(R.id.numberOfTopicsSwitch)
        apiKeyNoteLabel = findViewById(R.id.apiKeyNoteLabel)
        studyProgressBar = findViewById(R.id.studyProgressBar)
        languageSpinner = findViewById(R.id.languageSpinner)

        val languageSelector: List<String> = listOf("English", "Català", "Español", "Français", "Deutsch", "Italiano")


        val currentLocale: String = jLocale.getDefault().language

        languageSpinner.adapter = ArrayAdapter(this, R.layout.spinner_dropdown, languageSelector)

        var index=1
        for (localeCode in listOf("ca", "es", "fr", "de", "it")) {
            if (currentLocale == localeCode) {
                languageSpinner.setSelection(index)
                break
            }
            index++
        }


        topicText = findViewById(R.id.topicText)


        // Load any saved API key when the activity starts
        loadApiKey()

        //Update Study/Quiz text for clarity
        numberOfTopicsLabel.visibility = View.GONE
        numberOfTopicsText.visibility = View.GONE
        numberOfTopicsSeekBar.visibility = View.GONE
        numberOfTopicsSwitch.visibility= View.GONE
        numberOfTopicsSwitchLabel.visibility= View.GONE
        numberOfTopicsSwitch.text=this.getString(R.string.no)

        numberOfTopicsSeekBar.progress = 0
        numberOfTopicsText.text = "1"

        numberOfQuestionsSeekBar.progress=0
        numberOfQuestionsText.text="1"

        if (!sharedPreferences.getBoolean(HAS_SEEN_API_TUTORIAL, false)){
            apiKeyToggle.isChecked = true
            apiKeyLayout.visibility = View.VISIBLE

        }
        numberOfQuestionsSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Update the TextView to show the current progress
                val progressText = progress + 1
                numberOfQuestionsText.text = "$progressText"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // User has started interacting with the SeekBar
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }
        })

        numberOfTopicsSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Update the TextView to show the current progress
                val progressText = progress + 1
                numberOfTopicsText.text = "$progressText"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // User has started interacting with the SeekBar
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }
        })

        Linkify.addLinks(apiKeyNoteLabel, Linkify.WEB_URLS)

        quizOrStudySwitch.text = this.getString(R.string.quiz)
        quizOrStudySwitch.setOnCheckedChangeListener { _, isChecked ->
            quizOrStudySwitch.text = if (isChecked) this.getString(R.string.studyguide) else this.getString(R.string.quiz)
            if (isChecked) {
                numberOfQuestionsText.visibility = View.GONE
                numberOfQuestionsLabel.visibility = View.GONE
                numberOfQuestionsSeekBar.visibility = View.GONE
                numberOfTopicsSwitchLabel.visibility = View.VISIBLE
                numberOfTopicsSwitch.visibility = View.VISIBLE
                notifyText.text = this.getString(R.string.notice_generating_studyGuide)
                submitButton.text = this.getString(R.string.submit_button_getStudyGuide)
                if (numberOfTopicsSwitch.isChecked) {
                    numberOfTopicsLabel.visibility = View.VISIBLE
                    numberOfTopicsText.visibility = View.VISIBLE
                    numberOfTopicsSeekBar.visibility = View.VISIBLE
                    notifyText.text = ""
                }

            } else {
                numberOfQuestionsSeekBar.visibility = View.VISIBLE
                numberOfQuestionsText.visibility = View.VISIBLE
                numberOfQuestionsLabel.visibility = View.VISIBLE
                numberOfTopicsSwitch.visibility = View.GONE
                numberOfTopicsLabel.visibility = View.GONE
                numberOfTopicsText.visibility = View.GONE
                numberOfTopicsSeekBar.visibility = View.GONE
                numberOfTopicsSwitchLabel.visibility = View.GONE
                submitButton.text = this.getString(R.string.submit_button_getQuiz)
            }
        }
        numberOfTopicsSwitch.setOnCheckedChangeListener { _, isChecked ->
            numberOfTopicsSwitch.text = if (isChecked) "Yes" else "No"
            if (isChecked) {
                numberOfTopicsLabel.visibility = View.VISIBLE
                numberOfTopicsText.visibility = View.VISIBLE
                numberOfTopicsSeekBar.visibility = View.VISIBLE
            } else {
                numberOfTopicsLabel.visibility = View.GONE
                numberOfTopicsText.visibility = View.GONE
                numberOfTopicsSeekBar.visibility = View.GONE
            }
        }


        // ToggleButton click listener
        if (!apiKeyToggle.isChecked) {
            apiKeyLayout.visibility = View.GONE
        } else {
            apiKeyLayout.visibility = View.VISIBLE
        }
        apiKeyToggle.setOnCheckedChangeListener { _, isChecked ->
            apiKeyLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        if (!advancedOptionsToggle.isChecked) {
            advancedOptionsLayout.visibility = View.GONE
        } else {
            advancedOptionsLayout.visibility = View.VISIBLE
        }
        advancedOptionsToggle.setOnCheckedChangeListener { _, isChecked ->
            advancedOptionsLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Submit button click listener
        submitButton.setOnClickListener {
            if ((topicText.text.toString().trim().length > 0) && (apiKeyText.text.toString().trim().length > 0 )) {
                notifyText.text = if (quizOrStudySwitch.isChecked) {
                    this.getString(R.string.studyguide_generating)
                } else {
                    this.getString(R.string.flashcard_generating)
                }
                processApiRequest(this) // Call the method that uses networkClient
            }
            else if (topicText.text.toString().trim().length == 0) {
                Toast.makeText(this, R.string.no_topic_toast, Toast.LENGTH_SHORT).show()
                topicText.requestFocus()
            }
            else if (apiKeyText.text.toString().trim().length == 0){
                if (!apiKeyToggle.isChecked){
                    apiKeyToggle.isChecked = true
                    apiKeyLayout.callOnClick()
                }
                apiKeyText.requestFocus()
                Toast.makeText(this, R.string.no_api_key_toast, Toast.LENGTH_SHORT).show()
            }
        }
        loadHasSeenNotice(this)

        val tutorialComposeView = findViewById<ComposeView>(R.id.tutorial_get_json_compose_view)

        // Observe tutorial state from ViewModel
        lifecycleScope.launch {
            combine(
                tutorialViewModel.isTutorialActive,
                tutorialViewModel.isTutorialFinished,
                tutorialViewModel.currentStepIndex
            ) { isActive, isFinished, stepIndex -> // isActive, isFinished, stepIndex are the latest values from the flows
                Log.d("TutorialDebug", "GETJSON: combine block executing. isActive=$isActive, isFinished=$isFinished, stepIndex=$stepIndex")

                val currentStepObject = if (isActive) tutorialViewModel.currentTutorialStep else null
                Log.d("TutorialDebug", "GETJSON: combine -> currentStep for UI: ${currentStepObject?.id}")

                if (isFinished) {
                    // Handle side effects of finishing the tutorial *before* emitting the state
                    // It's generally better to handle such side-effects in the collector
                    // or in response to state changes rather than directly in the combine's transform.
                    // However, if you must do it here, ensure you still return the correct state object.
                    tutorialViewModel.skipTutorial() // This might trigger further state changes
                    // The UI update for tutorialComposeView.visibility should happen in collect
                    sharedPreferences.edit(commit = true) {
                        putBoolean(
                            HAS_SEEN_API_TUTORIAL,
                            true
                        )
                    }
                    TutorialCombinedState(isActive = false, isFinished = true, currentStep = null) // Emit a state reflecting it's finished
                } else if (isActive) {
                    TutorialCombinedState(isActive = true, isFinished = false, currentStep = currentStepObject)
                } else {
                    TutorialCombinedState(isActive = false, isFinished = false, currentStep = null)
                }
            }.collect { combinedState -> // Now 'combinedState' is of type TutorialCombinedState
                Log.d("TutorialDebug", "GETJSON: collect block executing. combinedState=$combinedState")

                if (combinedState.isFinished) { // Check the isFinished flag from the combined state
                    tutorialComposeView.visibility = View.GONE
                    // Potentially navigate away or perform other cleanup
                } else if (combinedState.isActive) {
                    tutorialComposeView.setContent {
                        MaterialTheme {
                            TutorialStepOverlay(
                                step = combinedState.currentStep,
                                onNext = { tutorialViewModel.nextStep() },
                                onSkip = { tutorialViewModel.skipTutorial() },
                                context = this@GetJSON
                            )
                        }
                    }
                    tutorialComposeView.visibility = View.VISIBLE
                } else {
                    tutorialComposeView.visibility = View.GONE
                }
            }
        }
        if (!sharedPreferences.getBoolean(HAS_SEEN_API_TUTORIAL, false)) {
            // It's crucial that views are laid out before calculating their coordinates.
            // Using viewTreeObserver or post for all targets can be complex for multi-step.
            // A common approach for multi-step view-based tutorials is to calculate
            // coordinates for the *current* step just before showing it, or have a slight delay.

            // For simplicity, let's assume we can get them.
            // A more robust way might be to pass lambdas to get coordinates just-in-time.
            binding.root.post { // Post on a root view or a common parent
                val steps = mutableListOf<TutorialStep>()

                // Step 1: Highlight 'getstarted' button
                val apiKeyNoteRect = calculateRectForOverlay(binding.getJsonContents.apiKeyNoteLabel)
                val apiKeyInputRect = calculateRectForOverlay(binding.getJsonContents.apiKeyText)
                val apiKeyToggleRect = calculateRectForOverlay(binding.getJsonContents.apiKeyToggle)

                steps.add(
                    TutorialStep(
                        id = "step0_intro",
                        text = this.getString(R.string.tutorial_1),
                        targetCoordinates = null
                    )
                )
                if (apiKeyNoteRect != null) {
                    steps.add(
                        TutorialStep(
                            id = "step1_getstarted",
                            text = this.getString(R.string.tutorial_2),
                            targetCoordinates = apiKeyNoteRect
                        )
                    )
                }
                if (apiKeyInputRect != null) {
                    steps.add(
                        TutorialStep(
                            id = "step2_getstarted",
                            text = this.getString(R.string.tutorial_3),
                            targetCoordinates = apiKeyInputRect
                        )
                    )
                }

                if (apiKeyToggleRect != null) {
                    steps.add(
                        TutorialStep(
                            id = "step3_getstarted",
                            text = this.getString((R.string.tutorial_4)),
                            targetCoordinates = apiKeyToggleRect
                        )
                    )
                }


                if (steps.isNotEmpty()) {
                    tutorialViewModel.initializeTutorial(steps)
                } else {
                    sharedPreferences.edit(commit = true) { putBoolean(HAS_SEEN_API_TUTORIAL, true) } // No steps, mark as seen
                }
            }
        }

    }


    // This is the function that uses the networkClient instance



    @Composable
    fun TutorialStepOverlay(
        // Renamed or kept, depending on preference
        step: TutorialStep?, // Current step to display, null if tutorial is not active
        onNext: () -> Unit,
        onSkip: () -> Unit,
        context: Context
        // You might also pass a modifier if needed from the caller
    ) {
        if (step == null) {
            // If step is null, tutorial is not active or has no current step, so draw nothing
            return
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.99f) // For BlendMode.Clear
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = Color.Black.copy(alpha = 0.7f))
                step.targetCoordinates?.let {
                    drawRect(
                        color = Color.Transparent,
                        topLeft = it.topLeft,
                        size = it.size,
                        blendMode = BlendMode.Clear
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter) // Or dynamically position based on step.targetCoordinates
                    .padding(16.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(48.dp)
            ) {
                Text(step.text)
                Spacer(modifier = Modifier.height(16.dp))
                Row {
                    androidx.compose.material3.Button(onClick = onSkip) { Text(context.getString(R.string.skip)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Change "Next" to "Finish" on the last step (optional)
                    val nextButtonText = if (/* Logic to check if it's the last step */ true /* Replace */) context.getString(R.string.next) else context.getString(R.string.finish)
                    androidx.compose.material3.Button(onClick = onNext) { Text(nextButtonText) }
                }
            }
        }
    }

    private suspend fun asyncAiCall(topic: String, geminiUrlWithKey: String, requestJsonPayload: String) : String{
        Log.d(TAG, "Attempting to send request to: $geminiUrlWithKey")
        Log.d(TAG, "Request payload: $requestJsonPayload")
        var aiResponseJson = ""
        var trycount=0
        var responseText=""
        val listType = object : TypeToken<TopicList>() {}.type
        while (true) {

            try {
                // NOW it can find networkClient.sendPostRequestWithCustomHeader
                aiResponseJson = networkClient.sendPostRequestWithCustomHeader(
                    urlString = geminiUrlWithKey,
                    jsonBody = requestJsonPayload,
                    customHeaderName = "Content-Type", // For Gemini API, key is in URL
                    customHeaderValue = "application/json".toString()
                )
                val geminiApiResponse = jsonParser.decodeFromString<GeminiResponse>(aiResponseJson)
                for (i in geminiApiResponse.candidates!!){
                    for (j in i.content!!.parts){
                        responseText+=j.text
                    }
                }
                break
            }
            catch (e: Exception) {
                trycount+=1
                Log.d("Issue with response for ${topic}, trying again in 5 seconds", e.toString())
                delay(5000L+ (5000L*trycount))
                if (trycount> 60) {
                    break
                    Log.d("Tried $trycount times. Giving up.", e.toString())

                }
            }
        }
        return(responseText)
    }

    @SuppressLint("DiscouragedApi")
    fun getStatusBarHeight(context: Context): Int {
        var result = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    fun calculateRectForOverlay(targetView: View): Rect? {
        val locationInWindow = IntArray(2)

        val statusBarHeight = getStatusBarHeight(this)

        targetView.getLocationInWindow(locationInWindow) // Gets top-left (x,y) relative to the window

        val left = locationInWindow[0].toFloat()
        val top = locationInWindow[1].toFloat() - statusBarHeight
        val right = (locationInWindow[0] + targetView.width).toFloat()
        val bottom = (locationInWindow[1] + targetView.height).toFloat() - statusBarHeight

        // Ensure the view has valid dimensions
        if (targetView.width > 0 && targetView.height > 0) {
            var calculatedRectForOverlay = androidx.compose.ui.geometry.Rect(left, top, right, bottom)
            Log.d("TutorialDebug", "Using view's window bounds: $calculatedRectForOverlay")
            return calculatedRectForOverlay
        } else {
            Log.w("TutorialDebug", "'getstarted' view has zero width/height. Cannot determine bounds.")
            return null
        }
    }

    private fun buildStudyJson(topic: String, subtopics: MutableMap<String, String>): JSONObject{
        var root=JSONObject()
        root.put("subject", topic)
        var subtopicsObj= JSONArray()
        for (subtopic in subtopics.keys){
            var subtopicObj= JSONObject()
            subtopicObj.put("name", subtopic)
            subtopicObj.put("text", subtopics[subtopic])
            subtopicsObj.put(subtopicObj)
        }
        root.put("topics", subtopicsObj)
        return root

    }
    private fun processApiRequest(context: Context) {
        val languagesForPrompt: List<String> = listOf("English", "Catalan", "Castilian Spanish", "French", "German", "Italian")

        val topic = topicText.text.toString().trim()
        val numQuestionsStr = numberOfQuestionsText.text.toString().trim()
        var currentApiKey = ""

        var languagePrompt = "Write it in the " + languagesForPrompt.get(languageSpinner.selectedItemPosition) + " language."

        if (apiKeyLayout.isVisible) {
            currentApiKey = apiKeyText.text.toString().trim()
            if (currentApiKey.isNotEmpty()) {
                saveApiKey(currentApiKey)
            } else {
                Toast.makeText(this, this.getString(R.string.error_apiKeyEmpty), Toast.LENGTH_SHORT).show()
            }
        } else {
            currentApiKey = sharedPreferences.getString(API_KEY_PREF, "") ?: ""
        }

        if (topic.isEmpty()) {
            topicText.error = this.getString(R.string.error_topicNameEmpty)
            return
        }

        if (currentApiKey.isEmpty()) {
            Toast.makeText(this, this.getString(R.string.apikeymissing), Toast.LENGTH_LONG).show()
            if (apiKeyLayout.visibility != View.VISIBLE) {
                apiKeyToggle.isChecked = true
            }
            apiKeyText.requestFocus()
            return
        }
        var additionalPrompt = extraPromptText.text.toString().trim()
        if (additionalPrompt != "" && !additionalPrompt.endsWith("."))  {
            additionalPrompt += "."
        }
        if (!quizOrStudySwitch.isChecked) {
            val promptText = if (isExamSwitch.isChecked) {
                "Find ${numQuestionsStr} questions for the ${topic} qualification and create a mock exam. ${additionalPrompt} ${languagePrompt} Provide only the questions. Provide detailed explanations as to why an answer is correct. Ensure that the questions are evenly split among all disciplines within the exam."
            } else {
                "Find ${numQuestionsStr} questions on the topic of ${topic} and create a quiz. ${additionalPrompt} ${languagePrompt} Provide only the questions. Provide detailed explanations as to why an answer is correct. Ensure that the questions are evenly split among all disciplines within the exam."
            }
            val generationConfig = createQuizGenerationObject()
            val requestJsonPayload = createJsonWithContentKey(promptText, generationConfig)

            if (requestJsonPayload == null) {
                Toast.makeText(this, "Failed to create JSON payload.", Toast.LENGTH_SHORT).show()
                return
            }

            val apiUrl =
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
            val geminiUrlWithKey = "$apiUrl?key=$currentApiKey"
            submitButton.isEnabled = false
            var jsonStringToSaveForSAF: String? = null

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    Log.d(TAG, "Attempting to send request to: $geminiUrlWithKey")
                    Log.d(TAG, "Request payload: $requestJsonPayload")

                    // NOW it can find networkClient.sendPostRequestWithCustomHeader
                    val aiResponseJson = networkClient.sendPostRequestWithCustomHeader(
                        urlString = geminiUrlWithKey,
                        jsonBody = requestJsonPayload,
                        customHeaderName = "Content-Type", // For Gemini API, key is in URL
                        customHeaderValue = "application/json".toString()
                    )
                    //val parsedQuestions: List<QuestionItem> = Gson().fromJson(aiResponseJson, listType)
                    Log.d(TAG, "API Success. Response: $aiResponseJson")
                    Toast.makeText(this@GetJSON, context.getString(R.string.quiz_gen_successful), Toast.LENGTH_SHORT)
                        .show()

                    var concatenatedTextFromParts: String? = null

                    try {
                        // Step 1: Parse the outer Gemini API response structure
                        val geminiApiResponse =
                            jsonParser.decodeFromString<GeminiResponse>(aiResponseJson)

                        // Step 2: Navigate to the 'parts' and concatenate their 'text'
                        // We'll concatenate parts from the first candidate, assuming it's the primary response.
                        val partsList = geminiApiResponse.candidates?.firstOrNull()?.content?.parts

                        if (partsList.isNullOrEmpty()) {
                            Log.w(
                                TAG,
                                "No 'parts' found in the AI response or candidate list is empty."
                            )
                            // Decide how to handle this: maybe the response is in a different format,
                            // or it's an error response that doesn't have parts.
                            // For now, we might try to see if the raw response itself is parsable as a Quiz.
                            // This is a fallback and depends on how the AI might respond on errors.
                        } else {
                            // Concatenate text from all parts
                            val stringBuilder = StringBuilder()
                            for (part in partsList) {
                                stringBuilder.append(part.text)
                            }
                            concatenatedTextFromParts = stringBuilder.toString()
                            Log.i(
                                TAG,
                                "Successfully concatenated text from ${partsList.size} parts."
                            )
                            Log.d(
                                TAG,
                                "Concatenated Text (first 500 chars): ${
                                    concatenatedTextFromParts?.take(500)
                                }"
                            )
                        }

                        // Step 3: Now, attempt to parse the 'concatenatedTextFromParts' as your Quiz JSON
                        if (concatenatedTextFromParts != null) {
                            jsonStringToSaveForSAF =
                                concatenatedTextFromParts // This is the clean, combined JSON
                            val listType: Type = object : TypeToken<List<QuestionItem>>() {}.type
                            val parsedQuestions: List<QuestionItem> =
                                Gson().fromJson(concatenatedTextFromParts, listType)
                            Log.d(TAG, "Successfully parsed ${parsedQuestions.size} questions.")
                        } else if (jsonStringToSaveForSAF == null || jsonStringToSaveForSAF == "[]" ) { // Only if not already set by the fallback above
                            Log.e(
                                TAG,
                                "Concatenated text was null, and no fallback was set. Cannot proceed to parse Quiz."
                            )
                            Toast.makeText(
                                this@GetJSON,
                                "AI Error: No content to parse. It may have been blocked by content filters",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                    } catch (e: kotlinx.serialization.SerializationException) {
                        Log.e(
                            TAG,
                            "JSON Parsing Error (either outer GeminiResponse or inner Quiz): ${e.message}"
                        )
                        if (concatenatedTextFromParts != null) {
                            Log.e(
                                TAG,
                                "Problematic Concatenated Text (if parsing Quiz failed): ${
                                    concatenatedTextFromParts.take(1000)
                                }"
                            )
                        }
                        Toast.makeText(
                            this@GetJSON,
                            "Error: Generated content invalid or format mismatch.",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected error during parsing/concatenation: ${e.message}", e)
                        Toast.makeText(
                            this@GetJSON,
                            "Error: Problem processing AI response.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    currentJsonToSave = jsonStringToSaveForSAF

                    createJsonFileIntent("${topic}_quiz_${System.currentTimeMillis()}.json")

                } catch (e: HttpException) {
                    Log.e(TAG, "API HTTP Error ${e.code}: ${e.message}", e)
                    Toast.makeText(
                        this@GetJSON,
                        "API Error (${e.code}): ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: IOException) {
                    Log.e(TAG, "Network Error: ${e.message}", e)
                    Toast.makeText(
                        this@GetJSON,
                        "Network Error: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected Error: ${e.message}", e)
                    Toast.makeText(
                        this@GetJSON,
                        "An unexpected error occurred: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    notifyText.text = ""
                    submitButton.isEnabled = true
                }
            }
        } else {
            var promptText = if (isExamSwitch.isChecked) {
                "Give me a complete list of all of the topics covered in a $topic exam. $languagePrompt"
            } else {
                "Give me a comprehensive list of all of the topics related to the subject of \"${topic}\". $languagePrompt"
            }
            if (numberOfTopicsSwitch.isChecked){
                promptText += " Limit the number of topics to ${numberOfTopicsText.text}."
            }
            val generationConfig = createStudyGenerationObject()
            val requestJsonPayload = createJsonWithContentKey(promptText, generationConfig)

            if (requestJsonPayload == null) {
                Toast.makeText(this, "Failed to create JSON payload.", Toast.LENGTH_SHORT).show()
                return
            }

            val apiUrl =
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
            val geminiUrlWithKey = "$apiUrl?key=$currentApiKey"
            if (quizOrStudySwitch.isChecked) {
                Toast.makeText(this, this.getString(R.string.generating_study_guide), Toast.LENGTH_LONG).show()
            }
            else {
                Toast.makeText(this, this.getString(R.string.generating_questions), Toast.LENGTH_LONG).show()
            }
            submitButton.isEnabled = false
            var jsonStringToSaveForSAF: String? = null
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    Log.d(TAG, "Attempting to send request to: $geminiUrlWithKey")
                    Log.d(TAG, "Request payload: $requestJsonPayload")
                    var aiResponseJson = ""
                    var trycount=0
                    var topicList: TopicList
                    val listType = object : TypeToken<TopicList>() {}.type
                    var textResponse = ""
                    var deferredResultArray : MutableMap<String, Deferred<String>> = hashMapOf()
                    var resultArray  : MutableMap<String, String> = hashMapOf()
                    while (true) {

                        try {
                            // NOW it can find networkClient.sendPostRequestWithCustomHeader
                            aiResponseJson = networkClient.sendPostRequestWithCustomHeader(
                                urlString = geminiUrlWithKey,
                                jsonBody = requestJsonPayload,
                                customHeaderName = "Content-Type", // For Gemini API, key is in URL
                                customHeaderValue = "application/json".toString()
                            )
                            val geminiApiResponse =
                                jsonParser.decodeFromString<GeminiResponse>(aiResponseJson)
                            for (i in geminiApiResponse.candidates!!){
                                for (j in i.content!!.parts) {

                                    textResponse += j.text
                                }
                            }
                            topicList = Gson().fromJson(textResponse, listType)
                            break
                        }
                        catch (e: Exception) {
                            trycount+=1
                            Log.d("Issue with response, trying again in 5 seconds", e.toString())
                            delay(5000L)
                            if (trycount> 9) {
                                break
                                Log.d("Tried ${trycount} times. Giving up.", e.toString())

                            }
                        }
                    }
                    Log.d(TAG, "API Success. Response: $aiResponseJson")
                    topicList = Gson().fromJson(textResponse, listType)
                    val topicCount=topicList.topic_names.size
                    studyProgressBar.max=topicCount
                    studyProgressBar.progress=0
                    studyProgressBar.visibility=View.VISIBLE



                    try {
                        for (i in 0..topicCount-1) {
                            val subtopic = topicList.topic_names[i]
                            val promptValue = if (isExamSwitch.isChecked) {
                                "Provide me a detailed guide about the topic \"${subtopic}\" in relation to the \"${topic}\" exam. ${additionalPrompt}. Output in Markdown and output only the guide. $languagePrompt Remove any sort of introduction or top-level heading."
                            } else {
                                "Provide me a detailed guide about the topic \"${subtopic}\" in relation to the subject of \"${topic}\". ${additionalPrompt}. Output in Markdown and output only the guide. $languagePrompt Remove any sort of introduction or top-level heading."
                            }
                            val jsonValue = createJsonWithContentKey(promptValue)
                            deferredResultArray[subtopic]=async {
                                asyncAiCall(subtopic, geminiUrlWithKey, jsonValue.toString())
                            }

                        }
                        if (deferredResultArray.isNotEmpty()) {
                            Log.d(TAG, "deferredResultArray has ${deferredResultArray.size} items. Awaiting results...")
                            for (deferredResultKey in deferredResultArray.keys) { // Iterate over the Deferred objects
                                try {
                                    val deferredResult = deferredResultArray[deferredResultKey]!! // Get the Deferred object
                                    Log.d(TAG, "Awaiting result for key: $deferredResultKey")
                                    val resultValue = deferredResult.await() // Get the actual String result
                                    val progress= studyProgressBar.progress
                                    studyProgressBar.progress = progress + 1
                                    Log.d(TAG, "Progress: ${studyProgressBar.progress}/$topicCount")
                                    resultArray[deferredResultKey]=resultValue // Add the String to resultArray
                                    Log.d(TAG, "Added to resultArray: $resultValue")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error awaiting a deferred result: ${e.message}", e)
                                    // Decide how to handle individual errors: add a placeholder, skip, etc.
                                    // resultArray.add("Error: ${e.localizedMessage}") // Example: add error message
                                }
                            }
                            Log.d(TAG, "resultArray now has ${resultArray.size} items.")
                        } else {
                            Log.d(TAG, "deferredResultArray is empty, so resultArray will also be empty.")
                        }
                    } catch (e: Exception) {
                        println("Main (${Thread.currentThread().name}): An error occurred: ${e.message}")
                        // Handle exceptions from the async blocks here
                    } finally {
                        // IMPORTANT: Shut down your ExecutorService when you're done with it
                        // to allow threads to terminate and resources to be released.
                        // If the customDispatcher is derived from customThreadPool,
                        // closing the dispatcher also closes the underlying executor.
                        // (customDispatcher as? Closeable)?.close() // Alternative if you only have dispatcher
                        customThreadPool.shutdown()
                        println("Main (${Thread.currentThread().name}): Custom thread pool has been shut down.")
                        // For a clean exit in this example, you might want to wait for termination:
                        // if (!customThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                        //     customThreadPool.shutdownNow()
                        // }
                    }
                    var outputJson = buildStudyJson(topic, resultArray)
                    var outputJsonString = outputJson.toString(4)
                    if (!outputJsonString.isNullOrEmpty() && outputJsonString != "[]") {


                        currentJsonToSave = outputJsonString
                        createJsonFileIntent("${topic}_studyguide_${System.currentTimeMillis()}.json")

                    }
                    studyProgressBar.visibility = View.GONE

                } catch (e: HttpException) {
                    Log.e(TAG, "API HTTP Error ${e.code}: ${e.message}", e)
                    Toast.makeText(
                        this@GetJSON,
                        "API Error (${e.code}): ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: IOException) {
                    Log.e(TAG, "Network Error: ${e.message}", e)
                    Toast.makeText(
                        this@GetJSON,
                        "Network Error: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected Error: ${e.message}", e)
                    Toast.makeText(
                        this@GetJSON,
                        "An unexpected error occurred: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    notifyText.text = ""
                    submitButton.isEnabled = true
                }
            }
        }
    }

    fun createQuizGenerationObject(): JSONObject  {
        val generationConfig = JSONObject()

        // 2.1. "thinkingConfig" object
        val thinkingConfig = JSONObject()
        thinkingConfig.put("thinkingBudget", 8000)
        generationConfig.put("thinkingConfig", thinkingConfig)

        // 2.2. "responseMimeType"
        generationConfig.put("responseMimeType", "application/json")

        // 2.3. "responseSchema" object
        val responseSchema = JSONObject()
        responseSchema.put("type", "array")

        val itemsObject = JSONObject()
        itemsObject.put("type", "object")

        val propertiesObject = JSONObject()

        // Define properties for each field in the response items
        propertiesObject.put("question_number", JSONObject().put("type", "string"))
        propertiesObject.put("question", JSONObject().put("type", "string"))
        propertiesObject.put("domain", JSONObject().put("type", "string"))

        val choicesProperties = JSONObject()
        choicesProperties.put("A", JSONObject().put("type", "string"))
        choicesProperties.put("B", JSONObject().put("type", "string"))
        choicesProperties.put("C", JSONObject().put("type", "string"))
        choicesProperties.put("D", JSONObject().put("type", "string"))
        propertiesObject.put("choices", JSONObject().put("type", "object").put("properties", choicesProperties))
        val correctAnswersObject = JSONObject()
        correctAnswersObject.put("type", "array")
        correctAnswersObject.put("items", JSONObject().put("type", "string"))
        propertiesObject.put("correct_answer", correctAnswersObject) // Assuming single string for simplicity

        // If it can be an array of strings, type would be "array"
        // and you'd add an "items" schema for string.
        propertiesObject.put("explanation", JSONObject().put("type", "string"))

        itemsObject.put("properties", propertiesObject)

        // "required" array for items
        val requiredArray = JSONArray()
        requiredArray.put("question")
        requiredArray.put("domain")
        requiredArray.put("choices")
        requiredArray.put("correct_answer")
        requiredArray.put("explanation")
        // Note: "question_number" is not in your required list, which is fine if it's optional.
        itemsObject.put("required", requiredArray)

        responseSchema.put("items", itemsObject)
        generationConfig.put("responseSchema", responseSchema)
        return(generationConfig)
    }

    fun createStudyGenerationObject(): JSONObject  {
        val generationConfig = JSONObject()

        // 2.1. "thinkingConfig" object
        val thinkingConfig = JSONObject()
        thinkingConfig.put("thinkingBudget", -1)
        generationConfig.put("thinkingConfig", thinkingConfig)

        // 2.2. "responseMimeType"
        generationConfig.put("responseMimeType", "application/json")

        // 2.3. "responseSchema" object
        val responseSchema = JSONObject()
        responseSchema.put("type", "object")

        val propertiesObject = JSONObject()
        val topicNamesObject = JSONObject()
        topicNamesObject.put("type", "array")
        val itemsObject= JSONObject()
        itemsObject.put("type", "string")
        topicNamesObject.put("items", itemsObject)
        propertiesObject.put("topic_names", topicNamesObject)
        responseSchema.put("properties", propertiesObject)

        generationConfig.put("responseSchema", responseSchema)
        return(generationConfig)
    }

    // Your createJsonWithContentKey function (from previous example)
    fun createJsonWithContentKey(promptText: String, generationConfig : JSONObject = JSONObject()): String? {
        try {
            val root = JSONObject()

            // 1. "contents" array
            val contentsArray = JSONArray()
            val contentObject = JSONObject() // The single object within the "contents" array
            contentObject.put("role", "user")

            val partsArray = JSONArray()
            val partObject = JSONObject()
            partObject.put("text", promptText) // Use the input parameter here
            partsArray.put(partObject)

            contentObject.put("parts", partsArray)
            contentsArray.put(contentObject)
            root.put("contents", contentsArray)

            // 2. "generationConfig" object

            if (generationConfig.length() != 0){
                root.put("generationConfig", generationConfig)
            }

            return root.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating JSON with content key: ${e.message}")
            return null
        }
    }


    private fun saveApiKey(apiKey: String) {
        sharedPreferences.edit {
            putString(API_KEY_PREF, apiKey)
        }
        Log.d(TAG, "API Key saved to SharedPreferences.")
    }

    private fun loadHasSeenNotice(context: Context) {
        val hasSeenNotice = sharedPreferences.getBoolean(HAS_SEEN_NOTICE, false)
        if (!hasSeenNotice) {
            Log.d(TAG, "Notice not shown yet. Displaying now")
            val builder =
                AlertDialog.Builder(context) // For Material theme: MaterialAlertDialogBuilder(context)
            builder.setTitle(R.string.notice_title) // Or "Important Update", "New Feature", etc.
            builder.setMessage(R.string.notice_text)
            builder.setPositiveButton(R.string.notice_confirm) { dialog, _ ->
                // Mark the notice as shown
                sharedPreferences.edit {
                    putBoolean(HAS_SEEN_NOTICE, true)
                // Use apply() for asynchronous save, commit() for synchronous
                 }
                dialog.dismiss()
            }
            builder.setCancelable(false) // Optional: Prevent dismissing by tapping outside or back button until interacted with

            val dialog = builder.create()
            dialog.show()
        }
    }

    private fun loadApiKey() {
        val savedApiKey = sharedPreferences.getString(API_KEY_PREF, null)
        if (savedApiKey != null) {
            apiKeyText.setText(savedApiKey)
            Log.d(TAG, "API Key loaded from SharedPreferences.")
        } else {
            Log.d(TAG, "No API Key found in SharedPreferences.")
        }
    }

    // SAF related functions (implement or adapt from previous SAF example)
    private fun createJsonFileIntent(defaultFileName: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, defaultFileName)
        }
        try {
            createFileLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not launch ACTION_CREATE_DOCUMENT", e)
            Toast.makeText(this, "Error: Cannot open file saver. ${e.message}", Toast.LENGTH_LONG).show()
            currentJsonToSave = null
        }
    }

    private fun writeJsonToUri(jsonString: String, uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray())
                outputStream.flush()
                Log.i(TAG, "JSON saved successfully to URI: $uri")
                Toast.makeText(this, this.getString(R.string.quiz_saved_successfully), Toast.LENGTH_LONG).show()
            } ?: throw IOException("Failed to get OutputStream for URI: $uri")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write JSON to URI: $uri", e)
            Toast.makeText(this, "Error saving quiz: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    fun abbreviateString(input: String, startCharsToRemove: Int, endCharsToRemove: Int): String {
        // Calculate the length of the string
        val length = input.length

        // Calculate the start index for the substring
        // Ensure it doesn't go below 0 (if startCharsToRemove is too large)
        val startIndex = minOf(startCharsToRemove, length)

        // Calculate the end index for the substring (exclusive)
        // Ensure it doesn't go beyond the string length or cross the startIndex
        val endIndex = maxOf(startIndex, length - endCharsToRemove)

        // Check if the resulting substring would be empty or invalid (start index >= end index)
        if (startIndex >= endIndex) {
            return "" // Return an empty string if the operation results in an invalid range
        }

        return input.substring(startIndex, endIndex)
    }

}