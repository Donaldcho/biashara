package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDebt(debt: Debt): Long

    @Query("SELECT * FROM debts WHERE amount > 0 ORDER BY created_at DESC")
    fun getUnpaidDebts(): Flow<List<Debt>>

    @Query("SELECT * FROM debts WHERE customer_id = :customerId ORDER BY created_at DESC")
    fun getDebtsByCustomer(customerId: Long): Flow<List<Debt>>

    @Query("SELECT IFNULL(SUM(amount), 0) FROM debts WHERE amount > 0")
    fun getTotalOutstanding(): Flow<Double>

    /** Settles a debt row in-place (amount 0) so history remains; excluded from outstanding sums. */
    @Query("UPDATE debts SET amount = 0 WHERE id = :id AND amount > 0")
    suspend fun markPaid(id: Long): Int

    /** Sum of all debt rows for the customer (Phase 2 “outstanding” = recorded obligations). */
    @Query("SELECT IFNULL(SUM(amount), 0) FROM debts WHERE customer_id = :customerId")
    fun observeTotalOutstandingForCustomer(customerId: Long): Flow<Double>

    @Query("SELECT * FROM debts WHERE customer_id = :customerId ORDER BY created_at ASC")
    suspend fun getDebtsOldestFirst(customerId: Long): List<Debt>

    @Update
    suspend fun updateDebt(debt: Debt)

    /**
     * Reduces recorded debt FIFO by [totalToReduce] (e.g. credit sale return).
     */
    @Transaction
    suspend fun reduceAmount(customerId: Long, totalToReduce: Double) {
        var remaining = totalToReduce
        if (remaining <= 0) return
        val debts = getDebtsOldestFirst(customerId)
        for (d in debts) {
            if (remaining <= 0) break
            val take = minOf(d.amount, remaining)
            if (take <= 0) continue
            updateDebt(d.copy(amount = d.amount - take))
            remaining -= take
        }
    }
}
