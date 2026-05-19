package com.biasharaai.ui.pos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.data.local.db.AppSettings
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.Transaction
import com.biasharaai.data.local.db.TransactionRepository
import com.biasharaai.pos.receipt.PosReceiptAssembler
import com.biasharaai.pos.receipt.PosReceiptLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ReceiptUiState(
    val transaction: Transaction? = null,
    val lines: List<PosReceiptLine> = emptyList(),
    val settings: AppSettings? = null,
    val voucherIds: List<String> = emptyList(),
)

@HiltViewModel
class ReceiptViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val appSettingsDao: AppSettingsDao,
    private val posReceiptAssembler: PosReceiptAssembler,
) : ViewModel() {

    private val transactionId: Long =
        checkNotNull(savedStateHandle.get<Long>(ARG_TRANSACTION_ID)) {
            "transaction_id required"
        }

    private val navVoucherIds: List<String> =
        savedStateHandle.get<Array<String>>(ARG_ISSUED_VOUCHER_IDS)?.toList().orEmpty()

    val uiState: StateFlow<ReceiptUiState> = combine(
        transactionRepository.observeTransactionById(transactionId),
        appSettingsDao.getSettings(),
    ) { tx, settings ->
        tx to settings
    }.flatMapLatest { (tx, settings) ->
        flow {
            if (tx == null) {
                emit(ReceiptUiState(settings = settings))
                return@flow
            }
            val assembled = withContext(Dispatchers.IO) {
                posReceiptAssembler.assemble(transactionId, navVoucherIds)
            }
            emit(
                ReceiptUiState(
                    transaction = tx,
                    lines = assembled.lines,
                    settings = settings,
                    voucherIds = assembled.voucherIds,
                ),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiptUiState())

    companion object {
        const val ARG_TRANSACTION_ID: String = "transaction_id"
        const val ARG_ISSUED_VOUCHER_IDS: String = "issued_voucher_ids"
    }
}
