package com.biasharaai.skills

import com.google.gson.JsonParser
import java.time.LocalDate
import java.time.ZoneId

internal object SkillArgsParser {
    fun parseObject(argumentsJson: String): Result<Map<String, Any?>> = runCatching {
        val trimmed = argumentsJson.trim().ifBlank { "{}" }
        val obj = JsonParser.parseString(trimmed).asJsonObject
        obj.entrySet().associate { (k, v) ->
            k to when {
                v.isJsonNull -> null
                v.isJsonPrimitive && v.asJsonPrimitive.isBoolean -> v.asBoolean
                v.isJsonPrimitive && v.asJsonPrimitive.isNumber -> v.asDouble
                else -> v.asString
            }
        }
    }

    fun intArg(args: Map<String, Any?>, key: String, default: Int, min: Int, max: Int): Int {
        val raw = args[key]
        val n = when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        } ?: default
        return n.coerceIn(min, max)
    }

    fun stringArg(args: Map<String, Any?>, key: String): String? {
        val raw = args[key] ?: return null
        return raw?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun longArg(args: Map<String, Any?>, key: String): Long? {
        val raw = args[key]
        return when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        }
    }

    fun doubleArg(args: Map<String, Any?>, key: String): Double? {
        val raw = args[key]
        return when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        }
    }

    fun boolArg(args: Map<String, Any?>, key: String, default: Boolean): Boolean {
        val raw = args[key] ?: return default
        return when (raw) {
            is Boolean -> raw
            is String -> raw.equals("true", ignoreCase = true)
            is Number -> raw.toInt() != 0
            else -> default
        }
    }

    /** Inclusive lookback of [days] ending today (device timezone). Returns [startMillis, endExclusiveMillis). */
    fun periodBounds(days: Int, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val today = LocalDate.now(zone)
        val start = today.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val endExclusive = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to endExclusive
    }
}
