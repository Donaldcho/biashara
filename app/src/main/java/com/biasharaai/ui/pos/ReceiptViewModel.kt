package com.biasharaai.ui.pos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.data.local.db.AppSettings
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.SaleLineItem
import com.biasharaai.data.local.db.Transaction
import com.biasharaai.data.local.db.TransactionRepository
import com.biasharaai.data.local.db.SaleLineItemDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ReceiptUiState(
    val transaction: Transaction? = null,
    val lines: List<SaleLineItem> = emptyList(),
    val settings: AppSettings? = null,
)

@HiltViewModel
class ReceiptViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val saleLineItemDao: SaleLineItemDao,
    private val appSettingsDao: AppSettingsDao,
) : ViewModel() {

    private val transactionId: Long =
        checkNotNull(savedStateHandle.get<Long>(ARG_TRANSACTION_ID)) {
            "transaction_id required"
        }

    val uiState: StateFlow<ReceiptUiState> = combine(
        transactionRepository.observeTransactionById(transactionId),
        saleLineItemDao.getLineItemsByTransaction(transactionId),
        appSettingsDao.getSettings(),
    ) { tx: Transaction?, lines, settings ->
        ReceiptUiState(
            transaction = tx,
            lines = lines.filter { it.quantity != 0 },
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiptUiState())

    companion object {
        const val ARG_TRANSACTION_ID: String = "transaction_id"
    }
}
