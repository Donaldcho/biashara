package com.biasharaai.knowledge

data class MicroLesson(
    val lessonId: String,
    val featureId: String,
    val languageCode: String,
    val title: String,
    val steps: List<LessonStep>,
    val estimatedMinutes: Int = 2,
)

data class LessonStep(
    val stepNumber: Int,
    val instruction: String,
    val navigationHint: String? = null, // e.g. "Tap the + button in the top-right"
    val actionType: StepActionType = StepActionType.READ,
)

enum class StepActionType {
    READ,      // user just reads
    TAP,       // user taps something in the app
    TYPE,      // user types something
    CONFIRM,   // user confirms they completed the step
}
