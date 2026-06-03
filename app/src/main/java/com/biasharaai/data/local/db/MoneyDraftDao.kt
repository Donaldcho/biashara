package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MoneyDraftDao {

    @Insert
    suspend fun insert(draft: MoneyDraft): Long

    @Query("SELECT * FROM money_drafts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MoneyDraft?

    @Query(
        """
        SELECT * FROM money_drafts
        WHERE status = 'NEEDS_REVIEW'
        ORDER BY created_at DESC, id DESC
        """,
    )
    fun observePendingReview(): Flow<List<MoneyDraft>>

    @Query("SELECT COUNT(*) FROM money_drafts WHERE status = 'NEEDS_REVIEW'")
    suspend fun countPendingReview(): Int

    @Query(
        """
        UPDATE money_drafts
        SET status = 'APPROVED',
            approved_at = :approvedAt,
            ledger_entry_id = :ledgerEntryId
        WHERE id = :id AND status = 'NEEDS_REVIEW'
        """,
    )
    suspend fun markApproved(id: Long, ledgerEntryId: Long, approvedAt: Long)

    @Query(
        """
        UPDATE money_drafts
        SET status = 'REJECTED'
        WHERE id = :id AND status = 'NEEDS_REVIEW'
        """,
    )
    suspend fun markRejected(id: Long)
}
