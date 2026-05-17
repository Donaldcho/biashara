package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(alert: Alert): Long

    /** Marks alert as read / dismissed (matches `read` column in DB). */
    @Query("UPDATE alerts SET read = 1 WHERE id = :id")
    suspend fun dismissAlert(id: Long)

    @Query("SELECT * FROM alerts WHERE read = 0 ORDER BY created_at DESC")
    fun getActiveAlerts(): Flow<List<Alert>>

    /** Active loss-prevention cards for the Home dashboard (Prompt U5). */
    @Query(
        """
        SELECT * FROM alerts WHERE read = 0 AND alert_type LIKE 'LOSS_%'
        ORDER BY created_at DESC
        """,
    )
    fun getActiveLossAlerts(): Flow<List<Alert>>

    @Query(
        """
        SELECT COUNT(*) FROM alerts
        WHERE read = 0 AND dedupe_key IS NOT NULL AND dedupe_key = :key
        """,
    )
    suspend fun countActiveAlertsWithDedupeKey(key: String): Int

    @Query("DELETE FROM alerts WHERE created_at < :olderThanMillis")
    suspend fun deleteOldAlerts(olderThanMillis: Long): Int

    @Query("SELECT * FROM alerts ORDER BY created_at DESC LIMIT 500")
    suspend fun listRecentForExport(): List<Alert>
}
