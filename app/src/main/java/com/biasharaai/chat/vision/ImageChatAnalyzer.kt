package com.biasharaai.chat.vision

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Gallery-style **Ask Image** for a **text-only** on-device LLM: compresses a photo into a short
 * factual caption using ML Kit labels + Latin OCR, then the text model reasons over that summary.
 */
@Singleton
class ImageChatAnalyzer @Inject constructor() {

    suspend fun describeImage(absolutePath: String): String = withContext(Dispatchers.Default) {
        val file = File(absolutePath)
        if (!file.exists()) return@withContext ""
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext ""
        val image = InputImage.fromBitmap(bitmap, 0)
        val labels = runCatching { detectLabels(image) }.getOrElse { emptyList() }
        val ocr = runCatching { recognizeText(image) }.getOrElse { "" }
        buildString {
            if (labels.isNotEmpty()) {
                append("Detected items/scene (on-device): ")
                append(labels.joinToString(", "))
                append(". ")
            }
            if (ocr.isNotBlank()) {
                append("Text visible in the image: \"")
                append(ocr.trim().take(800))
                append("\".")
            }
            if (isEmpty()) append("(No labels or readable text were detected.)")
        }
    }

    private suspend fun detectLabels(image: InputImage): List<String> = suspendCancellableCoroutine { cont ->
        val client = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        client.process(image)
            .addOnSuccessListener { result ->
                val top = result
                    .sortedByDescending { it.confidence }
                    .take(8)
                    .map { it.text }
                cont.resume(top)
            }
            .addOnFailureListener { cont.resume(emptyList()) }
    }

    private suspend fun recognizeText(image: InputImage): String = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText -> cont.resume(visionText.text) }
            .addOnFailureListener { cont.resume("") }
    }
}
