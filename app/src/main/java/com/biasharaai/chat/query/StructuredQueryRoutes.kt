package com.biasharaai.chat.query

import com.biasharaai.data.local.db.Customer
import com.biasharaai.data.local.db.Debt
import com.biasharaai.data.local.db.PosSaleLineFact
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.Transaction
import com.biasharaai.data.local.db.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private const val MS_PER_DAY_ROUTE = 24L * 60L * 60L * 1000L

/**
 * Split from [ConversationalQueryLayer] so each route function stays under the JVM 64KB method limit.
 */
internal data class StructuredQueryContext(
    val question: String,
    val languageDisplayName: String,
    val q: String,
    val fmt: (Double) -> String,
    val txs: List<Transaction>,
    val products: List<Product>,
    val customers: List<Customer>,
    val unpaidDebts: List<Debt>,
    val outstanding: Double,
    val lines: List<PosSaleLineFact>,
    val zone: ZoneId,
    val today: LocalDate,
    val productById: Map<Long, Product>,
    val t0: Long,
    val t1: Long,
    val y0: Long,
    val y1: Long,
    val w0: Long,
    val wEnd: Long,
    val m0: Long,
    val m1: Long,
    val prevWeek0: Long,
    val prevWeek1: Long,
    val nowMs: Long,
    val fourteenDaysAgo: Long,
    val yStart: Long,
    val yEnd: Long,
    val lmStart: Long,
    val lmEnd: Long,
    val d90s: Long,
    val nowEnd: Long,
    val costNeedle: String?,
) {
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

    fun incomeInRange(start: Long, end: Long): Double =
        txs.asSequence()
            .filter { it.type == TransactionType.INCOME && it.amount > 0 && it.date in start..end }
            .sumOf { it.amount }

    fun expenseInRange(start: Long, end: Long): Double =
        txs.asSequence()
            .filter { it.type == TransactionType.EXPENSE && it.date in start..end }
            .sumOf { it.amount }

    fun linesInRange(start: Long, end: Long): List<PosSaleLineFact> =
        lines.filter { it.transactionDate in start..end }

    fun hasAny(vararg needles: String): Boolean = needles.any { q.contains(it) }

    fun lineGrossProfit(line: PosSaleLineFact): Double {
        val p = productById[line.productId] ?: return 0.0
        return line.lineTotal - p.cost * line.quantity
    }
}

private fun extractTrailingProductName(q: String, keywords: List<String>): String? {
    for (kw in keywords) {
        val idx = q.indexOf(kw)
        if (idx < 0) continue
        val tail = q.substring(idx + kw.length).trim().removePrefix("for").trim().removePrefix("of").trim()
            .removeSuffix("?").trim()
        if (tail.length in 2..40) return tail
    }
    return null
}

private fun extractCustomerNameFragment(q: String, customers: List<Customer>): String? {
    if (customers.isEmpty()) return null
    return customers.map { it.name }
        .filter { it.length > 2 && q.contains(it.lowercase(Locale.ROOT)) }
        .maxByOrNull { it.length }
        ?.lowercase(Locale.ROOT)
}


