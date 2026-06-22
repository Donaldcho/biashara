package com.biasharaai.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists on-device LLM UI settings aligned with **Google AI Edge Gallery → Configurations**.
 *
 * "Thinking" / "speculative decoding" are not exposed on [com.google.mediapipe.tasks.genai.llminference]
 * in `tasks-genai:0.10.33`; toggles are stored for forward compatibility and reflected in chat
 * prompts where applicable.
 */
@Singleton
class InferenceSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val PREFS = "inference_edge_gallery_prefs"

        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_TOP_K = "top_k"
        private const val KEY_TOP_P = "top_p"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_PREFER_CPU = "prefer_cpu"
        private const val KEY_ENABLE_THINKING = "enable_thinking"
        private const val KEY_ENABLE_SPECULATIVE = "enable_speculative"

        /** Edge Gallery defaults when sliders are at maximum (screenshots reference). */
        val DEFAULTS = InferenceUiConfig(
            maxTokens = 4000,
            topK = 64,
            topP = 0.95f,
            temperature = 1.0f,
            preferCpu = false,
            enableThinking = false,
            enableSpeculativeDecoding = false,
        )
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): InferenceUiConfig = InferenceUiConfig(
        maxTokens = prefs.getInt(KEY_MAX_TOKENS, DEFAULTS.maxTokens),
        topK = prefs.getInt(KEY_TOP_K, DEFAULTS.topK),
        topP = prefs.getFloat(KEY_TOP_P, DEFAULTS.topP),
        temperature = prefs.getFloat(KEY_TEMPERATURE, DEFAULTS.temperature),
        preferCpu = prefs.getBoolean(KEY_PREFER_CPU, DEFAULTS.preferCpu),
        enableThinking = prefs.getBoolean(KEY_ENABLE_THINKING, DEFAULTS.enableThinking),
        enableSpeculativeDecoding = prefs.getBoolean(KEY_ENABLE_SPECULATIVE, DEFAULTS.enableSpeculativeDecoding),
    )

    fun save(config: InferenceUiConfig) {
        prefs.edit()
            .putInt(KEY_MAX_TOKENS, config.maxTokens)
            .putInt(KEY_TOP_K, config.topK)
            .putFloat(KEY_TOP_P, config.topP)
            .putFloat(KEY_TEMPERATURE, config.temperature)
            .putBoolean(KEY_PREFER_CPU, config.preferCpu)
            .putBoolean(KEY_ENABLE_THINKING, config.enableThinking)
            .putBoolean(KEY_ENABLE_SPECULATIVE, config.enableSpeculativeDecoding)
            .apply()
    }

    fun resetToDefaults() {
        save(DEFAULTS)
    }
}

data class InferenceUiConfig(
    val maxTokens: Int,
    val topK: Int,
    val topP: Float,
    val temperature: Float,
    val preferCpu: Boolean,
    val enableThinking: Boolean,
    val enableSpeculativeDecoding: Boolean,
)
