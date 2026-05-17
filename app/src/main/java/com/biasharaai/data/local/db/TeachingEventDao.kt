package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TeachingEventDao {

    @Insert
    suspend fun insert(event: TeachingEvent): Long

    @Query(
        """
        SELECT * FROM teaching_events
        WHERE feature_id = :featureId
        ORDER BY created_at DESC
        """,
    )
    suspend fun getByFeature(featureId: String): List<TeachingEvent>

    @Query(
        """
        SELECT COUNT(*) FROM teaching_events
        WHERE feature_id = :featureId AND outcome = 'SUCCESS'
        """,
    )
    suspend fun countSuccessfulUses(featureId: String): Int

    @Query(
        """
        SELECT * FROM teaching_events
        ORDER BY created_at DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecent(limit: Int): List<TeachingEvent>

    @Query("DELETE FROM teaching_events WHERE created_at < :beforeMillis")
    suspend fun deleteBefore(beforeMillis: Long)
}
