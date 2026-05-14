package com.biasharaai.ui.order

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import com.biasharaai.R
import com.biasharaai.ai.GemmaService
import com.biasharaai.data.local.db.AppSettings
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.SaleRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderParserViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val gemma = mockk<GemmaService>()
    private val productDao = mockk<ProductDao>()
    private val saleRepository = mockk<SaleRepository>()
    private val appSettingsDao = mockk<AppSettingsDao>()
    private val context = mockk<android.content.Context>(relaxed = true)
    private val connectivityManager = mockk<ConnectivityManager>()
    private val activeNetwork = mockk<Network>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { connectivityManager.activeNetwork } returns activeNetwork
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        every { context.getString(R.string.order_parser_skipped_toast, any()) } returns "skipped"
        every { context.getString(R.string.order_parser_no_matched_lines) } returns "none"
        every { context.getString(R.string.order_parser_record_failed) } returns "fail"
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    private fun newViewModel() = OrderParserViewModel(
        gemma,
        productDao,
        saleRepository,
        appSettingsDao,
        context,
    )

    @Test
    fun startParse_fuzzyMatch_setsResolvedProduct() = runBlocking {
        every { gemma.isAvailable } returns true
        coEvery { gemma.generateResponse(any()) } returns """
            [{"productName":"unga ngano","quantity":4,"unit":"mifuko"}]
        """.trimIndent()
        val matched = Product(id = 10L, name = "Unga wa ngano 2kg", price = 200.0, cost = 150.0, stockQuantity = 30)
        coEvery { productDao.findProductByNameFuzzy("unga ngano") } returns matched

        val vm = newViewModel()
        vm.startParse("WhatsApp order")

        delay(800)

        val state = vm.uiState.value
        assertTrue(state is OrderParserUiState.Ready)
        val lines = (state as OrderParserUiState.Ready).lines
        assertEquals(1, lines.size)
        assertTrue(lines[0].isMatched)
        assertEquals(matched.id, lines[0].resolvedProduct!!.id)
    }

    @Test
    fun startParse_unmatched_keepsLineForReview() = runBlocking {
        every { gemma.isAvailable } returns true
        coEvery { gemma.generateResponse(any()) } returns """
            [{"productName":"unknown sku","quantity":1,"unit":null}]
        """.trimIndent()
        coEvery { productDao.findProductByNameFuzzy("unknown sku") } returns null

        val vm = newViewModel()
        vm.startParse("order")

        delay(800)

        val state = vm.uiState.value as OrderParserUiState.Ready
        assertEquals(1, state.lines.size)
        assertFalse(state.lines[0].isMatched)
        assertEquals("unknown sku", state.lines[0].parsedName)
    }

    @Test
    fun recordSale_skippedUnmatched_emitsToastAndSaleRecorded() = runBlocking {
        every { gemma.isAvailable } returns true
        coEvery { gemma.generateResponse(any()) } returns """
            [{"productName":"Known","quantity":1,"unit":null},{"productName":"Unknown","quantity":2,"unit":null}]
        """.trimIndent()
        coEvery { productDao.findProductByNameFuzzy("Known") } returns Product(
            id = 1L,
            name = "Known product",
            price = 50.0,
            cost = 25.0,
            stockQuantity = 9,
        )
        coEvery { productDao.findProductByNameFuzzy("Unknown") } returns null
        every { appSettingsDao.getSettingsSync() } returns AppSettings(taxRate = 0.0)
        coEvery {
            saleRepository.commitPosSale(any(), any(), any(), any(), any(), any(), any(), any())
        } returns 999L

        val vm = newViewModel()
        vm.startParse("x")
        delay(900)

        val events = mutableListOf<OrderParserEvent>()
        val job = launch(Dispatchers.Unconfined) {
            vm.events.collect { events.add(it) }
        }

        vm.recordSale()
        delay(600)
        job.cancel()

        assertTrue(events.any { it is OrderParserEvent.Toast })
        assertTrue(
            events.any {
                it is OrderParserEvent.SaleRecorded && (it as OrderParserEvent.SaleRecorded).transactionId == 999L
            },
        )
    }

    @Test
    fun startParse_noDefaultNetwork_emitsOffline() = runBlocking {
        every { connectivityManager.activeNetwork } returns null
        val vm = newViewModel()
        vm.startParse("some order text")
        delay(200)
        assertTrue(vm.uiState.value is OrderParserUiState.Error)
        assertEquals("OFFLINE", (vm.uiState.value as OrderParserUiState.Error).message)
    }

    /**
     * Fixture phrases for Phase 2 acceptance (Swahili WhatsApp orders). Each round-trips through
     * [OrderParserViewModel.extractJsonArray] and Gson like production parsing.
     */
    @Test
    fun swahiliOrderPhrases_jsonRoundTrip() {
        assertEquals(5, SWAHILI_ORDER_PHRASES.size)
        val gson = Gson()
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        for (phrase in SWAHILI_ORDER_PHRASES) {
            val raw = """prefix [{"productName":"$phrase","quantity":2,"unit":"karton"}] suffix"""
            val json = OrderParserViewModel.extractJsonArray(raw)
            val list = gson.fromJson<List<Map<String, Any>>>(json, type)
            assertEquals(1, list!!.size)
            assertEquals(phrase, list[0]["productName"] as String)
        }
    }

    companion object {
        val SWAHILI_ORDER_PHRASES = listOf(
            "nne za unga wa ngano",
            "maziwa ya mtindi lita mbili",
            "sukari kg moja",
            "mafuta ya kupika",
            "soda crate moja",
        )
    }
}
