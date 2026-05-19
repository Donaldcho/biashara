package com.biasharaai.inventory

import com.google.zxing.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryLabelGeneratorTest {

    @Test
    fun productBarcodeNumber_is13DigitsWithValidCheckDigit() {
        val code = InventoryLabelGenerator.generateProductBarcodeNumber()
        assertEquals(13, code.length)
        assertTrue(code.all { it.isDigit() })
        val digits = code.map { it.digitToInt() }
        val sum = digits.dropLast(1).mapIndexed { i, d -> if (i % 2 == 0) d else d * 3 }.sum()
        val check = (10 - sum % 10) % 10
        assertEquals(check, digits.last())
    }

    @Test
    fun barcodeFormatFor_13Digits_usesEan13() {
        // Valid EAN-13 check digit (4006381333931)
        assertEquals(BarcodeFormat.EAN_13, InventoryLabelGenerator.barcodeFormatFor("4006381333931"))
    }

    @Test
    fun barcodeFormatFor_alphanumeric_usesCode128() {
        assertEquals(BarcodeFormat.CODE_128, InventoryLabelGenerator.barcodeFormatFor("BSVOU:abc-123"))
    }

}
