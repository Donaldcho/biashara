package com.biasharaai.skills.builtin

import com.biasharaai.data.local.db.KnowledgeChunk
import com.biasharaai.knowledge.KnowledgeRetriever
import com.biasharaai.knowledge.RetrievedChunk
import com.biasharaai.skills.SkillResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QueryAppKnowledgeSkillTest {

    private val gson = Gson()

    private fun SkillResult.Success.outputMap(): Map<String, Any?> {
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return gson.fromJson(outputJson, type)
    }

    private lateinit var retriever: KnowledgeRetriever
    private lateinit var skill: QueryAppKnowledgeSkill

    private fun fakeChunk(id: Long, text: String) = KnowledgeChunk(
        id = id,
        contentText = text,
        sourcePath = "knowledge/en/test.md",
        languageCode = "en",
        chunkIndex = 0,
        embeddingBlob = null,
        createdAt = 0L,
    )

    @Before
    fun setUp() {
        retriever = mockk()
        skill = QueryAppKnowledgeSkill(retriever)
    }

    @Test
    fun execute_missingQuery_returnsFailure() = runTest {
        val result = skill.execute("{}")
        assertTrue(result is SkillResult.Failure)
        assertEquals("MISSING_QUERY", (result as SkillResult.Failure).code)
    }

    @Test
    fun execute_invalidJson_returnsFailure() = runTest {
        val result = skill.execute("not json")
        assertTrue(result is SkillResult.Failure)
        assertEquals("INVALID_ARGS", (result as SkillResult.Failure).code)
    }

    @Test
    fun execute_noResultsFound_returnsSuccessWithFoundFalse() = runTest {
        coEvery { retriever.retrieve(any(), any(), any()) } returns emptyList()
        val result = skill.execute("""{"query":"how do I add a product"}""")
        assertTrue(result is SkillResult.Success)
        val map = (result as SkillResult.Success).outputMap()
        assertEquals(false, map["found"])
        assertEquals(0, (map["chunkCount"] as Number).toInt())
    }

    @Test
    fun execute_withResults_returnsContextAndCount() = runTest {
        val chunks = listOf(
            RetrievedChunk(fakeChunk(1, "Tap Inventory then the + button to add a product."), 0.85f),
            RetrievedChunk(fakeChunk(2, "Fill in the name, price and stock fields then Save."), 0.70f),
        )
        coEvery { retriever.retrieve(any(), any(), any()) } returns chunks
        every { retriever.buildContext(any(), any()) } returns "Combined context text."
        val result = skill.execute("""{"query":"how to add a product","languageCode":"en","topK":5}""")
        assertTrue(result is SkillResult.Success)
        val map = (result as SkillResult.Success).outputMap()
        assertEquals(true, map["found"])
        assertEquals(2, (map["chunkCount"] as Number).toInt())
    }

    @Test
    fun execute_topK_clampedToAllowed() = runTest {
        coEvery { retriever.retrieve(any(), any(), topK = 10) } returns emptyList()
        // topK = 10 is within [1, 10] range — should pass through
        val result = skill.execute("""{"query":"test","topK":10}""")
        assertTrue(result is SkillResult.Success)
    }
}
