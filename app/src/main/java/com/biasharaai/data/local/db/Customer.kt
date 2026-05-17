package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Maps to the `customers` table created in migration 3→5 (Phase 2 baseline).
 */
@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val notes: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,

    /** Last POS visit / sale tied to this profile; **Prompt U2** (`last_visit` column). */
    @ColumnInfo(name = "last_visit")
    val lastVisit: Long = 0L,
)
