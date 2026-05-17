package com.biasharaai.voice

import android.util.Log
import com.biasharaai.ai.ActiveModelStore
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.ModelCapability
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VoiceRouterTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun whisperResult(text: String) = TranscriptionResult(
        text = text,
        language = "sw",
        confidence = 1f,
        isPartial = false,
        engine = TranscriptionEngine.WHISPER,
    )

    @Test
    fun classify_dataEntryScreen_returnsDataEntry() = runTest {
        val store = mockk<ActiveModelStore>(relaxed = true)
        every { store.isAvailable } returns false
        val router = VoiceRouter(store, CapabilityTier.RULES_BASED)
        val intent = router.classify(
            whisperResult("  Maize 2kg  "),
            VoiceScreenContext.ADD_EDIT_PRODUCT,
        )
        assertTrue(intent is VoiceIntent.DataEntry)
        assertEquals("Maize 2kg", (intent as VoiceIntent.DataEntry).text)
    }

    @Test
    fun classify_queryPattern_swahili_returnsQuery() = runTest {
        val store = mockk<ActiveModelStore>(relaxed = true)
        every { store.isAvailable } returns false
        val router = VoiceRouter(store, CapabilityTier.RULES_BASED)
        val intent = router.classify(
            whisperResult("Mauzo ya leo ni zipi"),
            VoiceScreenContext.AGENT_FEED,
        )
        assertTrue(intent is VoiceIntent.Query)
    }

    @Test
    fun classify_goHome_returnsCommand() = runTest {
        val store = mockk<ActiveModelStore>(relaxed = true)
        every { store.isAvailable } returns false
        val router = VoiceRouter(store, CapabilityTier.RULES_BASED)
        val intent = router.classify(
            whisperResult("nenda nyumbani"),
            VoiceScreenContext.CHAT,
        )
        assertTrue(intent is VoiceIntent.Command.GoHome)
    }

    @Test
    fun classify_openPos_returnsCommand() = runTest {
        val store = mockk<ActiveModelStore>(relaxed = true)
        every { store.isAvailable } returns false
        val router = VoiceRouter(store, CapabilityTier.RULES_BASED)
        val intent = router.classify(
            whisperResult("fungua pos"),
            VoiceScreenContext.CHAT,
        )
        assertTrue(intent is VoiceIntent.Command.OpenPOS)
    }

    @Test
    fun classify_usesLlmWhenModelAvailable() = runTest {
        val store = mockk<ActiveModelStore>(relaxed = true)
        every { store.isAvailable } returns true
        coEvery {
            store.sendPrompt(any(), ModelCapability.TEXT_GENERATION)
        } returns "QUERY"
        val router = VoiceRouter(store, CapabilityTier.PARTIAL_AI)
        val intent = router.classify(
            whisperResult("something ambiguous here"),
            VoiceScreenContext.AGENT_FEED,
        )
        assertTrue(intent is VoiceIntent.Query)
    }
}
