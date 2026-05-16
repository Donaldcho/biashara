package com.biasharaai.skills.builtin

import com.biasharaai.receipt.ReceiptLineItem
import com.biasharaai.receipt.ReceiptParser
import com.biasharaai.skills.SkillResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExtractReceiptDataSkillTest {

    private lateinit var receiptParser: ReceiptParser
    private lateinit var skill: ExtractReceiptDataSkill

    @Before
    fun setUp() {
        receiptParser = mockk()
        skill = ExtractReceiptDataSkill(receiptParser)
    }

    @Test
    fun execute_fromOcrText_success() = runTest {
        coEvery { receiptParser.parseFromOcrText(any()) } returns ReceiptParser.ParseResult.Success(
            listOf(ReceiptLineItem(name = "Flour", quantity = 1.0, cost = 80.0)),
        )

        val result = skill.execute("""{"ocrText":"FLOUR 80"}""")

        assertTrue(result is SkillResult.Success)
    }

    @Test
    fun execute_missingInput_returnsInvalidArgs() = runTest {
        val result = skill.execute("{}")
        assertTrue(result is SkillResult.Failure)
        assertEquals("INVALID_ARGS", (result as SkillResult.Failure).code)
    }
}
