package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for [Transaction] entities.
 */
@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    /** All transactions ordered by date descending. */
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    /** Synchronous fetch of all transactions. */
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    suspend fun getTransactionsList(): List<Transaction>

    /**
     * Transactions within a date range (inclusive), ordered by date descending.
     *
     * @param startDate epoch millis (UTC) — start of period.
     * @param endDate   epoch millis (UTC) — end of period.
     */
    @Query("SELECT * FROM transactions WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getTransactionsByPeriod(startDate: Long, endDate: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    suspend fun getTransactionsBetween(startDate: Long, endDate: Long): List<Transaction>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    fun observeTransactionById(id: Long): Flow<Transaction?>

    /**
     * POS sales only: [TransactionType.INCOME] with at least one positive-quantity line item.
     */
    @Query(
        """
        SELECT t.* FROM transactions t
        WHERE t.type = 'INCOME'
        AND EXISTS (
            SELECT 1 FROM sale_line_items sl
            WHERE sl.transaction_id = t.id AND sl.quantity > 0
        )
        ORDER BY t.date DESC
        """,
    )
    fun observeCompletedPosSales(): Flow<List<Transaction>>
}
