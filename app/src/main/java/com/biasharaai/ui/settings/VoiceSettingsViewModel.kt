package com.biasharaai.ui.settings

import android.content.Context
import android.util.Log
import android.speech.tts.TextToSpeech
import androidx.lifecycle.viewModelScope
import com.biasharaai.R
import com.biasharaai.ai.VoiceInputPreferences
import com.biasharaai.data.local.db.AppSettings
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.ui.base.BaseViewModel
import com.biasharaai.voice.BiasharaTtsEngine
import com.biasharaai.voice.WhisperTranscriber
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val appSettingsDao: AppSettingsDao,
    private val voiceInputPreferences: VoiceInputPreferences,
    private val whisperTranscriber: Lazy<WhisperTranscriber>,
    private val biasharaTtsEngine: BiasharaTtsEngine,
    @ApplicationContext private val context: Context,
) : BaseViewModel() {

    val settings: StateFlow<AppSettings?> = appSettingsDao.getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Serialises all read-modify-write settings updates so rapid concurrent changes don't overwrite each other.
    private val settingsMutex = Mutex()

    private val _preparingWhisper = MutableStateFlow(false)
    val preparingWhisper: StateFlow<Boolean> = _preparingWhisper.asStateFlow()

    sealed interface Event {
        data object WhisperReady : Event
        data class WhisperPrepareFailed(val message: String) : Event
    }

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private fun updateSetting(block: AppSettings.() -> AppSettings) {
        launchSafe {
            withContext(Dispatchers.IO) {
                settingsMutex.withLock {
                    val row = appSettingsDao.getSettingsSync() ?: AppSettings()
                    appSettingsDao.updateSettings(row.block())
                }
            }
        }
    }

    fun setVoiceInputEnabled(enabled: Boolean) {
        voiceInputPreferences.setVoiceInputEnabled(enabled)
        updateSetting { copy(voiceInputEnabled = enabled) }
    }

    fun setWhisperModelId(id: String) {
        updateSetting { copy(whisperModelId = id.trim()) }
        launchSafe { releaseWhisperQuietly() }
    }

    fun setSilenceTimeoutMs(ms: Int) =
        updateSetting { copy(silenceTimeoutMs = ms.coerceIn(1_000, 8_000)) }

    fun setVoiceLanguageMode(mode: String) {
        val normalised = mode.trim().let { raw ->
            if (raw.equals("auto", ignoreCase = true)) "AUTO" else raw.lowercase(Locale.US)
        }
        updateSetting { copy(voiceLanguageMode = normalised) }
    }

    fun setTtsEnabled(enabled: Boolean) = updateSetting { copy(ttsEnabled = enabled) }

    fun setTtsSpeechRate(rate: Double) =
        updateSetting { copy(ttsSpeechRate = rate.coerceIn(0.5, 1.5)) }

    fun setTtsPitch(pitch: Double) =
        updateSetting { copy(ttsPitch = pitch.coerceIn(0.5, 1.5)) }

    fun setTtsAutoReadAgentAlerts(enabled: Boolean) =
        updateSetting { copy(ttsAutoReadAgentAlerts = enabled) }

    fun setTtsAutoReadQueryAnswers(enabled: Boolean) =
        updateSetting { copy(ttsAutoReadQueryAnswers = enabled) }

    fun prepareWhisperModel() {
        if (_preparingWhisper.value) return
        launchSafe {
            _preparingWhisper.value = true
            try {
                if (!hasUsableNetwork()) {
                    _events.emit(
                        Event.WhisperPrepareFailed(
                            context.getString(R.string.voice_settings_whisper_failed_no_network),
                        ),
                    )
                    return@launchSafe
                }
                val transcriber = whisperOrNull()
                if (transcriber == null) {
                    _events.emit(
                        Event.WhisperPrepareFailed(
                            "Native speech model unavailable.",
                        ),
                    )
                    return@launchSafe
                }
                withTimeout(PREPARE_TIMEOUT_MS) {
                    transcriber.initialize()
                }
                _events.emit(Event.WhisperReady)
            } catch (e: CancellationException) {
                throw e
            } catch (e: TimeoutCancellationException) {
                releaseWhisperQuietly()
                _events.emit(
                    Event.WhisperPrepareFailed(
                        context.getString(R.string.voice_settings_whisper_failed_timeout),
                    ),
                )
            } catch (e: OutOfMemoryError) {
                releaseWhisperQuietly()
                _events.emit(
                    Event.WhisperPrepareFailed(
                        context.getString(R.string.voice_settings_whisper_failed_oom),
                    ),
                )
            } catch (t: Throwable) {
                releaseWhisperQuietly()
                _events.emit(
                    Event.WhisperPrepareFailed(
                        whisperFailureMessage(t),
                    ),
                )
            } finally {
                _preparingWhisper.value = false
            }
        }
    }

    private fun hasUsableNetwork(): Boolean {
        val cm = runCatching {
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        }.getOrElse {
            runCatching {
                Log.w(TAG, "Connectivity check unavailable; allowing speech model preparation", it)
            }
            return true
        } ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun playTestUtterance() {
        launchSafe {
            val phrase = context.getString(R.string.voice_settings_tts_test_phrase)
            biasharaTtsEngine.speak(phrase, queueMode = TextToSpeech.QUEUE_FLUSH)
        }
    }

    fun whisperIsReady(): Boolean = whisperOrNull()?.isReady == true

    private fun whisperOrNull(): WhisperTranscriber? =
        runCatching { whisperTranscriber.get() }.getOrElse {
            Log.w(TAG, "WhisperTranscriber creation failed", it)
            null
        }

    private suspend fun releaseWhisperQuietly() {
        val transcriber = whisperOrNull() ?: return
        runCatching {
            transcriber.releaseAsync()
        }.onFailure {
            if (it is CancellationException) throw it
            Log.w(TAG, "Whisper release failed", it)
        }
    }

    private fun whisperFailureMessage(t: Throwable): String {
        val raw = generateSequence(t) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
        return when {
            raw.contains("libQnnTFLiteDelegate.so", ignoreCase = true) ||
                raw.contains("dlopen failed", ignoreCase = true) ->
                "Speech model tried to use an unavailable phone AI delegate. This build now uses CPU mode; try again."
            raw.isNotBlank() -> raw.take(500)
            else -> t.javaClass.simpleName
        }
    }

    private companion object {
        private const val TAG = "VoiceSettingsViewModel"
        private const val PREPARE_TIMEOUT_MS = 900_000L
    }
}
