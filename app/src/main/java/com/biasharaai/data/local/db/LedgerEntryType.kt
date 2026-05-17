package com.biasharaai.data.local.db

/**
 * Kind of financial event recorded in the unified business ledger (Phase 9).
 */
enum class LedgerEntryType {
    // Money in
    SALE_PRODUCT,
    SALE_SERVICE,
    SALE_MIXED,
    VOUCHER_SALE,
    DEBT_REPAID,
    OTHER_INCOME,

    // Money out
    EXPENSE,
    STOCK_PURCHASE,
    REFUND,
    WARRANTY_CLAIM,

    // Neutral (no balance change, or special handling)
    VOUCHER_REDEEMED,
    CREDIT_EXTENDED,
    CASH_COUNT,
    ADJUSTMENT,
    OPENING_BALANCE,
}
