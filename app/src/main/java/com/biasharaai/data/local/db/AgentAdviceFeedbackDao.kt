package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentAdviceFeedbackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(feedback: AgentAdviceFeedback)

    @Query("SELECT * FROM agent_advice_feedback WHERE created_at >= :sinceMillis ORDER BY created_at DESC")
    fun observeFeedbackSince(sinceMillis: Long): Flow<List<AgentAdviceFeedback>>

    @Query("DELETE FROM agent_advice_feedback WHERE created_at < :olderThanMillis")
    suspend fun deleteOlderThan(olderThanMillis: Long): Int
}
