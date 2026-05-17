package com.biasharaai.data.local.db

/**
 * Aggregated ledger amount per [LedgerEntryType] (reports / agents).
 */
data class LedgerTypeTotal(
    val type: String,
    val total: Double,
)
