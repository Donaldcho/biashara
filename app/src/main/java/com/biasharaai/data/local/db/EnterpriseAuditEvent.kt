package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "enterprise_audit_events",
    indices = [
        Index(value = ["business_id", "created_at"]),
        Index(value = ["device_id"]),
        Index(value = ["action", "created_at"]),
        Index(value = ["entity_type", "entity_id"]),
    ],
)
data class EnterpriseAuditEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "business_id") val businessId: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "actor_staff_id") val actorStaffId: Long? = null,
    @ColumnInfo(name = "actor_role") val actorRole: String? = null,
    val action: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String? = null,
    val summary: String,
    val metadata: String? = null,
    @ColumnInfo(name = "deployment_mode") val deploymentMode: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
