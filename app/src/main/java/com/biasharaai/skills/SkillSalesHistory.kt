package com.biasharaai.skills

import com.biasharaai.data.local.db.PosSaleLineFact
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal object SkillSalesHistory {
    /**
     * Builds one integer per calendar day (oldest → newest) for the last [dayCount] days,
     * using POS sale line facts.
     */
    fun dailyTotalsFromFacts(
        facts: List<PosSaleLineFact>,
        dayCount: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Int> {
        val today = LocalDate.now(zone)
        val start = today.minusDays((dayCount - 1).toLong())
        val buckets = (0 until dayCount).associate { offset ->
            start.plusDays(offset.toLong()) to 0
        }.toMutableMap()

        for (fact in facts) {
            val day = Instant.ofEpochMilli(fact.transactionDate).atZone(zone).toLocalDate()
            if (day in buckets) {
                buckets[day] = buckets.getValue(day) + fact.quantity
            }
        }

        return buckets.keys.sorted().map { buckets.getValue(it) }
    }
}
