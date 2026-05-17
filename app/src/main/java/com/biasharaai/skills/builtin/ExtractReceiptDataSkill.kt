package com.biasharaai.skills.builtin

import android.graphics.BitmapFactory
import com.biasharaai.receipt.ReceiptLineItem
import com.biasharaai.receipt.ReceiptParser
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillArgsParser
import com.biasharaai.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** X7 — Supplier receipt OCR + Gemma JSON extraction ([ReceiptParser]). */
@Singleton
class ExtractReceiptDataSkill @Inject constructor(
    private val receiptParser: ReceiptParser,
) : BiasharaSkill {
    override val id: String = ID
    override val displayName: String = "Extract receipt data"
    override val parameterSchemaJson: String =
        """{"type":"object","properties":{"imagePath":{"type":"string"},"ocrText":{"type":"string"}}}"""

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        val args = SkillArgsParser.parseObject(argumentsJson).getOrElse {
            return@withContext SkillResult.Failure("INVALID_ARGS", it.message ?: "Invalid JSON")
        }
        val imagePath = SkillArgsParser.stringArg(args, "imagePath")
        val ocrText = SkillArgsParser.stringArg(args, "ocrText")

        when {
            !ocrText.isNullOrBlank() -> fromOcrText(ocrText)
            !imagePath.isNullOrBlank() -> fromImagePath(imagePath)
            else -> SkillResult.Failure(
                "INVALID_ARGS",
                "Provide imagePath (JPEG on disk) or ocrText.",
            )
        }
    }

    private suspend fun fromOcrText(text: String): SkillResult =
        mapParseResult(receiptParser.parseFromOcrText(text), ocrUsed = true)

    private suspend fun fromImagePath(path: String): SkillResult {
        val file = File(path)
        if (!file.isFile) {
            return SkillResult.Failure("NOT_FOUND", "Image file not found: $path")
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: return SkillResult.Failure("INVALID_IMAGE", "Could not decode image at $path")
        return try {
            mapParseResult(receiptParser.parseReceipt(bitmap), ocrUsed = false)
        } finally {
            bitmap.recycle()
        }
    }

    private fun mapParseResult(result: ReceiptParser.ParseResult, ocrUsed: Boolean): SkillResult =
        when (result) {
            is ReceiptParser.ParseResult.Success -> SkillResult.successMap(
                mapOf(
                    "manualFallback" to false,
                    "ocrUsed" to ocrUsed,
                    "itemCount" to result.items.size,
                    "items" to result.items.map { it.toSkillMap() },
                ),
                summary = "Extracted ${result.items.size} line item(s)",
            )
            ReceiptParser.ParseResult.ManualFallback -> SkillResult.successMap(
                mapOf(
                    "manualFallback" to true,
                    "ocrUsed" to ocrUsed,
                    "itemCount" to 0,
                    "items" to emptyList<Map<String, Any?>>(),
                ),
                summary = "Could not auto-extract; manual entry required",
            )
        }

    private fun ReceiptLineItem.toSkillMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "quantity" to quantity,
        "cost" to cost,
    )

    companion object {
        const val ID = "extract_receipt_data"
    }
}
