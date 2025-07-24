package com.conor.quizzer

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import androidx.compose.ui.semantics.text
import androidx.core.view.isVisible
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.io.filefilter.TrueFileFilter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken // Important for parsing lists
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.lang.reflect.Type
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.text.format
import kotlin.text.toDouble

object QuizConstants {
    const val EXTRA_FINAL_SCORE = "com.example.quizzer.FINAL_SCORE"
    const val EXTRA_TOTAL_QUESTIONS = "com.example.quizzer.TOTAL_QUESTIONS"
    const val EXTRA_TIME_TAKEN = "com.example.quizzer.TIME_TAKEN"
}

class Questioner : AppCompatActivity() {

    private val TAG = "Questioner"
    private var is_submitted: Boolean = false
    private lateinit var questionNumberTextView: TextView
    private lateinit var questionTextView: TextView
    private lateinit var choicesLayout: LinearLayout
    private lateinit var correctAnswersLayout: LinearLayout
    private lateinit var statusTextView: TextView
    private lateinit var selectNumberTextView: TextView
    private lateinit var explanationTextView: TextView
    private lateinit var submitNextButton: Button // Optional: To check the current answer
    private lateinit var noMoreQuestionsTextView: TextView
    private lateinit var scoreTextView: TextView
    private lateinit var questionDisplayGroup: LinearLayout // To show/hide question elements
    private lateinit var timerText: TextView


    private var handler: Handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var stopwatchTimeString: String = ""

    private var startTime: Long = 0L
    private var timeInMilliseconds: Long = 0L
    private var timeSwapBuff: Long = 0L // Stores time when paused
    private var updatedTime: Long = 0L

    private var isRunning: Boolean = false
    private var isPaused: Boolean = false

