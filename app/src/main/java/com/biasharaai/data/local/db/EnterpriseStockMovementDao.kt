package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EnterpriseStockMovementDao {
    @Insert
    suspend fun insert(movement: EnterpriseStockMovement): Long

    @Query("SELECT * FROM enterprise_stock_movements ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<EnterpriseStockMovement>>

    @Query("SELECT * FROM enterprise_stock_movements ORDER BY created_at DESC LIMIT :limit")
    suspend fun listRecent(limit: Int = 500): List<EnterpriseStockMovement>
}
