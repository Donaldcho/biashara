package com.biasharaai.ui.pos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.data.local.db.Transaction
import com.biasharaai.data.local.db.TransactionDao
import com.biasharaai.pos.payment.PaymentDraft
import com.biasharaai.pos.payment.PrimaryPaymentTab
import com.biasharaai.data.local.db.SaleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectBalanceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionDao: TransactionDao,
    private val saleRepository: SaleRepository,
) : ViewModel() {

    private val saleTransactionId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_TRANSACTION_ID))

    private val _sale = MutableStateFlow<Transaction?>(null)
    val sale: StateFlow<Transaction?> = _sale.asStateFlow()

    init {
        viewModelScope.launch {
            _sale.value = transactionDao.getTransactionById(saleTransactionId)
        }
    }

    suspend fun commitSettlement(
        tab: PrimaryPaymentTab,
        cashTendered: Double?,
        cashChange: Double?,
        mobileNetwork: String?,
        mobileRef: String?,
    ): SaleCommitResult {
        val original = _sale.value ?: return SaleCommitResult.Failure("Sale not found")
        val balance = original.balanceDue
        if (balance <= 0) return SaleCommitResult.Failure("No balance due")
        val draft = PaymentDraft(
            grandTotal = balance,
            splitMode = false,
            primaryTab = tab,
            cashAmountTendered = if (tab == PrimaryPaymentTab.CASH) cashTendered else null,
            cashChangeDue = if (tab == PrimaryPaymentTab.CASH) cashChange else null,
            mobileMoneyNetwork = if (tab == PrimaryPaymentTab.MOBILE_MONEY) mobileNetwork else null,
            mobileMoneyRef = if (tab == PrimaryPaymentTab.MOBILE_MONEY) mobileRef else null,
            creditCustomerId = null,
            creditDueDateMillis = null,
            splitLine1Method = null,
            splitLine1Amount = null,
            splitLine2Method = null,
            splitLine2Amount = null,
        )
        return try {
            val id = saleRepository.commitBalanceSettlement(saleTransactionId, draft)
            SaleCommitResult.Success(id, emptyList())
        } catch (e: Exception) {
            SaleCommitResult.Failure(e.message ?: "Settlement failed")
        }
    }

    companion object {
        const val ARG_TRANSACTION_ID = "sale_transaction_id"
    }
}
