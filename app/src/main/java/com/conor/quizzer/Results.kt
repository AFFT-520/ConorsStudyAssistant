package com.conor.quizzer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.conor.quizzer.databinding.ActivityResultsBinding
import kotlin.math.roundToInt

class Results : AppCompatActivity() {

    private lateinit var scoreTextViewResults: TextView
    private lateinit var timeTakenTextView: TextView
    private lateinit var backToMainButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results) // Create this layout file

        scoreTextViewResults = findViewById(R.id.testOverScore)
        timeTakenTextView = findViewById(R.id.testOverTime)
        backToMainButton = findViewById(R.id.testOverBack) // Initialize the button

        val finalScore = intent.getIntExtra(QuizConstants.EXTRA_FINAL_SCORE, 0)
        val totalQuestions = intent.getIntExtra(QuizConstants.EXTRA_TOTAL_QUESTIONS, 0)
        val timeTaken = intent.getStringExtra(QuizConstants.EXTRA_TIME_TAKEN)

        val percentage = ((finalScore.toDouble() / totalQuestions.toDouble()) * 100.0).roundToInt()
        scoreTextViewResults.text = this.getString(R.string.testOverScoreLabel).toString() + " $finalScore/$totalQuestions ($percentage%)"
        timeTakenTextView.text =  this.getString(R.string.testOverTimeLabel).toString() + " $timeTaken"

        backToMainButton.setOnClickListener {
            // Create an Intent to go back to MainActivity
            val intent = Intent(this, MainActivity::class.java)

            // Add flags to clear the activity stack and start MainActivity as a new task
            // This prevents building up a large back stack if the user plays multiple times.
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)

            startActivity(intent)
            finish() // Finish DisplayResultsActivity so it's removed from the back stack
        }
    }
}