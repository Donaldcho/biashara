package com.biasharaai.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.data.local.db.Transaction
import com.biasharaai.data.local.db.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class TodayPosSalesSummary(
    val totalAmount: Double,
    val transactionCount: Int,
)

@HiltViewModel
class TransactionHistoryViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
) : ViewModel() {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    val completedSales: StateFlow<List<Transaction>> =
        transactionRepository.observeCompletedPosSales()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayPosSalesSummary: StateFlow<TodayPosSalesSummary> =
        completedSales.map { list ->
            val today = LocalDate.now(zone)
            val todayTxs = list.filter { tx ->
                Instant.ofEpochMilli(tx.date).atZone(zone).toLocalDate() == today
            }
            TodayPosSalesSummary(
                totalAmount = todayTxs.sumOf { it.amount },
                transactionCount = todayTxs.size,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            TodayPosSalesSummary(0.0, 0),
        )
}
