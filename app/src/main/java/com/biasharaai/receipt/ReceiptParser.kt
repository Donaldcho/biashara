package com.biasharaai.receipt

import android.graphics.Bitmap
import com.biasharaai.ai.GemmaService
import com.google.android.gms.tasks.Tasks
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OCR (ML Kit) + Gemma JSON extraction for supplier receipts — Prompt U4.
 */
@Singleton
class ReceiptParser @Inject constructor(
    private val gemmaService: GemmaService,
) {

    sealed class ParseResult {
        data class Success(val items: List<ReceiptLineItem>) : ParseResult()
        data object ManualFallback : ParseResult()
    }

    suspend fun parseReceipt(bitmap: Bitmap): ParseResult = withContext(Dispatchers.IO) {
        val ocrText = runCatching { runOcr(bitmap) }.getOrNull()
        if (ocrText.isNullOrBlank()) {
            return@withContext ParseResult.ManualFallback
        }

        if (!gemmaService.isAvailable) {
            return@withContext ParseResult.ManualFallback
        }

        val prompt = """
Extract all product line items from this supplier receipt text.
Return ONLY a valid JSON array. No explanation, no markdown.
Each object must have: name (string), quantity (number), cost (number).
If a field is missing or unclear, use null.
Receipt text:
$ocrText
        """.trimIndent()

        val rawResponse = runCatching { gemmaService.generateResponse(prompt) }.getOrNull()
            ?: return@withContext ParseResult.ManualFallback

        val jsonPayload = extractJsonArrayPayload(rawResponse)
        val items = try {
            Gson().fromJson(jsonPayload, Array<ReceiptLineItem>::class.java).toList()
        } catch (_: Exception) {
            return@withContext ParseResult.ManualFallback
        }

        if (items.isEmpty()) ParseResult.ManualFallback else ParseResult.Success(items)
    }

    private fun runOcr(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = Tasks.await(recognizer.process(image))
        return result.textBlocks.joinToString("\n") { it.text }
    }

    private fun extractJsonArrayPayload(raw: String): String {
        var t = raw.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim()
            if (t.endsWith("```")) {
                t = t.removeSuffix("```").trim()
            }
        }
        val start = t.indexOf('[')
        val end = t.lastIndexOf(']')
        if (start >= 0 && end > start) {
            return t.substring(start, end + 1)
        }
        return t
    }
}

/** Parsed receipt line — Gson compatible (nulls allowed). Prompt U4. */
data class ReceiptLineItem(
    val name: String? = null,
    val quantity: Double? = null,
    val cost: Double? = null,
)
