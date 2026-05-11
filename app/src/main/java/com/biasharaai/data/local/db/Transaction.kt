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
)

/** Transaction direction. */
enum class TransactionType {
    INCOME,
    EXPENSE,
}
