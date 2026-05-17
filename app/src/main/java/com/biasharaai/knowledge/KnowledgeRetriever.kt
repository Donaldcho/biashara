package com.biasharaai.knowledge

import com.biasharaai.ai.EmbeddingEngine
import com.biasharaai.ai.VectorMath
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
) {
    companion object {
        private const val DEFAULT_TOP_K = 5
        private const val MIN_SCORE = 0.20f
    }

    /**
     * Returns the top-k knowledge chunks most semantically similar to [query].
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
        val candidates = chunkDao.getAllWithEmbeddings()
            .filter { it.languageCode == languageCode && it.embeddingBlob != null }

        return candidates
            .mapNotNull { chunk ->
                val vec = embeddingEngine.deserialize(chunk.embeddingBlob!!)
                val score = VectorMath.cosineSimilarity(queryVec, vec)
                if (score >= minScore) RetrievedChunk(chunk, score) else null
            }
            .sortedByDescending { it.score }
            .take(topK)
    }

    // Fallback: token overlap score (TF-like) when model not loaded
    private suspend fun retrieveKeyword(
        query: String,
        languageCode: String,
        topK: Int,
    ): List<RetrievedChunk> {
        val queryTokens = query.lowercase().split(Regex("\\s+")).toSet()
        val chunks = chunkDao.getByLanguage(languageCode)
        return chunks
            .map { chunk ->
                val chunkTokens = chunk.contentText.lowercase().split(Regex("\\s+")).toSet()
                val overlap = queryTokens.intersect(chunkTokens).size.toFloat()
                val score = if (chunkTokens.isEmpty()) 0f else overlap / chunkTokens.size.toFloat()
                RetrievedChunk(chunk, score)
            }
            .filter { it.score > 0f }
            .sortedByDescending { it.score }
            .take(topK)
    }

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
