package com.biasharaai.pos.cart

import com.biasharaai.data.local.db.Product

/**
 * One line in the in-memory POS cart. Not persisted until the sale is committed.
 */
data class CartItem(
    val product: Product,
    val quantity: Int = 1,
    /** When null, [Product.price] is used at checkout. */
    val overridePrice: Double? = null,
) {
    val effectivePrice: Double get() = overridePrice ?: product.price
    val lineTotal: Double get() = effectivePrice * quantity
}
