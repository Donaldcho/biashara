package com.biasharaai.ledger.intelligence

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class LedgerPeriodBounds(
    val startMillis: Long,
    val endExclusiveMillis: Long,
    val timezone: String,
)

object LedgerPeriodResolver {

    fun resolve(period: String, timezoneId: String, nowMillis: Long = System.currentTimeMillis()): LedgerPeriodBounds {
        val zone = resolveZone(timezoneId)
        val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val (start, endExclusive) = when (period.uppercase().replace('-', '_')) {
            "LAST_7_DAYS" -> {
                val end = today.plusDays(1)
                end.minusDays(7) to end
            }
            "LAST_30_DAYS" -> {
                val end = today.plusDays(1)
                end.minusDays(30) to end
            }
            "THIS_MONTH" -> {
                val start = today.withDayOfMonth(1)
                val end = start.plusMonths(1)
                start to end
            }
            "LAST_MONTH" -> {
                val start = today.withDayOfMonth(1).minusMonths(1)
                val end = start.plusMonths(1)
                start to end
            }
            "THIS_WEEK" -> {
                val start = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                start to start.plusWeeks(1)
            }
            else -> {
                val end = today.plusDays(1)
                end.minusDays(30) to end
            }
        }
        return LedgerPeriodBounds(
            startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli(),
            endExclusiveMillis = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli(),
            timezone = zone.id,
        )
    }

    fun resolveLastDays(
        days: Int,
        timezoneId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): LedgerPeriodBounds {
        val zone = resolveZone(timezoneId)
        val endExclusive = java.time.Instant.ofEpochMilli(nowMillis)
            .atZone(zone)
            .toLocalDate()
            .plusDays(1)
        val start = endExclusive.minusDays(days.coerceAtLeast(1).toLong())
        return LedgerPeriodBounds(
            startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli(),
            endExclusiveMillis = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli(),
            timezone = zone.id,
        )
    }

    private fun resolveZone(timezoneId: String): ZoneId =
        runCatching { ZoneId.of(timezoneId) }.getOrDefault(ZoneId.systemDefault())
}
