package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LedgerContextDao {

    @Insert
    suspend fun insert(row: LedgerContext): Long

    @Query(
        """
        SELECT * FROM ledger_context
        WHERE superseded_at_millis IS NULL
          AND related_anomaly_id = :anomalyId
        ORDER BY created_at_millis DESC
        LIMIT 20
        """,
    )
    suspend fun getActiveForAnomaly(anomalyId: String): List<LedgerContext>

    @Query(
        """
        SELECT * FROM ledger_context
        WHERE superseded_at_millis IS NULL
          AND (applies_from_millis IS NULL OR applies_from_millis <= :toMillis)
          AND (applies_to_millis IS NULL OR applies_to_millis >= :fromMillis)
        ORDER BY
          CASE source WHEN 'OWNER_CONFIRMED' THEN 0 WHEN 'SYSTEM' THEN 1 ELSE 2 END,
          created_at_millis DESC
        LIMIT :limit
        """,
    )
    suspend fun getActiveForPeriod(fromMillis: Long, toMillis: Long, limit: Int = 50): List<LedgerContext>

    @Query(
        """
        UPDATE ledger_context
        SET superseded_at_millis = :atMillis
        WHERE superseded_at_millis IS NULL
          AND related_anomaly_id = :anomalyId
          AND source != 'OWNER_CONFIRMED'
        """,
    )
    suspend fun supersedeAgentInferencesForAnomaly(anomalyId: String, atMillis: Long)

    @Query(
        """
        UPDATE ledger_context
        SET superseded_at_millis = :atMillis
        WHERE superseded_at_millis IS NULL
          AND related_anomaly_id = :anomalyId
          AND source = 'OWNER_CONFIRMED'
        """,
    )
    suspend fun supersedeOwnerConfirmedForAnomaly(anomalyId: String, atMillis: Long)
}
