package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SkillPackRecordDao {

    @Query("SELECT COUNT(*) FROM skill_pack_records")
    suspend fun count(): Int

    @Query("SELECT * FROM skill_pack_records ORDER BY installedAt DESC")
    suspend fun getAll(): List<SkillPackRecord>

    @Query("SELECT * FROM skill_pack_records WHERE packId = :packId LIMIT 1")
    suspend fun getById(packId: String): SkillPackRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: SkillPackRecord)

    @Query("DELETE FROM skill_pack_records WHERE packId = :packId")
    suspend fun delete(packId: String)

    @Query("UPDATE skill_pack_records SET isActive = :active WHERE packId = :packId")
    suspend fun setActive(packId: String, active: Boolean)
}