internal suspend fun structuredQueryPart1(
    ctx: StructuredQueryContext,
    polish: suspend (String, String, String) -> String,
): String? = with(ctx) {
    // ── Category 7: device / cashier (not in schema) ─────────────────
    if (q.contains("cashier") || (q.contains("device") && (q.contains("sale") || q.contains("sold")))) {
        return polish(
            question,
            "Per-device or per-cashier totals are not stored on transactions in this app version. " +
                "You can review each sale time and amount under Sales → history (receipts).",
            languageDisplayName,
        )
    }

    // ── Category 12: operational summaries ───────────────────────────
    if (q.contains("three most important") || q.contains("3 most important") ||
        (q.contains("summary") && q.contains("today"))
    ) {
        val inc = incomeInRange(t0, t1)
        val exp = expenseInRange(t0, t1)
        val lc = linesInRange(t0, t1).size
        val top = linesInRange(t0, t1).groupBy { it.productName }.mapValues { (_, v) -> v.sumOf { it.lineTotal } }
            .entries.sortedByDescending { it.value }.take(3)
        val b = StringBuilder()
        b.append("Today (${today}): income ").append(fmt(inc)).append(", expenses ").append(fmt(exp))
            .append(", net ").append(fmt(inc - exp)).append(". ")
        b.append("POS line movements today: ").append(lc).append(". ")
        if (top.isNotEmpty()) {
            b.append("Top products by line revenue today: ")
            b.append(top.joinToString("; ") { "${it.key} ${fmt(it.value)}" })
            b.append(". ")
        }
        b.append("Outstanding debt recorded: ").append(fmt(outstanding)).append(".")
        return polish(question, b.toString(), languageDisplayName)
    }

    if (q.contains("how was this week") || (q.contains("summary") && q.contains("week"))) {
        val inc = incomeInRange(w0, wEnd)
        val exp = expenseInRange(w0, wEnd)
        return polish(
            question,
            "This week (Mon–today, local calendar): total income ${fmt(inc)}, expenses ${fmt(exp)}, net ${fmt(inc - exp)}.",
            languageDisplayName,
        )
    }

    if (q.contains("last month") && (q.contains("happen") || q.contains("summary") || q.contains("overall"))) {
        val (a, b) = monthContaining(today.minusMonths(1))
        val inc = incomeInRange(dayRange(a).first, dayRange(b).second)
        val exp = expenseInRange(dayRange(a).first, dayRange(b).second)
        return polish(
            question,
            "Last calendar month: total income ${fmt(inc)}, expenses ${fmt(exp)}, net ${fmt(inc - exp)}.",
            languageDisplayName,
        )
    }

    // ── Category 1: sales & revenue ─────────────────────────────────
    // Yesterday totals (before "today" rules; avoids Gemma answering from today-only context)
    if (q.contains("yesterday") && !hasAny("compare", "versus", "vs")) {
        if (hasAny("make", "income", "revenue", "earn", "earned", "how much", "took in", "brought in")) {
            val yestDate = today.minusDays(1)
            val v = incomeInRange(y0, y1)
            val c = txs.count { it.type == TransactionType.INCOME && it.amount > 0 && it.date in y0..y1 }
            return polish(
                question,
                "Recorded income yesterday (local calendar $yestDate): total ${fmt(v)} from $c positive income transaction(s).",
                languageDisplayName,
            )
        }
        if (q.contains("how many") && hasAny("sale", "sales", "transaction")) {
            val c = txs.count { it.type == TransactionType.INCOME && it.amount > 0 && it.date in y0..y1 }
            return polish(
                question,
                "Yesterday (local calendar ${today.minusDays(1)}): $c income sale transaction(s) with positive totals.",
                languageDisplayName,
            )
        }
    }

    if ((q.contains("today") && q.contains("make")) || q.contains("how much did i make today") ||
        (q.contains("revenue") && q.contains("today")) || (q.contains("income") && q.contains("today"))
    ) {
        val v = incomeInRange(t0, t1)
        return polish(question, "Recorded income transactions today total ${fmt(v)} (local date $today).", languageDisplayName)
    }

    if (q.contains("how many sales") && q.contains("today")) {
        val c = txs.count { it.type == TransactionType.INCOME && it.amount > 0 && it.date in t0..t1 }
        return polish(question, "Today you have $c income sale transaction(s) with positive totals.", languageDisplayName)
    }

    if (hasAny("average", "mean") && hasAny("order", "sale", "ticket", "transaction") && hasAny("today", "leo")) {
        val inc = incomeInRange(t0, t1)
        val c = txs.count { it.type == TransactionType.INCOME && it.amount > 0 && it.date in t0..t1 }
        val aov = if (c > 0) inc / c else 0.0
        return polish(
            question,
            "Average income transaction amount today: ${fmt(aov)} (from $c positive income sale(s)).",
            languageDisplayName,
        )
    }

    if ((q.contains("revenue") && q.contains("week")) || (q.contains("this week") && q.contains("revenue"))) {
        val v = incomeInRange(w0, wEnd)
        return polish(question, "Income (revenue) this week so far: ${fmt(v)}.", languageDisplayName)
    }
    if ((q.contains("revenue") && q.contains("month")) || (q.contains("this month") && q.contains("revenue"))) {
        val v = incomeInRange(m0, m1)
        return polish(question, "Income (revenue) this calendar month so far: ${fmt(v)}.", languageDisplayName)
    }
    if ((q.contains("revenue") && q.contains("year")) || (q.contains("this year") && q.contains("revenue"))) {
        val v = incomeInRange(yStart, yEnd)
        return polish(question, "Income (revenue) this calendar year so far: ${fmt(v)}.", languageDisplayName)
    }

    if (q.contains("best day") && (q.contains("ever") || q.contains("all time"))) {
        val best = txs.asSequence()
            .filter { it.type == TransactionType.INCOME && it.amount > 0 }
            .groupBy { LocalDate.ofInstant(Instant.ofEpochMilli(it.date), zone) }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .maxByOrNull { it.value }
        return if (best != null) {
            polish(question, "Best single calendar day by summed income: ${best.key} at ${fmt(best.value)}.", languageDisplayName)
        } else {
            polish(question, "No positive income days found yet.", languageDisplayName)
        }
    }

    if (q.contains("worst day") && q.contains("month")) {
        val (ms, me) = monthContaining(today).let { (a, b) -> dayRange(a).first to dayRange(b).second }
        val worst = txs.asSequence()
            .filter { it.type == TransactionType.INCOME && it.amount > 0 && it.date in ms..me }
            .groupBy { LocalDate.ofInstant(Instant.ofEpochMilli(it.date), zone) }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .minByOrNull { it.value }
        return if (worst != null) {
            polish(question, "Lowest income day this month: ${worst.key} at ${fmt(worst.value)}.", languageDisplayName)
        } else {
            polish(question, "Not enough income days this month to compare.", languageDisplayName)
        }
    }

    if (q.contains("today") && q.contains("yesterday") && (q.contains("compare") || q.contains("versus") || q.contains(" vs"))) {
        val a = incomeInRange(t0, t1)
        val b = incomeInRange(y0, y1)
        return polish(question, "Today income ${fmt(a)} vs yesterday ${fmt(b)}.", languageDisplayName)
    }

    if (hasAny("jana", "yesterday") && hasAny("leo", "today") && hasAny("mapato", "mauzo", "income", "revenue", "faida", "pesa")) {
        val a = incomeInRange(t0, t1)
        val b = incomeInRange(y0, y1)
        return polish(question, "Recorded income leo (today) ${fmt(a)} vs jana (yesterday) ${fmt(b)}.", languageDisplayName)
    }

    if (q.contains("time of day") && q.contains("sell")) {
        val byHour = txs.asSequence()
            .filter { it.type == TransactionType.INCOME && it.amount > 0 }
            .groupBy { Instant.ofEpochMilli(it.date).atZone(zone).hour }
            .mapValues { (_, v) -> v.size }
            .maxByOrNull { it.value }
        return if (byHour != null) {
            polish(question, "Most frequent hour for income transactions (count): ${byHour.key}:00 (${byHour.value} tx).", languageDisplayName)
        } else {
            null
        }
    }

    if (q.contains("day of the week") || q.contains("busiest day")) {
        val byDow = txs.asSequence()
            .filter { it.type == TransactionType.INCOME && it.amount > 0 }
            .groupBy { Instant.ofEpochMilli(it.date).atZone(zone).dayOfWeek }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .maxByOrNull { it.value }
        return if (byDow != null) {
            polish(question, "Busiest weekday by summed income: ${byDow.key} (${fmt(byDow.value)}).", languageDisplayName)
        } else {
            null
        }
    }

    if (q.contains("average sale") || q.contains("average transaction")) {
        val inc = txs.filter { it.type == TransactionType.INCOME && it.amount > 0 }
        val avg = if (inc.isEmpty()) 0.0 else inc.sumOf { it.amount } / inc.size
        return polish(question, "Average income transaction amount (all time in database): ${fmt(avg)}.", languageDisplayName)
    }

    if (q.contains("largest") && (q.contains("sale") || q.contains("single"))) {
        val mx = txs.filter { it.type == TransactionType.INCOME && it.amount > 0 }.maxByOrNull { it.amount }
        return if (mx != null) {
            polish(question, "Largest single income transaction amount: ${fmt(mx.amount)} on ${mx.date}.", languageDisplayName)
        } else {
            null
        }
    }

    if (q.contains("cash") && q.contains("today") && (q.contains("how much") || q.contains("came"))) {
        val v = linesInRange(t0, t1).filter { it.paymentMethod.equals("CASH", true) }.sumOf { it.lineTotal }
        return polish(question, "POS lines today paid as CASH sum to ${fmt(v)} (line totals).", languageDisplayName)
    }
    if (q.contains("m-pesa") || q.contains("mpesa") || q.contains("mobile money")) {
        if (q.contains("today")) {
            val keys = listOf("MOMO", "MPESA", "MOBILE", "M_PESA")
            val v = linesInRange(t0, t1).filter { line ->
                keys.any { line.paymentMethod.contains(it, true) }
            }.sumOf { it.lineTotal }
            return polish(question, "Today, POS lines whose payment method looks like mobile money sum to ${fmt(v)}.", languageDisplayName)
        }
    }
    if (q.contains("credit") && (q.contains("percent") || q.contains("percentage"))) {
        val rev = lines.sumOf { abs(it.lineTotal) }
        val cred = lines.filter { it.customerId != null }.sumOf { abs(it.lineTotal) }
        val pct = if (rev > 0) cred / rev * 100.0 else 0.0
        return polish(question, "About ${"%.1f".format(pct)}% of POS line revenue is on sales with a customer (credit-style) attached.", languageDisplayName)
    }
    if (q.contains("payment method") && q.contains("most")) {
        val top = lines.groupBy { it.paymentMethod }.mapValues { (_, v) -> v.sumOf { abs(it.lineTotal) } }
            .maxByOrNull { it.value }
        return if (top != null) {
            polish(question, "Most revenue by payment_method on POS lines: ${top.key} (${fmt(top.value)}).", languageDisplayName)
        } else {
            null
        }
    }

    if ((q.contains("growing") || q.contains("shrinking")) && q.contains("revenue")) {
        val thisM = incomeInRange(m0, m1)
        val lastM = incomeInRange(lmStart, lmEnd)
        val cmp = when {
            lastM <= 0 -> "Last month had no recorded income to compare."
            else -> "This month so far ${fmt(thisM)} vs last full month ${fmt(lastM)}."
        }
        return polish(question, cmp, languageDisplayName)
    }

    if (q.contains("90 day") && q.contains("trend")) {
        val v = incomeInRange(d90s, nowEnd)
        return polish(question, "Total income last 90 days: ${fmt(v)} (single aggregate).", languageDisplayName)
    }

    if (q.contains("projected revenue") || (q.contains("rest of") && q.contains("month"))) {
        val dayOfMonth = today.dayOfMonth
        val dim = today.lengthOfMonth()
        val mtd = incomeInRange(m0, m1)
        val proj = if (dayOfMonth > 0) mtd * dim / dayOfMonth else mtd
        return polish(question, "Rough straight-line projection for full month from pace so far: about ${fmt(proj)}.", languageDisplayName)
    }

    if (hasAny("compare", "versus", "vs") && hasAny("today", "leo") && hasAny("last week")) {
        val todayInc = incomeInRange(t0, t1)
        val lastWeekInc = incomeInRange(prevWeek0, prevWeek1)
        val avgDaily = if (lastWeekInc > 0) lastWeekInc / 7.0 else 0.0
        return polish(
            question,
            "Today income ${fmt(todayInc)}. Last full calendar week (Mon–Sun) total ${fmt(lastWeekInc)} (~${fmt(avgDaily)} per day on average).",
            languageDisplayName,
        )
    }

    if (hasAny("revenue", "income", "make", "sales") && hasAny("last week") && !hasAny("compare", "versus", "vs")) {
        val v = incomeInRange(prevWeek0, prevWeek1)
        return polish(question, "Last full calendar week (Mon–Sun) income total: ${fmt(v)}.", languageDisplayName)
    }

    if (hasAny("on track") && hasAny("last month", "beat")) {
        val mtd = incomeInRange(m0, m1)
        val lastFull = incomeInRange(lmStart, lmEnd)
        val dayOfMonth = today.dayOfMonth
        val dim = today.lengthOfMonth()
        val pace = if (dayOfMonth > 0) mtd * dim / dayOfMonth else mtd
        val msg = when {
            lastFull <= 0 -> "No last-month income baseline to compare."
            pace >= lastFull -> "At current pace (~${fmt(pace)} projected month) you are at or above last month's total (${fmt(lastFull)})."
            else -> "At current pace (~${fmt(pace)} projected) you are below last month's total (${fmt(lastFull)}); you would need a stronger finish."
        }
        return polish(question, msg, languageDisplayName)
    }

    if (hasAny("grow", "grew", "growth") && hasAny("last month") && !hasAny("90")) {
        val thisM = incomeInRange(m0, m1)
        val lastM = incomeInRange(lmStart, lmEnd)
        val pct = if (lastM > 0) (thisM - lastM) / lastM * 100.0 else 0.0
        return polish(
            question,
            "This month income so far ${fmt(thisM)} vs last full month ${fmt(lastM)} (${"%.1f".format(pct)}% change on raw totals; month not finished).",
            languageDisplayName,
        )
    }

    if ((hasAny("per hour") || hasAny("each hour")) && hasAny("today", "leo")) {
        val byHour = txs.asSequence()
            .filter { it.type == TransactionType.INCOME && it.amount > 0 && it.date in t0..t1 }
            .groupBy { Instant.ofEpochMilli(it.date).atZone(zone).hour }
            .mapValues { (_, v) -> v.size }
            .entries.sortedBy { it.key }
        val txt = if (byHour.isEmpty()) {
            "No income transactions recorded today yet."
        } else {
            "Income transaction counts by hour today: " + byHour.joinToString(", ") { "${it.key}:00 → ${it.value}" } + "."
        }
        return polish(question, txt, languageDisplayName)
    }

    if (hasAny("best month", "best ever") && hasAny("compare", "versus", "vs", "how do i")) {
        val byMonth = txs.asSequence()
            .filter { it.type == TransactionType.INCOME && it.amount > 0 }
            .groupBy {
                val d = LocalDate.ofInstant(Instant.ofEpochMilli(it.date), zone)
                d.year * 100 + d.monthValue
            }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .maxByOrNull { it.value }
        val mtd = incomeInRange(m0, m1)
        return if (byMonth != null) {
            val y = byMonth.key / 100
            val m = byMonth.key % 100
            polish(
                question,
                "Best calendar month on record by income: $y-${"%02d".format(m)} at ${fmt(byMonth.value)}. This month so far: ${fmt(mtd)}.",
                languageDisplayName,
            )
        } else {
            null
        }
    }

    if (hasAny("busiest season", "busiest month", "busy season")) {
        val byMonth = txs.asSequence()
            .filter { it.type == TransactionType.INCOME && it.amount > 0 }
            .groupBy { LocalDate.ofInstant(Instant.ofEpochMilli(it.date), zone).monthValue }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .maxByOrNull { it.value }
        return if (byMonth != null) {
            polish(
                question,
                "Historically highest total income lands in calendar month #${byMonth.key} (${fmt(byMonth.value)} summed across all years in the database).",
                languageDisplayName,
            )
        } else {
            null
        }
    }

    // ── Category 2: products & inventory ────────────────────────────
    if (q.contains("best-selling") || q.contains("best selling") || q.contains("top product")) {
        val top = lines.groupBy { it.productName }.mapValues { (_, v) -> v.sumOf { it.quantity } }
            .entries.sortedByDescending { it.value }.firstOrNull()
        return if (top != null) {
            polish(question, "Best-selling product by units in the analysis window: ${top.key} (${top.value} units).", languageDisplayName)
        } else {
            polish(question, "No POS sale lines in the analysis window yet.", languageDisplayName)
        }
    }

    if (q.contains("worst-selling") || q.contains("worst selling")) {
        val g = lines.groupBy { it.productName }.mapValues { (_, v) -> v.sumOf { it.quantity } }
            .filter { it.value > 0 }.minByOrNull { it.value }
        return if (g != null) {
            polish(question, "Slowest seller among products that did sell (by units): ${g.key} (${g.value} units).", languageDisplayName)
        } else {
            null
        }
    }

    if (q.contains("top 5") && q.contains("revenue")) {
        val top = lines.groupBy { it.productName }.mapValues { (_, v) -> v.sumOf { it.lineTotal } }
            .entries.sortedByDescending { it.value }.take(5)
        return polish(question, "Top 5 products by line revenue: " + top.joinToString("; ") { "${it.key} ${fmt(it.value)}" } + ".", languageDisplayName)
    }

    if (q.contains("top 5") && q.contains("units")) {
        val top = lines.groupBy { it.productName }.mapValues { (_, v) -> v.sumOf { it.quantity } }
            .entries.sortedByDescending { it.value }.take(5)
        return polish(question, "Top 5 products by units sold: " + top.joinToString("; ") { "${it.key} ${it.value} u" } + ".", languageDisplayName)
    }

    if (hasAny("most profit", "highest profit") || (hasAny("product") && hasAny("profit") && hasAny("most", "highest", "best") && !hasAny("average", "margin across"))) {
        val byP = lines.groupBy { it.productName }.mapValues { (_, ls) -> ls.sumOf { lineGrossProfit(it) } }
        val top = byP.maxByOrNull { it.value }
        return if (top != null && top.value != 0.0) {
            polish(question, "Top product by estimated line gross profit (revenue − cost×qty): ${top.key} (${fmt(top.value)}).", languageDisplayName)
        } else {
            null
        }
    }

    if (hasAny("least profit", "lowest profit", "smallest profit") && hasAny("product")) {
        val byP = lines.groupBy { it.productName }.mapValues { (_, ls) -> ls.sumOf { lineGrossProfit(it) } }
        val low = byP.filter { it.value > 0.0 }.minByOrNull { it.value }
        return if (low != null) {
            polish(question, "Among sellers with positive estimated line profit, lowest is ${low.key} (${fmt(low.value)}).", languageDisplayName)
        } else {
            null
        }
    }

    if (q.contains("never sold")) {
        val soldIds = lines.map { it.productId }.toSet()
        val never = products.filter { it.id !in soldIds }.take(25)
        return if (never.isEmpty()) {
            polish(question, "Every catalog product appears on at least one POS line in the window, or catalog is empty.", languageDisplayName)
        } else {
            polish(question, "Products with no POS lines in the long lookback window (sample): " + never.joinToString(", ") { it.name } + ".", languageDisplayName)
        }
    }

    if (q.contains("not sold") && q.contains("7")) {
        val cutoff = today.minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val recent = lines.filter { it.transactionDate >= cutoff }.map { it.productId }.toSet()
        val idle = products.filter { it.id !in recent && lines.any { l -> l.productId == it.id } }.take(20)
        return polish(
            question,
            if (idle.isEmpty()) "No clear 'sold before but not last 7 days' pattern from quick scan."
            else "Some products with older sales but no lines in last 7 days (sample): " + idle.joinToString(", ") { it.name } + ".",
            languageDisplayName,
        )
    }

    if ((q.contains("total value") && q.contains("inventory")) || (q.contains("stock value") && q.contains("total"))) {
        val v = products.sumOf { it.price * it.stockQuantity }
        return polish(question, "Total retail value of on-hand stock (sum of list price × quantity): ${fmt(v)}.", languageDisplayName)
    }

    if (hasAny("thamani", "jumla") && hasAny("stock", "stoo", "hifadhi", "bidhaa", "inventory") &&
        (hasAny("jumla", "total", "nzima", "shelf") || q.contains("thamani ya"))
    ) {
        val v = products.sumOf { it.price * it.stockQuantity }
        return polish(question, "Total retail value of on-hand stock (list price × quantity): ${fmt(v)}.", languageDisplayName)
    }

    if (q.contains("low stock") || q.contains("running low")) {
        val low = products.filter { it.stockQuantity < 10 }
        return polish(
            question,
            if (low.isEmpty()) "No products below the default low-stock threshold (10 units)."
            else "Low stock (<10): " + low.joinToString(", ") { "${it.name} (${it.stockQuantity})" } + ".",
            languageDisplayName,
        )
    }

    if (q.contains("category") && (q.contains("sells") || q.contains("revenue"))) {
        val catRev = lines.groupBy { productById[it.productId]?.category ?: "Uncategorized" }
            .mapValues { (_, v) -> v.sumOf { it.lineTotal } }
            .maxByOrNull { it.value }
        return if (catRev != null) {
            polish(question, "Highest revenue category (from product.category on lines): ${catRev.key} (${fmt(catRev.value)}).", languageDisplayName)
        } else {
            null
        }
    }

    if (q.contains("category") && hasAny("profitable", "profit")) {
        val catProfit = lines.groupBy { productById[it.productId]?.category ?: "Uncategorized" }
            .mapValues { (_, ls) -> ls.sumOf { lineGrossProfit(it) } }
            .maxByOrNull { it.value }
        return if (catProfit != null) {
            polish(
                question,
                "Highest estimated line gross profit by category: ${catProfit.key} (${fmt(catProfit.value)}).",
                languageDisplayName,
            )
        } else {
            null
        }
    }

    val categoryMention = products.asSequence()
        .map { (it.category ?: "").trim() }
        .filter { it.length > 1 }
        .distinct()
        .filter { c -> q.contains(c.lowercase(Locale.ROOT)) }
        .maxByOrNull { it.length }
    if (categoryMention != null && hasAny("revenue", "sales", "income", "comes from", "share", "how much")) {
        val rev = lines.filter { productById[it.productId]?.category == categoryMention }.sumOf { it.lineTotal }
        val total = lines.sumOf { it.lineTotal }
        val pct = if (total > 0) rev / total * 100.0 else 0.0
        return polish(
            question,
            "POS line revenue linked to category \"$categoryMention\": ${fmt(rev)} (~${"%.1f".format(pct)}% of line revenue in the analysis window).",
            languageDisplayName,
        )
    }

    if (hasAny("overstock", "too much stock", "slow-moving")) {
        val thirtyAgo = today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()
        val units30 = lines.filter { it.transactionDate >= thirtyAgo }
            .groupBy { it.productId }
            .mapValues { (_, v) -> v.sumOf { it.quantity } }
        val fat = products.filter { p -> p.stockQuantity >= 30 && (units30[p.id] ?: 0) <= 2 }.take(15)
        return polish(
            question,
            if (fat.isEmpty()) {
                "No obvious overstock pattern (high on-hand count with ≤2 units sold in last 30 days) from quick rules."
            } else {
                "Possible overstock / slow movers (≥30 on hand, ≤2 units sold in 30d): " +
                    fat.joinToString(", ") { "${it.name} (stock ${it.stockQuantity})" } + "."
            },
            languageDisplayName,
        )
    }

    if (hasAny("sold") && hasAny("stock") && hasAny("week")) {
        val qty = linesInRange(w0, wEnd).sumOf { it.quantity }
        return polish(question, "Units moved on POS sale lines this week (Mon–today): $qty.", languageDisplayName)
    }

    if (hasAny("inventory", "stock") && hasAny("cost") && (hasAny("value", "worth", "total") || hasAny("at cost"))) {
        val v = products.sumOf { it.cost * it.stockQuantity }
        return polish(question, "Total on-hand inventory at recorded unit cost (cost × quantity): ${fmt(v)}.", languageDisplayName)
    }

    if ((hasAny("days of stock", "how many days") && hasAny("stock")) || hasAny("run out this week")) {
        val needle = costNeedle ?: extractTrailingProductName(q, listOf("for", "product"))
        if (needle != null) {
            val p = products.firstOrNull { it.name.contains(needle, true) }
            if (p != null) {
                val thirtyAgo = today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()
                val sold30 = lines.filter { it.productId == p.id && it.transactionDate >= thirtyAgo }.sumOf { it.quantity }
                val perDay = sold30 / 30.0
                val days = if (perDay > 0) p.stockQuantity / perDay else Double.POSITIVE_INFINITY
                val daysTxt = if (days.isFinite()) "~${"%.1f".format(days)} days at last-30d pace" else "no recent sales to pace from"
                return polish(
                    question,
                    "${p.name}: ${p.stockQuantity} on hand; $sold30 units sold last 30d → $daysTxt.",
                    languageDisplayName,
                )
            }
        }
    }

    if (hasAny("last restock", "when did i last restock")) {
        return polish(
            question,
            "Individual restock events are not logged in this database — only current stock levels on each product.",
            languageDisplayName,
        )
    }

    // Stock for named product
    val stockNeedle = extractTrailingProductName(q, listOf("stock", "inventory", "have left", "units of"))
    if (stockNeedle != null) {
        val m = products.filter { it.name.contains(stockNeedle, true) }
        if (m.isNotEmpty()) {
            return polish(
                question,
                m.joinToString("; ") { "${it.name}: ${it.stockQuantity} units at list ${fmt(it.price)}, cost ${fmt(it.cost)}." },
                languageDisplayName,
            )
        }
    }

    return null
}


