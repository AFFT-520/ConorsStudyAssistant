package com.conor.quizzer

import androidx.compose.ui.geometry.Rect

data class TutorialStep(
    val id: String, // Unique identifier for the step
    val text: String,
    val targetCoordinates: Rect?, // Nullable if a step doesn't highlight anything
    // You could add more properties like:
    // val highlightedComposableKey: Any? = null, // To dynamically find composables
    // val onStepShown: (() -> Unit)? = null
)