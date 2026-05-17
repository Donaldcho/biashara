package com.biasharaai.knowledge

import com.biasharaai.data.local.db.LessonCompletion
import com.biasharaai.data.local.db.LessonCompletionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class LessonSession(
    val lesson: MicroLesson,
    val currentStepIndex: Int = 0,
) {
    val currentStep: LessonStep? get() = lesson.steps.getOrNull(currentStepIndex)
    val isComplete: Boolean get() = currentStepIndex >= lesson.steps.size
    val progress: Float get() = if (lesson.steps.isEmpty()) 1f else currentStepIndex.toFloat() / lesson.steps.size
}

@Singleton
class LessonRunner @Inject constructor(
    private val lessonLibrary: LessonLibrary,
    private val completionDao: LessonCompletionDao,
    private val behaviourTracker: OwnerBehaviourTracker,
) {
    fun startLesson(lessonId: String): LessonSession? {
        val lesson = lessonLibrary.lessonById(lessonId) ?: return null
        return LessonSession(lesson = lesson, currentStepIndex = 0)
    }

    fun advance(session: LessonSession): LessonSession =
        session.copy(currentStepIndex = session.currentStepIndex + 1)

    suspend fun completeLesson(session: LessonSession, score: Float = 1f) = withContext(Dispatchers.IO) {
        completionDao.insert(
            LessonCompletion(
                lessonId = session.lesson.lessonId,
                completedAt = System.currentTimeMillis(),
                score = score,
            ),
        )
        behaviourTracker.recordLessonCompleted(
            featureId = session.lesson.featureId,
            lessonId = session.lesson.lessonId,
            score = score,
        )
    }

    suspend fun hasCompleted(lessonId: String): Boolean = withContext(Dispatchers.IO) {
        completionDao.countCompletions(lessonId) > 0
    }

    suspend fun completionCount(lessonId: String): Int = withContext(Dispatchers.IO) {
        completionDao.countCompletions(lessonId)
    }
}