internal suspend fun structuredQueryPart2(
    ctx: StructuredQueryContext,
    polish: suspend (String, String, String) -> String,
): String? = with(ctx) {
    // ── Category 3: profit & margins ─────────────────────────────────
    if (q.contains("below cost") || (q.contains("sold") && q.contains("below"))) {
        val bad = lines.mapNotNull { line ->
            val p = productById[line.productId] ?: return@mapNotNull null
            if (line.unitPrice < p.cost) {
                Triple(line.productName, line.unitPrice, p.cost)
            } else {
                null
            }
        }.distinctBy { it.first }.take(15)
        return if (bad.isEmpty()) {
            polish(question, "No POS lines in the window have unit_price below that product's recorded cost.", languageDisplayName)
        } else {
            polish(
                question,
                "Possible below-cost lines (sample): " +
                    bad.joinToString("; ") { "${it.first} sold at ${fmt(it.second)} vs cost ${fmt(it.third)}" },
                languageDisplayName,
            )
        }
    }

    if (q.contains("average") && q.contains("margin")) {
        val margins = products.map { p ->
            if (p.price > 0) (p.price - p.cost) / p.price else null
        }.filterNotNull()
        val avg = if (margins.isEmpty()) 0.0 else margins.average()
        return polish(question, "Simple average of per-product (list−cost)/list across catalog: ${"%.1f".format(avg * 100)}%.", languageDisplayName)
    }

    val marginProductNeedle = extractTrailingProductName(q, listOf("margin on", "margin for", "profit margin on"))
    if (marginProductNeedle != null && hasAny("margin", "profit")) {
        val p = products.firstOrNull { it.name.contains(marginProductNeedle, true) }
        if (p != null && p.price > 0) {
            val m = (p.price - p.cost) / p.price * 100.0
            return polish(
                question,
                "${p.name}: list ${fmt(p.price)}, cost ${fmt(p.cost)} → about ${"%.1f".format(m)}% margin on list price.",
                languageDisplayName,
            )
        }
    }

    if (q.contains("gross profit")) {
        val start = when {
            q.contains("today") -> t0
            q.contains("week") -> w0
            q.contains("month") -> m0
            else -> m0
        }
        val end = when {
            q.contains("today") -> t1
            q.contains("week") -> wEnd
            q.contains("month") -> m1
            else -> m1
        }
        val lr = linesInRange(start, end)
        var gp = 0.0
        for (line in lr) {
            val p = productById[line.productId] ?: continue
            gp += line.lineTotal - p.cost * line.quantity
        }
        return polish(question, "Estimated gross profit on POS lines (revenue − cost×qty) in range: ${fmt(gp)}.", languageDisplayName)
    }

    if (hasAny("gross profit", "profit") && hasAny("last month") && !hasAny("margin", "percent", "more", "less")) {
        val (a, b) = monthContaining(today.minusMonths(1))
        val r0 = dayRange(a).first
        val r1 = dayRange(b).second
        val lr = linesInRange(r0, r1)
        var gp = 0.0
        for (line in lr) {
            val p = productById[line.productId] ?: continue
            gp += line.lineTotal - p.cost * line.quantity
        }
        return polish(question, "Estimated POS line gross profit last calendar month: ${fmt(gp)}.", languageDisplayName)
    }

    if (hasAny("profit") && hasAny("last month") && (hasAny("more", "less", "than"))) {
        val (a, b) = monthContaining(today.minusMonths(1))
        val r0 = dayRange(a).first
        val r1 = dayRange(b).second
        val lrPrev = linesInRange(r0, r1)
        var gpPrev = 0.0
        for (line in lrPrev) {
            val p = productById[line.productId] ?: continue
            gpPrev += line.lineTotal - p.cost * line.quantity
        }
        val lrNow = linesInRange(m0, m1)
        var gpNow = 0.0
        for (line in lrNow) {
            val p = productById[line.productId] ?: continue
            gpNow += line.lineTotal - p.cost * line.quantity
        }
        val cmp = when {
            gpNow > gpPrev -> "more"
            gpNow < gpPrev -> "less"
            else -> "about the same as"
        }
        return polish(
            question,
            "Estimated line gross profit: this month so far ${fmt(gpNow)} vs last full month ${fmt(gpPrev)} → $cmp last month at this snapshot.",
            languageDisplayName,
        )
    }

    if (hasAny("eating", "eroding") && hasAny("margin")) {
        val worst = products.filter { it.price > 0 }.minByOrNull { (it.price - it.cost) / it.price }
        return if (worst != null) {
            val m = (worst.price - worst.cost) / worst.price * 100.0
            polish(
                question,
                "Tightest catalog margin on list price: ${worst.name} (~${"%.1f".format(m)}%). Small price or cost moves there swing profit the most.",
                languageDisplayName,
            )
        } else {
            null
        }
    }

    if (q.contains("10%") && q.contains("price")) {
        return polish(
            question,
            "Quick what-if: raising every list price by 10% would scale retail stock value from " +
                "${fmt(products.sumOf { it.price * it.stockQuantity })} to about " +
                "${fmt(products.sumOf { it.price * 1.1 * it.stockQuantity })} (catalog only, not demand).",
            languageDisplayName,
        )
    }

    // ── Category 4: expenses & cash flow ────────────────────────────
    if ((q.contains("spend") && q.contains("today")) || q.contains("spent today")) {
        val v = expenseInRange(t0, t1)
        return polish(question, "Expenses recorded today: ${fmt(v)}.", languageDisplayName)
    }
    if ((q.contains("spend") && q.contains("month")) || (q.contains("spent") && q.contains("this month"))) {
        val v = expenseInRange(m0, m1)
        return polish(question, "Expenses this calendar month so far: ${fmt(v)}.", languageDisplayName)
    }
    if ((hasAny("spend", "spent")) && hasAny("week")) {
        val v = expenseInRange(w0, wEnd)
        return polish(question, "Expenses this week (Mon–today): ${fmt(v)}.", languageDisplayName)
    }
    if (hasAny("expense", "expenses") && hasAny("income") && hasAny("month")) {
        val inc = incomeInRange(m0, m1)
        val exp = expenseInRange(m0, m1)
        return polish(
            question,
            "This month so far — income ${fmt(inc)}, expenses ${fmt(exp)}, net ${fmt(inc - exp)}.",
            languageDisplayName,
        )
    }
    if ((hasAny("cash") && hasAny("after expense")) || hasAny("cash after")) {
        val inc = incomeInRange(m0, m1)
        val exp = expenseInRange(m0, m1)
        return polish(question, "Simple month view: income ${fmt(inc)} minus expenses ${fmt(exp)} leaves about ${fmt(inc - exp)}.", languageDisplayName)
    }
    if (hasAny("spent", "spend") && hasAny("most money", "most cash", "biggest day") && hasAny("day", "which day")) {
        val top = txs.filter { it.type == TransactionType.EXPENSE }
            .groupBy { LocalDate.ofInstant(Instant.ofEpochMilli(it.date), zone) }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }.take(5)
        val txt = if (top.isEmpty()) "No expense rows found." else top.joinToString("; ") { "${it.key}: ${fmt(it.value)}" }
        return polish(question, "Highest expense days (by summed expense transactions): $txt.", languageDisplayName)
    }
    if (hasAny("spending") && (hasAny("going up", "going down", "up or down", "increasing", "decreasing"))) {
        val e0 = expenseInRange(m0, m1)
        val e1 = expenseInRange(lmStart, lmEnd)
        val diff = e0 - e1
        val dir = when {
            diff > 0 -> "higher"
            diff < 0 -> "lower"
            else -> "about even with"
        }
        return polish(
            question,
            "Expenses: this month so far ${fmt(e0)} vs all of last calendar month ${fmt(e1)} — $dir by about ${fmt(abs(diff))} (months not day-aligned).",
            languageDisplayName,
        )
    }
    if (hasAny("expense", "spending", "costs") && hasAny("fastest growing", "growing the fastest")) {
        val descThis = txs.filter { it.type == TransactionType.EXPENSE && it.date in m0..m1 }
            .groupBy { it.description }.mapValues { (_, v) -> v.sumOf { it.amount } }
        val descLast = txs.filter { it.type == TransactionType.EXPENSE && it.date in lmStart..lmEnd }
            .groupBy { it.description }.mapValues { (_, v) -> v.sumOf { it.amount } }
        val growth = descThis.keys.union(descLast.keys).map { d ->
            val a = descThis[d] ?: 0.0
            val b = descLast[d] ?: 0.0
            Triple(d, a - b, a)
        }.maxByOrNull { it.second }
        return if (growth != null) {
            polish(
                question,
                "Largest month-over-month jump in expense description bucket: \"${growth.first}\" (this month ${fmt(growth.third)} vs last month ${fmt(descLast[growth.first] ?: 0.0)}).",
                languageDisplayName,
            )
        } else {
            null
        }
    }
    if (q.contains("biggest expense") || q.contains("largest expense")) {
        val top = txs.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.description }.mapValues { (_, v) -> v.sumOf { it.amount } }
            .maxByOrNull { it.value }
        return if (top != null) {
            polish(question, "Largest expense bucket by description: \"${top.key}\" at ${fmt(top.value)} total.", languageDisplayName)
        } else {
            null
        }
    }
    if (hasAny("stock") && hasAny("spent", "spend") && hasAny("month")) {
        val v = txs.filter {
            it.type == TransactionType.EXPENSE && it.date in m0..m1 &&
                (it.description.contains("stock", true) || it.description.contains("inventory", true))
        }.sumOf { it.amount }
        return polish(
            question,
            "Expenses this month whose description mentions stock/inventory: ${fmt(v)} (depends on how you label purchases).",
            languageDisplayName,
        )
    }
    if (q.contains("cash flow") || q.contains("spending more than")) {
        val inc = incomeInRange(m0, m1)
        val exp = expenseInRange(m0, m1)
        return polish(question, "This month so far: income ${fmt(inc)}, expenses ${fmt(exp)}, net ${fmt(inc - exp)}.", languageDisplayName)
    }

    return null
}


