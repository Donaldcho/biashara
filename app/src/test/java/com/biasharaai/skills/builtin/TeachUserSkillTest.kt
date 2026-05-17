package com.biasharaai.skills.builtin

import com.biasharaai.knowledge.LessonLibrary
import com.biasharaai.knowledge.TeachingEngine
import com.biasharaai.knowledge.TeachingSuggestion
import com.biasharaai.skills.SkillResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TeachUserSkillTest {

    private lateinit var teachingEngine: TeachingEngine
    private lateinit var lessonLibrary: LessonLibrary
    private lateinit var skill: TeachUserSkill

    @Before
    fun setUp() {
        teachingEngine = mockk()
        lessonLibrary = LessonLibrary()
        skill = TeachUserSkill(teachingEngine, lessonLibrary)
    }

    @Test
    fun execute_invalidJson_returnsFailure() = runTest {
        val result = skill.execute("not-json")
        assertTrue(result is SkillResult.Failure)
        assertEquals("INVALID_ARGS", (result as SkillResult.Failure).errorCode)
    }

    @Test
    fun execute_knownFeatureId_returnsLessonSteps() = runTest {
        val result = skill.execute("""{"featureId":"add_product","languageCode":"en"}""")
        assertTrue(result is SkillResult.Success)
        val map = (result as SkillResult.Success).data as Map<*, *>
        assertEquals("add_product", map["featureId"])
        assertTrue((map["stepCount"] as Int) > 0)
    }

    @Test
    fun execute_unknownFeatureId_returnsNoLessonFailure() = runTest {
        val result = skill.execute("""{"featureId":"totally_unknown_xyz"}""")
        assertTrue(result is SkillResult.Failure)
        assertEquals("NO_LESSON", (result as SkillResult.Failure).errorCode)
    }

    @Test
    fun execute_noFeatureId_withSuggestion_returnsLesson() = runTest {
        coEvery { teachingEngine.nextSuggestion("en") } returns TeachingSuggestion(
            featureId = "pos_sale",
            lessonId = "pos_sale_basics",
            reason = "New feature to explore",
            priority = 1,
        )
        val result = skill.execute("""{}""")
        assertTrue(result is SkillResult.Success)
        val map = (result as SkillResult.Success).data as Map<*, *>
        assertEquals("pos_sale", map["featureId"])
    }

    @Test
    fun execute_noFeatureId_allMastered_returnsAllMasteredSuccess() = runTest {
        coEvery { teachingEngine.nextSuggestion("en") } returns null
        val result = skill.execute("""{}""")
        assertTrue(result is SkillResult.Success)
        val map = (result as SkillResult.Success).data as Map<*, *>
        assertEquals(true, map["allMastered"])
    }

    @Test
    fun execute_stepsContainRequiredFields() = runTest {
        val result = skill.execute("""{"featureId":"add_product"}""")
        assertTrue(result is SkillResult.Success)
        val map = (result as SkillResult.Success).data as Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val steps = map["steps"] as List<Map<*, *>>
        val firstStep = steps.first()
        assertTrue(firstStep.containsKey("step"))
        assertTrue(firstStep.containsKey("instruction"))
        assertTrue(firstStep.containsKey("actionType"))
    }
}
