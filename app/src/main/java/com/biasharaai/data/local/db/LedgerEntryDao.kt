package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Append-only access to [ledger_entries]. Intentionally has no `@Update` or `@Delete` except
 * [updateBalance] for sync recompute (Phase 9 L9).
 */
@Dao
interface LedgerEntryDao {

    @Insert
    suspend fun insert(entry: LedgerEntry): Long

    @Insert
    suspend fun insertAll(entries: List<LedgerEntry>)

    @Query(
        """
        SELECT running_balance FROM ledger_entries
        ORDER BY occurred_at DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun getCurrentBalance(): Double?

    @Query(
        """
        SELECT * FROM ledger_entries
        WHERE occurred_at BETWEEN :from AND :to
        ORDER BY occurred_at DESC, id DESC
        """,
    )
    fun getEntriesForPeriod(from: Long, to: Long): Flow<List<LedgerEntry>>

    @Query(
        """
        SELECT * FROM ledger_entries
        WHERE occurred_at BETWEEN :from AND :to AND direction = :dir
        ORDER BY occurred_at DESC, id DESC
        """,
    )
    fun getEntriesForPeriodByDirection(
        from: Long,
        to: Long,
        dir: String,
    ): Flow<List<LedgerEntry>>

    @Query(
        """
        SELECT * FROM ledger_entries
        WHERE occurred_at BETWEEN :from AND :to AND type IN (:types)
        ORDER BY occurred_at DESC, id DESC
        """,
    )
    fun getEntriesForPeriodByTypes(
        from: Long,
        to: Long,
        types: List<String>,
    ): Flow<List<LedgerEntry>>

    @Query(
        """
        SELECT SUM(amount) FROM ledger_entries
        WHERE direction = :dir AND occurred_at BETWEEN :from AND :to
        """,
    )
    suspend fun getTotalForDirection(dir: String, from: Long, to: Long): Double?

    @Query(
        """
        SELECT direction, SUM(amount) AS total FROM ledger_entries
        WHERE direction IN ('MONEY_IN', 'MONEY_OUT')
          AND occurred_at BETWEEN :from AND :to
        GROUP BY direction
        """,
    )
    suspend fun getTotalsByDirection(from: Long, to: Long): List<LedgerDirectionTotal>

    @Query(
        """
        SELECT type, SUM(amount) AS total FROM ledger_entries
        WHERE occurred_at BETWEEN :from AND :to AND direction != 'NEUTRAL'
        GROUP BY type
        ORDER BY total DESC
        """,
    )
    suspend fun getBreakdownByType(from: Long, to: Long): List<LedgerTypeTotal>

    @Query(
        """
        SELECT * FROM ledger_entries
        WHERE description LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%'
        ORDER BY occurred_at DESC, id DESC
        LIMIT 200
        """,
    )
    fun search(query: String): Flow<List<LedgerEntry>>

    @Query(
        """
        SELECT * FROM ledger_entries
        WHERE customer_id = :id
        ORDER BY occurred_at DESC, id DESC
        """,
    )
    fun getEntriesForCustomer(id: Long): Flow<List<LedgerEntry>>

    @Query(
        """
        SELECT SUM(amount) FROM ledger_entries
        WHERE type = 'CREDIT_EXTENDED'
          AND debt_id IN (SELECT id FROM debts WHERE amount > 0)
        """,
    )
    suspend fun getPendingCreditTotal(): Double?

    @Query(
        """
        SELECT
          COALESCE(SUM(amount), 0.0) AS amount,
          COUNT(*) AS count
        FROM ledger_entries
        WHERE type = 'CREDIT_EXTENDED'
          AND debt_id IN (SELECT id FROM debts WHERE amount > 0)
        """,
    )
    suspend fun getPendingCreditSummary(): LedgerPendingCreditSummary

    @Query(
        """
        SELECT * FROM ledger_entries
        ORDER BY occurred_at DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentEntries(limit: Int): List<LedgerEntry>

    @Query(
        """
        SELECT * FROM ledger_entries
        WHERE occurred_at BETWEEN :from AND :to
        ORDER BY occurred_at ASC, id ASC
        """,
    )
    suspend fun getEntriesForReportSync(from: Long, to: Long): List<LedgerEntry>

    @Query(
        """
        SELECT
          occurred_at AS occurredAt,
          direction AS direction,
          amount AS amount
        FROM ledger_entries
        WHERE occurred_at BETWEEN :from AND :to
          AND direction IN ('MONEY_IN', 'MONEY_OUT')
        ORDER BY occurred_at ASC, id ASC
        """,
    )
    suspend fun getAmountPointsForReport(from: Long, to: Long): List<LedgerAmountPoint>

    @Query("SELECT COUNT(*) FROM ledger_entries")
    suspend fun count(): Int

    @Query(
        """
        SELECT * FROM ledger_entries
        WHERE is_synced = 0
        ORDER BY occurred_at ASC, id ASC
        """,
    )
    suspend fun getUnsynced(): List<LedgerEntry>

    @Query("UPDATE ledger_entries SET is_synced = 1 WHERE sync_id = :syncId")
    suspend fun markSynced(syncId: String)

    @Query("UPDATE ledger_entries SET running_balance = :balance WHERE id = :id")
    suspend fun updateBalance(id: Long, balance: Double)

    @Query(
        """
        SELECT * FROM ledger_entries
        ORDER BY occurred_at ASC, id ASC
        """,
    )
    suspend fun getAllSortedByOccurredAt(): List<LedgerEntry>
}
