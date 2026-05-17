package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentRunLogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLog(log: AgentRunLog): Long

    @Query(
        """
        SELECT * FROM agent_run_log
        WHERE agent_type = :agentType
        ORDER BY ran_at DESC
        LIMIT 1
        """,
    )
    suspend fun getLastRunForAgent(agentType: String): AgentRunLog?

    @Query(
        """
        SELECT * FROM agent_run_log
        WHERE agent_type = :agentType
        ORDER BY ran_at DESC
        LIMIT :limit
        """,
    )
    suspend fun getRunHistoryForAgent(agentType: String, limit: Int): List<AgentRunLog>

    @Query("SELECT * FROM agent_run_log ORDER BY ran_at DESC LIMIT :limit")
    fun getRecentLogs(limit: Int): Flow<List<AgentRunLog>>
}
