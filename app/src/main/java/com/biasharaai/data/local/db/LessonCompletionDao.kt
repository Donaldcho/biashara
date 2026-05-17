package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LessonCompletionDao {

    @Insert
    suspend fun insert(completion: LessonCompletion): Long

    @Query(
        """
        SELECT * FROM lesson_completions
        WHERE lesson_id = :lessonId
        ORDER BY completed_at DESC
        """,
    )
    suspend fun getByLesson(lessonId: String): List<LessonCompletion>

    @Query("SELECT COUNT(*) FROM lesson_completions WHERE lesson_id = :lessonId")
    suspend fun countCompletions(lessonId: String): Int

    @Query("SELECT AVG(score) FROM lesson_completions WHERE lesson_id = :lessonId")
    suspend fun averageScore(lessonId: String): Float?
}
