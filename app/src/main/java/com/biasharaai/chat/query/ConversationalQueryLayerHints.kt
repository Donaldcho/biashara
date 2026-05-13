package com.biasharaai.chat.query

import com.biasharaai.data.local.db.Transaction
import com.biasharaai.data.local.db.TransactionType
import java.time.LocalDate

/**
 * Compact, numeric tone hints for [GemmaAnswerFormatter] (optional; facts block stays authoritative).
 */
internal fun buildStructuredPolishHints(
    txs: List<Transaction>,
    today: LocalDate,
    t0: Long,
    t1: Long,
    y0: Long,
    y1: Long,
    m0: Long,
    m1: Long,
    outstanding: Double,
    fmt: (Double) -> String,
): String {
    fun inc(a: Long, b: Long): Double =
        txs.asSequence()
            .filter { it.type == TransactionType.INCOME && it.amount > 0 && it.date in a..b }
            .sumOf { it.amount }
    val todayInc = inc(t0, t1)
    val yestInc = inc(y0, y1)
    val mtd = inc(m0, m1)
    return buildString {
        append("Tone_hints (numbers for contrast only; the Facts block is the source of truth):\n")
        append("- calendar_day=").append(today).append('\n')
        append("- income_today=").append(fmt(todayInc)).append('\n')
        append("- income_yesterday=").append(fmt(yestInc)).append('\n')
        append("- income_month_to_date=").append(fmt(mtd)).append('\n')
        append("- outstanding_debt_total=").append(fmt(outstanding)).append('\n')
    }
}
