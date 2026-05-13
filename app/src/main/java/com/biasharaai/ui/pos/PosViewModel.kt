package com.biasharaai.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.GemmaService
import com.biasharaai.data.local.db.Customer
import com.biasharaai.data.local.db.CustomerDao
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.pos.CustomerSuggestionEngine
import com.biasharaai.pos.cart.CartItem
import com.biasharaai.pos.cart.CartManager
import com.biasharaai.pos.cart.CartRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

data class PriceWarningEvent(
    val message: String,
    val productId: Long,
    val previousOverride: Double?,
)

data class CustomerSuggestionsUi(
    val products: List<Product> = emptyList(),
    val gemmaSubtitle: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PosViewModel @Inject constructor(
    private val productDao: ProductDao,
    private val customerDao: CustomerDao,
    private val cartManager: CartManager,
    private val cartRepository: CartRepository,
    private val customerSuggestionEngine: CustomerSuggestionEngine,
    private val capabilityTier: CapabilityTier,
    private val gemmaService: GemmaService,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val searchResults: StateFlow<List<Product>> = searchQuery
        .flatMapLatest { q ->
            val trimmed = q.trim()
            if (trimmed.isEmpty()) {
                flowOf(emptyList())
            } else {
                productDao.searchProductsByNameOrBarcode(trimmed)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allProducts: StateFlow<List<Product>> = productDao.getProductsOrderedForPos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedCustomer: StateFlow<Customer?> = cartRepository.selectedCustomer

    /** Repeat-purchase chips + optional Gemma subtitle (FULL_AI). */
    val customerSuggestionsUi: StateFlow<CustomerSuggestionsUi> = selectedCustomer
        .flatMapLatest { c ->
            if (c == null) {
                flowOf(CustomerSuggestionsUi())
            } else {
                flow {
                    val products = customerSuggestionEngine.topProductsForCustomer(c.id)
                    val subtitle = customerSuggestionEngine.gemmaRepeatPurchaseHint(c, products)
                    emit(CustomerSuggestionsUi(products = products, gemmaSubtitle = subtitle))
                }.flowOn(Dispatchers.IO)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomerSuggestionsUi())

    val quickSaleMode: StateFlow<Boolean> = cartRepository.activeSettings
        .map { it?.quickSaleMode == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val cartItems = cartRepository.items
    val grandTotal = cartRepository.grandTotal

    private val _unknownBarcode = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val unknownBarcode: SharedFlow<String> = _unknownBarcode.asSharedFlow()

    private val _priceWarning = MutableSharedFlow<PriceWarningEvent>(extraBufferCapacity = 1)
    val priceWarning: SharedFlow<PriceWarningEvent> = _priceWarning.asSharedFlow()

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun clearSearch() {
        searchQuery.value = ""
    }

    fun addProductToCart(product: Product, qty: Int = 1) {
        cartManager.addProduct(product, qty)
    }

    fun selectWalkInCustomer() {
        cartRepository.setSelectedCustomer(null)
    }

    fun selectCustomer(customer: Customer) {
        cartRepository.setSelectedCustomer(customer)
    }

    fun selectCustomerById(id: Long) {
        viewModelScope.launch {
            val c = customerDao.getCustomerById(id) ?: return@launch
            cartRepository.setSelectedCustomer(c)
        }
    }

    fun onScannedBarcode(raw: String) {
        val value = raw.trim()
        if (value.isEmpty()) return
        viewModelScope.launch {
            val product = productDao.getProductByBarcode(value).firstOrNull()
            if (product == null) {
                _unknownBarcode.emit(value)
                return@launch
            }
            cartManager.addProduct(product, 1)
        }
    }

    /**
     * Prompt P9: after a deep discount override, PARTIAL_AI+ may ask Gemma for a one-line check-in.
     */
    fun applyLinePriceOverride(item: CartItem, newUnitPrice: Double) {
        val product = item.product
        val previousOverride = item.overridePrice
        cartManager.setOverridePrice(product.id, newUnitPrice)
        if (capabilityTier == CapabilityTier.RULES_BASED) return
        if (newUnitPrice >= product.price * 0.6) return
        if (!gemmaService.isAvailable) return
        viewModelScope.launch(Dispatchers.IO) {
            val language = Locale.getDefault().displayLanguage
            val cur = cartRepository.activeSettings.value?.currencySymbol?.takeIf { it.isNotBlank() }
                ?: runCatching { Currency.getInstance(Locale.getDefault()).symbol }.getOrNull().orEmpty()
            val prompt = """
                A shop owner just changed the price of '${product.name}'
                from ${product.price} to $newUnitPrice $cur.
                In one sentence in $language, ask if this is intentional
                or if they made a mistake. Be friendly and brief.
            """.trimIndent()
            val warning = runCatching { gemmaService.generateResponse(prompt) }.getOrNull()?.trim().orEmpty()
            if (warning.isNotBlank()) {
                _priceWarning.emit(PriceWarningEvent(warning, product.id, previousOverride))
            }
        }
    }

    fun undoPriceOverride(productId: Long, previousOverride: Double?) {
        if (previousOverride != null) {
            cartManager.setOverridePrice(productId, previousOverride)
        } else {
            cartManager.clearOverridePrice(productId)
        }
    }
}
