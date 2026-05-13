package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Maps to the `alerts` table created in migration **3→5** (Phase 2 baseline).
 *
 * Prompt U1 design used `type` / `productId` / `isDismissed`; this app’s shipped DDL uses
 * **title**, **severity**, and **read** (0 = active, 1 = dismissed/read).
 */
@Entity(tableName = "alerts")
data class Alert(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val message: String,
    /** e.g. INFO, WARN, CRITICAL — matches migration default `INFO`. */
    val severity: String = "INFO",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,
    /** Stored as INTEGER 0/1; `false` = active (show in alerts list). */
    @ColumnInfo(name = "read")
    val read: Boolean = false,
    /** Prompt U5 — `LOSS_*` for dashboard loss cards; `LEGACY` for pre-U5 rows. */
    @ColumnInfo(name = "alert_type", defaultValue = "LEGACY")
    val alertType: String = "LEGACY",
    @ColumnInfo(name = "product_id")
    val productId: Long? = null,
    @ColumnInfo(name = "dedupe_key")
    val dedupeKey: String? = null,
    /** Gemma translation cache (FULL_AI); UI prefers this over [message] when set. */
    @ColumnInfo(name = "localized_message")
    val localizedMessage: String? = null,
    @ColumnInfo(name = "related_transaction_id")
    val relatedTransactionId: Long? = null,
)