internal suspend fun structuredQueryPart3(
    ctx: StructuredQueryContext,
    polish: suspend (String, String, String) -> String,
): String? = with(ctx) {
    // Swahili / mixed phrasing — mirrors English debt totals (experiment branch).
    if (q.contains("jumla ya deni") || q.contains("deni jumla") ||
        (hasAny("deni", "madeni") && hasAny("jumla", "kiasi") && (q.contains("deni") || q.contains("madeni")))
    ) {
        return polish(question, "Total outstanding recorded debt: ${fmt(outstanding)}.", languageDisplayName)
    }
    if ((q.contains("nani") && hasAny("deni", "madeni", "wanidai", "inadaiwa")) ||
        hasAny("nani anadaiwa", "nani ananidai")
    ) {
        val top = unpaidDebts.groupBy { it.customerId }
            .mapValues { (_, d) -> d.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }.take(12)
        val txt = if (top.isEmpty()) {
            "No outstanding debt rows."
        } else {
            top.joinToString("; ") { e ->
                val name = customers.find { it.id == e.key }?.name ?: "#${e.key}"
                "$name ${fmt(e.value)}"
            }
        }
        return polish(question, "Outstanding by customer (top slice): $txt.", languageDisplayName)
    }

    // ── Category 5–6: customers & debt ───────────────────────────────
    if (q.contains("owe") && (q.contains("total") || q.contains("how much"))) {
        return polish(question, "Total outstanding recorded debt: ${fmt(outstanding)}.", languageDisplayName)
    }
    if (q.contains("who owes") && q.contains("most")) {
        val byC = unpaidDebts.groupBy { it.customerId }.mapValues { (_, d) -> d.sumOf { it.amount } }
            .maxByOrNull { it.value }
        return if (byC != null) {
            val name = customers.find { it.id == byC.key }?.name ?: "Customer #${byC.key}"
            polish(question, "Largest unpaid bucket by customer: $name at ${fmt(byC.value)}.", languageDisplayName)
        } else {
            polish(question, "No unpaid debt rows.", languageDisplayName)
        }
    }

    if (q.contains("best customer") || q.contains("top customer")) {
        val spend = lines.filter { it.customerId != null }
            .groupBy { it.customerId!! }.mapValues { (_, v) -> v.sumOf { it.lineTotal } }
            .maxByOrNull { it.value }
        return if (spend != null) {
            val name = customers.find { it.id == spend.key }?.name ?: "Customer #${spend.key}"
            polish(question, "Top customer by POS line revenue (with customer linked): $name at ${fmt(spend.value)}.", languageDisplayName)
        } else {
            polish(question, "No POS lines with a linked customer yet.", languageDisplayName)
        }
    }

    if (q.contains("how many customers")) {
        return polish(question, "Customers in address book: ${customers.size}.", languageDisplayName)
    }

    if (hasAny("new customer") && hasAny("month")) {
        val c = customers.count { it.createdAt in m0..m1 && it.createdAt > 0 }
        return polish(question, "Customer profiles first saved this calendar month: $c.", languageDisplayName)
    }

    if (hasAny("came back") && hasAny("month") && hasAny("customer")) {
        val back = customers.count { it.lastVisit in m0..m1 && it.createdAt > 0 && it.createdAt < m0 }
        return polish(
            question,
            "Heuristic 'returning' customers: $back profiles with last_visit this month but created before the month started.",
            languageDisplayName,
        )
    }

    if (hasAny("repeat customer") || (hasAny("repeat") && hasAny("sale"))) {
        val byCust = lines.filter { it.customerId != null }.groupBy { it.customerId!! }
        val withSales = byCust.keys.size
        val repeaters = byCust.values.count { ls -> ls.map { it.transactionId }.distinct().size > 1 }
        val pct = if (withSales > 0) repeaters * 100.0 / withSales else 0.0
        return polish(
            question,
            "Among customers linked on POS lines, $repeaters of $withSales (~${"%.0f".format(pct)}%) have more than one receipt in the analysis window.",
            languageDisplayName,
        )
    }

    if (hasAny("top 10") && hasAny("customer")) {
        val top = lines.filter { it.customerId != null }
            .groupBy { it.customerId!! }
            .mapValues { (_, v) -> v.sumOf { it.lineTotal } }
            .entries.sortedByDescending { it.value }.take(10)
        val txt = top.joinToString("; ") { e ->
            val name = customers.find { it.id == e.key }?.name ?: "#${e.key}"
            "$name ${fmt(e.value)}"
        }
        return polish(question, "Top 10 customers by POS line revenue: $txt.", languageDisplayName)
    }

    if (hasAny("not come back", "not been back", "inactive customer", "lost customer")) {
        val threshold = today.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
        val dormant = customers.filter { it.lastVisit in 1 until threshold }
            .sortedBy { it.lastVisit }
            .take(15)
        return polish(
            question,
            if (dormant.isEmpty()) {
                "No customers with last_visit between 1 and 30 days ago using the simple rule."
            } else {
                "Customers with last_visit over ~30 days ago (sample): " +
                    dormant.joinToString(", ") {
                        "${it.name} (${Instant.ofEpochMilli(it.lastVisit).atZone(zone).toLocalDate()})"
                    } + "."
            },
            languageDisplayName,
        )
    }

    if (hasAny("average spend") && (hasAny("visit", "customer", "per"))) {
        val grouped = txs.filter { it.type == TransactionType.INCOME && it.amount > 0 && it.customerId != null }
        val avg = if (grouped.isEmpty()) 0.0 else grouped.sumOf { it.amount } / grouped.size
        return polish(
            question,
            "Mean income transaction amount when a customer is linked on the receipt: ${fmt(avg)} (${grouped.size} transactions).",
            languageDisplayName,
        )
    }

    if (hasAny("credit") && hasAny("customer") && hasAny("most", "top", "biggest")) {
        val top = lines.filter { it.customerId != null }
            .groupBy { it.customerId!! }
            .mapValues { (_, v) -> v.sumOf { it.lineTotal } }
            .entries.sortedByDescending { it.value }.take(5)
        val txt = top.joinToString("; ") { e ->
            val name = customers.find { it.id == e.key }?.name ?: "#${e.key}"
            "$name ${fmt(e.value)}"
        }
        return polish(
            question,
            "Customers with the highest POS line totals while linked (often credit-style checkouts): $txt.",
            languageDisplayName,
        )
    }

    if ((hasAny("owe", "owing") && hasAny("right now", "currently")) || hasAny("who owes me money")) {
        val top = unpaidDebts.groupBy { it.customerId }
            .mapValues { (_, d) -> d.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }.take(12)
        val txt = if (top.isEmpty()) {
            "No outstanding debt rows."
        } else {
            top.joinToString("; ") { e ->
                val name = customers.find { it.id == e.key }?.name ?: "#${e.key}"
                "$name ${fmt(e.value)}"
            }
        }
        return polish(question, "Outstanding by customer (top slice): $txt.", languageDisplayName)
    }

    if (hasAny("oldest") && hasAny("debt", "unpaid")) {
        val oldest = unpaidDebts.minByOrNull { it.createdAt }
        return if (oldest != null && oldest.amount > 0) {
            val days = (nowMs - oldest.createdAt) / MS_PER_DAY_ROUTE
            val name = customers.find { it.id == oldest.customerId }?.name ?: "Customer #${oldest.customerId}"
            polish(
                question,
                "Oldest still-positive debt row: $name, ${fmt(oldest.amount)}, created ~${days} days ago.",
                languageDisplayName,
            )
        } else {
            polish(question, "No unpaid debt rows to rank.", languageDisplayName)
        }
    }

    if (hasAny("overdue") && hasAny("customer", "debt", "how many", "count")) {
        val od = unpaidDebts.count { it.dueDate != null && it.dueDate!! < nowMs }
        return polish(question, "Debt rows with a due date in the past and amount > 0: $od.", languageDisplayName)
    }

    if (hasAny("14") && hasAny("day") && hasAny("overdue", "past due", "late")) {
        val od = unpaidDebts.filter { it.dueDate != null && it.dueDate!! < fourteenDaysAgo }
        val sum = od.sumOf { it.amount }
        return polish(
            question,
            "Debts whose due date is more than 14 days ago: ${od.size} rows totaling about ${fmt(sum)}.",
            languageDisplayName,
        )
    }

    if (hasAny("credit") && hasAny("extend") && hasAny("month")) {
        val ext = unpaidDebts.filter { it.createdAt in m0..m1 }.sumOf { it.amount }
        return polish(
            question,
            "Sum of still-outstanding debt rows first created this month: ${fmt(ext)} (not the same as repayments).",
            languageDisplayName,
        )
    }

    if (hasAny("repayment", "repaid") && hasAny("credit", "debt", "month")) {
        return polish(
            question,
            "Credit repayments are applied inside debt rows (no separate repayment ledger in chat). Check customer debt history in the app for details.",
            languageDisplayName,
        )
    }

    if (hasAny("repayment rate", "credit repayment")) {
        return polish(
            question,
            "Repayment rate needs a repayment timeline; only outstanding totals are exposed here. Outstanding recorded: ${fmt(outstanding)}.",
            languageDisplayName,
        )
    }

    if (hasAny("too much credit", "extending too much")) {
        val rev = lines.sumOf { abs(it.lineTotal) }
        val linked = lines.filter { it.customerId != null }.sumOf { abs(it.lineTotal) }
        val pct = if (rev > 0) linked / rev * 100.0 else 0.0
        return polish(
            question,
            "Heuristic: ${"%.1f".format(pct)}% of POS line revenue has a customer linked; outstanding debt ${fmt(outstanding)}. Compare to your own comfort level.",
            languageDisplayName,
        )
    }

    if (hasAny("this time last year", "year ago", "than last year") && hasAny("revenue", "income", "money", "making")) {
        val symEnd = today.minusYears(1)
        val symStart = today.with(TemporalAdjusters.firstDayOfMonth()).minusYears(1)
        val (ly0, ly1) = dayRange(symStart).first to dayRange(symEnd).second
        val incNow = incomeInRange(m0, m1)
        val incThen = incomeInRange(ly0, ly1)
        return polish(
            question,
            "Income this month so far ${fmt(incNow)} vs the same calendar slice last year (through ${symEnd}): ${fmt(incThen)}.",
            languageDisplayName,
        )
    }

    if (hasAny("revenue target", "sales target", "hitting my target")) {
        return polish(
            question,
            "No monthly revenue target is stored in the local database yet — ask with a number (e.g. target 50000 this month) for a pace check.",
            languageDisplayName,
        )
    }

    if (hasAny("offline") && (hasAny("sync", "sale", "recorded", "device"))) {
        return polish(
            question,
            "Offline queue / per-device sync counts are not stored on transaction rows in this build.",
            languageDisplayName,
        )
    }

    if (hasAny("suspicious", "fraud", "should i worry")) {
        val suspiciousLines = lines.count { line ->
            val p = productById[line.productId] ?: return@count false
            p.price > 0 && line.unitPrice > 0 && line.unitPrice < p.price * 0.5
        }
        val sb = StringBuilder()
        sb.append("Heuristic: ").append(suspiciousLines).append(" POS lines sold below 50% of current list price. ")
        val expAmt = txs.filter { it.type == TransactionType.EXPENSE && it.amount > 0 }.map { it.amount }.sorted()
        if (expAmt.size >= 5) {
            val med = expAmt[expAmt.size / 2]
            val big = txs.filter { it.type == TransactionType.EXPENSE && it.amount > med * 5 }.take(3)
            if (big.isNotEmpty()) {
                sb.append("Large expenses (>5× median expense): ")
                sb.append(big.joinToString { "${fmt(it.amount)} (${it.description})" })
                sb.append(". ")
            }
        }
        return polish(question, sb.toString().trim(), languageDisplayName)
    }

    // Named customer spend / owe / last visit
    val custFrag = extractCustomerNameFragment(q, customers)
    if (custFrag != null) {
        val cs = customers.filter { it.name.contains(custFrag, true) }
        if (cs.size == 1) {
            val id = cs.first().id
            val spent = lines.filter { it.customerId == id }.sumOf { it.lineTotal }
            val owe = unpaidDebts.filter { it.customerId == id }.sumOf { it.amount }
            val lv = cs.first().lastVisit
            val lvTxt = if (lv > 0) Instant.ofEpochMilli(lv).atZone(zone).toLocalDate().toString() else "never recorded"
            if (q.contains("spent") || q.contains("pay")) {
                return polish(question, "${cs.first().name}: POS line revenue with customer linked ${fmt(spent)}; unpaid debt rows total ${fmt(owe)}; last_visit $lvTxt.", languageDisplayName)
            }
            if (q.contains("owe")) {
                return polish(question, "${cs.first().name} unpaid debt total: ${fmt(owe)}.", languageDisplayName)
            }
            if (q.contains("last") && q.contains("shop")) {
                return polish(question, "${cs.first().name} last_visit (from customer record): $lvTxt.", languageDisplayName)
            }
            if (hasAny("usually", "often") && hasAny("buy", "purchase", "get", "pick")) {
                val usual = lines.filter { it.customerId == id }
                    .groupBy { it.productName }
                    .mapValues { (_, v) -> v.sumOf { it.quantity } }
                    .entries.sortedByDescending { it.value }.take(5)
                val txt = usual.joinToString(", ") { "${it.key} (${it.value} u)" }
                return polish(
                    question,
                    "${cs.first().name} — most common linked products by units in the window: $txt.",
                    languageDisplayName,
                )
            }
            if (hasAny("how often", "how many times") && hasAny("visit")) {
                val visits = lines.filter { it.customerId == id }.map { it.transactionId }.distinct().size
                return polish(
                    question,
                    "${cs.first().name}: $visits linked POS receipt(s) in the analysis window.",
                    languageDisplayName,
                )
            }
        }
    }

    return null
}


