package com.conor.quizzer

import com.google.gson.annotations.SerializedName

data class StudyItem(
    @SerializedName("subject") // Maps JSON key to this field name
    val subject: String,

    @SerializedName("topics")
    val topics: List<Map<String, String>>,
)