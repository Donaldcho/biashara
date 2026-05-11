package com.biasharaai.ai

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Verifies [InferenceSettingsStore] round-trips the same values consumed by [InferenceRuntimeSpec]
 * and [GemmaService] (no Robolectric — uses in-memory prefs).
 */
class InferenceSettingsStoreTest {

    private lateinit var memPrefs: MemSharedPreferences
    private lateinit var context: Context
    private lateinit var store: InferenceSettingsStore

    @Before
    fun setup() {
        memPrefs = MemSharedPreferences()
        context = mockk(relaxed = true)
        every {
            context.getSharedPreferences("inference_edge_gallery_prefs", Context.MODE_PRIVATE)
        } returns memPrefs
        store = InferenceSettingsStore(context)
    }

    @Test
    fun `save and load round trip`() {
        val custom = InferenceUiConfig(
            maxTokens = 3200,
            topK = 40,
            topP = 0.88f,
            temperature = 0.6f,
            preferCpu = true,
            enableThinking = true,
            enableSpeculativeDecoding = false,
        )
        store.save(custom)
        assertEquals(custom, store.load())
    }

    @Test
    fun `defaults when prefs empty`() {
        assertEquals(InferenceSettingsStore.DEFAULTS, store.load())
    }

    @Test
    fun `resetToDefaults persists DEFAULTS`() {
        store.save(
            InferenceUiConfig(
                maxTokens = 2000,
                topK = 5,
                topP = 0.1f,
                temperature = 0f,
                preferCpu = true,
                enableThinking = true,
                enableSpeculativeDecoding = true,
            ),
        )
        store.resetToDefaults()
        assertEquals(InferenceSettingsStore.DEFAULTS, store.load())
    }

    @Test
    fun `stored values match InferenceRuntimeSpec for FULL tier`() {
        store.save(
            InferenceUiConfig(
                maxTokens = 2500,
                topK = 10,
                topP = 0.5f,
                temperature = 0.25f,
                preferCpu = false,
                enableThinking = false,
                enableSpeculativeDecoding = false,
            ),
        )
        val r = InferenceRuntimeSpec.resolve(CapabilityTier.FULL_AI, store.load())
        assertEquals(2500, r.engineMaxTokens)
        assertEquals(10, r.sessionTopK)
        assertEquals(10, r.engineMaxTopK)
        assertEquals(0.5f, r.sessionTopP, 0.001f)
        assertEquals(0.25f, r.sessionTemperature, 0.001f)
    }
}
