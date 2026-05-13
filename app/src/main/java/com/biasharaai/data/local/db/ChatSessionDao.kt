package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(row: ChatSessionEntity): Long

    @Query("UPDATE chat_sessions SET title = :title, updated_at = :updatedAt WHERE id = :sessionId")
    suspend fun updateSessionTitle(sessionId: Long, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chat_sessions SET updated_at = :updatedAt WHERE id = :sessionId")
    suspend fun touchSession(sessionId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1")
    suspend fun getSession(id: Long): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions ORDER BY updated_at DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions ORDER BY updated_at DESC")
    suspend fun listSessions(): List<ChatSessionEntity>

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessage(row: ChatSessionMessageEntity): Long

    @Query(
        """
        SELECT * FROM chat_session_messages
        WHERE session_id = :sessionId
        ORDER BY id ASC
        """,
    )
    suspend fun messagesForSession(sessionId: Long): List<ChatSessionMessageEntity>

    @Query("SELECT COUNT(*) FROM chat_session_messages WHERE session_id = :sessionId")
    suspend fun countMessages(sessionId: Long): Int

    @Query(
        """
        DELETE FROM chat_session_messages WHERE id IN (
            SELECT id FROM chat_session_messages
            WHERE session_id = :sessionId
            ORDER BY id ASC
            LIMIT :excess
        )
        """,
    )
    suspend fun deleteOldestMessages(sessionId: Long, excess: Int)

}
