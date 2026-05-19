package com.biasharaai.pos.cart

import com.biasharaai.data.local.db.Product
import com.biasharaai.service.ServiceCartLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val _serviceItems = MutableStateFlow<List<ServiceCartLine>>(emptyList())
    val serviceItems: StateFlow<List<ServiceCartLine>> = _serviceItems.asStateFlow()

    private val _voucherItems = MutableStateFlow<List<VoucherCartItem>>(emptyList())
    val voucherItems: StateFlow<List<VoucherCartItem>> = _voucherItems.asStateFlow()

    /** Sum of line totals before tax (products + services + vouchers). */
    val subtotal: Double get() =
        _items.value.sumOf { it.lineTotal } +
            _serviceItems.value.sumOf { it.lineTotal } +
            _voucherItems.value.sumOf { it.totalAmount }

    val isEmpty: Boolean get() =
        _items.value.isEmpty() && _serviceItems.value.isEmpty() && _voucherItems.value.isEmpty()

    /** Total cart line units (products + services + voucher rows). */
    val lineUnitCount: Int get() =
        _items.value.sumOf { it.quantity } +
            _serviceItems.value.sumOf { it.quantity } +
            _voucherItems.value.size

    /** Products, services, and vouchers in display order for the cart recycler. */
    fun unifiedLines(): Flow<List<PosCartLine>> = combine(items, serviceItems, voucherItems) { products, services, vouchers ->
        buildList {
            addAll(products.map { PosCartLine.Product(it) })
            addAll(services.map { PosCartLine.Service(it) })
            addAll(vouchers.map { PosCartLine.Voucher(it) })
        }
    }

    fun removeLine(line: PosCartLine) {
        when (line) {
            is PosCartLine.Product -> removeItem(line.item.product.id)
            is PosCartLine.Service -> removeService(line.line.service.id)
            is PosCartLine.Voucher -> removeVoucherItem(line.item)
        }
    }

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

    fun addService(service: com.biasharaai.data.local.db.ServiceItem, qty: Int = 1, staffName: String? = null) {
        if (qty <= 0) return
        val existing = _serviceItems.value.find { it.service.id == service.id }
        _serviceItems.value = if (existing != null) {
            _serviceItems.value.map {
                if (it.service.id == service.id) {
                    it.copy(quantity = it.quantity + qty)
                } else {
                    it
                }
            }
        } else {
            _serviceItems.value + ServiceCartLine(service = service, quantity = qty, staffName = staffName)
        }
    }

    fun updateServiceQuantity(serviceId: Long, qty: Int) {
        if (qty <= 0) {
            removeService(serviceId)
            return
        }
        _serviceItems.value = _serviceItems.value.map {
            if (it.service.id == serviceId) it.copy(quantity = qty) else it
        }
    }

    fun removeService(serviceId: Long) {
        _serviceItems.value = _serviceItems.value.filter { it.service.id != serviceId }
    }

    fun setServiceOverridePrice(serviceId: Long, price: Double) {
        _serviceItems.value = _serviceItems.value.map {
            if (it.service.id == serviceId) it.copy(overridePrice = price) else it
        }
    }

    fun addVoucherItem(item: VoucherCartItem) {
        _voucherItems.value = _voucherItems.value + item
    }

    fun removeVoucherItem(item: VoucherCartItem) {
        _voucherItems.value = _voucherItems.value.filter { it != item }
    }

    fun clearVoucherItems() {
        _voucherItems.value = emptyList()
    }

    fun clear() {
        _items.value = emptyList()
        _serviceItems.value = emptyList()
        _voucherItems.value = emptyList()
    }
}
