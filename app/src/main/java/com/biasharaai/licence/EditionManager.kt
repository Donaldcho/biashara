package com.biasharaai.licence

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditionManager @Inject constructor(
    private val licenceValidator: LicenceValidator,
) {
    fun edition(): Edition = licenceValidator.getStoredKey()?.edition ?: Edition.PRIVATE

    fun isSmePlus(): Boolean = edition() == Edition.SME || edition() == Edition.ENTERPRISE

    fun isEnterprise(): Boolean = edition() == Edition.ENTERPRISE

    fun maxDevices(): Int = licenceValidator.getStoredKey()?.maxDevices ?: 1
}
