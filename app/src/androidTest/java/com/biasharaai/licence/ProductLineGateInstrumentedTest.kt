package com.biasharaai.licence

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.biasharaai.data.local.db.LedgerEntryType
import com.biasharaai.productline.ProductLineManager
import com.biasharaai.ui.scanner.BarcodeScanRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductLineGateInstrumentedTest {

    private lateinit var validator: LicenceValidator
    private lateinit var productLineManager: ProductLineManager

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        validator = LicenceValidator(context)
        productLineManager = ProductLineManager(validator, EditionManager(validator))
        validator.clearLicenceKey()
    }

    @Test
    fun shopLicence_disablesProAndServiceTokens() {
        validator.storeLicenceKey(LicenceValidator.DEV_SHOP_PRIVATE).getOrThrow()
        assertEquals(ProductLine.SHOP, productLineManager.productLine())
        assertFalse(productLineManager.isProEnabled())
        assertFalse(
            BarcodeScanRouter.shouldHandleServiceToken("BSVC:test-service", productLineManager),
        )
    }

    @Test
    fun proLicence_enablesProAndServiceTokenRouting() {
        validator.storeLicenceKey(LicenceValidator.DEV_PRO_ENTERPRISE).getOrThrow()
        assertTrue(productLineManager.isProEnabled())
        assertTrue(
            BarcodeScanRouter.shouldHandleServiceToken("BSVOU:voucher-1", productLineManager),
        )
    }

    @Test
    fun upgradeToPro_isInstantWithoutMigration() {
        validator.storeLicenceKey(LicenceValidator.DEV_SHOP_PRIVATE).getOrThrow()
        assertFalse(productLineManager.isProEnabled())

        validator.storeLicenceKey(LicenceValidator.DEV_PRO_ENTERPRISE).getOrThrow()
        assertTrue(productLineManager.isProEnabled())
    }

    @Test
    fun shopInstallation_serviceLedgerTypesAreGated() {
        validator.storeLicenceKey(LicenceValidator.DEV_SHOP_PRIVATE).getOrThrow()
        val proOnlyTypes = listOf(
            LedgerEntryType.SALE_SERVICE,
            LedgerEntryType.VOUCHER_SALE,
            LedgerEntryType.VOUCHER_REDEEMED,
            LedgerEntryType.WARRANTY_CLAIM,
        )
        assertEquals(4, proOnlyTypes.size)
        assertFalse(productLineManager.isProEnabled())
    }
}
