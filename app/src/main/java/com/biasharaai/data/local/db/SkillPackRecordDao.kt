package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Query

@Dao
interface SkillPackRecordDao {

    @Query("SELECT COUNT(*) FROM skill_pack_records")
    suspend fun count(): Int
}
