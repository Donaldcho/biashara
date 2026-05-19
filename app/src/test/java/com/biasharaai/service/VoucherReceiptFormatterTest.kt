package com.biasharaai.service

import com.biasharaai.data.local.db.ServiceItem
import com.biasharaai.data.local.db.ServiceVoucher
import org.junit.Assert.assertTrue
import org.junit.Test

class VoucherReceiptFormatterTest {

    @Test
    fun format_producesNonEmptyEscPosWithToken() {
        val voucher = ServiceVoucher(
            voucherId = "v-1",
            serviceItemId = 1L,
            totalUses = 5,
            remainingUses = 5,
            amountPaid = 4000.0,
            expiresAt = System.currentTimeMillis() + 90 * 86_400_000L,
        )
        val service = ServiceItem(id = 1L, name = "Braids", basePrice = 800.0, catalogueToken = "BSVC:1")
        val token = ServiceTokenCodec.voucherToken("v-1")
        val bytes = VoucherReceiptFormatter.format(voucher, service, "Test Shop", token)
        val text = String(bytes)
        assertTrue(bytes.isNotEmpty())
        assertTrue(text.contains("Test Shop"))
        assertTrue(text.contains("Braids"))
        assertTrue(text.contains(token))
    }
}
