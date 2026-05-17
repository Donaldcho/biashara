package com.biasharaai.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.biasharaai.data.local.db.AppSettings
import com.biasharaai.data.local.db.AppSettingsDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

private const val WARMUP_UTTERANCE_ID = "warmup"

/**
 * On-device TTS using Android [TextToSpeech], driven by [AppSettings] voice/TTS prefs.
 * Call [initialize] once from the application (main thread); call [speak] from any coroutine.
 */
@Singleton
class BiasharaTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDao: AppSettingsDao,
) {
    private val mutex = Mutex()
    private var tts: TextToSpeech? = null

    // Tracks async TTS init completion; speak() awaits this before speaking.
    private val _isReady = MutableStateFlow(false)
    private val isReady get() = _isReady.value

    /** TTS init callback runs on the main thread; Room forbids sync DAO reads there — load prefs off the UI thread. */
    private val warmupExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "BiasharaTts-warmup").apply { isDaemon = true }
    }

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    @Synchronized
    fun initialize() {
        if (tts != null) return
        startEngine()
    }

    @Synchronized
    private fun ensureInitialized() {
        if (tts != null) return
        startEngine()
    }

    @Synchronized
    private fun startEngine() {
        val engine = TextToSpeech(context) { status ->
            _isReady.value = status == TextToSpeech.SUCCESS
            if (_isReady.value) applySettingsWarmup()
        }
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId != WARMUP_UTTERANCE_ID) {
                        _isSpeaking.value = true
                    }
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId != WARMUP_UTTERANCE_ID) {
                        _isSpeaking.value = false
                    }
                }

                @Suppress("DEPRECATION")
                @Deprecated("Implement only until minSdk reaches the non-deprecated path exclusively")
                override fun onError(utteranceId: String?) {
                    if (utteranceId != WARMUP_UTTERANCE_ID) {
                        _isSpeaking.value = false
                    }
                }
            },
        )
        tts = engine
    }

    suspend fun speak(
        text: String,
        languageCode: String? = null,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
    ) = mutex.withLock {
        val settings = withContext(Dispatchers.IO) {
            settingsDao.getSettingsSync()
        } ?: AppSettings()
        if (!settings.ttsEnabled) return@withLock

        withContext(Dispatchers.Main) {
            ensureInitialized()
            // Wait up to 3 s for the async TTS init callback to fire before giving up.
            if (!isReady) {
                withTimeoutOrNull(3_000) { _isReady.first { it } }
            }
            val engine = tts
            if (!isReady || engine == null) return@withContext

            val lang = languageCode ?: settings.voiceLanguageMode.let { mode ->
                if (mode == "AUTO") "sw" else mode
            }
            val locale = TtsLanguageMapper.toLocale(lang)
            val available = TtsLanguageMapper.isAvailable(engine, lang)
            engine.language = if (available) locale else Locale.ENGLISH
            engine.setSpeechRate(settings.ttsSpeechRate.toFloat())
            engine.setPitch(settings.ttsPitch.toFloat())

            val clean = sanitiseForSpeech(text.trim(), lang)
            if (clean.isBlank()) return@withContext

            val utteranceId = System.currentTimeMillis().toString()
            engine.speak(clean, queueMode, Bundle.EMPTY, utteranceId)
        }
    }

    suspend fun stop() = mutex.withLock {
        withContext(Dispatchers.Main) {
            tts?.stop()
            _isSpeaking.value = false
        }
    }

    @Synchronized
    fun release() {
        warmupExecutor.shutdownNow()
        tts?.shutdown()
        tts = null
        _isReady.value = false
        _isSpeaking.value = false
    }

    private fun applySettingsWarmup() {
        warmupExecutor.execute {
            val settings = runCatching { settingsDao.getSettingsSync() }.getOrNull() ?: AppSettings()
            ContextCompat.getMainExecutor(context).execute {
                val engine = tts ?: return@execute
                engine.setSpeechRate(settings.ttsSpeechRate.toFloat())
                engine.setPitch(settings.ttsPitch.toFloat())
                engine.speak("", TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, WARMUP_UTTERANCE_ID)
            }
        }
    }

    private fun sanitiseForSpeech(text: String, lang: String): String {
        val stripped = text
            .replace(Regex("[*_~`]"), "")
            .replace(Regex("\\n+"), ". ")
        return when (lang.lowercase()) {
            "sw" -> stripped.replace(Regex("KSh", RegexOption.IGNORE_CASE), "Shilingi")
            "am" -> stripped.replace(Regex("ETB", RegexOption.IGNORE_CASE), "Birr")
            else -> stripped
                .replace(Regex("₦"), "Naira")
                .replace(Regex("\\bNGN\\b", RegexOption.IGNORE_CASE), "Naira")
                .replace(Regex("\\bETB\\b", RegexOption.IGNORE_CASE), "Birr")
        }.trim()
    }
}
