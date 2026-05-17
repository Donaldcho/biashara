package com.biasharaai.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User-controlled opt-in for microphone / speech-to-text features (chat mic, product name dictation).
 *
 * Default is **off** until the user enables voice input in Settings.
 */
@Singleton
class VoiceInputPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    companion object {
        private const val PREFS = "biashara_voice_input_prefs"
        private const val KEY_ENABLED = "voice_input_enabled"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _voiceInputEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val voiceInputEnabled: StateFlow<Boolean> = _voiceInputEnabled.asStateFlow()

    fun isVoiceInputEnabled(): Boolean = _voiceInputEnabled.value

    fun setVoiceInputEnabled(enabled: Boolean) {
        if (_voiceInputEnabled.value == enabled) return
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _voiceInputEnabled.value = enabled
    }
}
