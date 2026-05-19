package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceDeliveryDao {
    @Query("SELECT * FROM service_deliveries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ServiceDelivery?

    @Query("SELECT * FROM service_deliveries WHERE receipt_token = :token LIMIT 1")
    suspend fun getByReceiptToken(token: String): ServiceDelivery?

    @Query(
        """
        SELECT * FROM service_deliveries
        WHERE transaction_id = :transactionId
        ORDER BY id ASC
        """,
    )
    suspend fun getByTransactionOnce(transactionId: Long): List<ServiceDelivery>

    @Query(
        """
        SELECT * FROM service_deliveries
        WHERE transaction_id = :transactionId
        ORDER BY id ASC
        """,
    )
    fun observeByTransaction(transactionId: Long): Flow<List<ServiceDelivery>>

    @Query("SELECT COUNT(*) FROM service_deliveries")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(delivery: ServiceDelivery): Long

    @Update
    suspend fun update(delivery: ServiceDelivery)

    @Query("SELECT * FROM service_deliveries WHERE delivered_at >= :since ORDER BY delivered_at ASC")
    suspend fun getDeliveriesSince(since: Long): List<ServiceDelivery>

    @Query(
        """
        SELECT IFNULL(SUM(charged_amount), 0) FROM service_deliveries
        WHERE delivered_at >= :startMillis AND delivered_at <= :endMillis
        """,
    )
    suspend fun sumChargedInPeriod(startMillis: Long, endMillis: Long): Double
}
