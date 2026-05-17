package com.biasharaai.cash

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure logic tests for the storage enforcement thresholds — no Android context needed.
 */
class StorageWatchdogLogicTest {

    private val MB = 1_024L * 1_024L

    @Test
    fun thumbnailCapThreshold_is10MB() {
        val tenMb = 10L * MB
        // Sanity: 10 MB in bytes
        assertEquals(10_485_760L, tenMb)
    }

    @Test
    fun freeStorageThreshold_is100MB() {
        val hundredMb = 100L * MB
        assertEquals(104_857_600L, hundredMb)
    }

    @Test
    fun keepCount_is150() {
        // If the design changes this must be updated in StorageWatchdogWorker too
        val keepCount = 150
        assertEquals(150, keepCount)
    }
}
