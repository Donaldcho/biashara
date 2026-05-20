package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "enterprise_branches",
    indices = [
        Index(value = ["business_id", "code"], unique = true),
        Index(value = ["business_id", "is_default"]),
        Index(value = ["is_active"]),
    ],
)
data class EnterpriseBranch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "business_id") val businessId: String,
    val code: String,
    val name: String,
    val location: String? = null,
    @ColumnInfo(name = "is_default", defaultValue = "0") val isDefault: Boolean = false,
    @ColumnInfo(name = "is_active", defaultValue = "1") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)
