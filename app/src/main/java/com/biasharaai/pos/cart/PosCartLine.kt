package com.biasharaai.pos.cart

import com.biasharaai.service.ServiceCartLine

/** Unified cart row for POS display (products, services, vouchers). */
sealed class PosCartLine {
    abstract val key: String
    abstract val displayName: String
    abstract val subtitle: String?
    abstract val quantity: Int
    abstract val lineTotal: Double
    abstract val allowsPriceOverride: Boolean

    data class Product(val item: CartItem) : PosCartLine() {
        override val key = "p_${item.product.id}"
        override val displayName = item.product.name
        override val subtitle: String? = null
        override val quantity = item.quantity
        override val lineTotal = item.lineTotal
        override val allowsPriceOverride = true
    }

    data class Service(val line: ServiceCartLine) : PosCartLine() {
        override val key = "s_${line.service.id}"
        override val displayName = line.service.name
        override val subtitle: String? = line.staffName?.let { staff -> "Staff: $staff" }
        override val quantity = line.quantity
        override val lineTotal = line.lineTotal
        override val allowsPriceOverride = true
    }

    data class Voucher(val item: VoucherCartItem) : PosCartLine() {
        override val key = "v_${item.serviceItem.id}_${item.uses}_${item.pricePerUse}"
        override val displayName = item.serviceItem.name
        override val subtitle: String? = "${item.uses} sessions (prepaid)"
        override val quantity = 1
        override val lineTotal = item.totalAmount
        override val allowsPriceOverride = false
    }
}
