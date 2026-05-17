package com.biasharaai.ai

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * After ML Kit reads a product label, proposes [description] and [category] via Gemma — JSON only.
 */
@Singleton
class LabelProductEnricher @Inject constructor(
    private val gemmaService: GemmaService,
) {

    data class Enrichment(
        val description: String?,
        val category: String?,
    )

    suspend fun enrich(productName: String, fullOcrText: String): Enrichment =
        withContext(Dispatchers.IO) {
            if (!gemmaService.isAvailable) return@withContext Enrichment(null, null)
            val name = productName.trim().ifBlank { "Unknown product" }
            val ocr = fullOcrText.trim().ifBlank { name }
            val prompt = """
You help a small African shop digitize product labels from OCR text.
Return ONLY a valid JSON object. No markdown, no explanation.
Keys: "description" (string, one short sentence for inventory — what the product is, size, or brand; infer from the label text), "category" (string, one short English retail shelf category, e.g. Snacks, Beverages, Cleaning).
If you cannot infer a value, use null for that key.
Preferred product name: $name
Full label OCR text:
$ocr
            """.trimIndent()

            val raw = runCatching { gemmaService.generateResponse(prompt).trim() }.getOrNull()
                ?: return@withContext Enrichment(null, null)
            val json = extractJsonObjectPayload(raw)
            return@withContext try {
                val row = Gson().fromJson(json, LabelEnrichmentJson::class.java)
                Enrichment(
                    description = row.description?.trim()?.takeIf { it.isNotEmpty() },
                    category = row.category?.trim()?.takeIf { it.isNotEmpty() },
                )
            } catch (_: Exception) {
                Enrichment(null, null)
            }
        }

    private fun extractJsonObjectPayload(raw: String): String {
        var t = raw.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim()
            if (t.endsWith("```")) t = t.removeSuffix("```").trim()
        }
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        if (start >= 0 && end > start) return t.substring(start, end + 1)
        return t
    }

    private data class LabelEnrichmentJson(
        val description: String? = null,
        val category: String? = null,
    )
}
