package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "enterprise_sync_outbox",
    indices = [
        Index(value = ["status", "next_attempt_at"]),
        Index(value = ["business_id", "created_at"]),
        Index(value = ["payload_type", "payload_entity_id"]),
    ],
)
data class EnterpriseSyncOutboxItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "business_id") val businessId: String,
    @ColumnInfo(name = "branch_id") val branchId: Long? = null,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "destination_mode") val destinationMode: String,
    @ColumnInfo(name = "payload_type") val payloadType: String,
    @ColumnInfo(name = "payload_entity_id") val payloadEntityId: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    val status: String = STATUS_PENDING,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SENT = "SENT"
        const val STATUS_FAILED = "FAILED"
    }
}
