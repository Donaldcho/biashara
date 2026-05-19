package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(appointment: Appointment): Long

    @Query(
        """
        SELECT * FROM appointments
        WHERE scheduled_at BETWEEN :from AND :to
        ORDER BY scheduled_at ASC
        """,
    )
    fun getByDateRange(from: Long, to: Long): Flow<List<Appointment>>

    @Query(
        """
        SELECT * FROM appointments
        WHERE scheduled_at < :before AND status = :status
        ORDER BY scheduled_at ASC
        """,
    )
    suspend fun getOverdue(before: Long, status: String = Appointment.STATUS_BOOKED): List<Appointment>

    @Query("UPDATE appointments SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query(
        """
        SELECT COUNT(*) FROM appointments
        WHERE customer_id = :customerId
          AND status = :noShowStatus
          AND scheduled_at >= :since
        """,
    )
    suspend fun countNoShowsForCustomer(
        customerId: Long,
        since: Long,
        noShowStatus: String = Appointment.STATUS_NO_SHOW,
    ): Int

    @Query(
        """
        SELECT * FROM appointments
        WHERE customer_id = :customerId
        ORDER BY scheduled_at DESC
        """,
    )
    fun getForCustomer(customerId: Long): Flow<List<Appointment>>
}
