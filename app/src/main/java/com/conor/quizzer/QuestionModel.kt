package com.conor.quizzer

import com.google.gson.annotations.SerializedName

data class QuestionItem(
    @SerializedName("question_number") // Maps JSON key to this field name
    val questionNumber: String,

    @SerializedName("question")
    val questionText: String,

    @SerializedName("domain")
    val domain: String?, // Can be null if empty string in JSON

    @SerializedName("choices")
    val choices: Map<String, String>, // A, B, C, D will be keys

    @SerializedName("correct_answer")
    val correctAnswer: List<String>, // It's an array in JSON, so a List

    @SerializedName("explanation")
    val explanation: String? // Can be null if empty string in JSON
)