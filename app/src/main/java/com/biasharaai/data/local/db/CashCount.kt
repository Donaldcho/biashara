package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * End-of-day physical cash reconciliation (Phase 9 — Prompt L5).
 */
@Entity(
    tableName = "cash_counts",
    indices = [Index(value = ["counted_at"], name = "idx_cash_counts_counted_at")],
)
data class CashCount(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "counted_at")
    val countedAt: Long,

    @ColumnInfo(name = "expected_balance")
    val expectedBalance: Double,

    @ColumnInfo(name = "actual_balance")
    val actualBalance: Double,

    @ColumnInfo(name = "difference")
    val difference: Double,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "device_id")
    val deviceId: String,
)
