package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "enterprise_registered_devices",
    indices = [
        Index(value = ["device_id"], unique = true),
        Index(value = ["business_id"]),
        Index(value = ["last_seen_at"]),
    ],
)
data class EnterpriseRegisteredDevice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "business_id") val businessId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "deployment_mode") val deploymentMode: String,
    @ColumnInfo(name = "app_version_name") val appVersionName: String,
    @ColumnInfo(name = "max_devices_snapshot") val maxDevicesSnapshot: Int,
    @ColumnInfo(name = "first_seen_at") val firstSeenAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_active", defaultValue = "1") val isActive: Boolean = true,
)
