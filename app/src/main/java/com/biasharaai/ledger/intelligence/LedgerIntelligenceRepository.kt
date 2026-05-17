package com.biasharaai.ledger.intelligence

import com.biasharaai.data.local.db.CashCountDao
import com.biasharaai.data.local.db.LedgerContext
import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.LedgerEntryDao
import com.biasharaai.data.local.db.LedgerEntryType
import com.biasharaai.ledger.LedgerContextRepository
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic ledger aggregates for agents (Ledger Intelligence v2).
 * All financial facts for skills originate here — not from LLM inference.
 */
@Singleton
class LedgerIntelligenceRepository @Inject constructor(
    private val ledgerEntryDao: LedgerEntryDao,
    private val cashCountDao: CashCountDao,
    private val ledgerContextRepository: LedgerContextRepository,
) {

    suspend fun queryV2(
        period: String,
        timezone: String,
        include: Set<String>,
    ): Map<String, Any?> {
        val bounds = LedgerPeriodResolver.resolve(period, timezone)
        val from = bounds.startMillis
        val toInclusive = bounds.endExclusiveMillis - 1
        val zone = ZoneId.of(bounds.timezone)

        val result = linkedMapOf<String, Any?>(
            "version" to 2,
            "period" to mapOf(
                "startMillis" to bounds.startMillis,
                "endExclusiveMillis" to bounds.endExclusiveMillis,
                "timezone" to bounds.timezone,
            ),
        )

        if (shouldInclude(include, "summary")) {
            val totals = ledgerEntryDao.getTotalsByDirection(from, toInclusive)
                .associate { it.direction to it.total }
            val moneyIn = totals[LedgerDirection.MONEY_IN.name] ?: 0.0
            val moneyOut = totals[LedgerDirection.MONEY_OUT.name] ?: 0.0
            result["summary"] = mapOf(
                "moneyIn" to moneyIn,
                "moneyOut" to moneyOut,
                "net" to (moneyIn - moneyOut),
                "runningBalance" to (ledgerEntryDao.getCurrentBalance() ?: 0.0),
            )
        }

        if (shouldInclude(include, "breakdown")) {
            result["breakdown"] = buildBreakdown(from, toInclusive)
        }

        if (shouldInclude(include, "pending_credit")) {
            val pending = ledgerEntryDao.getPendingCreditSummary()
            result["pendingCredit"] = mapOf(
                "amount" to pending.amount,
                "count" to pending.count,
            )
        }

        if (shouldInclude(include, "cash_reconciliation")) {
            result["cashReconciliation"] = buildCashReconciliation()
        }

        if (shouldInclude(include, "hourly")) {
            result["hourly"] = buildHourly(from, toInclusive, zone)
        }

        if (shouldInclude(include, "trend")) {
            val entries = ledgerEntryDao.getAmountPointsForReport(
                bounds.endExclusiveMillis - TWELVE_WEEKS_MS,
                toInclusive,
            )
            val trend = LedgerTrendCalculator.compute(entries, zone, windowWeeks = 12)
            result["trend"] = mapOf(
                "window" to trend.window,
                "moneyInMonthlyGrowthRate" to trend.moneyInMonthlyGrowthRate,
                "moneyOutMonthlyGrowthRate" to trend.moneyOutMonthlyGrowthRate,
                "netCashTrend" to trend.netCashTrend,
                "movingAverage4WeekIn" to trend.movingAverage4WeekIn,
                "movingAverage12WeekIn" to trend.movingAverage12WeekIn,
                "expenseRatioTrend" to trend.expenseRatioTrend,
                "weeks" to trend.weeks,
            )
        }

        if (shouldInclude(include, "context")) {
            result["context"] = buildContextPayload(from, toInclusive)
        }

        return result
    }

    suspend fun queryByHour(
        windowDays: Int,
        timezone: String,
    ): Map<String, Any?> {
        val requestedDays = windowDays.coerceIn(1, 90)
        val bounds = LedgerPeriodResolver.resolveLastDays(requestedDays, timezone)
        val zone = ZoneId.of(bounds.timezone)
        return mapOf(
            "timezone" to bounds.timezone,
            "windowDays" to requestedDays,
            "hours" to buildHourly(bounds.startMillis, bounds.endExclusiveMillis - 1, zone),
        )
    }

    suspend fun queryTrend(
        windowWeeks: Int,
        timezone: String,
    ): Map<String, Any?> {
        val bounds = LedgerPeriodResolver.resolve("LAST_30_DAYS", timezone)
        val toInclusive = bounds.endExclusiveMillis - 1
        val from = bounds.endExclusiveMillis - windowWeeks * 7L * 24L * 60L * 60L * 1000L
        val zone = ZoneId.of(bounds.timezone)
        val entries = ledgerEntryDao.getAmountPointsForReport(from, toInclusive)
        val trend = LedgerTrendCalculator.compute(entries, zone, windowWeeks.coerceIn(4, 12))
        return mapOf(
            "window" to trend.window,
            "moneyInMonthlyGrowthRate" to trend.moneyInMonthlyGrowthRate,
            "moneyOutMonthlyGrowthRate" to trend.moneyOutMonthlyGrowthRate,
            "netCashTrend" to trend.netCashTrend,
            "movingAverage4WeekIn" to trend.movingAverage4WeekIn,
            "movingAverage12WeekIn" to trend.movingAverage12WeekIn,
            "expenseRatioTrend" to trend.expenseRatioTrend,
            "weeks" to trend.weeks,
        )
    }

    private suspend fun buildBreakdown(from: Long, to: Long): Map<String, Double> {
        val rows = ledgerEntryDao.getBreakdownByType(from, to)
        val totalsByType = rows.associate { it.type to it.total }
        fun sumTypes(vararg types: LedgerEntryType): Double =
            types.sumOf { totalsByType[it.name] ?: 0.0 }

        return mapOf(
            "productSales" to sumTypes(LedgerEntryType.SALE_PRODUCT),
            "services" to sumTypes(LedgerEntryType.SALE_SERVICE),
            "vouchers" to sumTypes(LedgerEntryType.VOUCHER_SALE),
            "debtRepayments" to sumTypes(LedgerEntryType.DEBT_REPAID),
            "manualIncome" to sumTypes(LedgerEntryType.OTHER_INCOME, LedgerEntryType.ADJUSTMENT),
            "expenses" to sumTypes(
                LedgerEntryType.EXPENSE,
                LedgerEntryType.STOCK_PURCHASE,
                LedgerEntryType.REFUND,
                LedgerEntryType.WARRANTY_CLAIM,
            ),
        )
    }

    private suspend fun buildCashReconciliation(): Map<String, Any?>? {
        val latest = cashCountDao.getLatest() ?: return null
        return mapOf(
            "lastCountedAt" to latest.countedAt,
            "expectedCash" to latest.expectedBalance,
            "countedCash" to latest.actualBalance,
            "variance" to latest.difference,
        )
    }

    private suspend fun buildHourly(from: Long, to: Long, zone: ZoneId): List<Map<String, Any?>> {
        val entries = ledgerEntryDao.getAmountPointsForReport(from, to)
        val moneyIn = DoubleArray(HOURS_PER_DAY)
        val moneyOut = DoubleArray(HOURS_PER_DAY)
        val transactionCount = IntArray(HOURS_PER_DAY)
        val moneyInCount = IntArray(HOURS_PER_DAY)

        entries.forEach { entry ->
            val hour = Instant.ofEpochMilli(entry.occurredAt).atZone(zone).hour
            transactionCount[hour] += 1
            when (entry.direction) {
                LedgerDirection.MONEY_IN.name -> {
                    moneyIn[hour] += entry.amount
                    moneyInCount[hour] += 1
                }
                LedgerDirection.MONEY_OUT.name -> moneyOut[hour] += entry.amount
            }
        }

        return (0 until HOURS_PER_DAY)
            .filter { transactionCount[it] > 0 }
            .map { hour ->
                mapOf(
                    "hour" to hour,
                    "moneyIn" to moneyIn[hour],
                    "moneyOut" to moneyOut[hour],
                    "transactionCount" to transactionCount[hour],
                    "averageTicket" to if (moneyInCount[hour] > 0) moneyIn[hour] / moneyInCount[hour] else 0.0,
                )
            }
    }

    private suspend fun buildContextPayload(from: Long, to: Long): List<Map<String, Any?>> =
        ledgerContextRepository.getActiveForPeriod(from, to).map { it.toPayload() }

    private fun LedgerContext.toPayload(): Map<String, Any?> = mapOf(
        "id" to id,
        "contextType" to contextType,
        "prompt" to prompt,
        "ownerAnswer" to ownerAnswer,
        "source" to source,
        "confidence" to confidence,
        "appliesFromMillis" to appliesFromMillis,
        "appliesToMillis" to appliesToMillis,
        "relatedAnomalyId" to relatedAnomalyId,
    )

    private fun shouldInclude(include: Set<String>, section: String): Boolean =
        if (include.isEmpty()) {
            section == "summary"
        } else {
            section in include || "all" in include
        }

    companion object {
        private const val TWELVE_WEEKS_MS = 12L * 7L * 24L * 60L * 60L * 1000L
        private const val HOURS_PER_DAY = 24
    }
}
