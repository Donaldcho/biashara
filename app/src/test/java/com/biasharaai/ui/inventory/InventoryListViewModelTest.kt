package com.biasharaai.ui.inventory

import com.biasharaai.ai.DemandForecaster
import com.biasharaai.ai.GemmaService
import com.biasharaai.data.local.db.ForecastCalibrationDao
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.media.ProductPhotoStore
import com.biasharaai.service.ServiceRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [InventoryListViewModel].
 *
 * Note: Forecast generation dispatches on [Dispatchers.IO],
 * so we use [Thread.sleep] to let those coroutines settle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InventoryListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var productDao: ProductDao
    private lateinit var demandForecaster: DemandForecaster
    private lateinit var gemmaService: GemmaService
    private lateinit var calibrationDao: ForecastCalibrationDao
    private lateinit var productPhotoStore: ProductPhotoStore
    private lateinit var serviceRepository: ServiceRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0

        productDao = mockk(relaxed = true)
        gemmaService = mockk(relaxed = true)
        calibrationDao = mockk(relaxed = true)
        coEvery { calibrationDao.avgBiasRatio(any(), any()) } returns null
        coEvery { calibrationDao.insert(any()) } returns 1L
        demandForecaster = DemandForecaster(gemmaService, calibrationDao)
        productPhotoStore = mockk(relaxed = true)
        serviceRepository = mockk(relaxed = true)
        every { serviceRepository.observeServices() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(android.util.Log::class)
    }

    /** Allow Dispatchers.IO coroutines to complete. */
    private fun awaitIoCompletion() {
        Thread.sleep(500)
    }

    @Test
    fun `products StateFlow emits product list from DAO`() {
        val products = listOf(
            Product(id = 1, name = "Sugar", price = 120.0, cost = 95.0, stockQuantity = 50),
            Product(id = 2, name = "Rice", price = 350.0, cost = 280.0, stockQuantity = 100),
        )
        every { productDao.getAllProducts() } returns flowOf(products)
        every { gemmaService.isAvailable } returns false

        val viewModel = InventoryListViewModel(
            productDao,
            serviceRepository,
            demandForecaster,
            productPhotoStore,
        )
        awaitIoCompletion()

        assertEquals(2, viewModel.products.value.size)
        assertEquals("Sugar", viewModel.products.value[0].name)
        assertEquals("Rice", viewModel.products.value[1].name)
    }

    @Test
    fun `products StateFlow starts with empty list`() {
        every { productDao.getAllProducts() } returns flowOf(emptyList())
        every { gemmaService.isAvailable } returns false

        val viewModel = InventoryListViewModel(
            productDao,
            serviceRepository,
            demandForecaster,
            productPhotoStore,
        )
        awaitIoCompletion()

        assertTrue(viewModel.products.value.isEmpty())
    }

    @Test
    fun `forecasts generated for products with sufficient stock`() {
        val products = listOf(
            Product(id = 1, name = "Sugar", price = 120.0, cost = 95.0, stockQuantity = 100),
        )
        every { productDao.getAllProducts() } returns flowOf(products)
        every { gemmaService.isAvailable } returns false

        val viewModel = InventoryListViewModel(
            productDao,
            serviceRepository,
            demandForecaster,
            productPhotoStore,
        )
        awaitIoCompletion()

        val forecasts = viewModel.forecasts.value
        assertTrue("Should generate forecast for product with high stock", forecasts.containsKey(1L))
    }

    @Test
    fun `no forecast for products with zero stock`() {
        val products = listOf(
            Product(id = 1, name = "Out of Stock", price = 50.0, cost = 30.0, stockQuantity = 0),
        )
        every { productDao.getAllProducts() } returns flowOf(products)
        every { gemmaService.isAvailable } returns false

        val viewModel = InventoryListViewModel(
            productDao,
            serviceRepository,
            demandForecaster,
            productPhotoStore,
        )
        awaitIoCompletion()

        val forecasts = viewModel.forecasts.value
        assertTrue("Should not forecast for zero-stock products", forecasts.isEmpty())
    }
}
