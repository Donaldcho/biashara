package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "feature_mastery",
    indices = [
        Index(value = ["mastery_level"]),
    ],
)
data class FeatureMastery(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "feature_id") val featureId: String,
    @ColumnInfo(name = "mastery_level") val masteryLevel: String = MasteryLevel.UNDISCOVERED.name,
    @ColumnInfo(name = "first_seen_at") val firstSeenAt: Long,
    @ColumnInfo(name = "last_practiced_at") val lastPracticedAt: Long,
    @ColumnInfo(name = "practice_count") val practiceCount: Int = 0,
)

enum class MasteryLevel {
    UNDISCOVERED,
    DISCOVERED,
    LEARNING,
    PROFICIENT,
    MASTERED,
}
