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
import com.biasharaai.profile.BusinessProfileRepository
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
    private val businessProfileRepository: BusinessProfileRepository,
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

        val profileAnswer = businessProfileAnswer(q, fmt)
        if (profileAnswer != null) {
            return gemmaAnswerFormatter.maybePolish(
                question,
                memoryPrefix + profileAnswer,
                languageDisplayName,
                polishHints,
            )
        }

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

    private suspend fun businessProfileAnswer(
        q: String,
        fmt: (Double) -> String,
    ): String? {
        val profile = try {
            businessProfileRepository.getOrCreate()
        } catch (_: Exception) {
            return null
        }
        if (!profile.hasIdentity()) return null

        fun hasAny(vararg needles: String): Boolean = needles.any { q.contains(it) }
        fun valueOrMissing(label: String, value: String?): String =
            "$label: ${value?.takeIf { it.isNotBlank() } ?: "not recorded"}."

        if (hasAny("what do you know about my business", "summarize my business", "summarise my business", "my business profile")) {
            val target = profile.targetCustomer?.takeIf { it.isNotBlank() } ?: "not recorded"
            val offer = profile.whatTheyOffer() ?: "not recorded"
            val location = profile.location?.takeIf { it.isNotBlank() } ?: "not recorded"
            val goal = profile.businessGoal?.takeIf { it.isNotBlank() } ?: "not recorded"
            val targetMoney = profile.monthlyRevenueTarget?.let(fmt) ?: "not set"
            return "Business profile: ${profile.businessName}. Owner: ${profile.ownerName.ifBlank { "not recorded" }}. " +
                "Type: ${profile.businessType}. Offers: $offer. Target customers: $target. " +
                "Location: $location. Monthly revenue target: $targetMoney. Goal: $goal."
        }

        if (hasAny("business name", "name of my business", "what is my business called")) {
            return "Your business name is ${profile.businessName}."
        }
        if (hasAny("owner name", "who owns", "who am i", "my name")) {
            return valueOrMissing("Owner", profile.ownerName)
        }
        if (hasAny("what do i sell", "what do we sell", "what do i offer", "what does my business offer", "services offered", "products sold")) {
            return "Recorded offer: ${profile.whatTheyOffer() ?: "not recorded"}."
        }
        if (hasAny("business type", "type of business")) {
            return "Business type: ${profile.businessType}."
        }
        if (hasAny("target customer", "target customers", "who are my customers", "main customers")) {
            return valueOrMissing("Target customers", profile.targetCustomer)
        }
        if (hasAny("where is my business", "business location", "where are we located", "my location")) {
            return valueOrMissing("Location", profile.location)
        }
        if (hasAny("open hours", "opening hours", "when are we open", "open days", "business hours")) {
            val open = listOfNotNull(
                profile.openDays?.takeIf { it.isNotBlank() },
                profile.openHours?.takeIf { it.isNotBlank() },
            ).joinToString(", ")
            return valueOrMissing("Open", open)
        }
        if (hasAny("suppliers", "main supplier", "who supplies")) {
            return valueOrMissing("Suppliers", profile.mainSuppliers)
        }
        if (hasAny("business goal", "my goal", "growth goal")) {
            return valueOrMissing("Business goal", profile.businessGoal)
        }
        if (hasAny("revenue target", "sales target", "monthly target", "hitting my target")) {
            return "Monthly revenue target: ${profile.monthlyRevenueTarget?.let(fmt) ?: "not set"}."
        }
        return null
    }
}
