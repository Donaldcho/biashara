package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "agent_run_log",
    indices = [Index(value = ["agent_type", "ran_at"])],
)
data class AgentRunLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "agent_type") val agentType: String,
    @ColumnInfo(name = "ran_at") val ranAt: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "actions_generated") val actionsGenerated: Int = 0,
    val outcome: String,
)
