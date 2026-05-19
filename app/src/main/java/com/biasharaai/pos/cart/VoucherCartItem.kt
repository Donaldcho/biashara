package com.biasharaai.pos.cart

import com.biasharaai.data.local.db.ServiceItem

/** Prepaid multi-use voucher sold at POS (Pro). */
data class VoucherCartItem(
    val serviceItem: ServiceItem,
    val uses: Int,
    val pricePerUse: Double,
    val customerId: Long? = null,
    val customerName: String? = null,
) {
    val totalAmount: Double get() = uses * pricePerUse
}
