package com.biasharaai.chat.query

import android.content.Context
import com.biasharaai.data.local.db.ChatMemoryRepository
import com.biasharaai.data.local.db.CustomerDao
import com.biasharaai.data.local.db.DebtDao
import com.biasharaai.data.local.db.Transaction
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.data.local.db.SaleLineItemDao
import com.biasharaai.data.local.db.TransactionDao
import com.biasharaai.locale.LanguagePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rule-first conversational query layer: maps natural questions to Room-backed facts.
 * Covers the business-Q catalog where data exists in Biashara AI; unknown device/cashier
 * dimensions return an explicit "not stored" note. [GemmaAnswerFormatter] optionally rewrites
 * the factual string in the owner's language on PARTIAL_AI / FULL_AI.
 */
@Singleton
class ConversationalQueryLayer @Inject constructor(
    private val productDao: ProductDao,
    private val transactionDao: TransactionDao,
    private val saleLineItemDao: SaleLineItemDao,
    private val customerDao: CustomerDao,
    private val debtDao: DebtDao,
    private val gemmaAnswerFormatter: GemmaAnswerFormatter,
    private val chatMemoryRepository: ChatMemoryRepository,
    @ApplicationContext private val appContext: Context,
) {

    suspend fun tryStructuredAnswer(
        question: String,
        languageDisplayName: String,
    ): String? {
        val q = question.trim().lowercase(Locale.ROOT)
        if (q.length < 3) return null

        val tag = LanguagePreferences.getPersistedLocaleTag(appContext)?.substringBefore("-")
            ?: Locale.getDefault().language.lowercase(Locale.ROOT)
        val fmt = { v: Double -> String.format(Locale.forLanguageTag(tag), "%,.0f", v) }

        val txs: List<Transaction> = try {
            transactionDao.getTransactionsList()
        } catch (_: Exception) {
            return null
        }
        val products = try {
            productDao.getProductsList()
        } catch (_: Exception) {
            emptyList()
        }
        val customers = try {
            customerDao.getAllCustomers().first()
        } catch (_: Exception) {
            emptyList()
        }
        val unpaidDebts = try {
            debtDao.getUnpaidDebts().first()
        } catch (_: Exception) {
            emptyList()
        }
        val outstanding = try {
            debtDao.getTotalOutstanding().first()
        } catch (_: Exception) {
            0.0
        }
        val sinceMillis = System.currentTimeMillis() - DAYS_ANALYTICS * MS_PER_DAY
        val lines = try {
            saleLineItemDao.posSaleLineFactsSince(sinceMillis)
        } catch (_: Exception) {
            emptyList()
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val productById = products.associateBy { it.id }

        fun extractCostOrPriceNeedle(raw: String): String? {
            val ql = raw.trim().lowercase(Locale.ROOT).removeSuffix("?").removeSuffix(".").trim()
            val patterns = listOf(
                Regex(
                    """(?:what\s+is|what's|whats)\s+the\s+(?:cost|price)\s+of\s+(?:a|an|the|one|1|each)?\s*(.+)$""",
                ),
                Regex("""(?:cost|price)\s+of\s+(?:a|an|the|one|1|each)?\s*(.+)$"""),
                Regex(
                    """how\s+much\s+(?:is|does)\s+(?:a|an|the|one|1)?\s*(.+?)(?:\s+cost|\s+sell|\s+go\s+for)?$""",
                ),
                Regex("""how\s+much\s+for\s+(?:a|an|the|one|1)?\s*(.+)$"""),
            )
            for (p in patterns) {
                val m = p.find(ql) ?: continue
                val rawG = m.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim() ?: continue
                val cleaned = rawG
                    .removeSuffix("?")
                    .removeSuffix(".")
                    .trim()
                    .removePrefix("a ")
                    .removePrefix("an ")
                    .removePrefix("the ")
                    .removePrefix("one ")
                    .removePrefix("1 ")
                    .trim()
                if (cleaned.length in 2..50) return cleaned
            }
            return null
        }

        val costNeedle = extractCostOrPriceNeedle(q)

        fun dayRange(d: LocalDate): Pair<Long, Long> {
            val s = d.atStartOfDay(zone).toInstant().toEpochMilli()
            val e = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            return s to e
        }

        fun monthContaining(d: LocalDate): Pair<LocalDate, LocalDate> {
            val start = d.with(TemporalAdjusters.firstDayOfMonth())
            val end = d.with(TemporalAdjusters.lastDayOfMonth())
            return start to end
        }

        val (t0, t1) = dayRange(today)
        val yest = today.minusDays(1)
        val (y0, y1) = dayRange(yest)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val (w0, _) = dayRange(weekStart)
        val (_, wEnd) = dayRange(today)
        val monthStart = today.with(TemporalAdjusters.firstDayOfMonth())
        val (m0, m1) = dayRange(monthStart).let { it.first to dayRange(today).second }
        val prevWeekStart = weekStart.minusWeeks(1)
        val prevWeekEnd = weekStart.minusDays(1)
        val (prevWeek0, _) = dayRange(prevWeekStart)
        val (_, prevWeek1) = dayRange(prevWeekEnd)
        val nowMs = System.currentTimeMillis()
        val fourteenDaysAgo = nowMs - 14L * MS_PER_DAY
        val yearStart = today.with(TemporalAdjusters.firstDayOfYear())
        val (yStart, yEnd) = dayRange(yearStart).first to dayRange(today).second
        val lastMonth = today.minusMonths(1)
        val (lmStart, lmEnd) = monthContaining(lastMonth).let { (a, b) ->
            dayRange(a).first to dayRange(b).second
        }
        val d90 = today.minusDays(89)
        val (d90s, _) = dayRange(d90)
        val (_, nowEnd) = dayRange(today)

        val polishHints = buildStructuredPolishHints(
            txs = txs,
            today = today,
            t0 = t0,
            t1 = t1,
            y0 = y0,
            y1 = y1,
            m0 = m0,
            m1 = m1,
            outstanding = outstanding,
            fmt = fmt,
        )
        val memoryPrefix = chatMemoryRepository.formatMemoryPrefixForFacts()

        if (costNeedle != null && (q.contains("cost") || q.contains("price") || q.contains("how much"))) {
            val m = products.filter { it.name.contains(costNeedle, ignoreCase = true) }
            if (m.isNotEmpty()) {
                val body = m.joinToString("; ") {
                    "${it.name}: unit cost ${fmt(it.cost)}, list price ${fmt(it.price)}, stock ${it.stockQuantity}."
                }
                return gemmaAnswerFormatter.maybePolish(
                    question,
                    memoryPrefix + body,
                    languageDisplayName,
                    polishHints,
                )
            }
        }

        val ctx = StructuredQueryContext(
            question = question,
            languageDisplayName = languageDisplayName,
            q = q,
            fmt = fmt,
            txs = txs,
            products = products,
            customers = customers,
            unpaidDebts = unpaidDebts,
            outstanding = outstanding,
            lines = lines,
            zone = zone,
            today = today,
            productById = productById,
            t0 = t0,
            t1 = t1,
            y0 = y0,
            y1 = y1,
            w0 = w0,
            wEnd = wEnd,
            m0 = m0,
            m1 = m1,
            prevWeek0 = prevWeek0,
            prevWeek1 = prevWeek1,
            nowMs = nowMs,
            fourteenDaysAgo = fourteenDaysAgo,
            yStart = yStart,
            yEnd = yEnd,
            lmStart = lmStart,
            lmEnd = lmEnd,
            d90s = d90s,
            nowEnd = nowEnd,
            costNeedle = costNeedle,
        )
        return runStructuredQueryRoutes(ctx) { qq, t, ll ->
            gemmaAnswerFormatter.maybePolish(qq, memoryPrefix + t, ll, polishHints)
        }
    }

    companion object {
        private const val DAYS_ANALYTICS = 400L
        private const val MS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
