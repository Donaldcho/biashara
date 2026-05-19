package com.biasharaai.pos.receipt

/** One row on a POS sale receipt (product, service delivery, or prepaid voucher). */
data class PosReceiptLine(
    val name: String,
    val quantity: Int,
    val unitPrice: Double,
    val lineTotal: Double,
    val kind: Kind,
) {
    enum class Kind {
        PRODUCT,
        SERVICE,
        VOUCHER,
    }
}