    private var allQuestions: MutableList<QuestionItem> = mutableListOf()
    private var currentQuestion: QuestionItem? = null
    private var originalQuestionCount = 0
    private var score = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_questioner) // Ensure this layout exists

        // Initialize UI elements
        questionNumberTextView = findViewById(R.id.questionNumber)
        questionTextView = findViewById(R.id.questionText)
        choicesLayout = findViewById(R.id.choicesLayout)
        correctAnswersLayout = findViewById(R.id.correctAnswersLayout)
        explanationTextView = findViewById(R.id.explanation)
        statusTextView = findViewById(R.id.status)
        selectNumberTextView = findViewById(R.id.selectNumber)
        scoreTextView = findViewById(R.id.score)
        submitNextButton = findViewById(R.id.submitNext)
        timerText = findViewById(R.id.timerText)

        scoreTextView.text = this.getString(R.string.quiz_score, 0, 0, 0)




        val cachedFilename = intent.getStringExtra(CacheConstants.EXTRA_CACHE_FILENAME)
        val loadedResults = mutableListOf<String>()
        if (cachedFilename != null) {
            val file = File(cacheDir, cachedFilename)
            if (file.exists()) {
                try {
                    FileInputStream(file).use { fis ->
                        InputStreamReader(fis).use { isr ->
                            BufferedReader(isr).use { reader ->
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    line?.let { loadedResults.add(it) }
                                }
                            }
                        }
                    }
                    if (file.delete()) {
                        Log.d("CacheCleanup", "Cache file deleted successfully after reading: ${file.name}")
                    } else {
                        Log.w("CacheCleanup", "Failed to delete cache file after reading: ${file.name}")
                    }
                    // Option 2: If saved as JSON
                    // val jsonString = file.readText()
                    // val jsonArray = JSONArray(jsonString)
                    // for (i in 0 until jsonArray.length()) {
                    //    loadedResults.add(jsonArray.getString(i))
                    // }

                    Log.d("CacheRead", "Successfully loaded ${loadedResults.size} results from cache.")
                    // Now use loadedResults to populate your UI
                    // e.g., resultsTextView.text = loadedResults.joinToString("\n\n")

                    // Optionally, delete the cache file if it's a one-time use
                    // if (file.delete()) {
                    //     Log.d("CacheRead", "Cache file deleted successfully: ${file.name}")
                    // } else {
                    //     Log.w("CacheRead", "Failed to delete cache file: ${file.name}")
                    // }

                } catch (e: IOException) {
                    Log.e("CacheRead", "Error reading results from cache file: ${file.absolutePath}", e)
                    // Display error message
                } catch (e: org.json.JSONException) { // If parsing JSON
                    Log.e("CacheRead", "Error parsing JSON from cache file: ${file.absolutePath}", e)
                }
            } else {
                Log.w("CacheRead", "Cache file not found: ${file.absolutePath}")
                // Handle case where cache file doesn't exist (maybe it was cleared by system)
            }
        } else {
            Log.w("CacheRead", "No cache filename passed in intent.")
            // Handle case where no filename was provided
        }

        val jsonContent = loadedResults.joinToString("\n")

        if (jsonContent != null) {
            Log.d(TAG, "Received JSON Content")
            try {
                val listType: Type = object : TypeToken<List<QuestionItem>>() {}.type
                val parsedQuestions: List<QuestionItem> = Gson().fromJson(jsonContent, listType)

                if (parsedQuestions.isNotEmpty()) {
                    allQuestions.addAll(parsedQuestions) // Add to our mutable list
                    originalQuestionCount = allQuestions.size
                    Log.d(TAG, "Successfully parsed $originalQuestionCount questions.")
                    loadAndDisplayRandomQuestion()
                } else {
                    handleNoQuestions("No questions found in the file.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing JSON", e)
                handleNoQuestions("Error: Invalid questionnaire data format.")
            }
        } else {
            Log.e(TAG, "No JSON content received!")
            handleNoQuestions("Error: No questionnaire data received.")
        }

        submitNextButton.setOnClickListener {
            if (currentQuestion == null && allQuestions.isEmpty()) {
                // Handle case where there are no more questions and user clicks "Start Over?"
                // For now, let's assume we just log or disable further.
                // You might want to re-initialize the quiz here.
                Log.d(TAG, "All questions finished. 'Start Over?' clicked.")
                // Example: Re-initialize (you'd need to store originalJsonContent)
                // if (originalJsonContent != null) loadInitialData(originalJsonContent)
                return@setOnClickListener
            }
            if (is_submitted) {

                // If answer has been submitted, this click is to "Next Question"
                explanationTextView.visibility = View.GONE // Hide previous explanation
                loadAndDisplayRandomQuestion() // This function should now set isAnswerSubmitted = false
                is_submitted = false


            } else {
                checkCurrentAnswer()
                is_submitted = true

            }
        }
        startStopwatch(this)
    }
    private fun loadAndDisplayRandomQuestion() {
        if (allQuestions.isEmpty()) {
            handleNoMoreQuestions()
            return
        }

        val randomIndex = Random.nextInt(allQuestions.size)
        currentQuestion = allQuestions.removeAt(randomIndex) // Get and remove
        displayQuestion(currentQuestion!!)
        updateProgress()
    }

    private fun displayQuestion(questionItem: QuestionItem) {
        statusTextView.visibility = View.INVISIBLE
        submitNextButton.text = this.getString(R.string.quiz_submit) // Reset button text
        questionNumberTextView.text = this.getString(R.string.quiz_questionNumber, originalQuestionCount - allQuestions.size,  originalQuestionCount)
        //questionNumberTextView.text = "Question ${originalQuestionCount - allQuestions.size} of $originalQuestionCount"
        // Or if you prefer the original number:
        // questionNumberTextView.text = "Q: ${questionItem.questionNumber}"
        questionTextView.text = questionItem.questionText
        explanationTextView.visibility = View.INVISIBLE
        explanationTextView.text =questionItem.explanation
        choicesLayout.removeAllViews()
        correctAnswersLayout.removeAllViews()

        val marginint=8.dpToPx(this)
        val choiceboxes = mutableMapOf<String, CheckBox>()
        val correct_answer_boxes = mutableMapOf<String, CheckBox>()
        for (choice in questionItem.choices) {
            val choicebox = CheckBox(this).apply {
                text = "${choice.key}) ${choice.value}"
                isChecked = false
                id=View.generateViewId()
                tag="answerTest${choice.key}"
                textSize = 19f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = marginint
                    bottomMargin = marginint
                }

            }
            choicesLayout.addView(choicebox)
            choiceboxes += Pair(choice.key, choicebox)

            val answerbox = CheckBox(this).apply {
                visibility = View.GONE
                isChecked = false
                tag="correctAnswer${choice.key}"
            }
            correctAnswersLayout.addView(answerbox)
            correct_answer_boxes += Pair(choice.key, answerbox)

        }

        var numAnswers=0
        for (answer in questionItem.correctAnswer){
            val key = answer[0].uppercase()
            correct_answer_boxes[key]?.isChecked=true
            numAnswers += 1
        }
        selectNumberTextView.setText(this.getString(R.string.quiz_select, numAnswers))




        questionItem.choices.entries.sortedBy { it.key }.forEach { (key, value) -> // Sort choices by key (A, B, C, D)
            val choiceRadioButton = RadioButton(this).apply {
                val choicebox=choiceboxes[key]
                if (choicebox != null) {
                    choicebox.visibility=View.VISIBLE
                    if (value.isNotBlank()){
                        choicebox.text="${key}) ${value}"
                    }
                }
            }
        }

        explanationTextView.visibility = View.GONE // Hide explanation until checked
        submitNextButton.text = this.getString(R.string.quiz_submit) // Reset button text

        Log.d(TAG, "Displaying Q: ${questionItem.questionNumber}")
    }
    private fun checkCurrentAnswer() {
        val chosen_answers = mutableListOf<String>()
        val correct_answers = mutableListOf<String>()

        val correct_answer_boxes = mutableMapOf<String, CheckBox>()
        val choiceboxes = mutableMapOf<String, CheckBox>()
        for (i in 0 until choicesLayout.childCount) {
            val child = choicesLayout.getChildAt(i) as CheckBox
            val key = child.tag.toString().last().toString()
            choiceboxes += Pair(key, child)
        }
        for (i in 0 until correctAnswersLayout.childCount) {
            val child = correctAnswersLayout.getChildAt(i) as CheckBox
            val key = child.tag.toString().last().toString()
            correct_answer_boxes += Pair(key, child)
        }


        for ((key, choicebox)in choiceboxes){
            if (choicebox.isChecked){
                chosen_answers.add(key)
            }
        }
        for ((key, choicebox)in correct_answer_boxes){
            if (choicebox.isChecked){
                correct_answers.add(key)
            }
        }
        val is_correct = chosen_answers.toSet() == correct_answers.toSet()

        if (is_correct){
            val color = ContextCompat.getColor(this, android.R.color.holo_green_dark)
            statusTextView.setTextColor(color)
            statusTextView.text = this.getString(R.string.quiz_correct)
            statusTextView.visibility = View.VISIBLE
            score+=1

        }
        else {
            val color = ContextCompat.getColor(this, android.R.color.holo_red_dark)
            statusTextView.setTextColor(color)
            statusTextView.text = this.getString(R.string.quiz_incorrect)
            statusTextView.visibility = View.VISIBLE
        }
        submitNextButton.text = this.getString(R.string.quiz_next)
        updateScoreDisplay()
        explanationTextView.visibility = View.VISIBLE


    }
    private fun updateProgress() {
        // You can add more sophisticated progress display here if needed
        Log.d(TAG, "${allQuestions.size} questions remaining.")
    }

    private fun handleNoMoreQuestions() {
        pauseStopwatch()
        val resultsIntent = Intent(this, Results::class.java).apply {
            putExtra(QuizConstants.EXTRA_FINAL_SCORE, score)
            putExtra(QuizConstants.EXTRA_TOTAL_QUESTIONS, originalQuestionCount)
            putExtra(QuizConstants.EXTRA_TIME_TAKEN, stopwatchTimeString) // Pass the formatted time string
        }
        startActivity(resultsIntent)
        finish()


    }

    private fun updateScoreDisplay() {
        val questionsAnswered = (originalQuestionCount - allQuestions.size)
        val percentage = ((score.toDouble() / questionsAnswered.toDouble()) * 100.0).roundToInt()
        scoreTextView.text = this.getString(R.string.quiz_score, score, questionsAnswered, percentage)
        // Or you could show score out of questions attempted:
        // scoreTextView.text = "Score: $currentScore / $questionsAttempted (Total: $originalQuestionCount)"
    }

    private fun handleNoQuestions(message: String) {
        questionDisplayGroup.visibility = View.GONE
        noMoreQuestionsTextView.text = message
        noMoreQuestionsTextView.visibility = View.VISIBLE
        submitNextButton.isEnabled = false
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.w(TAG, message)
    }
    fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
    private fun startStopwatch(context: Context) {
        if (!isRunning) {
            startTime = SystemClock.uptimeMillis() // Get current time as a base
            runnable = object : Runnable {
                override fun run() {
                    timeInMilliseconds = SystemClock.uptimeMillis() - startTime
                    updatedTime = timeSwapBuff + timeInMilliseconds // Add paused time buffer

                    updateTimerText(updatedTime, context)

                    handler.postDelayed(this, 10) // Update UI frequently for smoother milliseconds
                }
            }
            handler.postDelayed(runnable!!, 0)
            isRunning = true
            isPaused = false

        }
    }



    private fun updateTimerText(timeInMillis: Long, context: Context) {
        val secs = (timeInMillis / 1000).toInt()
        val mins = secs / 60
        val hours = mins / 60
        val displaySecs = secs % 60
        val milliseconds = (timeInMillis % 1000).toInt() / 10 // Display centiseconds
        val timeLabel = context.getText(R.string.timeElapsed).toString()

        val timeFormatted = String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d:%02d",
            hours,
            mins,
            displaySecs,
            milliseconds
        )
        timerText.text = timeLabel + " " + timeFormatted
        stopwatchTimeString = timeFormatted

    }
    private fun pauseStopwatch() {
        if (isRunning && !isPaused) {
            timeSwapBuff += timeInMilliseconds // Add current elapsed time to buffer
            handler.removeCallbacks(runnable!!) // Stop the handler
            isPaused = true
        }
    }
}