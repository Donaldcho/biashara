package com.biasharaai.voice

import android.speech.tts.TextToSpeech
import java.util.Locale

object TtsLanguageMapper {

    fun toLocale(languageCode: String): Locale =
        when (languageCode.lowercase()) {
            "sw" -> Locale("sw", "KE")
            "ha" -> Locale("ha")
            "yo" -> Locale("yo")
            "am" -> Locale("am", "ET")
            "ig" -> Locale("ig")
            "om" -> Locale("om")
            else -> Locale.ENGLISH
        }

    fun isAvailable(tts: TextToSpeech, languageCode: String): Boolean {
        val locale = toLocale(languageCode)
        val result = tts.isLanguageAvailable(locale)
        return result == TextToSpeech.LANG_AVAILABLE ||
            result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
    }
}
