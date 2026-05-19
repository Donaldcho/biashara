package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A structured fact extracted from weekly KPI reviews or chat sessions.
 * Injected as RAG context into the Gemma system prompt so the AI answers
 * with knowledge of this specific business's history, goals, and patterns.
 *
 * Types:
 *   "kpi_week"   — weekly revenue/product/service snapshot narrative
 *   "pattern"    — observed recurring business pattern
 *   "goal"       — owner-stated business goal extracted from chat
 *   "preference" — owner-stated operational preference extracted from chat
 *   "fact"       — discrete business fact (e.g. "owner runs a salon")
 */
@Entity(
    tableName = "business_memory_entries",
    indices = [
        Index(value = ["type"]),
        Index(value = ["created_at"]),
    ],
)
data class BusinessMemoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,
    val content: String,
    /** Space-separated lowercase keywords for fallback keyword retrieval. */
    val keywords: String? = null,
    /** MiniLM-L6-v2 384-dim embedding, little-endian floats — null when engine unavailable. */
    @ColumnInfo(name = "embedding_blob") val embeddingBlob: ByteArray? = null,
    /** "weekly_review" | "chat_extract" */
    val source: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    /** Null = permanent. Epoch ms after which this entry is ignored (used for KPI weeks). */
    @ColumnInfo(name = "expires_at") val expiresAt: Long? = null,
)
