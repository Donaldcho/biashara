package com.biasharaai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaOutputSanitizerTest {

    @Test
    fun finalAnswer_stripsThoughtChannel() {
        val raw = "<|channel|>thought hidden reasoning<|end|>Sales today are FCFA 12,000."
        assertEquals("Sales today are FCFA 12,000.", GemmaOutputSanitizer.finalAnswer(raw))
    }

    @Test
    fun finalAnswer_stripsTemplateTokens() {
        val raw = "<start_of_turn>model\nHello shop owner."
        assertEquals("Hello shop owner.", GemmaOutputSanitizer.finalAnswer(raw))
    }

    @Test
    fun finalAnswer_trimsFakeFollowUpTurn() {
        val raw = "Your stock is low.\n\nuser: what about rice?"
        assertEquals("Your stock is low.", GemmaOutputSanitizer.finalAnswer(raw))
    }

    @Test
    fun finalAnswer_collapsesNumberLoop() {
        val raw = "29. 29. 29. 29. 29."
        val out = GemmaOutputSanitizer.finalAnswer(raw)
        assertTrue(out.startsWith("29."))
        assertFalse(out.contains("29. 29. 29"))
    }

    @Test
    fun streamingPreview_keepsPartialAnswerWhileStrippingTokens() {
        val raw = "<|end|>Revenue this week is"
        assertEquals("Revenue this week is", GemmaOutputSanitizer.streamingPreview(raw))
    }
}
