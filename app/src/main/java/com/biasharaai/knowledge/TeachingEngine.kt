package com.biasharaai.knowledge

import com.biasharaai.data.local.db.FeatureMastery
import com.biasharaai.data.local.db.MasteryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class TeachingSuggestion(
    val featureId: String,
    val lessonId: String,
    val reason: String,
    val priority: Int, // 1 = highest
)

@Singleton
class TeachingEngine @Inject constructor(
    private val masteryRepository: FeatureMasteryRepository,
    private val lessonLibrary: LessonLibrary,
    private val behaviourTracker: OwnerBehaviourTracker,
) {
    /**
     * Returns the next lesson the user should see, or null if all features are mastered.
     * Priority: undiscovered features first, then least-practiced non-mastered.
     */
    suspend fun nextSuggestion(languageCode: String = "en"): TeachingSuggestion? =
        withContext(Dispatchers.IO) {
            val allFeatureIds = lessonLibrary.allFeatureIds()

            // 1. Features never seen
            val undiscovered = masteryRepository.getUndiscoveredFeatures(allFeatureIds)
            if (undiscovered.isNotEmpty()) {
                val featureId = undiscovered.first()
                val lesson = lessonLibrary.firstLessonForFeature(featureId, languageCode) ?: return@withContext null
                return@withContext TeachingSuggestion(
                    featureId = featureId,
                    lessonId = lesson.lessonId,
                    reason = "New feature to explore",
                    priority = 1,
                )
            }

            // 2. Least-practiced non-mastered
            val leastMastered = masteryRepository.getLeastMastered(limit = 3)
            for (mastery in leastMastered) {
                val lesson = lessonLibrary.nextLessonForMastery(mastery, languageCode) ?: continue
                return@withContext TeachingSuggestion(
                    featureId = mastery.featureId,
                    lessonId = lesson.lessonId,
                    reason = masteryReason(mastery),
                    priority = 2,
                )
            }
            null
        }

    suspend fun markSuggestionShown(featureId: String) {
        behaviourTracker.recordFeatureUsed(featureId, success = false)
    }

    private fun masteryReason(mastery: FeatureMastery): String {
        val level = runCatching { MasteryLevel.valueOf(mastery.masteryLevel) }
            .getOrDefault(MasteryLevel.DISCOVERED)
        return when (level) {
            MasteryLevel.DISCOVERED -> "You've discovered this — try it out!"
            MasteryLevel.LEARNING -> "Keep practising to improve"
            MasteryLevel.PROFICIENT -> "Almost mastered — one more session!"
            else -> "Continue learning"
        }
    }
}
