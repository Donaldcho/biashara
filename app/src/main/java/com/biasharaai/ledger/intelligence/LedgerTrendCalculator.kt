package com.biasharaai.ledger.intelligence

import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.LedgerAmountPoint
import com.biasharaai.util.millisToLocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

data class LedgerWeekBucket(
    val weekKey: String,
    val moneyIn: Double,
    val moneyOut: Double,
)

data class LedgerTrendSnapshot(
    val window: String,
    val moneyInMonthlyGrowthRate: Double?,
    val moneyOutMonthlyGrowthRate: Double?,
    val netCashTrend: String,
    val weeks: List<Map<String, Any?>>,
    val movingAverage4WeekIn: Double?,
    val movingAverage12WeekIn: Double?,
    val expenseRatioTrend: String?,
)

object LedgerTrendCalculator {

    fun compute(
        entries: List<LedgerAmountPoint>,
        zone: ZoneId,
        windowWeeks: Int,
    ): LedgerTrendSnapshot {
        val weeks = bucketByWeek(entries, zone).takeLast(windowWeeks)
        val moneyInSeries = weeks.map { it.moneyIn }
        val moneyOutSeries = weeks.map { it.moneyOut }
        val netSeries = weeks.map { it.moneyIn - it.moneyOut }

        val ma4 = movingAverage(moneyInSeries, 4)
        val ma12 = movingAverage(moneyInSeries, 12)

        val growthIn = monthOverMonthGrowth(entries, zone, LedgerDirection.MONEY_IN)
        val growthOut = monthOverMonthGrowth(entries, zone, LedgerDirection.MONEY_OUT)

        val netTrend = when {
            netSeries.size < 2 -> "FLAT"
            netSeries.last() > netSeries.first() * 1.05 -> "GROWING"
            netSeries.last() < netSeries.first() * 0.95 -> "DECLINING"
            else -> "FLAT"
        }

        val expenseRatioFirst = ratio(moneyOutSeries.firstOrNull() ?: 0.0, moneyInSeries.firstOrNull() ?: 0.0)
        val expenseRatioLast = ratio(moneyOutSeries.lastOrNull() ?: 0.0, moneyInSeries.lastOrNull() ?: 0.0)
        val expenseRatioTrend = when {
            expenseRatioLast > expenseRatioFirst + 0.05 -> "RISING"
            expenseRatioLast < expenseRatioFirst - 0.05 -> "FALLING"
            else -> "STABLE"
        }

        return LedgerTrendSnapshot(
            window = "${windowWeeks}_WEEKS",
            moneyInMonthlyGrowthRate = growthIn,
            moneyOutMonthlyGrowthRate = growthOut,
            netCashTrend = netTrend,
            weeks = weeks.map {
                mapOf(
                    "weekKey" to it.weekKey,
                    "moneyIn" to it.moneyIn,
                    "moneyOut" to it.moneyOut,
                    "net" to (it.moneyIn - it.moneyOut),
                )
            },
            movingAverage4WeekIn = ma4,
            movingAverage12WeekIn = ma12,
            expenseRatioTrend = expenseRatioTrend,
        )
    }

    private fun bucketByWeek(entries: List<LedgerAmountPoint>, zone: ZoneId): List<LedgerWeekBucket> {
        val wf = WeekFields.of(Locale.getDefault())
        return entries
            .groupBy { e ->
                val d = millisToLocalDate(e.occurredAt, zone)
                "${d.get(wf.weekBasedYear())}-W${d.get(wf.weekOfWeekBasedYear())}"
            }
            .toSortedMap()
            .map { (key, list) ->
                LedgerWeekBucket(
                    weekKey = key,
                    moneyIn = list.filter { it.direction == LedgerDirection.MONEY_IN.name }.sumOf { it.amount },
                    moneyOut = list.filter { it.direction == LedgerDirection.MONEY_OUT.name }.sumOf { it.amount },
                )
            }
    }

    private fun movingAverage(series: List<Double>, window: Int): Double? {
        if (series.size < window) return null
        return series.takeLast(window).average()
    }

    private fun monthOverMonthGrowth(
        entries: List<LedgerAmountPoint>,
        zone: ZoneId,
        direction: LedgerDirection,
    ): Double? {
        val byMonth = entries
            .filter { it.direction == direction.name }
            .groupBy { millisToLocalDate(it.occurredAt, zone).let { d -> d.year * 100 + d.monthValue } }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .toSortedMap()
        if (byMonth.size < 2) return null
        val months = byMonth.values.toList()
        val prev = months[months.size - 2]
        val last = months.last()
        if (abs(prev) < 0.01) return null
        return (last - prev) / prev
    }

    private fun ratio(out: Double, `in`: Double): Double =
        if (`in` <= 0.0) 0.0 else out / `in`
}
