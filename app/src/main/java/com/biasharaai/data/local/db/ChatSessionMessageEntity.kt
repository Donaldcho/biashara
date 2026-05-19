package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_session_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["session_id"])],
)
data class ChatSessionMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    /** "user" or "assistant" — matches [ChatMemoryRepository.ROLE_*] */
    val role: String,
    val body: String,
    @ColumnInfo(name = "image_path")
    val imagePath: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    /** Optional user feedback on assistant replies: `1` helpful, `-1` not helpful, `null` unset. */
    @ColumnInfo(name = "feedback_vote")
    val feedbackVote: Int? = null,
    /** Pipe-delimited source chips displayed under assistant replies. */
    @ColumnInfo(name = "source_tags")
    val sourceTags: String? = null,
    @ColumnInfo(name = "confidence_label")
    val confidenceLabel: String? = null,
    @ColumnInfo(name = "action_hint")
    val actionHint: String? = null,
)
