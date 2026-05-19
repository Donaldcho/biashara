package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "staff_members")
data class StaffMember(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val phone: String? = null,
    @ColumnInfo(defaultValue = "STAFF") val role: String = ROLE_STAFF,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val ROLE_OWNER = "OWNER"
        const val ROLE_MANAGER = "MANAGER"
        const val ROLE_STAFF = "STAFF"
    }
}
