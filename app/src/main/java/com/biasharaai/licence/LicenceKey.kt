package com.biasharaai.licence

/**
 * Parsed licence payload (signature verified by [LicenceValidator] before storage).
 */
data class LicenceKey(
    val businessId: String,
    val productLine: ProductLine,
    val edition: Edition,
    val maxDevices: Int,
    val issuedAt: Long,
    val expiresAt: Long?,
)
