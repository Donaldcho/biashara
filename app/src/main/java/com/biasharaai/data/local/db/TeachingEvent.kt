package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Records a single owner interaction with an app feature.
 *
 * [eventType] values: FEATURE_USED, LESSON_STARTED, LESSON_COMPLETED, HELP_REQUESTED
 * [outcome] values: SUCCESS, FAILURE, SKIPPED
 */
@Entity(
    tableName = "teaching_events",
    indices = [
        Index(value = ["feature_id"]),
        Index(value = ["created_at"]),
    ],
)
data class TeachingEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "feature_id") val featureId: String,
    @ColumnInfo(name = "skill_invoked") val skillInvoked: String? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0L,
    @ColumnInfo(name = "outcome") val outcome: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
