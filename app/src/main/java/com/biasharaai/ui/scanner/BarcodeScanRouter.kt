package com.biasharaai.ui.scanner

import com.biasharaai.productline.ProductLineManager

/**
 * Routes Pro service QR prefixes (BSVC / BSRC / BSVOU) vs standard product barcodes.
 * Shop installs always fall through to product lookup.
 */
object BarcodeScanRouter {
    private val SERVICE_PREFIXES = listOf("BSVC:", "BSRC:", "BSVOU:")

    fun isServiceToken(rawValue: String): Boolean =
        SERVICE_PREFIXES.any { rawValue.startsWith(it, ignoreCase = false) }

    fun shouldHandleServiceToken(rawValue: String, productLineManager: ProductLineManager): Boolean =
        productLineManager.isProEnabled() && isServiceToken(rawValue)
}
