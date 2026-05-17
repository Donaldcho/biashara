package com.biasharaai.ui.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.knowledge.LessonRunner
import com.biasharaai.knowledge.LessonSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LessonViewModel @Inject constructor(
    private val lessonRunner: LessonRunner,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Step(
            val lessonTitle: String,
            val stepNumber: Int,
            val totalSteps: Int,
            val progressPercent: Int,
            val instruction: String,
            val navigationHint: String?,
            val isLastStep: Boolean,
        ) : UiState
        data object Complete : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var session: LessonSession? = null

    fun loadLesson(lessonId: String) {
        val s = lessonRunner.startLesson(lessonId)
        if (s == null) {
            _uiState.value = UiState.Error("Lesson not found: $lessonId")
            return
        }
        session = s
        emitCurrentStep(s)
    }

    fun advance() {
        val current = session ?: return
        if (current.isComplete) return

        val next = lessonRunner.advance(current)
        session = next

        if (next.isComplete) {
            viewModelScope.launch {
                lessonRunner.completeLesson(next)
                _uiState.value = UiState.Complete
            }
        } else {
            emitCurrentStep(next)
        }
    }

    fun skip() {
        // Record partial completion (score 0.5) then exit — caller navigates up
        val current = session ?: return
        viewModelScope.launch {
            lessonRunner.completeLesson(current, score = 0.5f)
        }
    }

    private fun emitCurrentStep(s: LessonSession) {
        val step = s.currentStep ?: return
        _uiState.value = UiState.Step(
            lessonTitle = s.lesson.title,
            stepNumber = step.stepNumber,
            totalSteps = s.lesson.steps.size,
            progressPercent = (s.progress * 100).toInt(),
            instruction = step.instruction,
            navigationHint = step.navigationHint,
            isLastStep = s.currentStepIndex == s.lesson.steps.size - 1,
        )
    }
}
