package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Phase 6 — Skills Engine: metadata for a registered tool/skill (built-in or from a pack).
 */
@Entity(tableName = "skill_descriptors")
data class SkillDescriptor(
    @PrimaryKey
    @ColumnInfo(name = "skillId")
    val skillId: String,
    @ColumnInfo(name = "displayName")
    val displayName: String,
    @ColumnInfo(name = "schemaJson")
    val schemaJson: String,
    @ColumnInfo(name = "isEnabled")
    val isEnabled: Boolean = true,
    @ColumnInfo(name = "packId")
    val packId: String? = null,
    @ColumnInfo(name = "lastExecutedAt")
    val lastExecutedAt: Long? = null,
    @ColumnInfo(name = "executionCount")
    val executionCount: Long = 0L,
)
