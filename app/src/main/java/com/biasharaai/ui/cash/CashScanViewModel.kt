package com.biasharaai.ui.cash

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.cash.CashParser
import com.biasharaai.cash.ParsedFields
import com.biasharaai.data.local.db.LedgerDirection
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class CashScanViewModel @Inject constructor(
    private val cashParser: CashParser,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CashScanUiState>(CashScanUiState.Idle)
    val uiState: StateFlow<CashScanUiState> = _uiState

    var direction: LedgerDirection = LedgerDirection.MONEY_IN

    fun onImageCaptured(bitmap: Bitmap) {
        _uiState.value = CashScanUiState.Scanning
        viewModelScope.launch {
            val qr = runCatching { detectBiasharaQr(bitmap) }.getOrNull()
            if (qr != null) {
                _uiState.value = CashScanUiState.QrFound(qr)
                return@launch
            }
            runCatching { runOcr(bitmap) }
                .onSuccess { (rawText, parsed) ->
                    _uiState.value = CashScanUiState.Confirming(
                        parsed = parsed,
                        rawText = rawText,
                        thumbnail = scaledThumbnail(bitmap),
                        direction = direction,
                    )
                }
                .onFailure { e ->
                    Log.e(TAG, "OCR failed", e)
                    _uiState.value = CashScanUiState.Error(e.localizedMessage ?: "OCR failed")
                }
        }
    }

    private suspend fun detectBiasharaQr(bitmap: Bitmap): BiasharaQrPayload? =
        withContext(Dispatchers.Default) {
            val scanner = BarcodeScanning.getClient()
            val image = InputImage.fromBitmap(bitmap, 0)
            val barcodes = scanner.process(image).await()
            scanner.close()
            barcodes.firstNotNullOfOrNull { barcode ->
                barcode.rawValue
                    ?.takeIf { it.startsWith("BIASHARA:") }
                    ?.let { BiasharaQrPayload.decode(it) }
            }
        }

    private suspend fun runOcr(bitmap: Bitmap): Pair<String, ParsedFields> =
        withContext(Dispatchers.Default) {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            recognizer.close()
            val rawText = result.text.take(2000)
            val parsed = cashParser.parse(rawText, direction)
            rawText to parsed
        }

    private fun scaledThumbnail(source: Bitmap): Bitmap? = runCatching {
        val maxDim = 400
        val scale = minOf(maxDim.toFloat() / source.width, maxDim.toFloat() / source.height, 1f)
        if (scale >= 1f) return@runCatching null
        Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt(),
            (source.height * scale).toInt(),
            true,
        )
    }.getOrNull()

    fun resetToIdle() {
        _uiState.value = CashScanUiState.Idle
    }

    companion object {
        private const val TAG = "CashScanViewModel"
    }
}
