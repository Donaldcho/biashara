package com.biasharaai.ui.inventory

import androidx.lifecycle.viewModelScope
import com.biasharaai.ai.DemandForecaster
import com.biasharaai.media.ProductPhotoStore
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InventoryListViewModel @Inject constructor(
    private val productDao: ProductDao,
    private val demandForecaster: DemandForecaster,
    private val productPhotoStore: ProductPhotoStore,
) : BaseViewModel() {

    /** Reactive product list backed by Room's Flow, exposed as StateFlow. */
    val products: StateFlow<List<Product>> = productDao.getAllProducts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList(),
        )

    /**
     * Map of product ID → forecast string.
     *
     * Populated asynchronously after the product list loads.
     * Only products with sufficient sales history get a forecast.
     */
    private val _forecasts = MutableStateFlow<Map<Long, String>>(emptyMap())
    val forecasts: StateFlow<Map<Long, String>> = _forecasts.asStateFlow()

    init {
        // When the product list changes, generate forecasts for eligible products
        viewModelScope.launch {
            products.collect { productList ->
                generateForecasts(productList)
            }
        }
    }

    /**
     * Generate demand forecasts for products with enough sales data.
     *
     * Currently uses simulated sales history since the Sales entity
     * is not yet implemented (Prompt future). Once SaleDao is available,
     * this will pull real daily sales counts.
     */
    private fun generateForecasts(productList: List<Product>) {
        viewModelScope.launch(Dispatchers.IO) {
            val forecastMap = mutableMapOf<Long, String>()

            for (product in productList) {
                // TODO: Replace with real sales data from SaleDao once implemented.
                // For now, simulate sales history from stock quantity to demonstrate
                // the forecasting pipeline.
                val simulatedHistory = generateSimulatedHistory(product)

                if (simulatedHistory.size >= DemandForecaster.MIN_DATA_POINTS) {
                    val forecast = demandForecaster.predictDemand(
                        productName = product.name,
                        salesHistory = simulatedHistory,
                    )
                    if (forecast.isNotBlank()) {
                        forecastMap[product.id] = forecast
                    }
                }
            }

            _forecasts.value = forecastMap
        }
    }

    /**
     * Generate simulated daily sales history from the product's stock quantity.
     *
     * This is a placeholder that creates a believable sales pattern based on
     * the product's current stock level. It will be replaced with real data
     * from the sales table in a future prompt.
     */
    private fun generateSimulatedHistory(product: Product): List<Int> {
        if (product.stockQuantity <= 0) return emptyList()

        // Use the product ID as a seed for repeatable results
        val seed = product.id
        val baseDaily = (product.stockQuantity / 10).coerceIn(1, 20)

        return (0 until 7).map { day ->
            val variation = ((seed + day) % 5) - 2  // -2 to +2
            (baseDaily + variation.toInt()).coerceAtLeast(0)
        }
    }

    suspend fun deleteProduct(product: Product) {
        withContext(Dispatchers.IO) {
            productPhotoStore.deleteIfAppStored(product.imageUrl)
            productDao.deleteProduct(product)
        }
    }

    /**
     * Removes units from on-hand stock (damage, shrink, own use). Does not create a transaction row.
     */
    suspend fun removeStockUnits(productId: Long, quantity: Int) {
        require(quantity > 0) { "Quantity must be positive" }
        withContext(Dispatchers.IO) {
            val p = productDao.getProductByIdOnce(productId)
                ?: error("Product not found")
            if (p.stockQuantity < quantity) {
                error("Only ${p.stockQuantity} in stock")
            }
            productDao.incrementStock(productId, -quantity)
        }
    }
}
