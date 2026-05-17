package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Owner- or agent-provided explanations for ledger anomalies (Ledger Intelligence v2).
 * Rows are superseded, not deleted, when updated.
 */
@Entity(
    tableName = "ledger_context",
    indices = [
        Index(value = ["related_ledger_entry_id"]),
        Index(value = ["related_anomaly_id"]),
        Index(value = ["created_at_millis"]),
        Index(
            value = ["related_anomaly_id", "superseded_at_millis"],
            name = "idx_ledger_context_anomaly_active",
        ),
        Index(
            value = ["superseded_at_millis", "applies_from_millis", "applies_to_millis"],
            name = "idx_ledger_context_active_period",
        ),
    ],
)
data class LedgerContext(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "related_ledger_entry_id")
    val relatedLedgerEntryId: Long? = null,

    @ColumnInfo(name = "related_anomaly_id")
    val relatedAnomalyId: String? = null,

    @ColumnInfo(name = "context_type")
    val contextType: String,

    @ColumnInfo(name = "prompt")
    val prompt: String,

    @ColumnInfo(name = "owner_answer")
    val ownerAnswer: String? = null,

    /** [LedgerContextSource] name */
    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "confidence")
    val confidence: Double? = null,

    @ColumnInfo(name = "applies_from_millis")
    val appliesFromMillis: Long? = null,

    @ColumnInfo(name = "applies_to_millis")
    val appliesToMillis: Long? = null,

    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "resolved_at_millis")
    val resolvedAtMillis: Long? = null,

    @ColumnInfo(name = "superseded_at_millis")
    val supersededAtMillis: Long? = null,
)

enum class LedgerContextSource {
    OWNER_CONFIRMED,
    AGENT_INFERRED,
    SYSTEM,
}
