package com.biasharaai.voice

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TtsLanguageMapperTest {

    @Test
    fun toLocale_swahili_kenya() {
        assertEquals(Locale("sw", "KE"), TtsLanguageMapper.toLocale("sw"))
    }

    @Test
    fun toLocale_amharic_ethiopia() {
        assertEquals(Locale("am", "ET"), TtsLanguageMapper.toLocale("am"))
    }

    @Test
    fun toLocale_unknown_defaultsEnglish() {
        assertEquals(Locale.ENGLISH, TtsLanguageMapper.toLocale("xx"))
    }
}
