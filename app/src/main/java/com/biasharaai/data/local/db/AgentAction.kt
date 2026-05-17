package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "agent_actions",
    indices = [
        Index(value = ["status", "created_at"]),
        Index(value = ["agent_type"]),
    ],
)
data class AgentAction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "agent_type") val agentType: String,
    val urgency: String,
    @ColumnInfo(name = "execution_type") val executionType: String = "REQUIRES_APPROVAL",
    val headline: String,
    val detail: String = "",
    @ColumnInfo(name = "action_payload") val actionPayload: String? = null,
    @ColumnInfo(name = "action_verb") val actionVerb: String? = null,
    val status: String = "PENDING",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long? = null,
    @ColumnInfo(name = "related_entity_id") val relatedEntityId: Long? = null,
    @ColumnInfo(name = "related_entity_type") val relatedEntityType: String? = null,
)
