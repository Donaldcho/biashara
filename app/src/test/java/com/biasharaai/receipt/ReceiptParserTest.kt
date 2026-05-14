package com.biasharaai.receipt

import com.biasharaai.ai.GemmaService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {

    private val gemma: GemmaService = mockk()

    @Test
    fun parseFromOcrText_validGemmaJson_returnsSuccessItems() = runBlocking {
        every { gemma.isAvailable } returns true
        coEvery { gemma.generateResponse(any()) } returns """
            Here is the data:
            ```json
            [{"name":"Sugar 1kg","quantity":2.0,"cost":150.5}]
            ```
        """.trimIndent()

        val parser = ReceiptParser(gemma)
        val result = parser.parseFromOcrText("SUPERMARKET\nSugar 1kg x2")

        assertTrue(result is ReceiptParser.ParseResult.Success)
        val items = (result as ReceiptParser.ParseResult.Success).items
        assertEquals(1, items.size)
        assertEquals("Sugar 1kg", items[0].name)
        assertEquals(2.0, items[0].quantity!!, 0.001)
        assertEquals(150.5, items[0].cost!!, 0.001)
    }

    @Test
    fun parseFromOcrText_invalidJson_returnsManualFallback() = runBlocking {
        every { gemma.isAvailable } returns true
        coEvery { gemma.generateResponse(any()) } returns "NOT_JSON"

        val parser = ReceiptParser(gemma)
        val result = parser.parseFromOcrText("some receipt text")

        assertTrue(result is ReceiptParser.ParseResult.ManualFallback)
    }

    @Test
    fun parseFromOcrText_blankOcr_returnsManualFallback() = runBlocking {
        val parser = ReceiptParser(gemma)
        val result = parser.parseFromOcrText("   ")
        assertTrue(result is ReceiptParser.ParseResult.ManualFallback)
    }

    @Test
    fun parseFromOcrText_gemmaUnavailable_returnsManualFallback() = runBlocking {
        every { gemma.isAvailable } returns false
        val parser = ReceiptParser(gemma)
        val result = parser.parseFromOcrText("line item text")
        assertTrue(result is ReceiptParser.ParseResult.ManualFallback)
    }
}
