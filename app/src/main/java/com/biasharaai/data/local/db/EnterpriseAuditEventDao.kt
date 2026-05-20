package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EnterpriseAuditEventDao {

    @Insert
    suspend fun insert(event: EnterpriseAuditEvent): Long

    @Query("SELECT * FROM enterprise_audit_events ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<EnterpriseAuditEvent>>

    @Query("SELECT * FROM enterprise_audit_events ORDER BY created_at DESC LIMIT :limit")
    suspend fun listRecent(limit: Int = 1000): List<EnterpriseAuditEvent>

    @Query("DELETE FROM enterprise_audit_events WHERE created_at < :olderThanMillis")
    suspend fun deleteOlderThan(olderThanMillis: Long): Int
}
