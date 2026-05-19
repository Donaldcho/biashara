package com.biasharaai.pos.payment

import org.junit.Assert.assertEquals
import org.junit.Test

class MixedSaleAllocatorTest {

    @Test
    fun creditServices_paysProductsOnly() {
        val breakdown = MixedSaleAllocator.Breakdown(
            productSubtotal = 100.0,
            serviceSubtotal = 50.0,
            voucherSubtotal = 0.0,
            taxAmount = 15.0,
            grandTotal = 165.0,
        )
        val split = MixedSaleAllocator.paymentSplit(breakdown, MixedPaymentPlan.CREDIT_SERVICES, null)
        assertEquals(110.0, split.paidNow, 0.01)
        assertEquals(55.0, split.balanceDue, 0.01)
    }

    @Test
    fun deposit_leavesBalance() {
        val breakdown = MixedSaleAllocator.Breakdown(
            productSubtotal = 80.0,
            serviceSubtotal = 20.0,
            voucherSubtotal = 0.0,
            taxAmount = 10.0,
            grandTotal = 110.0,
        )
        val split = MixedSaleAllocator.paymentSplit(breakdown, MixedPaymentPlan.DEPOSIT, 40.0)
        assertEquals(40.0, split.paidNow, 0.01)
        assertEquals(70.0, split.balanceDue, 0.01)
    }
}
