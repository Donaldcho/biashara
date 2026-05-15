package com.biasharaai.agent

import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.CustomerDao
import com.biasharaai.data.local.db.DebtDao
import com.biasharaai.data.local.db.SaleLineItemDao
import com.biasharaai.data.local.db.Transaction
import com.biasharaai.data.local.db.TransactionDao
import com.biasharaai.data.local.db.TransactionType
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A7 — Compiles **last completed ISO week** (Mon 00:00 → next Mon 00:00, local zone) vs the prior week
 * for [WeeklyReviewWorker] + Gemma prompt context.
 */
@Singleton
class WeeklyReviewBuilder @Inject constructor(
    private val transactionDao: TransactionDao,
    private val saleLineItemDao: SaleLineItemDao,
    private val customerDao: CustomerDao,
    private val debtDao: DebtDao,
    private val appSettingsDao: AppSettingsDao,
) {

    data class WeeklyReviewStats(
        val businessName: String,
        val currencySymbol: String,
        val weekRevenue: Double,
        val lastWeekRevenue: Double,
        val txCount: Long,
        val topProduct: String,
        val topQty: Int,
        val topRevenue: Double,
        val slowProduct: String,
        val slowQty: Int,
        val newCustomers: Long,
        val returningCustomers: Long,
        val totalCredit: Double,
        val debtCustomerCount: Long,
        val bestDay: String,
        val bestHour: Int,
        /** Start of the reporting week (Monday 00:00 local) — dedupe + [AgentAction.relatedEntityId]. */
        val weekStartMillis: Long,
        val chipsForPayload: List<Pair<String, String>>,
    )

    suspend fun buildLastCompletedIsoWeek(zone: ZoneId, locale: Locale = Locale.getDefault()): WeeklyReviewStats {
        val now = ZonedDateTime.now(zone)
        val thisMonday = now.toLocalDate().with(DayOfWeek.MONDAY)
        val weekStartDate = thisMonday.minusWeeks(1)
        val weekEndExclusiveDate = thisMonday
        val prevWeekStartDate = thisMonday.minusWeeks(2)

        val weekStartMs = weekStartDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val weekEndExMs = weekEndExclusiveDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val prevWeekStartMs = prevWeekStartDate.atStartOfDay(zone).toInstant().toEpochMilli()

        val weekRev = transactionDao.sumIncomeAmountBetween(weekStartMs, weekEndExMs)
        val lastWeekRev = transactionDao.sumIncomeAmountBetween(prevWeekStartMs, weekStartMs)
        val txCount = transactionDao.countIncomeTransactionsBetween(weekStartMs, weekEndExMs)

        val lineEndInclusive = weekEndExMs - 1L
        val lines = saleLineItemDao.saleLinesInPeriod(weekStartMs, lineEndInclusive)
        val byProduct = lines.groupBy { it.productId }.mapValues { (_, rows) ->
            val qty = rows.sumOf { it.quantity }
            val rev = rows.sumOf { it.lineTotal }
            Triple(rows.first().productName, qty, rev)
        }
        val positive = byProduct.values.filter { it.second > 0 }
        val top = positive.maxByOrNull { it.second }
        val slow = positive.minByOrNull { it.second }

        val newCustomers = customerDao.countCustomersCreatedBetween(weekStartMs, weekEndExMs)
        val returningCustomers = customerDao.countReturningBuyerCustomersInWeek(weekStartMs, weekEndExMs)

        val totalCredit = debtDao.getTotalOutstandingOnce()
        val debtCustomerCount = debtDao.countCustomersWithOutstandingDebt()

        val weekIncomeTx = transactionDao.getTransactionsBetween(weekStartMs, lineEndInclusive)
            .filter { it.type == TransactionType.INCOME }

        val bestDay = bestTradingDayLabel(weekIncomeTx, zone, locale)
        val bestHour = bestTradingHour(weekIncomeTx, zone)

        val app = appSettingsDao.getSettingsSync()
        val businessName = app?.businessName?.takeIf { it.isNotBlank() } ?: "My shop"
        val currencySymbol = app?.currencySymbol?.takeIf { it.isNotBlank() } ?: "KSh"

        val money = { v: Double -> String.format(Locale.US, "%.2f", v) }
        val chips = buildList {
            add("Revenue" to "$currencySymbol${money(weekRev)}")
            add("Last week" to "$currencySymbol${money(lastWeekRev)}")
            add("Transactions" to txCount.toString())
            add("New customers" to newCustomers.toString())
            add("Returning" to returningCustomers.toString())
            add("Credit out" to "$currencySymbol${money(totalCredit)}")
            add("Debtors" to debtCustomerCount.toString())
            add("Best day" to bestDay)
            add("Best hour" to "${bestHour}:00")
            add("Top SKU" to (top?.first ?: "—"))
            add("Slow SKU" to (slow?.first ?: "—"))
        }

        return WeeklyReviewStats(
            businessName = businessName,
            currencySymbol = currencySymbol,
            weekRevenue = weekRev,
            lastWeekRevenue = lastWeekRev,
            txCount = txCount,
            topProduct = top?.first ?: "—",
            topQty = top?.second ?: 0,
            topRevenue = top?.third ?: 0.0,
            slowProduct = slow?.first ?: "—",
            slowQty = slow?.second ?: 0,
            newCustomers = newCustomers,
            returningCustomers = returningCustomers,
            totalCredit = totalCredit,
            debtCustomerCount = debtCustomerCount,
            bestDay = bestDay,
            bestHour = bestHour,
            weekStartMillis = weekStartMs,
            chipsForPayload = chips,
        )
    }

    private fun bestTradingDayLabel(
        income: List<Transaction>,
        zone: ZoneId,
        locale: Locale,
    ): String {
        if (income.isEmpty()) return "—"
        val byDow = income.groupBy { Instant.ofEpochMilli(it.date).atZone(zone).dayOfWeek }
        val best = byDow.maxByOrNull { (_, rows) -> rows.sumOf { it.amount } }?.key ?: return "—"
        return best.getDisplayName(TextStyle.FULL, locale)
    }

    private fun bestTradingHour(income: List<Transaction>, zone: ZoneId): Int {
        if (income.isEmpty()) return 0
        val byHour = income.groupBy { Instant.ofEpochMilli(it.date).atZone(zone).hour }
        return byHour.maxByOrNull { (_, rows) -> rows.sumOf { it.amount } }?.key ?: 0
    }
}
