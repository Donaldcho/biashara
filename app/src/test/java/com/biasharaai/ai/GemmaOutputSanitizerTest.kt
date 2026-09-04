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

    @Test
    fun finalAnswer_formatsSpacingBetweenLettersAndNumbers() {
        // Currency prefixes
        assertEquals("FCFA 45,000", GemmaOutputSanitizer.finalAnswer("FCFA45,000"))
        assertEquals("KES 10,000", GemmaOutputSanitizer.finalAnswer("KES10,000"))
        assertEquals("XAF 10,000", GemmaOutputSanitizer.finalAnswer("XAF10,000"))
        assertEquals("KSh 500", GemmaOutputSanitizer.finalAnswer("KSh500"))

        // Currency symbols
        assertEquals("$ 100", GemmaOutputSanitizer.finalAnswer("$100"))
        assertEquals("₦ 500", GemmaOutputSanitizer.finalAnswer("₦500"))

        // Suffixes and units
        assertEquals("10,000 FCFA", GemmaOutputSanitizer.finalAnswer("10,000FCFA"))
        assertEquals("100 units", GemmaOutputSanitizer.finalAnswer("100units"))
        assertEquals("10 kg", GemmaOutputSanitizer.finalAnswer("10kg"))
        assertEquals("500 g", GemmaOutputSanitizer.finalAnswer("500g"))

        // Ordinals should remain unchanged
        assertEquals("1st", GemmaOutputSanitizer.finalAnswer("1st"))
        assertEquals("2nd", GemmaOutputSanitizer.finalAnswer("2nd"))
        assertEquals("3rd", GemmaOutputSanitizer.finalAnswer("3rd"))
        assertEquals("4th", GemmaOutputSanitizer.finalAnswer("4th"))

        // Versioning and short prefixes should remain unchanged (letters length < 2)
        assertEquals("v2", GemmaOutputSanitizer.finalAnswer("v2"))
        assertEquals("v16", GemmaOutputSanitizer.finalAnswer("v16"))
    }
}
