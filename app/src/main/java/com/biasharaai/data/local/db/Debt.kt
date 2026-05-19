package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Maps to the `debts` table created in migration 3→5 (Phase 2 baseline).
 */
@Entity(
    tableName = "debts",
    indices = [Index(value = ["customer_id"])],
    foreignKeys = [
        ForeignKey(
            entity = Customer::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class Debt(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "customer_id")
    val customerId: Long,
    val amount: Double,
    val description: String,
    @ColumnInfo(name = "due_date")
    val dueDate: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,

    @ColumnInfo(name = "source_transaction_id")
    val sourceTransactionId: Long? = null,
)
