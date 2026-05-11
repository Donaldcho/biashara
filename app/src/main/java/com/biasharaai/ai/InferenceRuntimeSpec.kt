package com.biasharaai.ai

/**
 * Maps persisted [InferenceUiConfig] + device [CapabilityTier] to the exact numeric parameters
 * passed to MediaPipe [com.google.mediapipe.tasks.genai.llminference.LlmInference] and
 * [com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession].
 *
 * **GPU rule:** `LlmInferenceOptions.setMaxTopK` must be at least the session `setTopK` value.
 * We set both from the same coerced Top‑K so the engine always accepts the session.
 *
 * [GemmaService] must use this resolver for every engine build and every decode session so
 * Settings UI and native code stay in sync (see unit tests).
 */
object InferenceRuntimeSpec {

    data class Resolved(
        /** Passed to [LlmInference.LlmInferenceOptions.Builder.setMaxTokens]. */
        val engineMaxTokens: Int,
        /** Passed to [LlmInference.LlmInferenceOptions.Builder.setMaxTopK] (≥ session TopK). */
        val engineMaxTopK: Int,
        val sessionTopK: Int,
        val sessionTopP: Float,
        val sessionTemperature: Float,
        /** When true, [GemmaService] initializes with CPU (GPU fallback only if CPU init fails). */
        val userForcesCpu: Boolean,
    )

    fun resolve(tier: CapabilityTier, cfg: InferenceUiConfig): Resolved {
        val topK = cfg.topK.coerceIn(MIN_TOP_K, MAX_TOP_K)
        val topP = cfg.topP.coerceIn(MIN_TOP_P, MAX_TOP_P)
        val temperature = cfg.temperature.coerceIn(MIN_TEMPERATURE, MAX_TEMPERATURE)

        val engineMaxTokens = when (tier) {
            CapabilityTier.FULL_AI -> cfg.maxTokens.coerceIn(FULL_MIN_TOKENS, FULL_MAX_TOKENS)
            CapabilityTier.PARTIAL_AI -> cfg.maxTokens.coerceIn(PARTIAL_MIN_TOKENS, PARTIAL_MAX_TOKENS)
            else -> RULES_FALLBACK_TOKENS
        }

        return Resolved(
            engineMaxTokens = engineMaxTokens,
            engineMaxTopK = topK,
            sessionTopK = topK,
            sessionTopP = topP,
            sessionTemperature = temperature,
            userForcesCpu = cfg.preferCpu,
        )
    }

    private const val MIN_TOP_K = 5
    private const val MAX_TOP_K = 64
    private const val MIN_TOP_P = 0f
    private const val MAX_TOP_P = 0.95f
    private const val MIN_TEMPERATURE = 0f
    private const val MAX_TEMPERATURE = 1f

    private const val FULL_MIN_TOKENS = 2000
    private const val FULL_MAX_TOKENS = 4000
    private const val PARTIAL_MIN_TOKENS = 512
    private const val PARTIAL_MAX_TOKENS = 2048
    private const val RULES_FALLBACK_TOKENS = 512
}
