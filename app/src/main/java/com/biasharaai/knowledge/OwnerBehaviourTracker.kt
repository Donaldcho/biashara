package com.biasharaai.knowledge

import com.biasharaai.data.local.db.FeatureMastery
import com.biasharaai.data.local.db.FeatureMasteryDao
import com.biasharaai.data.local.db.MasteryLevel
import com.biasharaai.data.local.db.TeachingEvent
import com.biasharaai.data.local.db.TeachingEventDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Records owner interactions with app features for mastery tracking. */
@Singleton
class OwnerBehaviourTracker @Inject constructor(
    private val teachingEventDao: TeachingEventDao,
    private val featureMasteryDao: FeatureMasteryDao,
) {
    suspend fun recordFeatureUsed(
        featureId: String,
        skillInvoked: String? = null,
        durationMs: Long = 0,
        success: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        teachingEventDao.insert(
            TeachingEvent(
                eventType = TeachingEventType.FEATURE_USED,
                featureId = featureId,
                skillInvoked = skillInvoked,
                durationMs = durationMs,
                outcome = if (success) "SUCCESS" else "FAILURE",
                createdAt = now,
            ),
        )
        touchMastery(featureId, now, success)
    }

    suspend fun recordLessonStarted(featureId: String, lessonId: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        teachingEventDao.insert(
            TeachingEvent(
                eventType = TeachingEventType.LESSON_STARTED,
                featureId = featureId,
                skillInvoked = lessonId,
                outcome = "SUCCESS",
                createdAt = now,
            ),
        )
        touchMastery(featureId, now, success = false)
    }

    suspend fun recordLessonCompleted(featureId: String, lessonId: String, score: Float) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            teachingEventDao.insert(
                TeachingEvent(
                    eventType = TeachingEventType.LESSON_COMPLETED,
                    featureId = featureId,
                    skillInvoked = lessonId,
                    outcome = "SUCCESS",
                    createdAt = now,
                ),
            )
            touchMastery(featureId, now, success = true)
        }

    suspend fun recordHelpRequested(featureId: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        teachingEventDao.insert(
            TeachingEvent(
                eventType = TeachingEventType.HELP_REQUESTED,
                featureId = featureId,
                outcome = "SUCCESS",
                createdAt = now,
            ),
        )
        touchMastery(featureId, now, success = false)
    }

    private suspend fun touchMastery(featureId: String, now: Long, success: Boolean) {
        val existing = featureMasteryDao.getByFeature(featureId)
        if (existing == null) {
            featureMasteryDao.upsert(
                FeatureMastery(
                    featureId = featureId,
                    masteryLevel = MasteryLevel.DISCOVERED.name,
                    firstSeenAt = now,
                    lastPracticedAt = now,
                    practiceCount = 1,
                ),
            )
        } else {
            val newCount = existing.practiceCount + (if (success) 1 else 0)
            val newLevel = computeLevel(newCount, existing)
            featureMasteryDao.updateMastery(featureId, newLevel.name, now)
        }
    }

    private fun computeLevel(successCount: Int, existing: FeatureMastery): MasteryLevel {
        val currentLevel = runCatching { MasteryLevel.valueOf(existing.masteryLevel) }
            .getOrDefault(MasteryLevel.DISCOVERED)
        return when {
            successCount >= 20 -> MasteryLevel.MASTERED
            successCount >= 10 -> MasteryLevel.PROFICIENT
            successCount >= 3 -> MasteryLevel.LEARNING
            successCount >= 1 -> MasteryLevel.DISCOVERED
            else -> currentLevel
        }
    }
}

object TeachingEventType {
    const val FEATURE_USED = "FEATURE_USED"
    const val LESSON_STARTED = "LESSON_STARTED"
    const val LESSON_COMPLETED = "LESSON_COMPLETED"
    const val HELP_REQUESTED = "HELP_REQUESTED"
}
