package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Owner notes captured from chat ("remember that …") — injected into Gemma and structured answers. */
@Entity(tableName = "ai_business_memory")
data class AiBusinessMemory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val text: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
