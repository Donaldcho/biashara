package com.biasharaai.ui.pos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.data.local.db.ReturnLineCommit
import com.biasharaai.data.local.db.SaleLineItem
import com.biasharaai.data.local.db.SaleLineItemDao
import com.biasharaai.data.local.db.SaleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ReturnLineUiRow(
    val line: SaleLineItem,
    val checked: Boolean,
    /** Quantity user is returning on this submission (1..maxReturnable). */
    val returnQty: Int,
    val maxReturnable: Int,
)

@HiltViewModel
class ReturnViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val saleLineItemDao: SaleLineItemDao,
    private val saleRepository: SaleRepository,
) : ViewModel() {

    private val saleTransactionId: Long =
        checkNotNull(savedStateHandle.get<Long>(ReceiptViewModel.ARG_TRANSACTION_ID)) {
            "transaction_id required"
        }

    private val _rows = MutableStateFlow<List<ReturnLineUiRow>>(emptyList())
    val rows: StateFlow<List<ReturnLineUiRow>> = _rows.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _error.value = null
            val lines = saleLineItemDao.getLineItemsForTransactionOnce(saleTransactionId)
                .filter { it.quantity > 0 }
            val rowsOut = mutableListOf<ReturnLineUiRow>()
            for (line in lines) {
                val returned = saleLineItemDao.sumReturnedQuantityForOriginalLine(line.id)
                val maxLeft = line.quantity - returned
                if (maxLeft <= 0) continue
                rowsOut.add(
                    ReturnLineUiRow(
                        line = line,
                        checked = false,
                        returnQty = maxLeft,
                        maxReturnable = maxLeft,
                    ),
                )
            }
            _rows.value = rowsOut
        }
    }

    fun setChecked(lineId: Long, checked: Boolean) {
        _rows.value = _rows.value.map {
            if (it.line.id == lineId) it.copy(checked = checked) else it
        }
    }

    fun setReturnQty(lineId: Long, qty: Int) {
        _rows.value = _rows.value.map { row ->
            if (row.line.id != lineId) return@map row
            val clamped = qty.coerceIn(1, row.maxReturnable)
            row.copy(returnQty = clamped)
        }
    }

    /** Delegates to [SaleRepository.commitReturn]; Room transaction lives in the repository. */
    fun commitReturn(onSuccess: (Long) -> Unit) {
        viewModelScope.launch {
            _error.value = null
            val selected = _rows.value.filter { it.checked }
            if (selected.isEmpty()) {
                _error.value = ERROR_NONE
                return@launch
            }
            if (selected.any { it.returnQty < 1 || it.returnQty > it.maxReturnable }) {
                _error.value = ERROR_QTY
                return@launch
            }
            _busy.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val commits = selected.map {
                        ReturnLineCommit(
                            originalSaleLineItemId = it.line.id,
                            productId = it.line.productId,
                            productName = it.line.productName,
                            unitPrice = it.line.unitPrice,
                            returnQty = it.returnQty,
                        )
                    }
                    saleRepository.commitReturn(saleTransactionId, commits)
                }
            }
            _busy.value = false
            result.onSuccess { onSuccess(it) }
                .onFailure { _error.value = it.message ?: ERROR_GENERIC }
        }
    }

    fun clearError() {
        _error.value = null
    }

    companion object {
        const val ERROR_NONE = "__none__"
        const val ERROR_QTY = "__qty__"
        const val ERROR_GENERIC = "__generic__"
    }
}
