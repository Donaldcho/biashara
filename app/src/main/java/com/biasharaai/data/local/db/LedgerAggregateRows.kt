package com.biasharaai.data.local.db

/**
 * Lightweight projection rows for ledger aggregate queries.
 *
 * These keep agent-facing analytics from hydrating full [LedgerEntry] rows when
 * only timestamp, direction, and amount are needed.
 */
data class LedgerDirectionTotal(
    val direction: String,
    val total: Double,
)

data class LedgerAmountPoint(
    val occurredAt: Long,
    val direction: String,
    val amount: Double,
)

data class LedgerPendingCreditSummary(
    val amount: Double,
    val count: Int,
)
