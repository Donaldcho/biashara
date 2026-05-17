package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lesson_completions",
    indices = [
        Index(value = ["lesson_id"]),
    ],
)
data class LessonCompletion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    @ColumnInfo(name = "completed_at") val completedAt: Long,
    @ColumnInfo(name = "score") val score: Float = 0f,
)
