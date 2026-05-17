package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingNotificationDao {

    @Insert
    suspend fun insert(row: PendingNotification): Long

    @Query(
        """
        SELECT * FROM pending_notifications
        WHERE fire_at <= :nowMillis
        ORDER BY fire_at ASC
        """,
    )
    suspend fun getDue(nowMillis: Long): List<PendingNotification>

    @Query("DELETE FROM pending_notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE pending_notifications SET fire_at = :newFireAt WHERE id = :id")
    suspend fun updateFireAt(id: Long, newFireAt: Long)

    @Query("DELETE FROM pending_notifications")
    suspend fun deleteAll()
}
