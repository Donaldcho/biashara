package com.biasharaai.knowledge

import com.biasharaai.ai.EmbeddingEngine
import com.biasharaai.data.local.db.BusinessMemoryEntryDao
import com.biasharaai.data.local.db.KnowledgeChunk
import com.biasharaai.data.local.db.KnowledgeChunkDao
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KnowledgeRetrieverTest {

    private lateinit var chunkDao: KnowledgeChunkDao
    private lateinit var memoryDao: BusinessMemoryEntryDao
    private lateinit var embeddingEngine: EmbeddingEngine
    private lateinit var retriever: KnowledgeRetriever

    private val now = System.currentTimeMillis()

    private fun makeChunk(id: Long, text: String, lang: String = "en") = KnowledgeChunk(
        id = id,
        contentText = text,
        sourcePath = "knowledge/$lang/test.md",
        languageCode = lang,
        chunkIndex = 0,
        embeddingBlob = null,
        createdAt = now,
    )

    @Before
    fun setUp() {
        chunkDao = mockk()
        memoryDao = mockk()
        embeddingEngine = mockk()
        every { embeddingEngine.isLoaded } returns false
        coEvery { memoryDao.getActive(any()) } returns emptyList()
        retriever = KnowledgeRetriever(chunkDao, embeddingEngine, memoryDao)
    }

    @Test
    fun retrieve_keywordFallback_matchingTokens_returnsResults() = runTest {
        coEvery { chunkDao.getByLanguage("en") } returns listOf(
            makeChunk(1, "Add a product to inventory with name and price"),
            makeChunk(2, "View transaction history from the sales screen"),
        )
        val results = retriever.retrieve("add product", "en", topK = 5)
        assertTrue(results.isNotEmpty())
        assertEquals(1L, results.first().chunk.id)
    }

    @Test
    fun retrieve_keywordFallback_noMatch_returnsEmpty() = runTest {
        coEvery { chunkDao.getByLanguage("en") } returns listOf(
            makeChunk(1, "Add a product to inventory with name and price"),
        )
        val results = retriever.retrieve("bluetooth thermal printer pairing", "en", topK = 5)
        // "bluetooth" "thermal" "printer" "pairing" not present → score 0 → filtered out
        assertTrue(results.isEmpty())
    }

    @Test
    fun retrieve_respectsTopK() = runTest {
        val chunks = (1..10L).map { makeChunk(it, "keyword product inventory sale stock $it") }
        coEvery { chunkDao.getByLanguage("en") } returns chunks
        val results = retriever.retrieve("product", "en", topK = 3)
        assertTrue(results.size <= 3)
    }

    @Test
    fun retrieve_filtersToRequestedLanguage() = runTest {
        coEvery { chunkDao.getByLanguage("sw") } returns listOf(
            makeChunk(10, "Ongeza bidhaa kwenye hesabu ya bidhaa", "sw"),
        )
        val results = retriever.retrieve("bidhaa", "sw", topK = 5)
        results.forEach { assertEquals("sw", it.chunk.languageCode) }
    }

    @Test
    fun buildContext_concatenatesChunks_upToMaxChars() {
        val chunks = listOf(
            RetrievedChunk(makeChunk(1, "First chunk text about products."), 0.9f),
            RetrievedChunk(makeChunk(2, "Second chunk text about sales."), 0.8f),
        )
        val context = retriever.buildContext(chunks, maxChars = 2000)
        assertTrue(context.contains("First chunk"))
        assertTrue(context.contains("Second chunk"))
    }

    @Test
    fun buildContext_stopsBeforeExceedingMaxChars() {
        val longText = "X".repeat(600)
        val chunks = (1..5).map { i ->
            RetrievedChunk(makeChunk(i.toLong(), longText), 1f - i * 0.1f)
        }
        val context = retriever.buildContext(chunks, maxChars = 1000)
        assertTrue(context.length <= 1200) // some slack for separators
    }
}
