package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a financial transaction (income or expense).
 *
 * Each transaction has a [type] indicating whether it is money coming in
 * ([TransactionType.INCOME]) or going out ([TransactionType.EXPENSE]),
 * an [amount], a [description] that doubles as a category label for
 * expense grouping, and a [date] stored as epoch milliseconds.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["date"]),
        Index(value = ["type"])
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** INCOME or EXPENSE. */
    @ColumnInfo(name = "type")
    val type: TransactionType,

    /** Transaction amount in the user's local currency. */
    @ColumnInfo(name = "amount")
    val amount: Double,

    /**
     * Free-text description.
     *
     * For expenses, this also serves as the category label used by
     * [CashFlowAnalyzer] when identifying top expense categories.
     */
    @ColumnInfo(name = "description")
    val description: String,

    /**
     * Transaction date as epoch milliseconds (UTC).
     *
     * Stored as [Long] so Room can index and query by range efficiently.
     */
    @ColumnInfo(name = "date")
    val date: Long,

    // ── POS (Prompt P1) — persisted on `transactions` from migration 5→6 ─────

    @ColumnInfo(name = "payment_method", defaultValue = "CASH")
    val paymentMethod: String = "CASH",

    @ColumnInfo(name = "mobile_money_network")
    val mobileMoneyNetwork: String? = null,

    @ColumnInfo(name = "mobile_money_ref")
    val mobileMoneyRef: String? = null,

    @ColumnInfo(name = "amount_tendered")
    val amountTendered: Double? = null,

    @ColumnInfo(name = "change_due")
    val changeDue: Double? = null,

    @ColumnInfo(name = "receipt_number")
    val receiptNumber: String? = null,

    @ColumnInfo(name = "sale_group_id")
    val saleGroupId: String? = null,

    @ColumnInfo(name = "tax_rate", defaultValue = "0.0")
    val taxRate: Double = 0.0,

    @ColumnInfo(name = "tax_amount", defaultValue = "0.0")
    val taxAmount: Double = 0.0,
)

/** Transaction direction. */
enum class TransactionType {
    INCOME,
    EXPENSE,
}
