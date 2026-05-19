package com.biasharaai.ui.cash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.cash.CashMovementRepository
import com.biasharaai.cash.CashMovementRequest
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.CaptureMethod
import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.LedgerEntryType
import com.biasharaai.data.local.db.ParserEngine
import com.biasharaai.data.local.db.ProofType
import com.biasharaai.money.RegionalDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ConfirmationUiState {
    data object Idle : ConfirmationUiState
    data object Saving : ConfirmationUiState
    data class Saved(val ledgerEntryId: Long) : ConfirmationUiState
    data class Error(val message: String) : ConfirmationUiState
}

@HiltViewModel
class ConfirmationViewModel @Inject constructor(
    private val repository: CashMovementRepository,
    private val appSettingsDao: AppSettingsDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConfirmationUiState>(ConfirmationUiState.Idle)
    val uiState: StateFlow<ConfirmationUiState> = _uiState

    val currencySymbol: StateFlow<String> = appSettingsDao.getSettings()
        .map { it?.currencySymbol ?: RegionalDefaults.CURRENCY_SYMBOL }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RegionalDefaults.CURRENCY_SYMBOL)

    fun save(
        direction: LedgerDirection,
        type: LedgerEntryType,
        amount: Double,
        description: String,
        notes: String?,
        captureMethod: CaptureMethod,
        proofType: ProofType,
        rawText: String?,
        parsedReference: String?,
        parsedCounterparty: String?,
        parsedDate: Long?,
        parserConfidence: Float,
        parserEngine: ParserEngine,
    ) {
        if (_uiState.value is ConfirmationUiState.Saving) return
        _uiState.value = ConfirmationUiState.Saving
        viewModelScope.launch {
            runCatching {
                repository.saveCashMovement(
                    CashMovementRequest(
                        direction = direction,
                        type = type,
                        amount = amount,
                        description = description,
                        notes = notes,
                        captureMethod = captureMethod,
                        proofType = proofType,
                        rawText = rawText,
                        parsedReference = parsedReference,
                        parsedCounterparty = parsedCounterparty,
                        parsedDate = parsedDate,
                        parserConfidence = parserConfidence,
                        parserEngine = parserEngine,
                    ),
                )
            }.fold(
                onSuccess = { result -> _uiState.value = ConfirmationUiState.Saved(result.ledgerEntryId) },
                onFailure = { e -> _uiState.value = ConfirmationUiState.Error(e.localizedMessage ?: "Save failed") },
            )
        }
    }
}
