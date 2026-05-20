package com.biasharaai.ui.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.biasharaai.ai.PricingAdvisor
import com.biasharaai.inventory.InventoryLabelGenerator
import com.biasharaai.media.ProductPhotoStore
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.data.local.db.EnterpriseStockMovement
import com.biasharaai.enterprise.EnterpriseCatalogRepository
import com.biasharaai.enterprise.EnterprisePermissionRepository
import com.biasharaai.enterprise.EnterpriseRolePermissions
import com.biasharaai.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Add / Edit Product screen.
 *
 * Handles loading an existing product for editing, validating form inputs,
 * and persisting new or updated products via [ProductDao].
 */
@HiltViewModel
class AddEditProductViewModel @Inject constructor(
    private val productDao: ProductDao,
    private val pricingAdvisor: PricingAdvisor,
    private val productPhotoStore: ProductPhotoStore,
    private val enterpriseCatalogRepository: EnterpriseCatalogRepository,
    private val enterprisePermissionRepository: EnterprisePermissionRepository,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel() {

    /** The product being edited, or null if creating a new one. */
    private val _existingProduct = MutableStateFlow<Product?>(null)
    val existingProduct: StateFlow<Product?> = _existingProduct.asStateFlow()

    /** True while a save operation is in progress. */
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** One-shot events emitted to the fragment. */
    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    /** Product ID from navigation arguments (0L = new product). */
    private val productId: Long = savedStateHandle.get<Long>("product_id") ?: 0L

    /** Barcode value pre-filled from scanner (may be null). */
    val prefillBarcode: String? = savedStateHandle.get<String>("barcode_value")

    /** Whether we are editing an existing product vs creating a new one. */
    val isEditing: Boolean get() = productId != 0L

    /** Room id when editing; `0L` for new products (used for category-average exclude). */
    val editingProductId: Long get() = productId

    init {
        if (isEditing) {
            loadProduct(productId)
        }
    }

    private fun loadProduct(id: Long) {
        viewModelScope.launch {
            val product = productDao.getProductById(id).firstOrNull()
            _existingProduct.value = product
            if (product == null) {
                _events.emit(Event.Error("Product not found"))
            }
        }
    }

    /**
     * Validate inputs and save the product.
     *
     * @return validation errors via [Event.ValidationError], or [Event.Saved] on success.
     */
    fun saveProduct(
        name: String,
        description: String,
        priceText: String,
        costText: String,
        stockText: String,
        category: String,
        barcodeValue: String,
        imageUrl: String?,
    ) {
        // ── Validation ──────────────────────────────────────────────────
        val errors = mutableMapOf<String, String>()

        if (name.isBlank()) {
            errors["name"] = "Name is required"
        }

        val price = priceText.toDoubleOrNull()
        if (price == null || price < 0) {
            errors["price"] = "Enter a valid price"
        }

        val cost = costText.toDoubleOrNull()
        if (cost == null || cost < 0) {
            errors["cost"] = "Enter a valid cost"
        }

        val stock = stockText.toIntOrNull()
        if (stock == null || stock < 0) {
            errors["stock"] = "Enter a valid quantity"
        }

        if (errors.isNotEmpty()) {
            viewModelScope.launch { _events.emit(Event.ValidationError(errors)) }
            return
        }

        // ── Persist ─────────────────────────────────────────────────────
        _saving.value = true

        viewModelScope.launch {
            try {
                val existing = if (isEditing) _existingProduct.value else null
                val cleanName = name.trim()
                val cleanDescription = description.trim().ifBlank { null }
                val cleanCategory = category.trim().ifBlank { null }
                val hadBarcode = barcodeValue.trim().isNotEmpty()
                val resolvedBarcode = barcodeValue.trim().ifBlank {
                    InventoryLabelGenerator.generateProductBarcodeNumber()
                }
                val catalogPermissionCheck = if (
                    requiresCatalogPermission(
                        existing = existing,
                        newName = cleanName,
                        newDescription = cleanDescription,
                        newStock = stock!!,
                        newCategory = cleanCategory,
                        newBarcodeValue = resolvedBarcode,
                        newImageUrl = imageUrl,
                    )
                ) {
                    enterprisePermissionRepository.requirePermission(
                        permission = EnterpriseRolePermissions.PERMISSION_MANAGE_CATALOG,
                        action = "PRODUCT_CATALOG_SAVE",
                        entityType = "PRODUCT",
                        entityId = productId.takeIf { it > 0L }?.toString(),
                        summary = "Product catalog save blocked for $cleanName",
                        metadata = "stock=$stock; category=${cleanCategory.orEmpty()}; barcode=${resolvedBarcode.takeLast(6)}",
                    )
                } else {
                    null
                }
                if (catalogPermissionCheck?.allowed == false) {
                    emitPermissionDenied(catalogPermissionCheck)
                    return@launch
                }

                val pricePermissionCheck = if (requiresPricePermission(existing, price!!, cost!!)) {
                    enterprisePermissionRepository.requirePermission(
                        permission = EnterpriseRolePermissions.PERMISSION_CHANGE_PRICES,
                        action = "PRODUCT_PRICE_SAVE",
                        entityType = "PRODUCT",
                        entityId = productId.takeIf { it > 0L }?.toString(),
                        summary = "Product price save blocked for $cleanName",
                        metadata = "newPrice=$price; newCost=$cost",
                    )
                } else {
                    null
                }
                if (pricePermissionCheck?.allowed == false) {
                    emitPermissionDenied(pricePermissionCheck)
                    return@launch
                }
                val previousImage = existing?.imageUrl
                val draft = Product(
                    id = if (isEditing) productId else 0L,
                    name = cleanName,
                    description = cleanDescription,
                    price = price!!,
                    cost = cost!!,
                    stockQuantity = stock!!,
                    category = cleanCategory,
                    barcodeValue = resolvedBarcode,
                    imageUrl = imageUrl,
                    lastStockCheckAt = existing?.lastStockCheckAt ?: 0L,
                    enterpriseCatalogId = existing?.enterpriseCatalogId,
                    enterpriseCatalogVersion = existing?.enterpriseCatalogVersion ?: 0L,
                    enterpriseCatalogUpdatedAt = existing?.enterpriseCatalogUpdatedAt ?: 0L,
                    enterpriseSyncStatus = existing?.enterpriseSyncStatus ?: Product.SYNC_LOCAL,
                )
                var product = enterpriseCatalogRepository.prepareProductForLocalSave(existing, draft)
                val stockDelta = product.stockQuantity - (existing?.stockQuantity ?: 0)

                if (isEditing) {
                    productDao.updateProduct(product)
                    if (previousImage != imageUrl) {
                        productPhotoStore.deleteIfAppStored(previousImage)
                    }
                } else {
                    val id = productDao.insertProduct(product)
                    product = product.copy(id = id)
                }
                enterpriseCatalogRepository.onProductSaved(
                    product = product,
                    changeType = if (isEditing) "UPDATE" else "CREATE",
                )
                val stockMovementType = if (existing == null) {
                    EnterpriseStockMovement.TYPE_INITIAL_STOCK
                } else {
                    EnterpriseStockMovement.TYPE_ADJUSTMENT
                }
                enterpriseCatalogRepository.recordProductStockMovement(
                    product = product,
                    quantityDelta = stockDelta,
                    movementType = stockMovementType,
                    sourceType = EnterpriseStockMovement.SOURCE_CATALOG_SAVE,
                    sourceId = product.id.toString(),
                    note = if (existing == null) "Product created" else "Product stock edited",
                )

                _events.emit(
                    Event.Saved(
                        barcodeToPrint = if (!hadBarcode) resolvedBarcode else null,
                    ),
                )
            } catch (e: Exception) {
                _events.emit(Event.Error(e.message ?: "Save failed"))
            } finally {
                _saving.value = false
            }
        }
    }

    private suspend fun emitPermissionDenied(check: com.biasharaai.enterprise.EnterprisePermissionCheck) {
        val operator = check.operator
        _events.emit(
            Event.PermissionDenied(
                operatorName = operator?.name.orEmpty(),
                operatorRole = operator?.role.orEmpty(),
            ),
        )
    }

    private fun requiresCatalogPermission(
        existing: Product?,
        newName: String,
        newDescription: String?,
        newStock: Int,
        newCategory: String?,
        newBarcodeValue: String,
        newImageUrl: String?,
    ): Boolean {
        if (existing == null) return true
        return existing.name != newName ||
            existing.description != newDescription ||
            existing.stockQuantity != newStock ||
            existing.category != newCategory ||
            existing.barcodeValue != newBarcodeValue ||
            existing.imageUrl != newImageUrl
    }

    private fun requiresPricePermission(existing: Product?, newPrice: Double, newCost: Double): Boolean {
        if (existing == null) return true
        return existing.price != newPrice || existing.cost != newCost
    }

    /** Smart pricing (Gemma on PARTIAL_AI+ when model available; else rules). Prompt U3. */
    suspend fun suggestSellingPrice(product: Product): String =
        pricingAdvisor.suggestPrice(product)

    /** One-shot UI events. */
    sealed class Event {
        /** Non-null when a barcode was auto-assigned and the UI should offer printing. */
        data class Saved(val barcodeToPrint: String? = null) : Event()
        data class ValidationError(val errors: Map<String, String>) : Event()
        data class PermissionDenied(val operatorName: String, val operatorRole: String) : Event()
        data class Error(val message: String) : Event()
    }
}
