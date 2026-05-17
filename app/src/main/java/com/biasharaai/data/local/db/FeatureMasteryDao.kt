package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FeatureMasteryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mastery: FeatureMastery)

    @Query("SELECT * FROM feature_mastery WHERE feature_id = :featureId")
    suspend fun getByFeature(featureId: String): FeatureMastery?

    @Query(
        """
        SELECT * FROM feature_mastery
        ORDER BY mastery_level ASC, last_practiced_at ASC
        """,
    )
    suspend fun getAll(): List<FeatureMastery>

    @Query(
        """
        SELECT * FROM feature_mastery
        WHERE mastery_level != 'MASTERED'
        ORDER BY practice_count ASC
        LIMIT :limit
        """,
    )
    suspend fun getLeastMastered(limit: Int): List<FeatureMastery>

    @Query(
        """
        UPDATE feature_mastery
        SET mastery_level = :level,
            last_practiced_at = :now,
            practice_count = practice_count + 1
        WHERE feature_id = :featureId
        """,
    )
    suspend fun updateMastery(featureId: String, level: String, now: Long)
}
