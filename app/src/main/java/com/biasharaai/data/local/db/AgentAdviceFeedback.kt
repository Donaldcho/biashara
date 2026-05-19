package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "agent_advice_feedback",
    indices = [
        Index(value = ["agent_action_id"], unique = true),
        Index(value = ["content_hash", "created_at"]),
        Index(value = ["vote", "created_at"]),
    ],
)
data class AgentAdviceFeedback(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "agent_action_id") val agentActionId: Long,
    @ColumnInfo(name = "agent_type") val agentType: String,
    @ColumnInfo(name = "content_hash") val contentHash: String,
    val headline: String,
    val detail: String = "",
    val vote: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
