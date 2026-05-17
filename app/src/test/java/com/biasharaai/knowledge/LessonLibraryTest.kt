package com.biasharaai.knowledge

import com.biasharaai.data.local.db.FeatureMastery
import com.biasharaai.data.local.db.MasteryLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LessonLibraryTest {

    private lateinit var library: LessonLibrary

    @Before
    fun setUp() {
        library = LessonLibrary()
    }

    @Test
    fun allFeatureIds_returnsNonEmptyList() {
        val ids = library.allFeatureIds()
        assertTrue(ids.isNotEmpty())
    }

    @Test
    fun allFeatureIds_containsKnownFeature() {
        assertTrue(library.allFeatureIds().contains("add_product"))
    }

    @Test
    fun firstLessonForFeature_knownFeature_returnsLesson() {
        val lesson = library.firstLessonForFeature("add_product", "en")
        assertNotNull(lesson)
        assertEquals("add_product", lesson!!.featureId)
    }

    @Test
    fun firstLessonForFeature_unknownFeature_returnsNull() {
        val lesson = library.firstLessonForFeature("nonexistent_feature_xyz", "en")
        assertNull(lesson)
    }

    @Test
    fun lessonById_knownId_returnsLesson() {
        val featureId = library.allFeatureIds().first()
        val first = library.firstLessonForFeature(featureId, "en") ?: return
        val found = library.lessonById(first.lessonId)
        assertNotNull(found)
        assertEquals(first.lessonId, found!!.lessonId)
    }

    @Test
    fun lessonById_unknownId_returnsNull() {
        assertNull(library.lessonById("totally_unknown_lesson_id"))
    }

    @Test
    fun nextLessonForMastery_undiscoveredLevel_returnsFirstLesson() {
        val mastery = FeatureMastery(
            featureId = "add_product",
            masteryLevel = MasteryLevel.UNDISCOVERED.name,
            firstSeenAt = 0L,
            lastPracticedAt = 0L,
        )
        val lesson = library.nextLessonForMastery(mastery, "en")
        assertNotNull(lesson)
    }

    @Test
    fun nextLessonForMastery_masteredLevel_returnsNull() {
        val mastery = FeatureMastery(
            featureId = "add_product",
            masteryLevel = MasteryLevel.MASTERED.name,
            firstSeenAt = 0L,
            lastPracticedAt = 0L,
        )
        val lesson = library.nextLessonForMastery(mastery, "en")
        assertNull(lesson)
    }

    @Test
    fun allLessons_haveAtLeastOneStep() {
        library.allFeatureIds().forEach { featureId ->
            val lessons = library.allLessonsForFeature(featureId, "en")
            lessons.forEach { lesson ->
                assertTrue(
                    "Lesson ${lesson.lessonId} must have at least one step",
                    lesson.steps.isNotEmpty(),
                )
            }
        }
    }

    @Test
    fun allLessons_stepNumbers_arePositive() {
        library.allFeatureIds().forEach { featureId ->
            library.allLessonsForFeature(featureId, "en").forEach { lesson ->
                lesson.steps.forEach { step ->
                    assertTrue("Step number must be ≥ 1", step.stepNumber >= 1)
                }
            }
        }
    }
}
