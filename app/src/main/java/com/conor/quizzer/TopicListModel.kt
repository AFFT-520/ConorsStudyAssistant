package com.conor.quizzer

import com.google.gson.annotations.SerializedName

data class TopicList(
    @SerializedName("topic_names") // Maps JSON key to this field name
    val topic_names: List<String>,
)