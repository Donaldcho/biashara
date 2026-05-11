package com.biasharaai.ui.scanner

import android.media.Image
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * CameraX [ImageAnalysis.Analyzer] that runs ML Kit Text Recognition on each frame.
 *
 * Used by [LabelScannerFragment] to read product labels / signs / receipts entirely on-device.
 *
 * Fires [onTextDetected] exactly once with the **largest text block** that appears stable for
 * one or more frames in a row. Stable means the block's first line of text matches the previous
 * frame's first line — this avoids capturing transient noise.
 *
 * @param onTextDetected called on the analysis thread with the captured text.
 */
class TextAnalyzer(
    private val onTextDetected: (text: String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @Volatile
    private var detected = false

    @Volatile
    private var previousTopLine: String? = null

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (detected) {
            imageProxy.close()
            return
        }
        val mediaImage: Image = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )
        recognizer.process(inputImage)
            .addOnSuccessListener { result ->
                if (detected) return@addOnSuccessListener
                val biggestBlock = result.textBlocks
                    .maxByOrNull { block ->
                        block.boundingBox?.let { it.width() * it.height() } ?: 0
                    } ?: return@addOnSuccessListener
                val text = biggestBlock.text.trim()
                if (text.isBlank()) return@addOnSuccessListener
                val topLine = text.lineSequence().firstOrNull()?.trim().orEmpty()
                if (topLine.length < MIN_TEXT_LENGTH) {
                    previousTopLine = null
                    return@addOnSuccessListener
                }
                if (previousTopLine == topLine) {
                    detected = true
                    onTextDetected(text)
                } else {
                    previousTopLine = topLine
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun reset() {
        detected = false
        previousTopLine = null
    }

    companion object {
        private const val MIN_TEXT_LENGTH = 2
    }
}
