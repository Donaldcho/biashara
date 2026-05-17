package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_notifications",
    indices = [Index(value = ["fire_at"])],
)
data class PendingNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val body: String,
    val urgency: String,
    @ColumnInfo(name = "fire_at") val fireAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
