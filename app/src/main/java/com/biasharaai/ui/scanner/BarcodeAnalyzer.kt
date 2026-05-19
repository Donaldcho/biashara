package com.biasharaai.ui.scanner

import android.media.Image
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * CameraX [ImageAnalysis.Analyzer] that passes each frame to ML Kit Barcode Scanner.
 *
 * All processing is on-device — no internet connection required.
 *
 * Configured for retail barcodes plus app-generated labels:
 * UPC-A, UPC-E, EAN-13, EAN-8, CODE-128 (product/voucher labels), and QR codes.
 *
 * @param onBarcodeDetected callback fired **once** with the first valid [rawValue].
 *        After a successful detection, this analyzer stops processing to prevent
 *        duplicate callbacks.
 */
class BarcodeAnalyzer(
    private val onBarcodeDetected: (rawValue: String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_QR_CODE,
            )
            .build(),
    )

    /** Guard to ensure we only fire the callback once per scan session. */
    @Volatile
    private var detected = false

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

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val value = barcode.rawValue
                    if (!value.isNullOrBlank() && !detected) {
                        detected = true
                        onBarcodeDetected(value)
                        break
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    /** Reset the detector so a new barcode can be scanned. */
    fun reset() {
        detected = false
    }
}
