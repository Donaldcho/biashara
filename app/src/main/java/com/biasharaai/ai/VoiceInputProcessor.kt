package com.biasharaai.ai

import android.content.Intent
import android.speech.RecognizerIntent
import android.util.Log
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.voice.TranscriptionEngine
import com.biasharaai.voice.TranscriptionResult
import com.biasharaai.voice.WhisperTranscriber
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Voice input: tier-aware routing between **WhisperKit**, future **Gemma** audio, and
 * [RecognizerIntent] fallback.
 *
 * @see AudioCaptureHelper streaming PCM (16 kHz mono).
 * @see WhisperTranscriber on-device STT when [WhisperTranscriber.isAvailable].
 */
@Singleton
class VoiceInputProcessor @Inject constructor(
    /** Deferred so WhisperKit JNI is not loaded during app / [com.biasharaai.skills.SkillRegistry] startup. */
    private val whisperTranscriber: Lazy<WhisperTranscriber>,
    private val audioCaptureHelper: AudioCaptureHelper,
    private val appSettingsDao: AppSettingsDao,
    private val capabilityTier: CapabilityTier,
) {
    companion object {
        private const val TAG = "VoiceInputProcessor"
    }

    /**
     * Whether in-app capture + on-device STT should run instead of the system recognizer.
     * Lazily prepares WhisperKit when voice is enabled (Gallery-style on-demand init).
     */
    suspend fun shouldUseOnDeviceAi(): Boolean = withContext(Dispatchers.IO) {
        selectEngine(Locale.getDefault(), resolveLanguageHint(null)) != VoiceSttEngine.SPEECH_RECOGNIZER
    }

    /**
     * Loads Whisper weights if needed. Returns false when init fails (caller should fall back to
     * [RecognizerIntent]).
     */
    suspend fun ensureWhisperReady(): Boolean = withContext(Dispatchers.IO) {
        val whisper = whisperTranscriber.get()
        if (whisper.isAvailable()) return@withContext true
        runCatching {
            whisper.initialize()
        }.onFailure {
            Log.w(TAG, "WhisperKit initialize failed", it)
        }.isSuccess && whisper.isAvailable()
    }

    /**
     * Mic pipeline as events: listening → engine → partial/final transcripts, or fallback / error.
     */
    fun startListening(
        locale: Locale = Locale.getDefault(),
        languageHint: String? = null,
    ): Flow<VoiceInputEvent> = channelFlow {
        send(VoiceInputEvent.Listening)
        val hint = resolveLanguageHint(languageHint)
        val engine = selectEngine(locale, hint)
        send(VoiceInputEvent.EngineSelected(engine))
        when (engine) {
            VoiceSttEngine.SPEECH_RECOGNIZER -> {
                send(VoiceInputEvent.UseSpeechRecognizerFallback)
                return@channelFlow
            }
            VoiceSttEngine.WHISPER -> {
                val audioFlow = audioCaptureHelper.startRecording()
                whisperTranscriber.get().transcribeStream(audioFlow, hint).collect { r ->
                    send(
                        if (r.isPartial) {
                            VoiceInputEvent.PartialTranscription(r.text)
                        } else {
                            VoiceInputEvent.FinalTranscription(r)
                        },
                    )
                }
            }
            VoiceSttEngine.GEMMA_3N -> {
                val all = ArrayList<Short>(64_000)
                audioCaptureHelper.startRecording().collect { chunk ->
                    val data = chunk.pcmData
                    for (i in data.indices) all.add(data[i])
                }
                val text = transcribePcmWithGemma(all.toShortArray(), locale, hint)
                if (!text.isNullOrBlank()) {
                    send(
                        VoiceInputEvent.FinalTranscription(
                            TranscriptionResult(
                                text = text,
                                language = hint ?: locale.language,
                                confidence = 1f,
                                isPartial = false,
                                engine = TranscriptionEngine.GEMMA_3N,
                            ),
                        ),
                    )
                } else {
                    send(
                        VoiceInputEvent.Error(
                            "Gemma audio transcription is not available in this build (text-only LiteRT).",
                        ),
                    )
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * One-shot capture until silence / max duration; returns the latest non-empty Whisper text.
     * Returns `null` when the selected engine is [VoiceSttEngine.SPEECH_RECOGNIZER] or transcription fails.
     */
    suspend fun transcribeWithAi(
        locale: Locale = Locale.getDefault(),
        durationMs: Long = AudioCaptureHelper.DEFAULT_DURATION_MS,
    ): String? = withContext(Dispatchers.IO) {
        val hint = resolveLanguageHint(null)
        when (val engine = selectEngine(locale, hint)) {
            VoiceSttEngine.SPEECH_RECOGNIZER -> null
            VoiceSttEngine.GEMMA_3N -> transcribeWithGemmaPath(locale, durationMs, hint)
            VoiceSttEngine.WHISPER -> transcribeWithWhisperPath(locale, durationMs, hint)
        }
    }

    private suspend fun transcribeWithWhisperPath(
        locale: Locale,
        durationMs: Long,
        hint: String?,
    ): String? {
        if (!ensureWhisperReady()) return null
        var latest = ""
        runCatching {
            val cap = durationMs.coerceIn(1_000L, AudioCaptureHelper.MAX_STREAM_DURATION_MS)
            whisperTranscriber.get().transcribeStream(
                audioCaptureHelper.startRecording(maxDurationMs = cap),
                hint ?: locale.language,
            ).collect { r ->
                if (r.text.isNotBlank()) latest = r.text.trim()
            }
        }.onFailure { Log.w(TAG, "Whisper transcription failed", it) }
        return latest.takeIf { it.isNotEmpty() }
    }

    private suspend fun transcribeWithGemmaPath(
        locale: Locale,
        durationMs: Long,
        hint: String?,
    ): String? {
        if (!isGemmaAudioAvailable()) return null
        return runCatching {
            val all = ArrayList<Short>(64_000)
            val cap = durationMs.coerceIn(1_000L, AudioCaptureHelper.MAX_STREAM_DURATION_MS)
            audioCaptureHelper.startRecording(maxDurationMs = cap).collect { chunk ->
                val data = chunk.pcmData
                for (i in data.indices) all.add(data[i])
            }
            transcribePcmWithGemma(all.toShortArray(), locale, hint)?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrElse {
            Log.w(TAG, "Gemma audio path failed", it)
            null
        }
    }

    /** Prefer Whisper when voice is enabled; does not download/load weights (safe for routing checks). */
    private suspend fun canUseWhisper(): Boolean {
        val settings = appSettingsDao.getSettingsSync() ?: return false
        return settings.voiceInputEnabled
    }

    private suspend fun resolveLanguageHint(explicit: String?): String? {
        if (explicit != null) return explicit.lowercase(Locale.US)
        val mode = appSettingsDao.getSettingsSync()?.voiceLanguageMode ?: return null
        return if (mode.equals("AUTO", ignoreCase = true)) null else mode.lowercase(Locale.US)
    }

    private suspend fun selectEngine(locale: Locale, hint: String?): VoiceSttEngine {
        val lang = (hint ?: locale.language).lowercase(Locale.US)
        val prefersGemmaAudio = lang == "yo" || lang == "am"
        return when {
            prefersGemmaAudio &&
                capabilityTier == CapabilityTier.FULL_AI &&
                isGemmaAudioAvailable() -> VoiceSttEngine.GEMMA_3N
            canUseWhisper() -> VoiceSttEngine.WHISPER
            else -> VoiceSttEngine.SPEECH_RECOGNIZER
        }
    }

    @Suppress("unused")
    private fun isGemmaAudioAvailable(): Boolean = false

    private fun transcribePcmWithGemma(
        @Suppress("UNUSED_PARAMETER") pcm: ShortArray,
        @Suppress("UNUSED_PARAMETER") locale: Locale,
        @Suppress("UNUSED_PARAMETER") languageHint: String?,
    ): String? = null

    fun createSpeechRecognizerIntent(
        locale: Locale = Locale.getDefault(),
        prompt: String = "",
    ): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        if (prompt.isNotBlank()) {
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
    }
}
