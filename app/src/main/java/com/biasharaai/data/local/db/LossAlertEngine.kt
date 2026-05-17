package com.biasharaai.data.local.db

import com.biasharaai.util.millisToLocalDate
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure Room + calendar-window analysis for loss alerts (Prompt U5). No Gemma — English
 * templates only; translation happens in [com.biasharaai.loss.LossAlertWorker].
 */
@Singleton
class LossAlertEngine @Inject constructor(
    private val lossAlertDao: LossAlertDao,
    private val productDao: ProductDao,
) {

    suspend fun getStockShrinkageAlerts(nowMillis: Long): List<Alert> {
        val since = nowMillis - STOCK_SALE_LOOKBACK_MS
        val products = lossAlertDao.getProductsWithUnexplainedStockDrop(
            threshold = LOW_STOCK_THRESHOLD,
            sinceMillis = since,
        )
        return products.map { p ->
            Alert(
                title = "Possible stock loss",
                message = "Product \"${p.name}\" is below $LOW_STOCK_THRESHOLD units and has no POS sale " +
                    "recorded in the last 14 days. Check for theft, spoilage, or unrecorded sales.",
                severity = "WARN",
                createdAt = nowMillis,
                read = false,
                alertType = LossAlertTypes.SHRINKAGE,
                productId = p.id,
                dedupeKey = "LOSS_SHRINK_${p.id}",
                localizedMessage = null,
                relatedTransactionId = null,
            )
        }
    }

    suspend fun getSalesGapAlerts(nowMillis: Long, zoneOffset: ZoneOffset = ZoneOffset.UTC): List<Alert> {
        val since = nowMillis - SALES_GAP_LOOKBACK_MS
        val rows = lossAlertDao.getProductSaleQuantitiesByDay(since)
        val idToName = productDao.getProductsList().associate { it.id to it.name }
        val byProduct = rows.groupBy { it.productId }
        val today = millisToLocalDate(nowMillis, zoneOffset)
        val out = mutableListOf<Alert>()
        for ((productId, list) in byProduct) {
            val dayToQty = list.associate { LocalDate.parse(it.day) to it.totalQty }
            for (endOffset in 0L..25L) {
                val endDay = today.minusDays(endOffset)
                val sevenDays = (0L..6L).map { endDay.minusDays(6L - it) }
                val threeQuiet = (1L..3L).map { endDay.plusDays(it) }
                val hadSeven = sevenDays.all { d -> (dayToQty[d] ?: 0L) > 0L }
                val quietThree = threeQuiet.all { d -> (dayToQty[d] ?: 0L) == 0L }
                if (!hadSeven || !quietThree) continue
                val lastQuiet = threeQuiet.last()
                val daysSinceQuietEnd = ChronoUnit.DAYS.between(lastQuiet, today)
                if (daysSinceQuietEnd in 0L..5L) {
                    val name = idToName[productId] ?: "Product #$productId"
                    out.add(
                        Alert(
                            title = "Sales slowdown",
                            message = "\"$name\" had sales every day for a week, then no POS sales for " +
                                "three days in a row. Worth checking shelves, pricing, or competitors.",
                            severity = "WARN",
                            createdAt = nowMillis,
                            read = false,
                            alertType = LossAlertTypes.SALES_GAP,
                            productId = productId,
                            dedupeKey = "LOSS_GAP_${productId}_${endDay}",
                            localizedMessage = null,
                            relatedTransactionId = null,
                        ),
                    )
                    break
                }
            }
        }
        return out
    }

    suspend fun getLowPriceSaleAlerts(nowMillis: Long): List<Alert> {
        val since = nowMillis - LOW_PRICE_LOOKBACK_MS
        val txs = lossAlertDao.getSalesWithLowLinePrice(since)
        return txs.map { t ->
            Alert(
                title = "Unusually low sale price",
                message = "A recent sale (receipt-related) had a line price under 70% of the product's " +
                    "current list price. Review discounts, overrides, or possible mistakes.",
                severity = "WARN",
                createdAt = nowMillis,
                read = false,
                alertType = LossAlertTypes.LOW_PRICE,
                productId = null,
                dedupeKey = "LOSS_LOWPRICE_${t.id}",
                localizedMessage = null,
                relatedTransactionId = t.id,
            )
        }
    }

    suspend fun getHighExpenseDayAlerts(nowMillis: Long, zoneOffset: ZoneOffset = ZoneOffset.UTC): List<Alert> {
        val windowStart = nowMillis - EXPENSE_ANALYSIS_WINDOW_MS
        val expenses = lossAlertDao.getExpensesSince(windowStart)
        if (expenses.isEmpty()) return emptyList()
        val byDay = expenses.groupBy { tx ->
            millisToLocalDate(tx.date, zoneOffset)
        }.mapValues { (_, list) -> list.sumOf { it.amount } }
            .toSortedMap()
        val sortedDays = byDay.keys.toList()
        if (sortedDays.size < 8) return emptyList()
        val out = mutableListOf<Alert>()
        for (i in sortedDays.indices) {
            val day = sortedDays[i]
            val dayTotal = byDay[day] ?: continue
            val windowStartDay = day.minusDays(30)
            val priorDays = sortedDays.filter { it < day && !it.isBefore(windowStartDay) }
            if (priorDays.size < 5) continue
            val priorTotals = priorDays.mapNotNull { byDay[it] }
            if (priorTotals.isEmpty()) continue
            val avg = priorTotals.average()
            if (avg <= 0.0) continue
            if (dayTotal > avg * 3.0) {
                val dayStart = day.atStartOfDay(zoneOffset).toInstant().toEpochMilli()
                out.add(
                    Alert(
                        title = "High expense day",
                        message = "On $day, expenses totaled ${"%.0f".format(dayTotal)}, about three times your " +
                            "trailing 30-day daily average (${"%.0f".format(avg)}). Review large purchases or one-off costs.",
                        severity = "WARN",
                        createdAt = nowMillis,
                        read = false,
                        alertType = LossAlertTypes.HIGH_EXPENSE,
                        productId = null,
                        dedupeKey = "LOSS_EXPENSE_$dayStart",
                        localizedMessage = null,
                        relatedTransactionId = null,
                    ),
                )
            }
        }
        return out
    }

    suspend fun runAllDetections(nowMillis: Long, zoneOffset: ZoneOffset = ZoneOffset.UTC): List<Alert> =
        getStockShrinkageAlerts(nowMillis) +
            getSalesGapAlerts(nowMillis, zoneOffset) +
            getLowPriceSaleAlerts(nowMillis) +
            getHighExpenseDayAlerts(nowMillis, zoneOffset)

    companion object {
        private const val LOW_STOCK_THRESHOLD = 5
        private const val STOCK_SALE_LOOKBACK_MS = 14L * 24L * 60L * 60L * 1000L
        private const val SALES_GAP_LOOKBACK_MS = 45L * 24L * 60L * 60L * 1000L
        private const val LOW_PRICE_LOOKBACK_MS = 30L * 24L * 60L * 60L * 1000L
        private const val EXPENSE_ANALYSIS_WINDOW_MS = 60L * 24L * 60L * 60L * 1000L
    }
}
