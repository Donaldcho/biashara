package com.biasharaai.ai

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.util.Log
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Voice input processor with two execution paths:
 *
 * - **FULL_AI**: Uses Gemma 3n multimodal audio via [LlmInferenceSession.addAudio]
 *   for on-device, language-aware transcription. Fully offline.
 *
 * - **PARTIAL_AI / RULES_BASED**: Falls back to Android's built-in
 *   [RecognizerIntent] (requires internet on most devices).
 *
 * @see AudioCaptureHelper for audio capture details.
 */
@Singleton
class VoiceInputProcessor @Inject constructor(
    private val gemmaService: GemmaService,
    private val capabilityTier: CapabilityTier,
) {
    companion object {
        private const val TAG = "VoiceInputProcessor"

        /** Language names by locale code — used in the transcription prompt. */
        private val LANGUAGE_NAMES = mapOf(
            "en" to "English",
            "sw" to "Swahili",
            "ha" to "Hausa",
            "yo" to "Yoruba",
            "am" to "Amharic",
        )
    }

    /**
     * Whether this processor will use on-device AI transcription.
     *
     * When `false`, the caller should use [createSpeechRecognizerIntent]
     * to launch the system SpeechRecognizer via an ActivityResultLauncher.
     */
    val usesOnDeviceAi: Boolean
        get() = false // Gemma 4 E2B LiteRT is text-only; use SpeechRecognizer

    /**
     * On-device AI transcription is currently disabled: the LiteRT-LM model we ship is text-only,
     * not multimodal, and there is no `LlmInferenceSession.addAudio` API in this runtime.
     *
     * Kept as a no-op so the existing call sites (gated by [usesOnDeviceAi] = `false`) still
     * compile. When we ship a multimodal model (e.g. Gemma 3n), implement this with
     * `Content.AudioBytes(audioData)` via `GemmaService.generateStreaming`.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun transcribeWithAi(
        locale: Locale = Locale.getDefault(),
        durationMs: Long = AudioCaptureHelper.DEFAULT_DURATION_MS,
    ): String? {
        Log.d(TAG, "transcribeWithAi called but on-device AI transcription is disabled")
        return null
    }

    /**
     * Create an [Intent] for the system SpeechRecognizer.
     *
 * Used as a fallback on PARTIAL_AI / RULES_BASED devices,
 * or when the user doesn't grant RECORD_AUDIO permission.
 *
 * Chat and product forms only use the microphone when the user has opted in
 * under Settings → Voice input ([VoiceInputPreferences]).
     */
    fun createSpeechRecognizerIntent(
        locale: Locale = Locale.getDefault(),
        prompt: String = "",
    ): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
        if (prompt.isNotBlank()) {
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
    }
}
