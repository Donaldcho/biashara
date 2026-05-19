package com.biasharaai.licence

/** Deployment tier — independent of [ProductLine]. */
enum class Edition {
    PRIVATE,
    SME,
    ENTERPRISE,
    ;

    companion object {
        fun parse(raw: String?): Edition? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}
