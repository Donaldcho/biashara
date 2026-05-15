package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ModelDescriptorDao {

    @Query("SELECT COUNT(*) FROM model_descriptors")
    suspend fun count(): Int
}
