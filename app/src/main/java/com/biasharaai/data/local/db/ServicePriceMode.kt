package com.biasharaai.data.local.db

enum class ServicePriceMode {
    FIXED,
    NEGOTIABLE,
    FROM,
    ;

    companion object {
        fun parse(raw: String?): ServicePriceMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: FIXED
    }
}
