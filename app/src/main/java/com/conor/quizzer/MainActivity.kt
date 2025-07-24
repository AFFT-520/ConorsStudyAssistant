
package com.conor.quizzer

import TutorialViewModel
import android.app.Activity
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.collect
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.combine
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.copy
import androidx.compose.animation.core.isFinished
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import android.graphics.Rect as AndroidRect // Using an import alias for clarity
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.dp
import com.conor.quizzer.Questioner
import com.conor.quizzer.databinding.ActivityMainBinding // Ensure this matches your package
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.lang.reflect.Type
import kotlin.concurrent.write
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope

object CacheConstants {
    const val CACHED_RESULTS_FILENAME = "tmp.txt" // Or .json if you save as JSON
    const val EXTRA_CACHE_FILENAME = "com.conor.quizzer.EXTRA_CACHE_FILENAME"
}

data class TutorialCombinedState(
    val isActive: Boolean,
    val isFinished: Boolean, // Add this to the state
    val currentStep: TutorialStep?
)
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var generateQuizButton: Button
    private val tutorialViewModel: TutorialViewModel by viewModels()
    private val TAG = "MainActivity"

    // Modern way to handle Activity Results
    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    // The URI of the selected file is in 'uri'
                    handleSelectedFileQuiz(this, uri)
                } ?: run {
                    Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "File picking cancelled", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        generateQuizButton = findViewById(R.id.generateButton)
        generateQuizButton.setOnClickListener {
            // Create an Intent to start GetJSON activity
            val intent = Intent(this, GetJSON::class.java)
            startActivity(intent)
        }
        binding.getstarted.setOnClickListener {
            openFilePicker()
        }
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        val tutorialComposeView = findViewById<ComposeView>(R.id.tutorial_compose_view)

        // Observe tutorial state from ViewModel
        lifecycleScope.launch {
            combine(
                tutorialViewModel.isTutorialActive,
                tutorialViewModel.isTutorialFinished,
                tutorialViewModel.currentStepIndex
            ) { isActive, isFinished, stepIndex -> // isActive, isFinished, stepIndex are the latest values from the flows
                Log.d("TutorialDebug", "MAIN: combine block executing. isActive=$isActive, isFinished=$isFinished, stepIndex=$stepIndex")

                val currentStepObject = if (isActive) tutorialViewModel.currentTutorialStep else null
                Log.d("TutorialDebug", "MAIN: combine -> currentStep for UI: ${currentStepObject?.id}")

                if (isFinished) {
                    // Handle side effects of finishing the tutorial *before* emitting the state
                    // It's generally better to handle such side-effects in the collector
                    // or in response to state changes rather than directly in the combine's transform.
                    // However, if you must do it here, ensure you still return the correct state object.
                    tutorialViewModel.skipTutorial() // This might trigger further state changes
                    // The UI update for tutorialComposeView.visibility should happen in collect
                    prefs.edit(commit = true) { putBoolean("has_seen_tutorial", true) }
                    TutorialCombinedState(isActive = false, isFinished = true, currentStep = null) // Emit a state reflecting it's finished
                } else if (isActive) {
                    TutorialCombinedState(isActive = true, isFinished = false, currentStep = currentStepObject)
                } else {
                    TutorialCombinedState(isActive = false, isFinished = false, currentStep = null)
                }
            }.collect { combinedState -> // Now 'combinedState' is of type TutorialCombinedState
                Log.d("TutorialDebug", "MAIN: collect block executing. combinedState=$combinedState")

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
                                context = this@MainActivity
                            )
                        }
                    }
                    tutorialComposeView.visibility = View.VISIBLE
                } else {
                    tutorialComposeView.visibility = View.GONE
                }
            }
        }
            if (!prefs.getBoolean("has_seen_tutorial", false)) {
                // It's crucial that views are laid out before calculating their coordinates.
                // Using viewTreeObserver or post for all targets can be complex for multi-step.
                // A common approach for multi-step view-based tutorials is to calculate
                // coordinates for the *current* step just before showing it, or have a slight delay.

                // For simplicity, let's assume we can get them.
                // A more robust way might be to pass lambdas to get coordinates just-in-time.
                binding.root.post { // Post on a root view or a common parent
                    val steps = mutableListOf<TutorialStep>()

                    // Step 1: Highlight 'getstarted' button
                    val generateButtonCoords = calculateRectForOverlay(binding.generateButton)
                    steps.add(
                        TutorialStep(
                            id = "step0_intro",
                            text = this.getString(R.string.main_tutorial1),
                            targetCoordinates = null
                        )
                    )
                    if (generateButtonCoords != null) {
                        steps.add(
                            TutorialStep(
                                id = "step1_getstarted",
                                text = this.getString(R.string.main_tutorial2),
                                targetCoordinates = generateButtonCoords
                            )
                        )
                    }

                    // Step 2: Highlight 'generateButton'
                    val getStartedCoords = calculateRectForOverlay(binding.getstarted)
                    if (getStartedCoords != null) {
                        steps.add(
                            TutorialStep(
                                id = "step2_generate",
                                text = this.getString(R.string.main_tutorial3),
                                targetCoordinates = getStartedCoords
                            )
                        )
                    }

                    // Step 3: No specific highlight, just an info message
                    steps.add(
                        TutorialStep(
                            id = "step3_info",
                            text = this.getString(R.string.main_tutorial4),
                            targetCoordinates = null // No specific highlight
                        )
                    )

                    if (steps.isNotEmpty()) {
                        tutorialViewModel.initializeTutorial(steps)
                    } else {
                        prefs.edit(commit = true) { putBoolean("has_seen_tutorial", true) } // No steps, mark as seen
                    }
                }
            }



    }

    fun AndroidRect?.toNullableComposeRect(): ComposeRect? {
        return this?.let {
            ComposeRect(
                left = it.left.toFloat(),
                top = it.top.toFloat(),
                right = it.right.toFloat(),
                bottom = it.bottom.toFloat()
            )
        }
    }

    fun calculateRectForOverlay(targetView: View): Rect? {
        val locationInWindow = IntArray(2)

        targetView.getLocationInWindow(locationInWindow) // Gets top-left (x,y) relative to the window

        val left = locationInWindow[0].toFloat()
        val top = locationInWindow[1].toFloat()
        val right = (locationInWindow[0] + targetView.width).toFloat()
        val bottom = (locationInWindow[1] + targetView.height).toFloat()

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



    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json" // Be specific: only show JSON files
            // If you want to allow any file and then check, use type = "*/*"
            // and add more robust checking in handleSelectedFile
        }
        filePickerLauncher.launch(intent)
    }

    private fun tutorialStep(text: String, highlight: AndroidRect?, onNext: () -> Unit, onSkip: () -> Unit) {

    }


    private fun handleSelectedFileQuiz(context: Context, uri: Uri) {
        val fileName = getFileName(uri)
        Log.d(TAG, "Selected file URI: $uri, Name: $fileName")

        // Basic check for JSON extension (optional, as MIME type should handle it)
        if (fileName != null && !fileName.endsWith(".json", ignoreCase = true) && typeWasInitiallyWildcard()) {
            Toast.makeText(this, "Please select a valid JSON file (.json)", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val jsonContent = readFileContent(uri)
            var is_quiz = false
            var is_study = false
            try {
                val listType: Type = object : TypeToken<List<QuestionItem>>() {}.type
                val parsedQuestions: List<QuestionItem> = Gson().fromJson(jsonContent, listType)
                is_quiz = true
            }
            catch (e: Exception) {
                try{
                    val listType: Type = object : TypeToken<StudyItem>() {}.type
                    val parsedStudyMaterials: StudyItem = Gson().fromJson(jsonContent, listType)
                    is_study = true
                }
                catch (e: Exception){
                    Log.e(TAG, "Error parsing JSON", e)
                    Toast.makeText(this, "Error: Invalid JSON.", Toast.LENGTH_LONG).show()
                }
            }
            if (jsonContent != null && isValidJson(jsonContent)) {
                val file = File(context.cacheDir, CacheConstants.CACHED_RESULTS_FILENAME)
                var success = false
                try {
                    FileOutputStream(file).use { fos ->
                        // Option 1: Write each string on a new line
                            fos.write(jsonContent.toByteArray())

                        // Option 2: Serialize as JSON (using Gson or org.json.JSONArray) - More robust
                        // val jsonArray = JSONArray(resultArray)
                        // fos.write(jsonArray.toString().toByteArray())
                        success = true
                        Log.d("CacheSave", "Successfully saved results to cache: ${file.absolutePath}")
                    }
                } catch (e: IOException) {
                    Log.e("CacheSave", "Error saving results to cache", e)
                    // Handle error: maybe show a Toast, or decide not to start the next activity
                    return
                }
                if (success) {
                    if (is_quiz) {
                        Toast.makeText(this, "JSON quiz file processed!", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, Questioner::class.java).apply {
                            putExtra(CacheConstants.EXTRA_CACHE_FILENAME, CacheConstants.CACHED_RESULTS_FILENAME)
                        }
                        startActivity(intent)
                    } else if (is_study) {
                        Toast.makeText(this, "JSON study guide file processed!", Toast.LENGTH_SHORT)
                            .show()
                        val intent = Intent(this, StudyList::class.java).apply {
                            putExtra(CacheConstants.EXTRA_CACHE_FILENAME, CacheConstants.CACHED_RESULTS_FILENAME)
                        }
                        startActivity(intent)
                    }
                } else {
                    Log.d(TAG, "Issue with saving the cached file")
                }
                Log.d(TAG, "JSON Content: $jsonContent")

                // Start QuestionarActivity and pass the JSON content


            } else {
                Toast.makeText(this, "Could not read or invalid JSON content.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing file: ${e.message}", e)
            Toast.makeText(this, "Error processing file: ${e.message}", Toast.LENGTH_LONG).show()
        }

    }



    // Helper to check if the file picker was initially set to pick any file type
    private fun typeWasInitiallyWildcard(): Boolean {
        // This is a simple check. If you dynamically change the intent type,
        // you might need a more robust way to know the initial type.
        // For this example, we assume if it's not "application/json", it might have been "*/*".
        val intentUsedToPick = Intent(Intent.ACTION_OPEN_DOCUMENT) // Recreate to check default or what was set
        intentUsedToPick.type = "application/json" // The type we ideally want
        // This is a bit of a heuristic. A more robust way would be to store the type used.
        return intentUsedToPick.type != "application/json"
    }


    private fun readFileContent(uri: Uri): String? {
        val stringBuilder = StringBuilder()
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stringBuilder.append(line)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file content: ${e.message}", e)
            return null
        }
        return stringBuilder.toString()
    }

    private fun isValidJson(jsonString: String?): Boolean {
        if (jsonString.isNullOrEmpty()) return false
        return try {
            JSONObject(jsonString) // Or JSONArray(jsonString) if it can be an array
            true
        } catch (e: JSONException) {
            // It could be a JSON Array, try that too for more flexibility
            try {
                JSONArray(jsonString)
                true
            } catch (e2: JSONException) {
                Log.w(TAG, "Invalid JSON: $jsonString \nError: ${e2.message}")
                false
            }
        }
    }
    // Helper function to get file name from URI (can be null)
    private fun getFileName(uri: Uri): String? {
        var fileName: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    fileName = cursor.getString(displayNameIndex)
                }
            }
        }
        return fileName
    }
}