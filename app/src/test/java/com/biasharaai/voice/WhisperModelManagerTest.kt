package com.biasharaai.voice

import com.argmaxinc.whisperkit.ExperimentalWhisperKit
import com.argmaxinc.whisperkit.WhisperKit
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalWhisperKit::class)
class WhisperModelManagerTest {

    private lateinit var manager: WhisperModelManager

    @Before
    fun setUp() {
        val context = mockk<android.content.Context>(relaxed = true)
        every { context.filesDir } returns java.io.File(System.getProperty("java.io.tmpdir"))
        manager = WhisperModelManager(context)
    }

    @Test
    fun resolve_defaultsToOpenAiTiny() {
        assertEquals(WhisperKit.Builder.OPENAI_TINY, manager.resolveWhisperKitModelKey("whisper-tiny"))
    }

    @Test
    fun resolve_baseUsesOpenAiBase() {
        assertEquals(WhisperKit.Builder.OPENAI_BASE, manager.resolveWhisperKitModelKey("whisper-base"))
    }

    @Test
    fun resolve_englishTiny() {
        assertEquals(WhisperKit.Builder.OPENAI_TINY_EN, manager.resolveWhisperKitModelKey("whisper-tiny-en"))
    }
}
