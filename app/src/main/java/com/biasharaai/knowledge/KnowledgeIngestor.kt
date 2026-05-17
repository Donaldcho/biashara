package com.biasharaai.knowledge

import android.content.Context
import com.biasharaai.ai.EmbeddingEngine
import com.biasharaai.data.local.db.KnowledgeChunk
import com.biasharaai.data.local.db.KnowledgeChunkDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads all `.md` files from `assets/knowledge/<lang>/`, parses them into [KnowledgeChunk]s,
 * optionally embeds each chunk with [EmbeddingEngine], and stores the results in Room via
 * [KnowledgeChunkDao].
 *
 * Call [ingestAll] once on first run (or after a language pack update). When the embedding
 * engine is not yet loaded, chunks are stored without blobs; call [embedPendingChunks] later
 * to back-fill embeddings after the model finishes downloading.
 */
@Singleton
class KnowledgeIngestor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chunkDao: KnowledgeChunkDao,
    private val embeddingEngine: EmbeddingEngine,
) {
    companion object {
        private const val KNOWLEDGE_ASSET_DIR = "knowledge"
        val SUPPORTED_LANGUAGES = listOf("en", "sw", "fr", "ar", "pt", "hi", "zh")
    }

    /**
     * Iterates all supported languages and ingests any that have no chunks yet.
     * Pass [forceReingest] = `true` to wipe and re-ingest all languages (e.g. after a content update).
     */
    suspend fun ingestAll(forceReingest: Boolean = false) = withContext(Dispatchers.IO) {
        if (!embeddingEngine.isLoaded) {
            embeddingEngine.initialize(context)
        }
        for (lang in SUPPORTED_LANGUAGES) {
            val existingCount = chunkDao.countByLanguage(lang)
            if (existingCount > 0 && !forceReingest) continue
            ingestLanguage(lang, forceReingest)
        }
    }

    /** Ingests (or re-ingests) all `.md` files under `assets/knowledge/[languageCode]/`. */
    suspend fun ingestLanguage(languageCode: String, forceReingest: Boolean = false) =
        withContext(Dispatchers.IO) {
            val assetDir = "$KNOWLEDGE_ASSET_DIR/$languageCode"
            val files = runCatching { context.assets.list(assetDir) }.getOrNull()
                ?: return@withContext
            for (fileName in files.filter { it.endsWith(".md") }) {
                val sourcePath = "$assetDir/$fileName"
                if (forceReingest) {
                    chunkDao.deleteBySourcePath(sourcePath)
                }
                ingestFile(sourcePath, languageCode)
            }
        }

    /** Back-fills embedding BLOBs for any chunks that were stored without one. */
    suspend fun embedPendingChunks() = withContext(Dispatchers.IO) {
        if (!embeddingEngine.isLoaded) return@withContext
        val unembedded = SUPPORTED_LANGUAGES
            .flatMap { lang -> chunkDao.getByLanguage(lang) }
            .filter { it.embeddingBlob == null }
        for (chunk in unembedded) {
            val vec = embeddingEngine.embed(chunk.contentText)
            chunkDao.updateEmbedding(chunk.id, embeddingEngine.serialize(vec))
        }
    }

    private suspend fun ingestFile(sourcePath: String, languageCode: String) {
        val content = runCatching {
            context.assets.open(sourcePath).bufferedReader().readText()
        }.getOrNull() ?: return

        val parsed = KnowledgeChunkParser.parse(content, sourcePath, languageCode)
        val chunks = parsed.map { pc ->
            val blob = if (embeddingEngine.isLoaded) {
                val vec = embeddingEngine.embed(pc.contentText)
                embeddingEngine.serialize(vec)
            } else {
                null
            }
            KnowledgeChunk(
                contentText = pc.contentText,
                sourcePath = pc.sourcePath,
                languageCode = pc.languageCode,
                chunkIndex = pc.chunkIndex,
                embeddingBlob = blob,
                createdAt = System.currentTimeMillis(),
            )
        }
        if (chunks.isNotEmpty()) chunkDao.insertAll(chunks)
    }
}
