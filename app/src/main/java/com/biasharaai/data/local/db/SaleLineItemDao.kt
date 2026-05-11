package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleLineItemDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLineItem(item: SaleLineItem): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLineItems(items: List<SaleLineItem>): List<Long>

    @Query("SELECT * FROM sale_line_items WHERE transaction_id = :transactionId ORDER BY id ASC")
    fun getLineItemsByTransaction(transactionId: Long): Flow<List<SaleLineItem>>

    @Query("DELETE FROM sale_line_items WHERE transaction_id = :transactionId")
    suspend fun deleteByTransaction(transactionId: Long)
}
