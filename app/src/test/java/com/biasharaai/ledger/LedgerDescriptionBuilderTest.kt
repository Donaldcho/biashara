package com.biasharaai.ledger

import com.biasharaai.data.local.db.SaleLineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerDescriptionBuilderTest {

    @Test
    fun productSale_includesItemsAndReceipt() {
        val desc = LedgerDescriptionBuilder.productSale(
            listOf(
                SaleLineItem(
                    transactionId = 1,
                    productId = 1,
                    productName = "Rice",
                    unitPrice = 100.0,
                    quantity = 2,
                    lineTotal = 200.0,
                ),
            ),
            receiptNum = "RCP-1",
        )
        assertTrue(desc.contains("Rice x2"))
        assertTrue(desc.contains("RCP-1"))
    }

    @Test
    fun mixedSale_formatsCounts() {
        val desc = LedgerDescriptionBuilder.mixedSale(3, 2, "RCP-2")
        assertEquals("Sale: 3 products, 2 services [RCP-2]", desc)
    }
}
