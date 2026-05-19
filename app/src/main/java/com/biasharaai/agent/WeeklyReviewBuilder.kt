package com.biasharaai.agent

import com.biasharaai.data.local.db.AgentSetting
import com.biasharaai.data.local.db.AgentSettingDao
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.CustomerDao
import com.biasharaai.data.local.db.DebtDao
import com.biasharaai.analytics.SalesIntelligenceRepository
import com.biasharaai.data.local.db.Transaction
import com.biasharaai.data.local.db.ServiceDeliveryDao
import com.biasharaai.data.local.db.ServiceItemDao
import com.biasharaai.data.local.db.TransactionDao
import com.biasharaai.data.local.db.TransactionType
import com.biasharaai.money.RegionalDefaults
import com.biasharaai.productline.ProductLineManager
import com.biasharaai.service.ServiceInsightsHelper
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
    private val salesIntelligence: SalesIntelligenceRepository,
    private val customerDao: CustomerDao,
    private val debtDao: DebtDao,
    private val appSettingsDao: AppSettingsDao,
    private val serviceDeliveryDao: ServiceDeliveryDao,
    private val serviceItemDao: ServiceItemDao,
    private val productLineManager: ProductLineManager,
    private val agentSettingDao: AgentSettingDao,
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
        val productRevenue: Double,
        val serviceSalesRevenue: Double,
        val serviceStats: ServiceInsightsHelper.PeriodServiceStats?,
        val servicePromptBlock: String,
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

        val weekSummary = salesIntelligence.periodSummary(weekStartMs, weekEndExMs)
        val lastWeekSummary = salesIntelligence.periodSummary(prevWeekStartMs, weekStartMs)
        val weekRev = weekSummary.netRevenue
        val lastWeekRev = lastWeekSummary.netRevenue
        val txCount = transactionDao.countIncomeTransactionsBetween(weekStartMs, weekEndExMs)
        val serviceSalesRev = weekSummary.grossServiceSubtotal

        val lineEndInclusive = weekEndExMs - 1L
        val netRanks = salesIntelligence.netProductRanksInPeriod(weekStartMs, weekEndExMs)
        val top = netRanks.maxByOrNull { it.netQty }
        val slow = netRanks.filter { it.netQty > 0 }.minByOrNull { it.netQty }

        val newCustomers = customerDao.countCustomersCreatedBetween(weekStartMs, weekEndExMs)
        val returningCustomers = customerDao.countReturningBuyerCustomersInWeek(weekStartMs, weekEndExMs)

        val totalCredit = debtDao.getTotalOutstandingOnce()
        val debtCustomerCount = debtDao.countCustomersWithOutstandingDebt()

        val weekIncomeTx = transactionDao.getTransactionsBetween(weekStartMs, lineEndInclusive)
            .filter { it.type == TransactionType.INCOME || it.type == TransactionType.RETURN }

        val bestDay = bestTradingDayLabel(weekIncomeTx, zone, locale)
        val bestHour = bestTradingHour(weekIncomeTx, zone)

        val app = appSettingsDao.getSettingsSync()
        val businessName = app?.businessName?.takeIf { it.isNotBlank() } ?: "My shop"
        val currencySymbol = app?.currencySymbol?.takeIf { it.isNotBlank() } ?: RegionalDefaults.CURRENCY_SYMBOL

        val agentSettings = agentSettingDao.getSettingsSync() ?: AgentSetting()
        val serviceStats = if (productLineManager.isProEnabled()) {
            ServiceInsightsHelper.buildForPeriod(
                serviceDeliveryDao,
                serviceItemDao,
                agentSettings,
                weekStartMs,
                weekEndExMs,
            )
        } else {
            null
        }
        val serviceBlock = when {
            serviceStats != null && (serviceStats.hasActivity() || serviceStats.activeServiceCount > 0) ->
                ServiceInsightsHelper.formatForAgentPrompt(serviceStats, currencySymbol)
            productLineManager.isProEnabled() ->
                "Services: Pro enabled but no service deliveries recorded this week. Record service sales on the Sales tab (Services or Both mode)."
            else -> ""
        }

        val money = { v: Double -> String.format(Locale.US, "%.2f", v) }
        val chips = buildList {
            add("Revenue (net)" to "$currencySymbol${money(weekRev)}")
            add("Last week (net)" to "$currencySymbol${money(lastWeekRev)}")
            if (weekSummary.returnTransactionCount > 0) {
                add("Returns" to "$currencySymbol${money(weekSummary.returnsTotal)} (${weekSummary.returnTransactionCount})")
            }
            add("Product sales" to "$currencySymbol${money(weekSummary.grossProductSubtotal)}")
            if (productLineManager.isProEnabled()) {
                add("Service sales" to "$currencySymbol${money(serviceSalesRev)}")
                serviceStats?.let { s ->
                    add("Service visits" to s.deliveryCount.toString())
                    add("Top service" to s.topServiceName)
                    add("Utilisation" to "${s.utilisationPct}%")
                }
            }
            add("Transactions" to txCount.toString())
            add("New customers" to newCustomers.toString())
            add("Returning" to returningCustomers.toString())
            add("Credit out" to "$currencySymbol${money(totalCredit)}")
            add("Debtors" to debtCustomerCount.toString())
            add("Best day" to bestDay)
            add("Best hour" to "${bestHour}:00")
            add("Top SKU" to (top?.name ?: "—"))
            add("Slow SKU" to (slow?.name ?: "—"))
        }

        return WeeklyReviewStats(
            businessName = businessName,
            currencySymbol = currencySymbol,
            weekRevenue = weekRev,
            lastWeekRevenue = lastWeekRev,
            txCount = txCount,
            topProduct = top?.name ?: "—",
            topQty = top?.netQty ?: 0,
            topRevenue = top?.netRevenue ?: 0.0,
            slowProduct = slow?.name ?: "—",
            slowQty = slow?.netQty ?: 0,
            newCustomers = newCustomers,
            returningCustomers = returningCustomers,
            totalCredit = totalCredit,
            debtCustomerCount = debtCustomerCount,
            bestDay = bestDay,
            bestHour = bestHour,
            weekStartMillis = weekStartMs,
            chipsForPayload = chips,
            productRevenue = weekSummary.grossProductSubtotal,
            serviceSalesRevenue = serviceSalesRev,
            serviceStats = serviceStats,
            servicePromptBlock = serviceBlock,
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
