package com.biasharaai.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.R
import com.biasharaai.profile.BusinessOnboardingSteps
import com.biasharaai.profile.BusinessProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingChatLine(val fromAgent: Boolean, val text: String)

data class BusinessProfileOnboardingUiState(
    val lines: List<OnboardingChatLine> = emptyList(),
    val stepIndex: Int = 0,
    val currentQuestionResId: Int = R.string.business_onboarding_q_name,
    val isComplete: Boolean = false,
)

sealed class BusinessProfileOnboardingEvent {
    data object Finished : BusinessProfileOnboardingEvent()
    data class ShowMessage(val messageResId: Int) : BusinessProfileOnboardingEvent()
}

@HiltViewModel
class BusinessProfileOnboardingViewModel @Inject constructor(
    private val repository: BusinessProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BusinessProfileOnboardingUiState())
    val uiState: StateFlow<BusinessProfileOnboardingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BusinessProfileOnboardingEvent>()
    val events: SharedFlow<BusinessProfileOnboardingEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val profile = repository.getOrCreate()
            val step = profile.onboardingStep.coerceIn(0, BusinessOnboardingSteps.STEP_COUNT - 1)
            if (profile.onboardingComplete) {
                _uiState.value = BusinessProfileOnboardingUiState(isComplete = true)
                _events.emit(BusinessProfileOnboardingEvent.Finished)
                return@launch
            }
            val stepDef = BusinessOnboardingSteps.stepForIndex(step) ?: BusinessOnboardingSteps.steps.first()
            _uiState.value = BusinessProfileOnboardingUiState(
                stepIndex = step,
                currentQuestionResId = stepDef.questionResId,
            )
        }
    }

    fun submitAnswer(answer: String) {
        val trimmed = answer.trim()
        if (trimmed.length < 2) {
            viewModelScope.launch {
                _events.emit(BusinessProfileOnboardingEvent.ShowMessage(R.string.business_onboarding_answer_too_short))
            }
            return
        }
        viewModelScope.launch {
            val step = _uiState.value.stepIndex
            val updated = repository.applyOnboardingAnswer(step, trimmed)
            val confirm = OnboardingChatLine(fromAgent = true, text = trimmed)
            val userLine = OnboardingChatLine(fromAgent = false, text = trimmed)
            val nextStep = step + 1
            if (nextStep >= BusinessOnboardingSteps.STEP_COUNT) {
                repository.completeOnboarding()
                _uiState.update {
                    it.copy(
                        lines = it.lines + userLine + confirm,
                        isComplete = true,
                    )
                }
                _events.emit(BusinessProfileOnboardingEvent.Finished)
            } else {
                val next = BusinessOnboardingSteps.stepForIndex(nextStep)!!
                _uiState.update {
                    it.copy(
                        lines = it.lines + userLine + confirm,
                        stepIndex = nextStep,
                        currentQuestionResId = next.questionResId,
                    )
                }
            }
        }
    }

    fun skipStep() {
        viewModelScope.launch {
            val step = _uiState.value.stepIndex
            val nextStep = step + 1
            if (nextStep >= BusinessOnboardingSteps.STEP_COUNT) {
                repository.completeOnboarding()
                _events.emit(BusinessProfileOnboardingEvent.Finished)
            } else {
                val next = BusinessOnboardingSteps.stepForIndex(nextStep)!!
                _uiState.update {
                    it.copy(stepIndex = nextStep, currentQuestionResId = next.questionResId)
                }
            }
        }
    }
}
