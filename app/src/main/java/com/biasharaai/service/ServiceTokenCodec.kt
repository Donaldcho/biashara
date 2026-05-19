package com.biasharaai.service

/**
 * Pro QR token formats: BSVC (catalogue), BSRC (receipt), BSVOU (voucher).
 */
object ServiceTokenCodec {
    const val PREFIX_CATALOGUE = "BSVC:"
    const val PREFIX_RECEIPT = "BSRC:"
    const val PREFIX_VOUCHER = "BSVOU:"

    sealed class Parsed {
        data class Catalogue(val serviceItemId: Long) : Parsed()
        data class Receipt(val deliveryId: Long) : Parsed()
        data class Voucher(val voucherId: String) : Parsed()
    }

    fun catalogueToken(serviceItemId: Long): String = "$PREFIX_CATALOGUE$serviceItemId"

    fun receiptToken(deliveryId: Long): String = "$PREFIX_RECEIPT$deliveryId"

    fun voucherToken(voucherId: String): String = "$PREFIX_VOUCHER$voucherId"

    /** Raw voucher id from a scan, manual entry, or [voucherToken]. */
    fun resolveVoucherId(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return when (val parsed = parse(trimmed)) {
            is Parsed.Voucher -> parsed.voucherId
            else -> trimmed
        }
    }

    fun parse(raw: String): Parsed? {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith(PREFIX_CATALOGUE) -> {
                val id = trimmed.removePrefix(PREFIX_CATALOGUE).toLongOrNull() ?: return null
                Parsed.Catalogue(id)
            }
            trimmed.startsWith(PREFIX_RECEIPT) -> {
                val id = trimmed.removePrefix(PREFIX_RECEIPT).toLongOrNull() ?: return null
                Parsed.Receipt(id)
            }
            trimmed.startsWith(PREFIX_VOUCHER) -> {
                val id = trimmed.removePrefix(PREFIX_VOUCHER)
                if (id.isBlank()) null else Parsed.Voucher(id)
            }
            else -> null
        }
    }
}
