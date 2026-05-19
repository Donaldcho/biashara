package com.biasharaai.service

import com.biasharaai.data.local.db.ServiceItem
import com.biasharaai.data.local.db.ServiceVoucher
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ESC/POS bytes for a 58mm prepaid voucher card (Pro). */
object VoucherReceiptFormatter {
    private val charset = Charset.forName("ISO-8859-1")
    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    fun format(
        voucher: ServiceVoucher,
        service: ServiceItem,
        businessName: String,
        token: String,
    ): ByteArray {
        val lines = buildList {
            add(center(businessName, bold = true))
            add(center("------------------------"))
            add(center(service.name, bold = true))
            add(center("${voucher.totalUses} sessions prepaid"))
            voucher.expiresAt?.let { add(center("Valid: ${dateFormat.format(Date(it))}")) }
            add("")
            add(center(token))
            add(center("Scan at each visit · Biashara AI Pro"))
        }
        return lines.joinToString("\n").toByteArray(charset) + byteArrayOf(0x1D, 0x56, 0x00)
    }

    private fun center(text: String, bold: Boolean = false): String {
        val prefix = if (bold) "\u001B\u0045\u0001" else ""
        val suffix = if (bold) "\u001B\u0045\u0000" else ""
        return "$prefix$text$suffix"
    }
}
