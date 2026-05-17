package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentActionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAction(action: AgentAction): Long

    @Query(
        """
        SELECT COUNT(*) FROM agent_actions
        WHERE agent_type = :agentType AND related_entity_id = :relatedEntityId AND status = 'PENDING'
        """,
    )
    suspend fun countPendingForAgentAndEntity(agentType: String, relatedEntityId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM agent_actions
        WHERE agent_type = :agentType AND status = 'PENDING' AND headline = :headline
        """,
    )
    suspend fun countPendingWithExactHeadline(agentType: String, headline: String): Int

    @Query("UPDATE agent_actions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE agent_actions SET expires_at = :expiresAtMillis WHERE id = :id")
    suspend fun updateExpiresAt(id: Long, expiresAtMillis: Long)

    @Query("SELECT * FROM agent_actions WHERE status = 'PENDING' ORDER BY created_at DESC")
    fun getPendingActions(): Flow<List<AgentAction>>

    /**
     * Pending actions visible in the owner feed: not snoozed (no future [AgentAction.expiresAt]).
     * Uses SQLite wall time so snoozed rows reappear when the window passes without an app restart.
     */
    @Query(
        """
        SELECT * FROM agent_actions
        WHERE status = 'PENDING'
          AND (expires_at IS NULL OR expires_at <= (strftime('%s','now') * 1000))
        ORDER BY created_at DESC
        """,
    )
    fun observePendingActionsForFeed(): Flow<List<AgentAction>>

    @Query("SELECT * FROM agent_actions WHERE agent_type = :agentType ORDER BY created_at DESC")
    fun getActionsByAgent(agentType: String): Flow<List<AgentAction>>

    /**
     * [AgentAction.status] is `PENDING`, or `EXECUTED` with [AgentAction.createdAt] within the last 7 days
     * (wall-clock, approximate via SQLite `strftime`).
     */
    @Query(
        """
        SELECT * FROM agent_actions
        WHERE status = 'PENDING'
           OR (status = 'EXECUTED' AND created_at >= (strftime('%s','now') * 1000 - 604800000))
        ORDER BY created_at DESC
        """,
    )
    fun getActiveActions(): Flow<List<AgentAction>>

    @Query(
        """
        UPDATE agent_actions SET status = 'EXPIRED'
        WHERE status = 'PENDING' AND expires_at IS NOT NULL AND expires_at < :beforeMillis
        """,
    )
    suspend fun expireOldActions(beforeMillis: Long): Int

    @Query("DELETE FROM agent_actions WHERE status = 'EXECUTED' AND created_at < :olderThanMillis")
    suspend fun deleteExecuted(olderThanMillis: Long): Int
}
