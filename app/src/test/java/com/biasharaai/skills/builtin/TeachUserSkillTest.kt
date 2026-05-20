package com.biasharaai.skills.builtin

import com.biasharaai.knowledge.LessonLibrary
import com.biasharaai.knowledge.TeachingEngine
import com.biasharaai.knowledge.TeachingSuggestion
import com.biasharaai.skills.SkillResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TeachUserSkillTest {

    private val gson = Gson()

    private fun SkillResult.Success.outputMap(): Map<String, Any?> {
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return gson.fromJson(outputJson, type)
    }

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
        assertEquals("INVALID_ARGS", (result as SkillResult.Failure).code)
    }

    @Test
    fun execute_knownFeatureId_returnsLessonSteps() = runTest {
        val result = skill.execute("""{"featureId":"add_product","languageCode":"en"}""")
        assertTrue(result is SkillResult.Success)
        val map = (result as SkillResult.Success).outputMap()
        assertEquals("add_product", map["featureId"])
        assertTrue((map["stepCount"] as Number).toInt() > 0)
    }

    @Test
    fun execute_unknownFeatureId_returnsNoLessonFailure() = runTest {
        val result = skill.execute("""{"featureId":"totally_unknown_xyz"}""")
        assertTrue(result is SkillResult.Failure)
        assertEquals("NO_LESSON", (result as SkillResult.Failure).code)
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
        val map = (result as SkillResult.Success).outputMap()
        assertEquals("pos_sale", map["featureId"])
    }

    @Test
    fun execute_noFeatureId_allMastered_returnsAllMasteredSuccess() = runTest {
        coEvery { teachingEngine.nextSuggestion("en") } returns null
        val result = skill.execute("""{}""")
        assertTrue(result is SkillResult.Success)
        val map = (result as SkillResult.Success).outputMap()
        assertEquals(true, map["allMastered"])
    }

    @Test
    fun execute_stepsContainRequiredFields() = runTest {
        val result = skill.execute("""{"featureId":"add_product"}""")
        assertTrue(result is SkillResult.Success)
        val map = (result as SkillResult.Success).outputMap()
        @Suppress("UNCHECKED_CAST")
        val steps = map["steps"] as List<Map<*, *>>
        val firstStep = steps.first()
        assertTrue(firstStep.containsKey("step"))
        assertTrue(firstStep.containsKey("instruction"))
        assertTrue(firstStep.containsKey("actionType"))
    }
}
