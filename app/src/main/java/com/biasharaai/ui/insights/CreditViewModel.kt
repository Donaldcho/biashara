package com.biasharaai.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.data.local.db.DebtRepository
import com.biasharaai.data.local.db.UnpaidDebtRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreditViewModel @Inject constructor(
    private val debtRepository: DebtRepository,
) : ViewModel() {

    val unpaidRows: StateFlow<List<UnpaidDebtRow>> =
        debtRepository.observeUnpaidDebtsOldestFirst()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalOutstanding: StateFlow<Double> =
        debtRepository.observeTotalOutstanding()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    fun markPaid(debtId: Long) {
        viewModelScope.launch {
            debtRepository.markDebtRepaid(debtId)
        }
    }
}
