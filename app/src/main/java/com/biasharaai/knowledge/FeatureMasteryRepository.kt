package com.biasharaai.knowledge

import com.biasharaai.data.local.db.FeatureMastery
import com.biasharaai.data.local.db.FeatureMasteryDao
import com.biasharaai.data.local.db.MasteryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeatureMasteryRepository @Inject constructor(
    private val featureMasteryDao: FeatureMasteryDao,
) {
    suspend fun getMastery(featureId: String): FeatureMastery? =
        withContext(Dispatchers.IO) { featureMasteryDao.getByFeature(featureId) }

    suspend fun getMasteryLevel(featureId: String): MasteryLevel =
        withContext(Dispatchers.IO) {
            val mastery = featureMasteryDao.getByFeature(featureId)
            mastery?.let { runCatching { MasteryLevel.valueOf(it.masteryLevel) }.getOrNull() }
                ?: MasteryLevel.UNDISCOVERED
        }

    suspend fun getAllMasteries(): List<FeatureMastery> =
        withContext(Dispatchers.IO) { featureMasteryDao.getAll() }

    /** Features the user has not mastered, ordered from least-practiced first. */
    suspend fun getLeastMastered(limit: Int = 5): List<FeatureMastery> =
        withContext(Dispatchers.IO) { featureMasteryDao.getLeastMastered(limit) }

    suspend fun getUndiscoveredFeatures(knownFeatureIds: List<String>): List<String> =
        withContext(Dispatchers.IO) {
            val discovered = featureMasteryDao.getAll().map { it.featureId }.toSet()
            knownFeatureIds.filter { it !in discovered }
        }

    suspend fun masteredCount(): Int =
        withContext(Dispatchers.IO) {
            featureMasteryDao.getAll().count { it.masteryLevel == MasteryLevel.MASTERED.name }
        }

    suspend fun overallProgress(): Float =
        withContext(Dispatchers.IO) {
            val all = featureMasteryDao.getAll()
            if (all.isEmpty()) return@withContext 0f
            val totalPoints = all.sumOf { mastery ->
                when (runCatching { MasteryLevel.valueOf(mastery.masteryLevel) }.getOrDefault(MasteryLevel.UNDISCOVERED)) {
                    MasteryLevel.UNDISCOVERED -> 0
                    MasteryLevel.DISCOVERED -> 1
                    MasteryLevel.LEARNING -> 2
                    MasteryLevel.PROFICIENT -> 3
                    MasteryLevel.MASTERED -> 4
                }
            }
            totalPoints.toFloat() / (all.size * 4)
        }
}
