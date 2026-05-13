package com.biasharaai.pos.cart

import com.biasharaai.data.local.db.AppSettings
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.Customer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregates [CartManager] line items with [AppSettings] for tax-aware totals.
 *
 * [AppSettings.taxRate] is treated as a **percentage** (e.g. `16.0` = 16%).
 */
data class CartMonetary(
    val subtotal: Double,
    val taxAmount: Double,
    val grandTotal: Double,
) {
    companion object {
        val ZERO = CartMonetary(0.0, 0.0, 0.0)
    }
}

@Singleton
class CartRepository @Inject constructor(
    private val cartManager: CartManager,
    private val appSettingsDao: AppSettingsDao,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _selectedCustomer = MutableStateFlow<Customer?>(null)
    val selectedCustomer: StateFlow<Customer?> = _selectedCustomer.asStateFlow()

    /** Walk-in clears the selection (`null`). */
    fun setSelectedCustomer(customer: Customer?) {
        _selectedCustomer.value = customer
    }

    val items: StateFlow<List<CartItem>> = cartManager.items

    /** Latest row from `app_settings` (singleton `id = 1`), or null before first emit. */
    private val settingsState: StateFlow<AppSettings?> = appSettingsDao.getSettings()
        .stateIn(scope, SharingStarted.Eagerly, null)

    val activeSettings: StateFlow<AppSettings?> get() = settingsState

    /**
     * Subtotal, VAT-style tax from [AppSettings.taxRate] (%), and grand total.
     * Updates whenever cart lines or settings change.
     */
    val monetary: StateFlow<CartMonetary> = combine(
        cartManager.items,
        settingsState,
    ) { cartItems, settings ->
        val subtotal = cartItems.sumOf { it.lineTotal }
        val ratePercent = settings?.taxRate ?: 0.0
        val tax = subtotal * (ratePercent / 100.0)
        val grand = subtotal + tax
        CartMonetary(subtotal = subtotal, taxAmount = tax, grandTotal = grand)
    }.stateIn(scope, SharingStarted.Eagerly, CartMonetary.ZERO)

    val subtotal: StateFlow<Double> = monetary
        .map { it.subtotal }
        .stateIn(scope, SharingStarted.Eagerly, 0.0)

    val taxAmount: StateFlow<Double> = monetary
        .map { it.taxAmount }
        .stateIn(scope, SharingStarted.Eagerly, 0.0)

    val grandTotal: StateFlow<Double> = monetary
        .map { it.grandTotal }
        .stateIn(scope, SharingStarted.Eagerly, 0.0)
}
