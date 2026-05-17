package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {

    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE ASC")
    suspend fun getCustomersList(): List<Customer>

    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Long): Customer?

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    fun getCustomerByIdFlow(id: Long): Flow<Customer?>

    @Query(
        """
        SELECT * FROM customers
        WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%'
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun searchByName(query: String): Flow<List<Customer>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomer(customer: Customer): Long

    @Update
    suspend fun updateCustomer(customer: Customer)

    @Delete
    suspend fun deleteCustomer(customer: Customer)

    @Query("UPDATE customers SET last_visit = :millis WHERE id = :id")
    suspend fun updateLastVisit(id: Long, millis: Long)

    @Query(
        """
        SELECT COUNT(*) FROM customers
        WHERE created_at >= :startMillis AND created_at < :endExclusiveMillis
        """,
    )
    suspend fun countCustomersCreatedBetween(startMillis: Long, endExclusiveMillis: Long): Long

    /**
     * Distinct buyers in the window who already had a customer profile **before** [weekStartMillis]
     * (A7 “returning” heuristic vs new profiles created this week).
     */
    @Query(
        """
        SELECT COUNT(DISTINCT t.customer_id) FROM transactions t
        INNER JOIN customers c ON c.id = t.customer_id
        WHERE t.type = 'INCOME' AND t.customer_id IS NOT NULL
          AND t.date >= :weekStartMillis AND t.date < :weekEndExclusiveMillis
          AND c.created_at < :weekStartMillis
        """,
    )
    suspend fun countReturningBuyerCustomersInWeek(weekStartMillis: Long, weekEndExclusiveMillis: Long): Long
}
