package com.biasharaai.ui.settings

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.lifecycle.viewModelScope
import com.biasharaai.R
import com.biasharaai.ai.VoiceInputPreferences
import com.biasharaai.data.local.db.AppSettings
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.ui.base.BaseViewModel
import com.biasharaai.voice.BiasharaTtsEngine
import com.biasharaai.voice.WhisperTranscriber
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val appSettingsDao: AppSettingsDao,
    private val voiceInputPreferences: VoiceInputPreferences,
    private val whisperTranscriber: WhisperTranscriber,
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
        viewModelScope.launch(Dispatchers.IO) {
            settingsMutex.withLock {
                val row = appSettingsDao.getSettingsSync() ?: AppSettings()
                appSettingsDao.updateSettings(row.block())
            }
        }
    }

    fun setVoiceInputEnabled(enabled: Boolean) {
        voiceInputPreferences.setVoiceInputEnabled(enabled)
        updateSetting { copy(voiceInputEnabled = enabled) }
    }

    fun setWhisperModelId(id: String) {
        updateSetting { copy(whisperModelId = id.trim()) }
        viewModelScope.launch(Dispatchers.IO) { whisperTranscriber.release() }
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
        viewModelScope.launch {
            _preparingWhisper.value = true
            try {
                withContext(Dispatchers.IO) {
                    whisperTranscriber.initialize()
                }
                _events.emit(Event.WhisperReady)
            } catch (e: Exception) {
                _events.emit(Event.WhisperPrepareFailed(e.message ?: e.toString()))
            } finally {
                _preparingWhisper.value = false
            }
        }
    }

    fun playTestUtterance() {
        viewModelScope.launch {
            val phrase = context.getString(R.string.voice_settings_tts_test_phrase)
            biasharaTtsEngine.speak(phrase, queueMode = TextToSpeech.QUEUE_FLUSH)
        }
    }

    fun whisperIsReady(): Boolean = whisperTranscriber.isReady
}
