package com.biasharaai.ui.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.biasharaai.ai.PricingAdvisor
import com.biasharaai.media.ProductPhotoStore
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.ProductDao
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
                val previousImage = if (isEditing) _existingProduct.value?.imageUrl else null
                val product = Product(
                    id = if (isEditing) productId else 0L,
                    name = name.trim(),
                    description = description.trim().ifBlank { null },
                    price = price!!,
                    cost = cost!!,
                    stockQuantity = stock!!,
                    category = category.trim().ifBlank { null },
                    barcodeValue = barcodeValue.trim().ifBlank { null },
                    imageUrl = imageUrl,
                )

                if (isEditing) {
                    productDao.updateProduct(product)
                    if (previousImage != imageUrl) {
                        productPhotoStore.deleteIfAppStored(previousImage)
                    }
                } else {
                    productDao.insertProduct(product)
                }

                _events.emit(Event.Saved)
            } catch (e: Exception) {
                _events.emit(Event.Error(e.message ?: "Save failed"))
            } finally {
                _saving.value = false
            }
        }
    }

    /** Smart pricing (Gemma on PARTIAL_AI+ when model available; else rules). Prompt U3. */
    suspend fun suggestSellingPrice(product: Product): String =
        pricingAdvisor.suggestPrice(product)

    /** One-shot UI events. */
    sealed class Event {
        data object Saved : Event()
        data class ValidationError(val errors: Map<String, String>) : Event()
        data class Error(val message: String) : Event()
    }
}
