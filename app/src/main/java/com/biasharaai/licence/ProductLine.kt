package com.biasharaai.licence

/**
 * Product SKU encoded in every [LicenceKey].
 * One APK — [ProductLine.SHOP] or [ProductLine.PRO] gates service-layer features.
 */
enum class ProductLine {
    SHOP,
    PRO,
    ;

    companion object {
        fun parse(raw: String?): ProductLine? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}
