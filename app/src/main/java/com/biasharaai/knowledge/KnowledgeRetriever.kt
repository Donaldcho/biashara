package com.biasharaai.knowledge

import com.biasharaai.ai.EmbeddingEngine
import com.biasharaai.ai.VectorMath
import com.biasharaai.data.local.db.BusinessMemoryEntry
import com.biasharaai.data.local.db.BusinessMemoryEntryDao
import com.biasharaai.data.local.db.KnowledgeChunk
import com.biasharaai.data.local.db.KnowledgeChunkDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class RetrievedChunk(
    val chunk: KnowledgeChunk,
    val score: Float,
)

@Singleton
class KnowledgeRetriever @Inject constructor(
    private val chunkDao: KnowledgeChunkDao,
    private val embeddingEngine: EmbeddingEngine,
    private val memoryDao: BusinessMemoryEntryDao,
) {
    companion object {
        private const val DEFAULT_TOP_K = 5
        private const val MIN_SCORE = 0.20f
    }

    /**
     * Returns the top-k knowledge chunks most semantically similar to [query].
     * Results include both static knowledge chunks and live business memory entries.
     * Falls back to keyword search when the embedding engine is not loaded.
     */
    suspend fun retrieve(
        query: String,
        languageCode: String = "en",
        topK: Int = DEFAULT_TOP_K,
        minScore: Float = MIN_SCORE,
    ): List<RetrievedChunk> = withContext(Dispatchers.IO) {
        if (embeddingEngine.isLoaded) {
            retrieveSemantic(query, languageCode, topK, minScore)
        } else {
            retrieveKeyword(query, languageCode, topK)
        }
    }

    private suspend fun retrieveSemantic(
        query: String,
        languageCode: String,
        topK: Int,
        minScore: Float,
    ): List<RetrievedChunk> {
        val queryVec = embeddingEngine.embed(query)

        val knowledgeResults = chunkDao.getAllWithEmbeddings()
            .filter { it.languageCode == languageCode && it.embeddingBlob != null }
            .mapNotNull { chunk ->
                val score = VectorMath.cosineSimilarity(queryVec, embeddingEngine.deserialize(chunk.embeddingBlob!!))
                if (score >= minScore) RetrievedChunk(chunk, score) else null
            }

        val memoryResults = memoryDao.getActiveWithEmbeddings(System.currentTimeMillis())
            .mapNotNull { entry ->
                val score = VectorMath.cosineSimilarity(queryVec, embeddingEngine.deserialize(entry.embeddingBlob!!))
                if (score >= minScore) RetrievedChunk(entry.toVirtualChunk(), score) else null
            }

        return (knowledgeResults + memoryResults)
            .sortedByDescending { it.score }
            .take(topK)
    }

    // Fallback: token overlap score (TF-like) when model not loaded
    private suspend fun retrieveKeyword(
        query: String,
        languageCode: String,
        topK: Int,
    ): List<RetrievedChunk> {
        val queryTokens = tokenize(query)

        val knowledgeResults = chunkDao.getByLanguage(languageCode)
            .map { chunk ->
                val chunkTokens = tokenize(chunk.contentText + " " + chunk.sourcePath)
                val overlap = queryTokens.intersect(chunkTokens).size.toFloat()
                val score = if (queryTokens.isEmpty()) 0f else overlap / queryTokens.size.toFloat()
                RetrievedChunk(chunk, score)
            }
            .filter { it.score > 0f }

        val memoryResults = memoryDao.getActive(System.currentTimeMillis())
            .map { entry ->
                val entryTokens = tokenize(entry.keywords + " " + entry.content)
                val overlap = queryTokens.intersect(entryTokens).size.toFloat()
                val score = if (queryTokens.isEmpty()) 0f else overlap / queryTokens.size.toFloat()
                RetrievedChunk(entry.toVirtualChunk(), score)
            }
            .filter { it.score > 0f }

        return (knowledgeResults + memoryResults)
            .sortedByDescending { it.score }
            .take(topK)
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 2 }
            .flatMap { token ->
                sequenceOf(
                    token,
                    token.removeSuffix("ing"),
                    token.removeSuffix("ed"),
                    token.removeSuffix("es"),
                    token.removeSuffix("s"),
                )
            }
            .filter { it.length >= 2 }
            .toSet()

    private fun BusinessMemoryEntry.toVirtualChunk() = KnowledgeChunk(
        id = -id,
        contentText = content,
        sourcePath = "memory:$type",
        languageCode = "en",
        chunkIndex = 0,
        embeddingBlob = embeddingBlob,
        createdAt = createdAt,
    )

    /** Builds a context string from retrieved chunks for injection into an LLM prompt. */
    fun buildContext(chunks: List<RetrievedChunk>, maxChars: Int = 2000): String {
        val sb = StringBuilder()
        for ((chunk, _) in chunks) {
            val addition = chunk.contentText + "\n\n"
            if (sb.length + addition.length > maxChars) break
            sb.append(addition)
        }
        return sb.toString().trim()
    }
}
