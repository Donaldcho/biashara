package com.biasharaai.pos.cart

import com.biasharaai.data.local.db.ServiceItem
import org.junit.Assert.assertEquals
import org.junit.Test

class VoucherCartItemTest {

    @Test
    fun totalAmount_usesTimesPrice() {
        val service = ServiceItem(id = 1L, name = "Braids", basePrice = 800.0, catalogueToken = "BSVC:1")
        val item = VoucherCartItem(service, uses = 5, pricePerUse = 800.0)
        assertEquals(4000.0, item.totalAmount, 0.001)
    }
}
