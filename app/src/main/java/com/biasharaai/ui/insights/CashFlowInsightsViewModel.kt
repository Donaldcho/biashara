package com.biasharaai.ui.insights

import androidx.lifecycle.viewModelScope
import com.biasharaai.ai.CashFlowAnalyzer
import com.biasharaai.data.local.db.SaleLineItemDao
import com.biasharaai.data.local.db.ServiceDeliveryDao
import com.biasharaai.data.local.db.Transaction
import com.biasharaai.data.local.db.TransactionRepository
import com.biasharaai.data.local.db.TransactionType
import com.biasharaai.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CashFlowInsightsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val cashFlowAnalyzer: CashFlowAnalyzer,
    private val saleLineItemDao: SaleLineItemDao,
    private val serviceDeliveryDao: ServiceDeliveryDao,
) : BaseViewModel() {

    enum class Period {
        TODAY,
        LAST_7_DAYS,
        LAST_30_DAYS,
        THIS_MONTH,
    }

    data class TopProduct(val name: String, val revenue: Double, val qty: Int)
    data class TopService(val name: String, val revenue: Double, val sessions: Int)

    data class UiState(
        val selectedPeriod: Period = Period.THIS_MONTH,
        val periodLabel: String = "",
        val totalIncome: Double = 0.0,
        val totalExpenses: Double = 0.0,
        val netCashFlow: Double = 0.0,
        val transactionCount: Int = 0,
        val averageSale: Double = 0.0,
        val lastUpdatedMillis: Long = 0L,
        val telemetryPoints: List<BusinessTelemetryPoint> = emptyList(),
        val insightsText: String = "",
        val productRevenue: Double = 0.0,
        val serviceRevenue: Double = 0.0,
        val topProducts: List<TopProduct> = emptyList(),
        val topServices: List<TopService> = emptyList(),
        val isLoading: Boolean = true,
    )

    private data class PeriodRange(
        val period: Period,
        val startMillis: Long,
        val endExclusiveMillis: Long,
        val label: String,
    )

    private data class PeriodTransactions(
        val range: PeriodRange,
        val transactions: List<Transaction>,
    )

    private val selectedPeriod = MutableStateFlow(Period.THIS_MONTH)
    private val refreshTick = MutableStateFlow(0)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        observeInsights()
    }

    fun selectPeriod(period: Period) {
        selectedPeriod.value = period
    }

    fun refresh() {
        refreshTick.value = refreshTick.value + 1
    }

    private fun observeInsights() {
        viewModelScope.launch(Dispatchers.IO) {
            combine(selectedPeriod, refreshTick) { period, _ -> period }
                .flatMapLatest { period ->
                    val range = resolveRange(period)
                    _uiState.value = _uiState.value.copy(
                        selectedPeriod = period,
                        periodLabel = range.label,
                        isLoading = true,
                    )
                    transactionRepository
                        .getByPeriod(range.startMillis, range.endExclusiveMillis - 1L)
                        .map { transactions -> PeriodTransactions(range, transactions) }
                }
                .collectLatest { snapshot ->
                    publishSnapshot(snapshot)
                }
        }
    }

    private suspend fun publishSnapshot(snapshot: PeriodTransactions) {
        val transactions = snapshot.transactions
        val totalIncome = transactions
            .filter { it.type == TransactionType.INCOME || it.type == TransactionType.RETURN }
            .sumOf { it.amount }
        val totalExpenses = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }
        val netCashFlow = totalIncome - totalExpenses
        val saleCount = transactions.count { it.type == TransactionType.INCOME }
        val averageSale = if (saleCount > 0) totalIncome / saleCount else 0.0
        val periodLabel = snapshot.range.label

        val productRevenue = saleLineItemDao.sumPosRevenueBetween(
            snapshot.range.startMillis,
            snapshot.range.endExclusiveMillis,
        )
        val serviceRevenue = serviceDeliveryDao.sumChargedInPeriod(
            snapshot.range.startMillis,
            snapshot.range.endExclusiveMillis - 1L,
        )

        val rulesInsights = cashFlowAnalyzer.generateRulesInsights(
            transactions = transactions,
            periodLabel = periodLabel,
        )

        val topProducts = saleLineItemDao.netProductSalesInPeriod(
            snapshot.range.startMillis,
            snapshot.range.endExclusiveMillis - 1L,
        ).filter { it.netQty > 0 }
            .sortedByDescending { it.netRevenue }
            .take(5)
            .map { TopProduct(it.productName, it.netRevenue, it.netQty) }

        val topServices = serviceDeliveryDao.netServiceSalesInPeriod(
            snapshot.range.startMillis,
            snapshot.range.endExclusiveMillis - 1L,
        ).take(5)
            .map { TopService(it.serviceName, it.revenue, it.sessions) }

        _uiState.value = UiState(
            selectedPeriod = snapshot.range.period,
            periodLabel = periodLabel,
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            netCashFlow = netCashFlow,
            transactionCount = transactions.size,
            averageSale = averageSale,
            lastUpdatedMillis = System.currentTimeMillis(),
            telemetryPoints = buildTelemetryPoints(snapshot.range, transactions),
            insightsText = rulesInsights,
            productRevenue = productRevenue,
            serviceRevenue = serviceRevenue,
            topProducts = topProducts,
            topServices = topServices,
            isLoading = false,
        )

        val aiInsights = cashFlowAnalyzer.tryEnhanceInsightsWithAi(
            transactions = transactions,
            language = Locale.getDefault().language,
            periodLabel = periodLabel,
        )
        if (aiInsights != null) {
            _uiState.value = _uiState.value.copy(insightsText = aiInsights)
        }
    }

    private fun resolveRange(period: Period): PeriodRange {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.MILLISECOND, 0)
        val label: String
        val startMillis: Long
        val endExclusiveMillis: Long

        when (period) {
            Period.TODAY -> {
                startOfDay(calendar)
                startMillis = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                endExclusiveMillis = calendar.timeInMillis
                label = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(startMillis))
            }
            Period.LAST_7_DAYS -> {
                startOfDay(calendar)
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                endExclusiveMillis = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_MONTH, -7)
                startMillis = calendar.timeInMillis
                label = "Last 7 days"
            }
            Period.LAST_30_DAYS -> {
                startOfDay(calendar)
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                endExclusiveMillis = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_MONTH, -30)
                startMillis = calendar.timeInMillis
                label = "Last 30 days"
            }
            Period.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                startOfDay(calendar)
                startMillis = calendar.timeInMillis
                calendar.add(Calendar.MONTH, 1)
                endExclusiveMillis = calendar.timeInMillis
                label = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(startMillis))
            }
        }
        return PeriodRange(period, startMillis, endExclusiveMillis, label)
    }

    private fun buildTelemetryPoints(
        range: PeriodRange,
        transactions: List<Transaction>,
    ): List<BusinessTelemetryPoint> {
        return when (range.period) {
            Period.TODAY -> buildHourlyPoints(range, transactions)
            Period.LAST_7_DAYS,
            Period.LAST_30_DAYS,
            Period.THIS_MONTH -> buildDailyPoints(range, transactions)
        }
    }

    private fun buildHourlyPoints(
        range: PeriodRange,
        transactions: List<Transaction>,
    ): List<BusinessTelemetryPoint> {
        val points = mutableListOf<BusinessTelemetryPoint>()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = range.startMillis
        val labelFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        repeat(6) {
            val bucketStart = calendar.timeInMillis
            calendar.add(Calendar.HOUR_OF_DAY, 4)
            val bucketEnd = minOf(calendar.timeInMillis, range.endExclusiveMillis)
            points += bucketPoint(
                label = labelFormat.format(Date(bucketStart)),
                transactions = transactions,
                bucketStart = bucketStart,
                bucketEnd = bucketEnd,
            )
        }
        return points
    }

    private fun buildDailyPoints(
        range: PeriodRange,
        transactions: List<Transaction>,
    ): List<BusinessTelemetryPoint> {
        val points = mutableListOf<BusinessTelemetryPoint>()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = range.startMillis
        val labelFormat = SimpleDateFormat("d MMM", Locale.getDefault())

        while (calendar.timeInMillis < range.endExclusiveMillis) {
            val bucketStart = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            val bucketEnd = minOf(calendar.timeInMillis, range.endExclusiveMillis)
            points += bucketPoint(
                label = labelFormat.format(Date(bucketStart)),
                transactions = transactions,
                bucketStart = bucketStart,
                bucketEnd = bucketEnd,
            )
        }
        return points
    }

    private fun bucketPoint(
        label: String,
        transactions: List<Transaction>,
        bucketStart: Long,
        bucketEnd: Long,
    ): BusinessTelemetryPoint {
        val bucketTransactions = transactions.filter { it.date >= bucketStart && it.date < bucketEnd }
        val income = bucketTransactions
            .filter { it.type == TransactionType.INCOME || it.type == TransactionType.RETURN }
            .sumOf { it.amount }
        val expenses = bucketTransactions
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }
        return BusinessTelemetryPoint(
            label = label,
            income = income,
            expenses = expenses,
            net = income - expenses,
        )
    }

    private fun startOfDay(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }
}
