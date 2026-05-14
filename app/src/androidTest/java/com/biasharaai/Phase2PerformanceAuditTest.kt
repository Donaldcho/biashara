package com.biasharaai

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.GemmaService
import com.biasharaai.data.local.db.AppDatabase
import com.biasharaai.data.local.db.Customer
import com.biasharaai.data.local.db.LossAlertEngine
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.SaleLineItem
import com.biasharaai.data.local.db.Transaction
import com.biasharaai.data.local.db.TransactionType
import com.biasharaai.pos.CustomerSuggestionEngine
import com.biasharaai.receipt.ReceiptParser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prompt U10 — automated performance budgets (proxy for manual profiling).
 *
 * - **Loss alerts:** [LossAlertWorker] hot path is [LossAlertEngine.runAllDetections] + DB writes; worker
 *   adds Gemma translation only on FULL_AI + non-English. Budget **< 10s** matches Phase 2 checklist.
 * - **Receipt OCR path:** full capture → ML Kit → Gemma is device-dependent; this test only times
 *   [ReceiptParser.parseFromOcrText] with a mocked Gemma response (budget **< 15s**, typically ms).
 * - **Customer suggestions:** [CustomerSuggestionEngine.topProductsForCustomer] Room query; checklist
 *   **< 100ms** on device — CI emulators use a relaxed ceiling.
 */
@RunWith(AndroidJUnit4::class)
class Phase2PerformanceAuditTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun lossAlertEngine_runAllDetections_underTenSeconds() = runBlocking {
        val engine = LossAlertEngine(db.lossAlertDao(), db.productDao())
        db.productDao().insertProduct(Product(name = "Low", price = 1.0, cost = 0.5, stockQuantity = 2))
        val t0 = System.nanoTime()
        engine.runAllDetections(System.currentTimeMillis())
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("runAllDetections took ${ms}ms", ms < 10_000)
    }

    @Test
    fun customerSuggestion_topProducts_underRelaxedCeiling_withHeavyHistory() = runBlocking {
        val customerDao = db.customerDao()
        val productDao = db.productDao()
        val txDao = db.transactionDao()
        val lineDao = db.saleLineItemDao()
        val gemma = mockk<GemmaService>(relaxed = true)
        every { gemma.isAvailable } returns false

        val cid = customerDao.insertCustomer(Customer(name = "Heavy buyer", createdAt = 1L))
        val productIds = mutableListOf<Long>()
        repeat(5) { i ->
            val pid = productDao.insertProduct(
                Product(name = "SKU-$i", price = 10.0 + i, cost = 5.0, stockQuantity = 100),
            )
            productIds.add(pid)
        }

        var t = 1000L
        repeat(520) { i ->
            val pid = productIds[i % productIds.size]
            val txId = txDao.insertTransaction(
                Transaction(
                    type = TransactionType.INCOME,
                    amount = 50.0,
                    description = "POS",
                    date = t++,
                    customerId = cid,
                ),
            )
            lineDao.insertLineItem(
                SaleLineItem(
                    transactionId = txId,
                    productId = pid,
                    productName = "SKU",
                    unitPrice = 10.0,
                    quantity = 1,
                    lineTotal = 10.0,
                ),
            )
        }

        val engine = CustomerSuggestionEngine(lineDao, productDao, CapabilityTier.PARTIAL_AI, gemma)
        val t0 = System.nanoTime()
        val top = engine.topProductsForCustomer(cid, limit = 3)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("topProductsForCustomer took ${ms}ms (device target <100ms)", top.isNotEmpty())
        assertTrue("topProductsForCustomer took ${ms}ms (CI ceiling 3s)", ms < 3000)
    }

    @Test
    fun receiptParser_parseFromOcrText_mockedGemma_underFifteenSeconds() = runBlocking {
        val gemma = mockk<GemmaService>()
        every { gemma.isAvailable } returns true
        coEvery { gemma.generateResponse(any()) } returns """[{"name":"A","quantity":1.0,"cost":1.0}]"""
        val parser = ReceiptParser(gemma)
        val t0 = System.nanoTime()
        val result = parser.parseFromOcrText("receipt line text")
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue(result is ReceiptParser.ParseResult.Success)
        assertTrue("parseFromOcrText took ${ms}ms (UAT full pipeline target <15s)", ms < 15_000)
    }
}
