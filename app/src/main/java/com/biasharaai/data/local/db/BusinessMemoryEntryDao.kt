package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BusinessMemoryEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BusinessMemoryEntry): Long

    /** All non-expired entries, newest first. */
    @Query(
        """
        SELECT * FROM business_memory_entries
        WHERE expires_at IS NULL OR expires_at > :nowMillis
        ORDER BY created_at DESC
        """,
    )
    suspend fun getActive(nowMillis: Long = System.currentTimeMillis()): List<BusinessMemoryEntry>

    /** Non-expired entries that carry an embedding — used for semantic retrieval. */
    @Query(
        """
        SELECT * FROM business_memory_entries
        WHERE embedding_blob IS NOT NULL
          AND (expires_at IS NULL OR expires_at > :nowMillis)
        """,
    )
    suspend fun getActiveWithEmbeddings(nowMillis: Long = System.currentTimeMillis()): List<BusinessMemoryEntry>

    /** Remove all expired entries. */
    @Query("DELETE FROM business_memory_entries WHERE expires_at IS NOT NULL AND expires_at < :nowMillis")
    suspend fun purgeExpired(nowMillis: Long = System.currentTimeMillis())

    /** Replace weekly KPI entries from the given source before writing a fresh one. */
    @Query("DELETE FROM business_memory_entries WHERE type = :type AND source = :source")
    suspend fun deleteByTypeAndSource(type: String, source: String)

    /** Guard against re-extracting the same chat period twice in a week. */
    @Query(
        """
        SELECT COUNT(*) FROM business_memory_entries
        WHERE type = :type AND source = :source AND created_at > :sinceMillis
        """,
    )
    suspend fun countRecentByTypeAndSource(type: String, source: String, sinceMillis: Long): Int
}
