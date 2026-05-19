package com.biasharaai.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.data.local.db.BusinessProfile
import com.biasharaai.profile.BusinessProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BusinessProfileEditEvent {
    data object Saved : BusinessProfileEditEvent()
}

@HiltViewModel
class BusinessProfileEditViewModel @Inject constructor(
    private val repository: BusinessProfileRepository,
) : ViewModel() {

    val profile = repository.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BusinessProfile())

    private val _events = MutableSharedFlow<BusinessProfileEditEvent>()
    val events = _events.asSharedFlow()

    fun save(profile: BusinessProfile) {
        viewModelScope.launch {
            repository.upsert(profile.copy(onboardingComplete = true))
            _events.emit(BusinessProfileEditEvent.Saved)
        }
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            repository.resetOnboarding()
        }
    }
}
