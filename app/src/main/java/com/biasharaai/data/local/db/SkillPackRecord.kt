package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Phase 6 — Installed skill pack (signed bundle), OTA in later prompts (X11).
 */
@Entity(tableName = "skill_pack_records")
data class SkillPackRecord(
    @PrimaryKey
    @ColumnInfo(name = "packId")
    val packId: String,
    @ColumnInfo(name = "packName")
    val packName: String,
    @ColumnInfo(name = "version")
    val version: String,
    @ColumnInfo(name = "installedAt")
    val installedAt: Long,
    @ColumnInfo(name = "isActive")
    val isActive: Boolean = true,
    @ColumnInfo(name = "signatureValid")
    val signatureValid: Boolean = true,
)
