package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CashMovementEvidenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(evidence: CashMovementEvidence): Long

    @Query("SELECT * FROM cash_movement_evidence WHERE ledger_entry_id = :ledgerEntryId LIMIT 1")
    suspend fun getForEntry(ledgerEntryId: Long): CashMovementEvidence?

    /** Duplicate M-Pesa reference detection — excludes the row just inserted (pass its id). */
    @Query(
        """SELECT COUNT(*) FROM cash_movement_evidence
           WHERE parsed_reference = :ref AND id != :excludeId""",
    )
    suspend fun countByReference(ref: String, excludeId: Long = -1L): Int

    /**
     * Unverified cash-out detection: MONEY_OUT entries captured manually with no reference
     * in the last [since] window (epoch ms).
     */
    @Query(
        """SELECT COUNT(*) FROM cash_movement_evidence e
           JOIN ledger_entries l ON e.ledger_entry_id = l.id
           WHERE l.direction = 'MONEY_OUT'
           AND e.capture_method = 'MANUAL'
           AND e.parsed_reference IS NULL
           AND l.occurred_at >= :since""",
    )
    suspend fun countUnverifiedOutflowsSince(since: Long): Int

    /**
     * Large unverified outflows for fraud detection: MONEY_OUT, MANUAL, no reference,
     * parsed_amount > [threshold].
     */
    @Query(
        """SELECT e.* FROM cash_movement_evidence e
           JOIN ledger_entries l ON e.ledger_entry_id = l.id
           WHERE l.direction = 'MONEY_OUT'
           AND e.capture_method = 'MANUAL'
           AND e.parsed_reference IS NULL
           AND e.parsed_amount > :threshold
           AND l.occurred_at >= :since""",
    )
    suspend fun getLargeUnverifiedOutflows(since: Long, threshold: Double): List<CashMovementEvidence>

    // ── Storage management ────────────────────────────────────────────────

    @Query("SELECT COALESCE(SUM(thumbnail_size_bytes), 0) FROM cash_movement_evidence")
    suspend fun getTotalThumbnailBytes(): Long

    @Query("UPDATE cash_movement_evidence SET thumbnail_bytes = NULL, thumbnail_size_bytes = 0")
    suspend fun clearAllThumbnails()

    @Query(
        """UPDATE cash_movement_evidence
           SET thumbnail_bytes = NULL, thumbnail_size_bytes = 0
           WHERE id NOT IN (
               SELECT id FROM cash_movement_evidence
               ORDER BY created_at DESC
               LIMIT :keepCount
           )""",
    )
    suspend fun deleteOldestThumbnails(keepCount: Int)
}
