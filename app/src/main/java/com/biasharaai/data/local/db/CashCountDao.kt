package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CashCountDao {

    @Insert
    suspend fun insert(count: CashCount): Long

    @Query("SELECT * FROM cash_counts ORDER BY counted_at DESC")
    fun getAllOrderByCountedAtDesc(): Flow<List<CashCount>>

    @Query("SELECT * FROM cash_counts ORDER BY counted_at DESC LIMIT 1")
    suspend fun getLatest(): CashCount?
}
