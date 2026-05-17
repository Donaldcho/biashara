package com.biasharaai.ui.cash

import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.LedgerEntryType

data class BiasharaQrPayload(
    val direction: LedgerDirection,
    val entryType: LedgerEntryType,
    val label: String,
) {
    fun encode(): String = "BIASHARA:${direction.name}:${entryType.name}:$label"

    companion object {
        private const val PREFIX = "BIASHARA:"

        fun decode(raw: String): BiasharaQrPayload? {
            if (!raw.startsWith(PREFIX)) return null
            val parts = raw.removePrefix(PREFIX).split(":", limit = 3)
            if (parts.size < 3) return null
            val direction = runCatching { LedgerDirection.valueOf(parts[0]) }.getOrNull() ?: return null
            val type = runCatching { LedgerEntryType.valueOf(parts[1]) }.getOrNull() ?: return null
            return BiasharaQrPayload(direction = direction, entryType = type, label = parts[2])
        }

        val DEFAULT_CARDS: List<BiasharaQrPayload> = listOf(
            // Money In
            BiasharaQrPayload(LedgerDirection.MONEY_IN, LedgerEntryType.SALE_PRODUCT, "Sale – Product"),
            BiasharaQrPayload(LedgerDirection.MONEY_IN, LedgerEntryType.SALE_SERVICE, "Sale – Service"),
            BiasharaQrPayload(LedgerDirection.MONEY_IN, LedgerEntryType.DEBT_REPAID, "Debt Repaid"),
            BiasharaQrPayload(LedgerDirection.MONEY_IN, LedgerEntryType.OTHER_INCOME, "Other Income"),
            BiasharaQrPayload(LedgerDirection.MONEY_IN, LedgerEntryType.OTHER_INCOME, "Loan Received"),
            BiasharaQrPayload(LedgerDirection.MONEY_IN, LedgerEntryType.OTHER_INCOME, "Owner Injection"),
            // Money Out
            BiasharaQrPayload(LedgerDirection.MONEY_OUT, LedgerEntryType.EXPENSE, "Expense"),
            BiasharaQrPayload(LedgerDirection.MONEY_OUT, LedgerEntryType.STOCK_PURCHASE, "Stock Purchase"),
            BiasharaQrPayload(LedgerDirection.MONEY_OUT, LedgerEntryType.REFUND, "Refund"),
            BiasharaQrPayload(LedgerDirection.MONEY_OUT, LedgerEntryType.EXPENSE, "Salary"),
            BiasharaQrPayload(LedgerDirection.MONEY_OUT, LedgerEntryType.EXPENSE, "Utility"),
            BiasharaQrPayload(LedgerDirection.MONEY_OUT, LedgerEntryType.EXPENSE, "Transport"),
            BiasharaQrPayload(LedgerDirection.MONEY_OUT, LedgerEntryType.EXPENSE, "Rent"),
            BiasharaQrPayload(LedgerDirection.MONEY_OUT, LedgerEntryType.EXPENSE, "Owner Draw"),
        )
    }
}
