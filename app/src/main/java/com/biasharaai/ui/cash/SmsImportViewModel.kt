package com.biasharaai.ui.cash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.cash.CashParser
import com.biasharaai.cash.ParsedFields
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SmsImportUiState {
    data object Idle : SmsImportUiState
    data object Parsing : SmsImportUiState
    data class Ready(val rawText: String, val parsed: ParsedFields) : SmsImportUiState
    data class Error(val message: String) : SmsImportUiState
}

@HiltViewModel
class SmsImportViewModel @Inject constructor(
    private val cashParser: CashParser,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SmsImportUiState>(SmsImportUiState.Idle)
    val uiState: StateFlow<SmsImportUiState> = _uiState

    fun parse(rawText: String) {
        if (_uiState.value is SmsImportUiState.Parsing) return
        _uiState.value = SmsImportUiState.Parsing
        viewModelScope.launch {
            runCatching { cashParser.parse(rawText) }
                .fold(
                    onSuccess = { parsed -> _uiState.value = SmsImportUiState.Ready(rawText, parsed) },
                    onFailure = { e -> _uiState.value = SmsImportUiState.Error(e.localizedMessage ?: "Parse failed") },
                )
        }
    }

    fun resetToIdle() {
        _uiState.value = SmsImportUiState.Idle
    }
}
