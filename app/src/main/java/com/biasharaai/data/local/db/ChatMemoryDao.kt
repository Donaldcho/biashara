package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChatMemoryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMemory(row: AiBusinessMemory): Long

    @Query("SELECT * FROM ai_business_memory ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecentMemories(limit: Int): List<AiBusinessMemory>

    @Query("SELECT COUNT(*) FROM ai_business_memory")
    suspend fun countMemories(): Int

    @Query(
        """
        DELETE FROM ai_business_memory WHERE id IN (
            SELECT id FROM ai_business_memory ORDER BY created_at ASC LIMIT :excess
        )
        """,
    )
    suspend fun deleteOldestMemories(excess: Int)
}
