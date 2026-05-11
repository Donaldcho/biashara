package com.biasharaai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ensures persisted UI values map to the same numbers [GemmaService] feeds into MediaPipe.
 */
class InferenceRuntimeSpecTest {

    private fun cfg(
        maxTokens: Int = 4000,
        topK: Int = 64,
        topP: Float = 0.95f,
        temperature: Float = 1.0f,
        preferCpu: Boolean = false,
        enableThinking: Boolean = false,
        enableSpeculative: Boolean = false,
    ) = InferenceUiConfig(
        maxTokens = maxTokens,
        topK = topK,
        topP = topP,
        temperature = temperature,
        preferCpu = preferCpu,
        enableThinking = enableThinking,
        enableSpeculativeDecoding = enableSpeculative,
    )

    @Test
    fun `FULL_AI uses Edge defaults unchanged`() {
        val r = InferenceRuntimeSpec.resolve(CapabilityTier.FULL_AI, InferenceSettingsStore.DEFAULTS)
        assertEquals(4000, r.engineMaxTokens)
        assertEquals(64, r.engineMaxTopK)
        assertEquals(64, r.sessionTopK)
        assertEquals(0.95f, r.sessionTopP, 0.001f)
        assertEquals(1.0f, r.sessionTemperature, 0.001f)
        assertEquals(false, r.userForcesCpu)
    }

    @Test
    fun `engine maxTopK equals session topK for GPU compatibility`() {
        val r = InferenceRuntimeSpec.resolve(CapabilityTier.FULL_AI, cfg(topK = 40))
        assertEquals(r.engineMaxTopK, r.sessionTopK)
    }

    @Test
    fun `FULL_AI maxTokens clamped high`() {
        val r = InferenceRuntimeSpec.resolve(CapabilityTier.FULL_AI, cfg(maxTokens = 9000))
        assertEquals(4000, r.engineMaxTokens)
    }

    @Test
    fun `FULL_AI maxTokens clamped low`() {
        val r = InferenceRuntimeSpec.resolve(CapabilityTier.FULL_AI, cfg(maxTokens = 100))
        assertEquals(2000, r.engineMaxTokens)
    }

    @Test
    fun `PARTIAL_AI maxTokens capped at 2048`() {
        val r = InferenceRuntimeSpec.resolve(CapabilityTier.PARTIAL_AI, cfg(maxTokens = 4000))
        assertEquals(2048, r.engineMaxTokens)
    }

    @Test
    fun `PARTIAL_AI maxTokens floored at 512`() {
        val r = InferenceRuntimeSpec.resolve(CapabilityTier.PARTIAL_AI, cfg(maxTokens = 100))
        assertEquals(512, r.engineMaxTokens)
    }

    @Test
    fun `topK clamped to 5 and 64`() {
        val low = InferenceRuntimeSpec.resolve(CapabilityTier.FULL_AI, cfg(topK = 1))
        assertEquals(5, low.sessionTopK)
        val high = InferenceRuntimeSpec.resolve(CapabilityTier.FULL_AI, cfg(topK = 200))
        assertEquals(64, high.sessionTopK)
    }

    @Test
    fun `topP and temperature clamped`() {
        val r = InferenceRuntimeSpec.resolve(
            CapabilityTier.FULL_AI,
            cfg(topP = -1f, temperature = 5f),
        )
        assertEquals(0f, r.sessionTopP, 0.001f)
        assertEquals(1f, r.sessionTemperature, 0.001f)
    }

    @Test
    fun `RULES tier uses minimal token budget`() {
        val r = InferenceRuntimeSpec.resolve(CapabilityTier.RULES_BASED, cfg(maxTokens = 4000))
        assertEquals(512, r.engineMaxTokens)
    }

    @Test
    fun `preferCpu flows to resolved flag`() {
        val r = InferenceRuntimeSpec.resolve(CapabilityTier.FULL_AI, cfg(preferCpu = true))
        assertTrue(r.userForcesCpu)
    }
}
