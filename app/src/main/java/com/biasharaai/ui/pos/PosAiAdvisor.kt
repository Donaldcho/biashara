package com.biasharaai.ui.pos

import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.GemmaService
import com.biasharaai.data.local.db.SaleLineItemDao
import com.biasharaai.data.local.db.TransactionRepository
import com.biasharaai.data.local.db.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class EndOfDayStats(
    val totalSales: Double,
    val transactionCount: Int,
    val topProductName: String,
    val topProductQty: Int,
    val cashPct: Int,
    val mobilePct: Int,
    val creditPct: Int,
)

@Singleton
class PosAiAdvisor @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val saleLineItemDao: SaleLineItemDao,
    private val gemmaService: GemmaService,
) {

    suspend fun loadTodayStats(): EndOfDayStats = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val end = cal.timeInMillis - 1

        val txs = transactionRepository.getTransactionsBetween(start, end)
        val lines = saleLineItemDao.saleLinesInPeriod(start, end)
        val totalSales = lines.sumOf { it.lineTotal }
        val txIds = lines.map { it.transactionId }.toSet()
        val txCount = txIds.size

        val byProductId = lines.groupBy { it.productId }
        val topEntry = byProductId.maxByOrNull { (_, rows) -> rows.sumOf { it.quantity } }
        val topQty = topEntry?.value?.sumOf { it.quantity } ?: 0
        val topName = topEntry?.value?.firstOrNull()?.productName ?: "—"

        val posSales = txs.filter { it.type == TransactionType.INCOME && it.id in txIds }
        var cash = 0
        var mobile = 0
        var credit = 0
        for (t in posSales) {
            when (t.paymentMethod.uppercase(Locale.US)) {
                "CASH" -> cash++
                "MOBILE_MONEY", "MOBILE MONEY" -> mobile++
                "CREDIT" -> credit++
                else -> cash++
            }
        }
        val denom = (cash + mobile + credit).coerceAtLeast(1)
        val cashPct = (100.0 * cash / denom).toInt().coerceIn(0, 100)
        val mobilePct = (100.0 * mobile / denom).toInt().coerceIn(0, 100)
        val creditPct = (100.0 * credit / denom).toInt().coerceIn(0, 100)

        EndOfDayStats(
            totalSales = totalSales,
            transactionCount = txCount,
            topProductName = topName,
            topProductQty = topQty,
            cashPct = cashPct,
            mobilePct = mobilePct,
            creditPct = creditPct,
        )
    }

    fun buildEndOfDayPrompt(
        stats: EndOfDayStats,
        businessName: String,
        language: String,
        currency: String,
    ): String = """
        You are a friendly business advisor. Respond in $language. Under 150 words.
        Today's trading summary for $businessName:
        Total sales: ${stats.totalSales} $currency.
        Number of transactions: ${stats.transactionCount}.
        Top selling product: ${stats.topProductName} (${stats.topProductQty} units).
        Payment methods: Cash ${stats.cashPct}%, Mobile Money ${stats.mobilePct}%, Credit ${stats.creditPct}%.
        Write a warm, encouraging end-of-day summary. Mention the top seller.
        Give one practical tip for tomorrow based on the data.
    """.trimIndent()

    suspend fun generateEndOfDaySummary(
        stats: EndOfDayStats,
        businessName: String,
        language: String,
        currency: String,
        tier: CapabilityTier,
    ): String = withContext(Dispatchers.IO) {
        if (tier != CapabilityTier.FULL_AI || !gemmaService.isAvailable) {
            return@withContext rulesFallback(stats, businessName, currency)
        }
        val prompt = buildEndOfDayPrompt(stats, businessName, language, currency)
        runCatching { gemmaService.generateResponse(prompt).trim() }
            .getOrElse { rulesFallback(stats, businessName, currency) }
    }

    private fun rulesFallback(stats: EndOfDayStats, businessName: String, currency: String): String =
        buildString {
            appendLine("Great work at $businessName today.")
            appendLine(
                "You recorded ${stats.transactionCount} sale(s) for a total of " +
                    "${stats.totalSales} $currency.",
            )
            if (stats.topProductQty > 0) {
                appendLine("Top seller: ${stats.topProductName} (${stats.topProductQty} units).")
            }
            append("Payment mix: Cash ${stats.cashPct}%, Mobile ${stats.mobilePct}%, Credit ${stats.creditPct}%. ")
            appendLine("Review stock levels before opening tomorrow.")
        }.trim()
}
