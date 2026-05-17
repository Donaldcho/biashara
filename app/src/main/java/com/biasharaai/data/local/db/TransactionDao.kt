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

    // ── Fraud Sentinel (A3) — Room-only rules ─────────────────────────────

    /**
     * POS sale transactions in the lookback window with at least one line priced below half of
     * current product cost (cost &gt; 0).
     */
    @Query(
        """
        SELECT DISTINCT t.id FROM transactions t
        INNER JOIN sale_line_items sl ON sl.transaction_id = t.id
        INNER JOIN products p ON p.id = sl.product_id
        WHERE t.type = 'INCOME' AND sl.quantity > 0 AND t.date >= :sinceMillis
          AND p.cost > 0 AND sl.unit_price < (p.cost * 0.5)
        """,
    )
    suspend fun transactionIdsWithBelowHalfCostSaleLinesSince(sinceMillis: Long): List<Long>

    @Query("SELECT COUNT(*) FROM transactions WHERE type = 'RETURN' AND date >= :sinceMillis")
    suspend fun countReturnsSince(sinceMillis: Long): Long

    @Query(
        """
        SELECT id FROM transactions
        WHERE type = 'INCOME' AND amount <= 0 AND date >= :sinceMillis
        """,
    )
    suspend fun incomeTransactionIdsWithNonPositiveAmountSince(sinceMillis: Long): List<Long>

    @Query(
        """
        SELECT DISTINCT t1.id FROM transactions t1
        WHERE t1.receipt_number IS NOT NULL AND TRIM(t1.receipt_number) != ''
          AND t1.date >= :sinceMillis
          AND (
            SELECT COUNT(*) FROM transactions t2
            WHERE t2.receipt_number IS NOT NULL AND TRIM(t2.receipt_number) != ''
              AND TRIM(t2.receipt_number) = TRIM(t1.receipt_number)
              AND t2.date >= :sinceMillis
          ) > 1
        """,
    )
    suspend fun transactionIdsWithDuplicateReceiptNumberSince(sinceMillis: Long): List<Long>

    // ── Cash flow sentinel (A5) ───────────────────────────────────────────

    @Query(
        """
        SELECT IFNULL(SUM(amount), 0) FROM transactions
        WHERE type = 'INCOME' AND date >= :startMillis AND date < :endExclusiveMillis
        """,
    )
    suspend fun sumIncomeAmountBetween(startMillis: Long, endExclusiveMillis: Long): Double

    @Query(
        """
        SELECT IFNULL(SUM(amount), 0) FROM transactions
        WHERE type = 'EXPENSE' AND date >= :startMillis AND date < :endExclusiveMillis
        """,
    )
    suspend fun sumExpenseAmountBetween(startMillis: Long, endExclusiveMillis: Long): Double

    @Query(
        """
        SELECT IFNULL(SUM(amount), 0) FROM transactions
        WHERE type = 'INCOME' AND payment_method = 'CREDIT'
          AND date >= :startMillis AND date < :endExclusiveMillis
        """,
    )
    suspend fun sumCreditIncomeAmountBetween(startMillis: Long, endExclusiveMillis: Long): Double

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE type = 'INCOME' AND date >= :startMillis AND date < :endExclusiveMillis
        """,
    )
    suspend fun countIncomeTransactionsBetween(startMillis: Long, endExclusiveMillis: Long): Long

    // ── Customer relation (A6) — visit cadence from linked POS income rows ───────────────

    /** INCOME rows tied to a customer (ordered oldest → newest) for visit-gap analysis. */
    @Query(
        """
        SELECT date FROM transactions
        WHERE customer_id = :customerId AND type = 'INCOME'
        ORDER BY date ASC
        """,
    )
    suspend fun getIncomeDatesForCustomer(customerId: Long): List<Long>
}
