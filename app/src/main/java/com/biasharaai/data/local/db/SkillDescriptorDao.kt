package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SkillDescriptorDao {

    @Query("SELECT COUNT(*) FROM skill_descriptors")
    suspend fun count(): Int

    @Query("SELECT * FROM skill_descriptors ORDER BY displayName ASC")
    suspend fun getAll(): List<SkillDescriptor>

    @Query("SELECT * FROM skill_descriptors WHERE skillId = :skillId LIMIT 1")
    suspend fun getById(skillId: String): SkillDescriptor?

    @Query("SELECT * FROM skill_descriptors WHERE isEnabled = 1 ORDER BY displayName ASC")
    suspend fun getEnabled(): List<SkillDescriptor>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(descriptor: SkillDescriptor)

    @Query("UPDATE skill_descriptors SET isEnabled = :enabled WHERE skillId = :skillId")
    suspend fun setEnabled(skillId: String, enabled: Boolean)

    @Query("DELETE FROM skill_descriptors WHERE packId = :packId")
    suspend fun deleteByPackId(packId: String)

    @Query(
        """
        UPDATE skill_descriptors
        SET lastExecutedAt = :executedAt,
            executionCount = executionCount + 1
        WHERE skillId = :skillId
        """,
    )
    suspend fun recordExecution(skillId: String, executedAt: Long)
}
