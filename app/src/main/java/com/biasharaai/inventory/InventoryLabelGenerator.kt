package com.biasharaai.inventory

import android.graphics.Bitmap
import android.graphics.Color
import com.biasharaai.service.ServiceQrGenerator
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

/**
 * Generates printable product barcodes (EAN-13 / CODE_128) and Pro service catalogue QR codes.
 */
object InventoryLabelGenerator {

    /** 13-digit EAN-style number with valid check digit. */
    fun generateProductBarcodeNumber(): String {
        val digits = (0 until 12).map { (0..9).random() }
        val sum = digits.mapIndexed { i, d -> if (i % 2 == 0) d else d * 3 }.sum()
        val check = (10 - sum % 10) % 10
        return (digits + check).joinToString("")
    }

    /**
     * Renders a scannable 1D barcode. Numeric 13-digit values use EAN-13 (retail scanners);
     * everything else uses CODE-128 (voucher tokens, alphanumeric codes).
     */
    fun generateBarcodeBitmap(
        value: String,
        width: Int = 600,
        height: Int = 160,
    ): Bitmap? = runCatching {
        val format = barcodeFormatFor(value)
        val hints = mapOf(EncodeHintType.MARGIN to 2)
        val matrix = MultiFormatWriter().encode(value, format, width, height, hints)
        val w = matrix.width
        val h = matrix.height
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            for (x in 0 until w) {
                for (y in 0 until h) {
                    bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
        }
    }.getOrNull()

    internal fun barcodeFormatFor(value: String): BarcodeFormat =
        if (value.length == 13 && value.all { it.isDigit() }) {
            BarcodeFormat.EAN_13
        } else {
            BarcodeFormat.CODE_128
        }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap =
        ServiceQrGenerator.generateQrBitmap(content, sizePx)
}
