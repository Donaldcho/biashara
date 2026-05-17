package com.biasharaai.data.local.db

/**
 * How a [LedgerEntry] affects the running balance (owner-facing: Money In / Out / no change).
 */
enum class LedgerDirection {
    MONEY_IN,
    MONEY_OUT,
    NEUTRAL,
}