internal suspend fun structuredQueryPart4(
    ctx: StructuredQueryContext,
    polish: suspend (String, String, String) -> String,
): String? = with(ctx) {
    // ── Category 9: fraud / loss (light heuristics) ─────────────────
    if (q.contains("missing") && q.contains("stock")) {
        return polish(
            question,
            "For shrinkage-style checks use Home → Loss prevention cards (scheduled scan). " +
                "Chat does not re-run those rules here.",
            languageDisplayName,
        )
    }

    // ── Category 10–11: planning / targets (heuristic) ────────────
    if (q.contains("growth rate")) {
        val thisM = incomeInRange(m0, m1)
        val lastM = incomeInRange(lmStart, lmEnd)
        val g = if (lastM > 0) (thisM - lastM) / lastM * 100.0 else 0.0
        return polish(question, "Rough month-to-month income growth (this month so far vs last full month): ${"%.1f".format(g)}%.", languageDisplayName)
    }

    if (q.contains("need to sell") && q.contains("target")) {
        val m = Regex("""([\d,]+)""").find(q)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        val mtd = incomeInRange(m0, m1)
        val dim = today.lengthOfMonth()
        val day = today.dayOfMonth
        return if (m != null && day > 0) {
            val need = max(0.0, m - mtd)
            val daysLeft = dim - day + 1
            val perDay = need / daysLeft
            polish(question, "Target ${fmt(m)} this month; have ${fmt(mtd)} so far; need about ${fmt(need)} over ~$daysLeft days → ~${fmt(perDay)} per day.", languageDisplayName)
        } else {
            null
        }
    }

    if (hasAny("focus on") && hasAny("sell", "selling", "stock")) {
        val top = lines.groupBy { it.productName }.mapValues { (_, v) -> v.sumOf { it.lineTotal } }
            .entries.sortedByDescending { it.value }.firstOrNull()
        return if (top != null) {
            polish(
                question,
                "By recent POS line revenue, pushing ${top.key} (${fmt(top.value)}) moves the needle most — pair with margin checks.",
                languageDisplayName,
            )
        } else {
            null
        }
    }

    if (hasAny("next month") && hasAny("enough", "cover", "cash") && hasAny("expense")) {
        val inc = incomeInRange(m0, m1)
        val exp = expenseInRange(m0, m1)
        return polish(
            question,
            "Cash sufficiency needs bills and supplier timing not stored here. This month so far net about ${fmt(inc - exp)} from recorded income minus expenses.",
            languageDisplayName,
        )
    }

    if (hasAny("next month") && hasAny("revenue", "money", "make", "income", "likely", "forecast")) {
        val dayOfMonth = today.dayOfMonth
        val dim = today.lengthOfMonth()
        val mtd = incomeInRange(m0, m1)
        val pace = if (dayOfMonth > 0) mtd * dim / dayOfMonth else mtd
        val projNext = pace
        return polish(
            question,
            "Naive forecast: if next month paced like this month's income so far, expect roughly ${fmt(projNext)} (no seasonality adjustment).",
            languageDisplayName,
        )
    }

    if (hasAny("holiday", "festival", "ramadan", "christmas", "prepare")) {
        return polish(
            question,
            "Preparation is business-specific; data-wise, raise stock on your top sellers and watch low-stock alerts in Inventory.",
            languageDisplayName,
        )
    }

    if (hasAny("price sensitivity", "sensitive to price", "elastic")) {
        return polish(
            question,
            "Price elasticity is not estimated from this schema alone — you need A/B price tests or richer demand data.",
            languageDisplayName,
        )
    }

    if (hasAny("maximise", "maximize") && hasAny("profit") && hasAny("price")) {
        return polish(
            question,
            "Optimal price for profit needs demand elasticity; use margin on list and \"Suggest price\" in Inventory as a starting point.",
            languageDisplayName,
        )
    }

    if (hasAny("losing money") && hasAny("product")) {
        return polish(
            question,
            "Check POS lines where unit_price is below recorded cost (ask \"sold below cost\") and tighten discounts.",
            languageDisplayName,
        )
    }

    // Category 8: pricing (pointer)
    if (q.contains("right price") || q.contains("charge more")) {
        return polish(
            question,
            "For suggested list prices from costs and category, use Inventory → open product → \"Suggest price\". Chat uses recorded list/cost only.",
            languageDisplayName,
        )
    }
    if (hasAny("priced too low", "too cheap", "underpriced", "charge too little")) {
        return polish(
            question,
            "Compare each product's list price to cost and category norms in Inventory → \"Suggest price\"; also scan for deep discount lines.",
            languageDisplayName,
        )
    }

    if (hasAny("worried", "worry about", "should i be worried", "red flag")) {
        val low = products.count { it.stockQuantity < 10 }
        return polish(
            question,
            "Quick data signals: $low products under 10 units; outstanding debt ${fmt(outstanding)}; scan \"suspicious\" and below-cost sales if discounts feel off.",
            languageDisplayName,
        )
    }
    if ((hasAny("going well", "what is working")) && hasAny("business", "my business", "things")) {
        val top = lines.groupBy { it.productName }.mapValues { (_, v) -> v.sumOf { it.lineTotal } }
            .entries.sortedByDescending { it.value }.firstOrNull()
        val mtd = incomeInRange(m0, m1)
        return polish(
            question,
            "This month's income pace so far is ${fmt(mtd)}; top recent line revenue driver: ${top?.key ?: "—"}.",
            languageDisplayName,
        )
    }
    if (hasAny("differently tomorrow", "do differently") || (hasAny("tomorrow") && hasAny("what should i", "should i do"))) {
        return polish(
            question,
            "Practical loop: reconcile today's payments, restock anything that hit low-stock, and review any below-list POS lines from the suspicious scan.",
            languageDisplayName,
        )
    }
    if (hasAny("how much stock should i order", "should i order", "reorder this week")) {
        return polish(
            question,
            "Ordering quantity needs supplier lead times; use low-stock alerts plus each product's recent 30-day units (ask days-of-stock style questions) as a baseline.",
            languageDisplayName,
        )
    }

    return null
}

internal suspend fun runStructuredQueryRoutes(
    ctx: StructuredQueryContext,
    polish: suspend (String, String, String) -> String,
): String? =
    structuredQueryPart1(ctx, polish)
        ?: structuredQueryPart2(ctx, polish)
        ?: structuredQueryPart3(ctx, polish)
        ?: structuredQueryPart4(ctx, polish)
