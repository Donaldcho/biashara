package com.biasharaai.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [ProductDao] using an in-memory Room database.
 *
 * Acceptance criterion: Room query time < 50ms for 1,000 products.
 */
@RunWith(AndroidJUnit4::class)
class ProductDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var productDao: ProductDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        productDao = database.productDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ── Insert & Retrieve ───────────────────────────────────────────────

    @Test
    fun insertProduct_andGetAll_returnsInsertedProduct() = runTest {
        val product = Product(
            name = "Sugar 1kg",
            price = 120.0,
            cost = 95.0,
            stockQuantity = 50,
        )
        val id = productDao.insertProduct(product)
        assertTrue("Insert should return positive ID", id > 0)

        val all = productDao.getAllProducts().first()
        assertEquals(1, all.size)
        assertEquals("Sugar 1kg", all[0].name)
        assertEquals(50, all[0].stockQuantity)
    }

    // ── Barcode Lookup ──────────────────────────────────────────────────

    @Test
    fun getProductByBarcode_returnsCorrectProduct() = runTest {
        productDao.insertProduct(
            Product(name = "Milk", price = 80.0, cost = 60.0, stockQuantity = 30, barcodeValue = "4901234567890"),
        )
        productDao.insertProduct(
            Product(name = "Bread", price = 50.0, cost = 35.0, stockQuantity = 20, barcodeValue = "5901234567890"),
        )

        val result = productDao.getProductByBarcode("4901234567890").first()
        assertNotNull(result)
        assertEquals("Milk", result!!.name)
    }

    @Test
    fun getProductByBarcode_nonExistent_returnsNull() = runTest {
        productDao.insertProduct(
            Product(name = "Milk", price = 80.0, cost = 60.0, stockQuantity = 30, barcodeValue = "4901234567890"),
        )

        val result = productDao.getProductByBarcode("0000000000000").first()
        assertNull(result)
    }

    // ── Ordering ────────────────────────────────────────────────────────

    @Test
    fun getAllProducts_orderedByNameAsc() = runTest {
        productDao.insertProduct(Product(name = "Zucchini", price = 40.0, cost = 30.0, stockQuantity = 10))
        productDao.insertProduct(Product(name = "Apple", price = 60.0, cost = 45.0, stockQuantity = 25))
        productDao.insertProduct(Product(name = "Mango", price = 100.0, cost = 70.0, stockQuantity = 15))

        val all = productDao.getAllProducts().first()
        assertEquals(listOf("Apple", "Mango", "Zucchini"), all.map { it.name })
    }

    // ── Update ──────────────────────────────────────────────────────────

    @Test
    fun updateProduct_reflectsChanges() = runTest {
        val id = productDao.insertProduct(
            Product(name = "Rice 5kg", price = 350.0, cost = 280.0, stockQuantity = 100),
        )

        val existing = productDao.getProductById(id).first()!!
        productDao.updateProduct(existing.copy(stockQuantity = 85))

        val updated = productDao.getProductById(id).first()!!
        assertEquals(85, updated.stockQuantity)
    }

    // ── Delete ──────────────────────────────────────────────────────────

    @Test
    fun deleteProduct_removesFromDatabase() = runTest {
        val product = Product(name = "Soda", price = 50.0, cost = 30.0, stockQuantity = 200)
        val id = productDao.insertProduct(product)

        val inserted = productDao.getProductById(id).first()!!
        productDao.deleteProduct(inserted)

        val all = productDao.getAllProducts().first()
        assertTrue(all.isEmpty())
    }

    // ── Performance: 1,000 products query < 50ms ────────────────────────

    @Test
    fun queryPerformance_1000Products_under50ms() = runTest {
        // Insert 1,000 products
        repeat(1_000) { i ->
            productDao.insertProduct(
                Product(
                    name = "Product $i",
                    price = 100.0 + i,
                    cost = 50.0 + i,
                    stockQuantity = 10 + (i % 100),
                    category = "Category ${i % 10}",
                    barcodeValue = "BC${i.toString().padStart(10, '0')}",
                ),
            )
        }

        // Time the query
        val startNanos = System.nanoTime()
        val products = productDao.getAllProducts().first()
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000

        assertEquals(1_000, products.size)
        assertTrue(
            "Query took ${elapsedMs}ms — exceeds 50ms acceptance criterion",
            elapsedMs < 50,
        )
    }
}
