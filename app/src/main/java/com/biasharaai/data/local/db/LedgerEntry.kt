package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Immutable append-only row in the unified business ledger (Phase 9).
 *
 * Corrections use a new [LedgerEntryType.ADJUSTMENT] row — existing rows are never updated
 * except [runningBalance] during sync recompute ([LedgerEntryDao.updateBalance]).
 */
@Entity(
    tableName = "ledger_entries",
    indices = [
        Index(value = ["occurred_at"]),
        Index(value = ["direction"]),
        Index(value = ["type"]),
        Index(value = ["customer_id"]),
        Index(value = ["transaction_id"]),
        Index(value = ["direction", "occurred_at"], name = "idx_ledger_direction_occurred_at"),
        Index(value = ["type", "occurred_at"], name = "idx_ledger_type_occurred_at"),
        Index(value = ["is_synced"], name = "idx_ledger_is_synced"),
        Index(value = ["sync_id"], name = "idx_ledger_sync_id"),
    ],
)
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,

    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "type")
    val type: LedgerEntryType,

    @ColumnInfo(name = "direction")
    val direction: LedgerDirection,

    /** Always positive; [direction] determines sign applied to [runningBalance]. */
    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "currency")
    val currency: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "running_balance")
    val runningBalance: Double,

    @ColumnInfo(name = "transaction_id")
    val transactionId: Long? = null,

    @ColumnInfo(name = "service_delivery_id")
    val serviceDeliveryId: Long? = null,

    @ColumnInfo(name = "voucher_id")
    val voucherId: String? = null,

    @ColumnInfo(name = "debt_id")
    val debtId: Long? = null,

    @ColumnInfo(name = "customer_id")
    val customerId: Long? = null,

    @ColumnInfo(name = "product_id")
    val productId: Long? = null,

    @ColumnInfo(name = "service_item_id")
    val serviceItemId: Long? = null,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "staff_name")
    val staffName: String? = null,

    @ColumnInfo(name = "sync_id")
    val syncId: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,
)
