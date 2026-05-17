package com.biasharaai.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * [LocalDate.ofInstant] requires API 34; use this on API 26+ (minSdk).
 */
fun millisToLocalDate(epochMillis: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
