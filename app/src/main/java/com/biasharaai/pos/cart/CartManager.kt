package com.biasharaai.pos.cart

import com.biasharaai.data.local.db.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the current sale cart in memory only. Cleared on [clear] or when the user
 * abandons checkout (no Room writes until payment confirm — Prompt P3+).
 *
 * Injected into `PosViewModel` / `PaymentViewModel` (and optionally `CartRepository`).
 */
@Singleton
class CartManager @Inject constructor() {

    private val _items = MutableStateFlow<List<CartItem>>(emptyList())
    val items: StateFlow<List<CartItem>> = _items.asStateFlow()

    /** Sum of line totals before tax. */
    val subtotal: Double get() = _items.value.sumOf { it.lineTotal }

    fun addProduct(product: Product, qty: Int = 1) {
        if (qty <= 0) return
        val existing = _items.value.find { it.product.id == product.id }
        _items.value = if (existing != null) {
            _items.value.map {
                if (it.product.id == product.id) {
                    it.copy(quantity = it.quantity + qty)
                } else {
                    it
                }
            }
        } else {
            _items.value + CartItem(product = product, quantity = qty)
        }
    }

    fun updateQuantity(productId: Long, qty: Int) {
        if (qty <= 0) {
            removeItem(productId)
            return
        }
        _items.value = _items.value.map {
            if (it.product.id == productId) it.copy(quantity = qty) else it
        }
    }

    fun removeItem(productId: Long) {
        _items.value = _items.value.filter { it.product.id != productId }
    }

    fun setOverridePrice(productId: Long, price: Double) {
        _items.value = _items.value.map {
            if (it.product.id == productId) it.copy(overridePrice = price) else it
        }
    }

    fun clearOverridePrice(productId: Long) {
        _items.value = _items.value.map {
            if (it.product.id == productId) it.copy(overridePrice = null) else it
        }
    }

    fun clear() {
        _items.value = emptyList()
    }
}
