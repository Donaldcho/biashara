package com.biasharaai.ui.insights

import androidx.lifecycle.viewModelScope
import com.biasharaai.ai.CashFlowAnalyzer
import com.biasharaai.data.local.db.Transaction
import com.biasharaai.data.local.db.TransactionRepository
import com.biasharaai.data.local.db.TransactionType
import com.biasharaai.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CashFlowInsightsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val cashFlowAnalyzer: CashFlowAnalyzer,
) : BaseViewModel() {

    // ── UI State ────────────────────────────────────────────────────────

    data class UiState(
        val periodLabel: String = "",
        val totalIncome: Double = 0.0,
        val totalExpenses: Double = 0.0,
        val netCashFlow: Double = 0.0,
        val insightsText: String = "",
        val isLoading: Boolean = true,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadInsights()
    }

    /** Refresh insights — called from the toolbar Refresh button. */
    fun refresh() {
        loadInsights()
    }

    private fun loadInsights() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Current month boundaries
            val calendar = Calendar.getInstance()
            val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val periodLabel = monthFormat.format(Date())

            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfMonth = calendar.timeInMillis

            calendar.add(Calendar.MONTH, 1)
            val endOfMonth = calendar.timeInMillis - 1

            // Get transactions for this month
            var transactions = transactionRepository
                .getByPeriod(startOfMonth, endOfMonth)
                .first()

            // If no transactions exist, seed sample data for demonstration
            if (transactions.isEmpty()) {
                seedSampleTransactions(startOfMonth)
                transactions = transactionRepository
                    .getByPeriod(startOfMonth, endOfMonth)
                    .first()
            }

            // Calculate totals
            val totalIncome = transactions
                .filter { it.type == TransactionType.INCOME || it.type == TransactionType.RETURN }
                .sumOf { it.amount }

            val totalExpenses = transactions
                .filter { it.type == TransactionType.EXPENSE }
                .sumOf { it.amount }

            val netCashFlow = totalIncome - totalExpenses

            // Rules first (instant, no LLM) — keeps Chat responsive on mid-range phones
            val rulesInsights = cashFlowAnalyzer.generateRulesInsights(
                transactions = transactions,
                periodLabel = periodLabel,
            )
            _uiState.value = UiState(
                periodLabel = periodLabel,
                totalIncome = totalIncome,
                totalExpenses = totalExpenses,
                netCashFlow = netCashFlow,
                insightsText = rulesInsights,
                isLoading = false,
            )

            // Optional LLM enhancement (FULL_AI only) — runs after UI shows rules
            val lang = Locale.getDefault().language
            val aiInsights = cashFlowAnalyzer.tryEnhanceInsightsWithAi(
                transactions = transactions,
                language = lang,
                periodLabel = periodLabel,
            )
            if (aiInsights != null) {
                _uiState.value = _uiState.value.copy(insightsText = aiInsights)
            }
        }
    }

    /**
     * Seed sample transactions for demonstration.
     *
     * Creates a realistic mix of income and expense transactions
     * for the current month so the user can see the feature in action.
     * In production, real transactions would come from sales recording.
     */
    private suspend fun seedSampleTransactions(monthStart: Long) {
        val day = 24L * 60 * 60 * 1000 // millis in a day

        val samples = listOf(
            Transaction(type = TransactionType.INCOME, amount = 15000.0, description = "Sales", date = monthStart + 1 * day),
            Transaction(type = TransactionType.INCOME, amount = 12500.0, description = "Sales", date = monthStart + 3 * day),
            Transaction(type = TransactionType.INCOME, amount = 8000.0, description = "Services", date = monthStart + 5 * day),
            Transaction(type = TransactionType.INCOME, amount = 9500.0, description = "Sales", date = monthStart + 7 * day),
            Transaction(type = TransactionType.EXPENSE, amount = 5000.0, description = "Rent", date = monthStart + 1 * day),
            Transaction(type = TransactionType.EXPENSE, amount = 3200.0, description = "Stock purchase", date = monthStart + 2 * day),
            Transaction(type = TransactionType.EXPENSE, amount = 1500.0, description = "Transport", date = monthStart + 4 * day),
            Transaction(type = TransactionType.EXPENSE, amount = 2800.0, description = "Stock purchase", date = monthStart + 6 * day),
            Transaction(type = TransactionType.EXPENSE, amount = 800.0, description = "Utilities", date = monthStart + 3 * day),
            Transaction(type = TransactionType.EXPENSE, amount = 1200.0, description = "Marketing", date = monthStart + 5 * day),
            Transaction(type = TransactionType.EXPENSE, amount = 600.0, description = "Transport", date = monthStart + 8 * day),
        )

        samples.forEach { transactionRepository.insert(it) }
    }
}
