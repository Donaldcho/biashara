package com.biasharaai.ai

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DemandForecaster].
 *
 * Uses MockK to mock [GemmaService] and verify that the forecaster
 * correctly parses AI responses and falls back to rules-based predictions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DemandForecasterTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var gemmaService: GemmaService
    private lateinit var forecaster: DemandForecaster

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // Mock android.util.Log which is unavailable in JVM tests
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        gemmaService = mockk(relaxed = true)
        forecaster = DemandForecaster(gemmaService)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(android.util.Log::class)
    }

    // ── AI Response Parsing ─────────────────────────────────────────────

    @Test
    fun `predictDemand parses standard Day1 Day2 Day3 format`() = runTest {
        every { gemmaService.isAvailable } returns true
        coEvery { gemmaService.generateResponse(any()) } returns "Day1: 5, Day2: 8, Day3: 6"

        val result = forecaster.predictDemand("Sugar", listOf(3, 5, 4, 6, 7, 5, 4))
        assertEquals("Day1: 5, Day2: 8, Day3: 6", result)
    }

    @Test
    fun `predictDemand parses format with extra text around it`() = runTest {
        every { gemmaService.isAvailable } returns true
        coEvery { gemmaService.generateResponse(any()) } returns
            "Based on the data, I predict: Day1: 12, Day2: 10, Day3: 15. Good luck!"

        val result = forecaster.predictDemand("Milk", listOf(8, 10, 9, 11, 12, 10, 9))
        assertEquals("Day1: 12, Day2: 10, Day3: 15", result)
    }

    @Test
    fun `predictDemand parses format with spaces around colons`() = runTest {
        every { gemmaService.isAvailable } returns true
        coEvery { gemmaService.generateResponse(any()) } returns "Day 1 : 3, Day 2 : 7, Day 3 : 5"

        val result = forecaster.predictDemand("Bread", listOf(2, 4, 3, 5, 6, 4, 3))
        assertEquals("Day1: 3, Day2: 7, Day3: 5", result)
    }

    // ── Insufficient Data ───────────────────────────────────────────────

    @Test
    fun `predictDemand returns empty when data below minimum threshold`() = runTest {
        val result = forecaster.predictDemand("Sugar", listOf(3, 5, 4))
        assertEquals("", result)
    }

    @Test
    fun `predictDemand returns empty when data exactly one below minimum`() = runTest {
        val result = forecaster.predictDemand("Sugar", listOf(1, 2, 3, 4, 5, 6))
        assertEquals("", result)
    }

    // ── Rules-based Fallback ────────────────────────────────────────────

    @Test
    fun `predictDemand uses rules fallback when AI unavailable`() = runTest {
        every { gemmaService.isAvailable } returns false

        val result = forecaster.predictDemand("Rice", listOf(5, 5, 5, 5, 5, 5, 5))
        assertTrue("Rules fallback should produce Day1/Day2/Day3 format", result.contains("Day1:"))
        assertTrue(result.contains("Day2:"))
        assertTrue(result.contains("Day3:"))
    }

    @Test
    fun `rules fallback produces reasonable values for stable history`() = runTest {
        every { gemmaService.isAvailable } returns false

        // All constant 10s — average is 10, trend is ~0
        val result = forecaster.predictDemand("Water", listOf(10, 10, 10, 10, 10, 10, 10))
        assertTrue(result.contains("Day1: 10"))
    }

    // ── AI Exception Handling ───────────────────────────────────────────

    @Test
    fun `predictDemand falls back to rules when AI throws exception`() = runTest {
        every { gemmaService.isAvailable } returns true
        coEvery { gemmaService.generateResponse(any()) } throws RuntimeException("Model crashed")

        val result = forecaster.predictDemand("Soap", listOf(3, 4, 5, 3, 4, 5, 3))
        assertTrue("Should contain Day1 from rules fallback", result.contains("Day1:"))
    }

    // ── Unparseable AI Response ─────────────────────────────────────────

    @Test
    fun `predictDemand returns raw text when format cannot be parsed`() = runTest {
        every { gemmaService.isAvailable } returns true
        coEvery { gemmaService.generateResponse(any()) } returns "I don't know the answer."

        val result = forecaster.predictDemand("Oil", listOf(2, 3, 4, 2, 3, 4, 2))
        assertEquals("I don't know the answer.", result)
    }
}
