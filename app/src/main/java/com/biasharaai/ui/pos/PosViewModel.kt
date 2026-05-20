package com.biasharaai.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.GemmaService
import com.biasharaai.data.local.db.Customer
import com.biasharaai.data.local.db.CustomerDao
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.data.local.db.StaffMemberDao
import com.biasharaai.enterprise.EnterprisePermissionRepository
import com.biasharaai.enterprise.EnterpriseRolePermissions
import com.biasharaai.pos.cart.VoucherCartItem
import com.biasharaai.pos.CustomerSuggestionEngine
import com.biasharaai.pos.cart.CartItem
import com.biasharaai.service.ServiceCartLine
import com.biasharaai.pos.cart.CartManager
import com.biasharaai.data.local.db.ServiceItem
import com.biasharaai.pos.cart.CartRepository
import com.biasharaai.productline.ProductLineManager
import com.biasharaai.service.ServiceRepository
import com.biasharaai.service.ServiceTokenCodec
import com.biasharaai.ui.scanner.BarcodeScanRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class EnterprisePosPermissionDeniedEvent(
    val operatorName: String,
    val operatorRole: String,
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
    private val serviceRepository: ServiceRepository,
    private val productLineManager: ProductLineManager,
    private val staffMemberDao: StaffMemberDao,
    private val enterprisePermissionRepository: EnterprisePermissionRepository,
) : ViewModel() {

    private val _pendingStaffPick = MutableSharedFlow<ServiceItem>(extraBufferCapacity = 1)
    val pendingStaffPick: SharedFlow<ServiceItem> = _pendingStaffPick.asSharedFlow()

    private val _activeStaffCount = MutableStateFlow(0)
    val activeStaffCount: StateFlow<Int> = _activeStaffCount.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _activeStaffCount.value = staffMemberDao.getActiveCount()
        }
    }

    private val searchQuery = MutableStateFlow("")

    private val _catalogMode = MutableStateFlow(PosCatalogMode.PRODUCTS)
    val catalogMode: StateFlow<PosCatalogMode> = _catalogMode.asStateFlow()

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

    val allServices: StateFlow<List<ServiceItem>> = serviceRepository.observeServices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isProEnabled: Boolean get() = productLineManager.isProEnabled()

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

    private val _serviceScanMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val serviceScanMessage: SharedFlow<String> = _serviceScanMessage.asSharedFlow()

    private val _priceWarning = MutableSharedFlow<PriceWarningEvent>(extraBufferCapacity = 1)
    val priceWarning: SharedFlow<PriceWarningEvent> = _priceWarning.asSharedFlow()

    private val _priceChangeDenied = MutableSharedFlow<EnterprisePosPermissionDeniedEvent>(extraBufferCapacity = 1)
    val priceChangeDenied: SharedFlow<EnterprisePosPermissionDeniedEvent> = _priceChangeDenied.asSharedFlow()

    private val _voucherAdded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val voucherAdded: SharedFlow<Unit> = _voucherAdded.asSharedFlow()

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun clearSearch() {
        searchQuery.value = ""
    }

    fun setCatalogMode(mode: PosCatalogMode) {
        _catalogMode.value = mode
    }

    fun addProductToCart(product: Product, qty: Int = 1) {
        cartManager.addProduct(product, qty)
    }

    fun onServiceTapped(service: ServiceItem) {
        viewModelScope.launch {
            _activeStaffCount.value = staffMemberDao.getActiveCount()
            _pendingStaffPick.emit(service)
        }
    }

    fun addServiceToCart(service: ServiceItem, qty: Int = 1, staffName: String? = null) {
        cartManager.addService(service, qty, staffName)
    }

    fun addVoucherToCart(params: VoucherIssueBottomSheet.VoucherIssueParams) {
        viewModelScope.launch {
            if (params.pricePerUse != params.serviceItem.basePrice) {
                val allowed = requirePricePermission(
                    action = "POS_VOUCHER_PRICE_OVERRIDE",
                    entityType = "SERVICE_ITEM",
                    entityId = params.serviceItem.id.toString(),
                    summary = "Voucher price override blocked for ${params.serviceItem.name}",
                    metadata = "basePrice=${params.serviceItem.basePrice}; pricePerUse=${params.pricePerUse}",
                )
                if (!allowed) return@launch
            }
            val customer = cartRepository.selectedCustomer.value
            cartManager.addVoucherItem(
                VoucherCartItem(
                    serviceItem = params.serviceItem,
                    uses = params.uses,
                    pricePerUse = params.pricePerUse,
                    customerId = params.customerId ?: customer?.id,
                    customerName = params.customerName ?: customer?.name,
                ),
            )
            _voucherAdded.emit(Unit)
        }
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
            if (BarcodeScanRouter.isServiceToken(value)) {
                if (!productLineManager.isProEnabled()) {
                    _unknownBarcode.emit(value)
                    return@launch
                }
                handleServiceToken(value)
                return@launch
            }
            val product = productDao.getProductByBarcode(value).firstOrNull()
            if (product == null) {
                _unknownBarcode.emit(value)
                return@launch
            }
            cartManager.addProduct(product, 1)
        }
    }

    private suspend fun handleServiceToken(value: String) {
        when (val parsed = ServiceTokenCodec.parse(value)) {
            is ServiceTokenCodec.Parsed.Catalogue -> {
                val service = serviceRepository.getService(parsed.serviceItemId)
                    ?: serviceRepository.getServiceByCatalogueToken(value)
                if (service == null) {
                    _unknownBarcode.emit(value)
                } else {
                    cartManager.addService(service, 1)
                    _serviceScanMessage.emit(service.name)
                }
            }
            is ServiceTokenCodec.Parsed.Voucher -> {
                runCatching {
                    val service = serviceRepository.redeemVoucher(
                        parsed.voucherId,
                        cartRepository.selectedCustomer.value?.id,
                    )
                    cartManager.addService(service, 1)
                    _serviceScanMessage.emit(service.name)
                }.onFailure {
                    _unknownBarcode.emit(value)
                }
            }
            is ServiceTokenCodec.Parsed.Receipt -> {
                val delivery = serviceRepository.verifyReceiptToken(value)
                if (delivery == null) {
                    _unknownBarcode.emit(value)
                } else {
                    val service = serviceRepository.getService(delivery.serviceItemId)
                    _serviceScanMessage.emit(service?.name ?: "Service")
                }
            }
            null -> _unknownBarcode.emit(value)
        }
    }

    /**
     * Prompt P9: after a deep discount override, PARTIAL_AI+ may ask Gemma for a one-line check-in.
     */
    fun applyLinePriceOverride(item: CartItem, newUnitPrice: Double) {
        val product = item.product
        val previousOverride = item.overridePrice
        viewModelScope.launch {
            if (newUnitPrice != product.price) {
                val allowed = requirePricePermission(
                    action = "POS_PRODUCT_PRICE_OVERRIDE",
                    entityType = "PRODUCT",
                    entityId = product.id.toString(),
                    summary = "POS product price override blocked for ${product.name}",
                    metadata = "catalogPrice=${product.price}; overridePrice=$newUnitPrice",
                )
                if (!allowed) return@launch
            }
            cartManager.setOverridePrice(product.id, newUnitPrice)
            maybeEmitProductPriceWarning(product, newUnitPrice, previousOverride)
        }
    }

    fun applyServiceLinePriceOverride(line: ServiceCartLine, newUnitPrice: Double) {
        viewModelScope.launch {
            val service = line.service
            if (newUnitPrice != service.basePrice) {
                val allowed = requirePricePermission(
                    action = "POS_SERVICE_PRICE_OVERRIDE",
                    entityType = "SERVICE_ITEM",
                    entityId = service.id.toString(),
                    summary = "POS service price override blocked for ${service.name}",
                    metadata = "basePrice=${service.basePrice}; overridePrice=$newUnitPrice",
                )
                if (!allowed) return@launch
            }
            cartManager.setServiceOverridePrice(service.id, newUnitPrice)
        }
    }

    private suspend fun requirePricePermission(
        action: String,
        entityType: String,
        entityId: String,
        summary: String,
        metadata: String,
    ): Boolean {
        val permissionCheck = enterprisePermissionRepository.requirePermission(
            permission = EnterpriseRolePermissions.PERMISSION_CHANGE_PRICES,
            action = action,
            entityType = entityType,
            entityId = entityId,
            summary = summary,
            metadata = metadata,
        )
        if (permissionCheck.allowed) return true
        val operator = permissionCheck.operator
        _priceChangeDenied.emit(
            EnterprisePosPermissionDeniedEvent(
                operatorName = operator?.name.orEmpty(),
                operatorRole = operator?.role.orEmpty(),
            ),
        )
        return false
    }

    private fun maybeEmitProductPriceWarning(
        product: Product,
        newUnitPrice: Double,
        previousOverride: Double?,
    ) {
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
