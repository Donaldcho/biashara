package com.biasharaai.knowledge

import com.biasharaai.data.local.db.FeatureMastery
import com.biasharaai.data.local.db.MasteryLevel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TeachingEngineTest {

    private lateinit var masteryRepo: FeatureMasteryRepository
    private lateinit var lessonLibrary: LessonLibrary
    private lateinit var behaviourTracker: OwnerBehaviourTracker
    private lateinit var engine: TeachingEngine

    private fun mastery(featureId: String, level: MasteryLevel, practiceCount: Int = 0) =
        FeatureMastery(
            featureId = featureId,
            masteryLevel = level.name,
            firstSeenAt = 0L,
            lastPracticedAt = 0L,
            practiceCount = practiceCount,
        )

    @Before
    fun setUp() {
        masteryRepo = mockk()
        lessonLibrary = LessonLibrary()
        behaviourTracker = mockk(relaxed = true)
        engine = TeachingEngine(masteryRepo, lessonLibrary, behaviourTracker)
    }

    @Test
    fun nextSuggestion_allFeaturesMastered_returnsNull() = runTest {
        val allIds = lessonLibrary.allFeatureIds()
        val masteredAll = allIds.map { mastery(it, MasteryLevel.MASTERED) }
        coEvery { masteryRepo.getUndiscoveredFeatures(allIds) } returns emptyList()
        coEvery { masteryRepo.getLeastMastered(any()) } returns masteredAll.take(3)
        val suggestion = engine.nextSuggestion("en")
        assertNull(suggestion)
    }

    @Test
    fun nextSuggestion_undiscoveredExists_returnsUndiscoveredFirst() = runTest {
        val allIds = lessonLibrary.allFeatureIds()
        coEvery { masteryRepo.getUndiscoveredFeatures(allIds) } returns listOf("pos_sale")
        val suggestion = engine.nextSuggestion("en")
        assertNotNull(suggestion)
        assertEquals("pos_sale", suggestion!!.featureId)
        assertEquals(1, suggestion.priority)
    }

    @Test
    fun nextSuggestion_noUndiscovered_returnsleastMastered() = runTest {
        val allIds = lessonLibrary.allFeatureIds()
        coEvery { masteryRepo.getUndiscoveredFeatures(allIds) } returns emptyList()
        coEvery { masteryRepo.getLeastMastered(3) } returns listOf(
            mastery("customers", MasteryLevel.DISCOVERED, practiceCount = 1),
        )
        val suggestion = engine.nextSuggestion("en")
        assertNotNull(suggestion)
        assertEquals("customers", suggestion!!.featureId)
        assertEquals(2, suggestion.priority)
    }

    @Test
    fun nextSuggestion_reason_isNonEmpty() = runTest {
        val allIds = lessonLibrary.allFeatureIds()
        coEvery { masteryRepo.getUndiscoveredFeatures(allIds) } returns listOf("chat")
        val suggestion = engine.nextSuggestion("en")
        assertNotNull(suggestion)
        assertTrue("Reason must be non-blank", suggestion!!.reason.isNotBlank())
    }
}
