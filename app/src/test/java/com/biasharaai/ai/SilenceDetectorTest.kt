package com.biasharaai.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilenceDetectorTest {

    @Test
    fun quietFrames_beforeAnySpeech_neverEnd() {
        val d = SilenceDetector(silenceTimeoutMs = 80)
        d.reset()
        repeat(20) {
            assertFalse(d.process(shortArrayOf(0, 0, 0, 0)))
        }
    }

    @Test
    fun silence_afterSpeech_exceedsTimeout_ends() {
        val d = SilenceDetector(silenceTimeoutMs = 120)
        d.reset()
        assertFalse(
            d.process(
                ShortArray(400) { 8000 },
            ),
        )
        Thread.sleep(60)
        assertFalse(d.process(shortArrayOf(0, 0)))
        Thread.sleep(200)
        assertTrue(d.process(shortArrayOf(0, 0)))
    }

    @Test
    fun reset_clearsSpeechLatch() {
        val d = SilenceDetector(silenceTimeoutMs = 50)
        d.reset()
        assertFalse(d.process(ShortArray(200) { 7000 }))
        Thread.sleep(80)
        assertTrue(d.process(shortArrayOf(0, 0)))
        d.reset()
        assertFalse(d.process(shortArrayOf(0, 0)))
    }
}
