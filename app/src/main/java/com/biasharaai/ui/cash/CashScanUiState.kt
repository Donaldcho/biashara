package com.biasharaai.ui.cash

import android.graphics.Bitmap
import com.biasharaai.cash.ParsedFields
import com.biasharaai.data.local.db.LedgerDirection

sealed interface CashScanUiState {
    data object Idle : CashScanUiState
    data object Scanning : CashScanUiState
    data class Confirming(
        val parsed: ParsedFields,
        val rawText: String,
        val thumbnail: Bitmap?,
        val direction: LedgerDirection,
    ) : CashScanUiState
    data class QrFound(val payload: BiasharaQrPayload) : CashScanUiState
    data object Saving : CashScanUiState
    data class Saved(val ledgerEntryId: Long) : CashScanUiState
    data class Error(val message: String) : CashScanUiState
}
