package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Query

@Dao
interface SkillDescriptorDao {

    @Query("SELECT COUNT(*) FROM skill_descriptors")
    suspend fun count(): Int
}
