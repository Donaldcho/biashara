package com.biasharaai.ui.scanner

import com.biasharaai.productline.ProductLineManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeScanRouterTest {

    @Test
    fun serviceToken_onShop_isNotHandled() {
        val manager = mockk<ProductLineManager>()
        every { manager.isProEnabled() } returns false
        assertFalse(BarcodeScanRouter.shouldHandleServiceToken("BSVC:svc-1", manager))
    }

    @Test
    fun serviceToken_onPro_isHandled() {
        val manager = mockk<ProductLineManager>()
        every { manager.isProEnabled() } returns true
        assertTrue(BarcodeScanRouter.shouldHandleServiceToken("BSVOU:v1", manager))
    }

    @Test
    fun productBarcode_neverRoutedAsService() {
        val manager = mockk<ProductLineManager>()
        every { manager.isProEnabled() } returns true
        assertFalse(BarcodeScanRouter.shouldHandleServiceToken("5901234123457", manager))
    }
}
