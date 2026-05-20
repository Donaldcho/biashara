package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EnterpriseSyncOutboxDao {

    @Insert
    suspend fun insert(item: EnterpriseSyncOutboxItem): Long

    @Query(
        """
        SELECT * FROM enterprise_sync_outbox
        WHERE status = :status AND next_attempt_at <= :now
        ORDER BY created_at ASC
        LIMIT :limit
        """,
    )
    suspend fun listReady(
        now: Long = System.currentTimeMillis(),
        status: String = EnterpriseSyncOutboxItem.STATUS_PENDING,
        limit: Int = 100,
    ): List<EnterpriseSyncOutboxItem>

    @Query("SELECT COUNT(*) FROM enterprise_sync_outbox WHERE status = :status")
    fun observeCount(status: String = EnterpriseSyncOutboxItem.STATUS_PENDING): Flow<Int>

    @Query("SELECT COUNT(*) FROM enterprise_sync_outbox WHERE status = :status")
    suspend fun count(status: String = EnterpriseSyncOutboxItem.STATUS_PENDING): Int

    @Query(
        """
        UPDATE enterprise_sync_outbox
        SET status = :status, updated_at = :updatedAt, last_error = NULL
        WHERE id = :id
        """,
    )
    suspend fun markStatus(
        id: Long,
        status: String,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query(
        """
        UPDATE enterprise_sync_outbox
        SET status = :status,
            attempt_count = :attemptCount,
            last_error = :lastError,
            next_attempt_at = :nextAttemptAt,
            updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun markRetryState(
        id: Long,
        status: String,
        attemptCount: Int,
        lastError: String?,
        nextAttemptAt: Long,
        updatedAt: Long = System.currentTimeMillis(),
    )
}
