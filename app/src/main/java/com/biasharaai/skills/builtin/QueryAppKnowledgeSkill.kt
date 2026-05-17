package com.biasharaai.skills.builtin

import com.biasharaai.knowledge.KnowledgeRetriever
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillArgsParser
import com.biasharaai.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * K4 — Answers questions about how to use the BiasharaAI app by retrieving
 * relevant knowledge-base chunks and returning a context-enriched answer.
 */
@Singleton
class QueryAppKnowledgeSkill @Inject constructor(
    private val retriever: KnowledgeRetriever,
) : BiasharaSkill {

    override val id: String = ID
    override val displayName: String = "Query app knowledge"
    override val parameterSchemaJson: String = """
        {
          "type": "object",
          "properties": {
            "query": {"type": "string", "description": "The user's question about how to use the app"},
            "languageCode": {"type": "string", "description": "BCP-47 language code, e.g. 'en' or 'sw'", "default": "en"},
            "topK": {"type": "integer", "minimum": 1, "maximum": 10, "default": 5}
          },
          "required": ["query"]
        }
    """.trimIndent()

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        val args = SkillArgsParser.parseObject(argumentsJson).getOrElse {
            return@withContext SkillResult.Failure("INVALID_ARGS", it.message ?: "Invalid JSON")
        }
        val query = SkillArgsParser.stringArg(args, "query")
            ?: return@withContext SkillResult.Failure("MISSING_QUERY", "query argument is required")
        val lang = SkillArgsParser.stringArg(args, "languageCode") ?: "en"
        val topK = SkillArgsParser.intArg(args, "topK", default = 5, min = 1, max = 10)

        val results = retriever.retrieve(query = query, languageCode = lang, topK = topK)
        if (results.isEmpty()) {
            return@withContext SkillResult.successMap(
                mapOf("found" to false, "context" to "", "chunkCount" to 0),
                summary = "No knowledge found for: $query",
            )
        }

        val context = retriever.buildContext(results)
        SkillResult.successMap(
            mapOf(
                "found" to true,
                "context" to context,
                "chunkCount" to results.size,
                "topScore" to results.first().score,
                "sources" to results.map { it.chunk.sourcePath }.distinct(),
            ),
            summary = "Found ${results.size} relevant chunks (top score: ${"%.2f".format(results.first().score)})",
        )
    }

    companion object {
        const val ID = "query_app_knowledge"
    }
}
