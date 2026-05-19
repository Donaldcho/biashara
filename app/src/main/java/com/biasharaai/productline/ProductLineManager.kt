package com.biasharaai.productline

import com.biasharaai.licence.EditionManager
import com.biasharaai.licence.LicenceValidator
import com.biasharaai.licence.ProductLine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gates Pro-only features (services, vouchers, warranty) from [LicenceKey.productLine].
 * Default install is **Shop** until a Pro licence is applied in Settings.
 */
@Singleton
class ProductLineManager @Inject constructor(
    private val licenceValidator: LicenceValidator,
    private val editionManager: EditionManager,
) {
    fun productLine(): ProductLine =
        licenceValidator.getStoredKey()?.productLine ?: ProductLine.SHOP

    fun isProEnabled(): Boolean = productLine() == ProductLine.PRO

    fun isSmePlusAndPro(): Boolean = isProEnabled() && editionManager.isSmePlus()

    /** Instrumented / unit tests only. */
    fun applyLicenceKeyForTests(keyString: String) {
        licenceValidator.storeLicenceKey(keyString).getOrThrow()
    }
}
