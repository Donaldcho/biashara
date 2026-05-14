package com.biasharaai.ai

import android.content.Context
import com.biasharaai.data.local.db.Transaction
import com.biasharaai.data.local.db.TransactionType
import com.biasharaai.money.MoneyFormatter
import com.biasharaai.pos.cart.CartRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CashFlowAnalyzerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var gemmaService: GemmaService
    private lateinit var appContext: Context
    private lateinit var modelDownloadManager: ModelDownloadManager

    private fun capabilityResult(tier: CapabilityTier) = CapabilityResult(
        tier = tier,
        apiLevel = 34,
        totalRamMb = 8192L,
        freeStorageMb = 10_000L,
        meetsApiRequirement = true,
        meetsStorageRequirement = true,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        mockkObject(DeviceCapabilityChecker)
        every {
            DeviceCapabilityChecker.evaluate(any(), any())
        } returns capabilityResult(CapabilityTier.PARTIAL_AI)

        gemmaService = mockk(relaxed = true)
        every { gemmaService.isAvailable } returns true
        appContext = mockk(relaxed = true)
        modelDownloadManager = mockk(relaxed = true)
        every { modelDownloadManager.isModelDownloaded } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(android.util.Log::class)
        unmockkObject(DeviceCapabilityChecker)
    }

    private fun analyzer() = CashFlowAnalyzer(
        gemmaService,
        appContext,
        modelDownloadManager,
        testMoneyFormatter(),
    )

    private fun testMoneyFormatter(): MoneyFormatter {
        val cartRepo = mockk<CartRepository>(relaxed = true)
        every { cartRepo.activeSettings } returns MutableStateFlow(null)
        return MoneyFormatter(cartRepo)
    }

    private fun sampleTransactions() = listOf(
        Transaction(id = 1, type = TransactionType.INCOME, amount = 10000.0, description = "Sales", date = 1_000L),
        Transaction(id = 2, type = TransactionType.INCOME, amount = 5000.0, description = "Services", date = 2_000L),
        Transaction(id = 3, type = TransactionType.EXPENSE, amount = 3000.0, description = "Rent", date = 1_000L),
        Transaction(id = 4, type = TransactionType.EXPENSE, amount = 2000.0, description = "Transport", date = 1_500L),
        Transaction(id = 5, type = TransactionType.EXPENSE, amount = 1500.0, description = "Rent", date = 2_000L),
    )

    @Test
    fun `generateInsights produces rules summary when AI unavailable`() = runTest {
        every { gemmaService.isAvailable } returns false

        val result = analyzer().generateInsights(sampleTransactions(), "en")

        assertTrue(result.contains("Income"))
        assertTrue(result.contains("Expenses"))
        assertTrue(result.contains("Financial Summary"))
    }

    @Test
    fun `rules fallback shows profit when income exceeds expenses`() = runTest {
        every { gemmaService.isAvailable } returns false

        val result = analyzer().generateInsights(sampleTransactions(), "en")
        assertTrue(result.contains("saving"))
    }

    @Test
    fun `rules fallback shows warning when expenses exceed income`() = runTest {
        every { gemmaService.isAvailable } returns false

        val lossTransactions = listOf(
            Transaction(id = 1, type = TransactionType.INCOME, amount = 1000.0, description = "Sales", date = 1_000L),
            Transaction(id = 2, type = TransactionType.EXPENSE, amount = 5000.0, description = "Rent", date = 1_000L),
        )

        val result = analyzer().generateInsights(lossTransactions, "en")
        assertTrue(result.contains("expenses exceed"))
    }

    @Test
    fun `rules fallback lists top expense categories`() = runTest {
        every { gemmaService.isAvailable } returns false

        val result = analyzer().generateInsights(sampleTransactions(), "en")
        assertTrue(result.contains("Rent"))
        assertTrue(result.contains("Transport"))
    }

    @Test
    fun `partial AI tier never calls LLM even if model is on disk`() = runTest {
        every {
            DeviceCapabilityChecker.evaluate(any(), any())
        } returns capabilityResult(CapabilityTier.PARTIAL_AI)
        every { gemmaService.isAvailable } returns true

        val result = analyzer().generateInsights(sampleTransactions(), "en")
        assertTrue(result.contains("Financial Summary"))
        coVerify(exactly = 0) { gemmaService.generateResponse(any()) }
    }

    @Test
    fun `generateInsights calls AI when full AI tier and available`() = runTest {
        every {
            DeviceCapabilityChecker.evaluate(any(), any())
        } returns capabilityResult(CapabilityTier.FULL_AI)
        every { gemmaService.isAvailable } returns true
        coEvery { gemmaService.generateResponse(any()) } returns
            "Your business is doing great! You're saving 57% of your income."

        val result = analyzer().generateInsights(sampleTransactions(), "en")
        assertTrue(result.contains("doing great"))
    }

    @Test
    fun `AI path falls back to rules on exception`() = runTest {
        every {
            DeviceCapabilityChecker.evaluate(any(), any())
        } returns capabilityResult(CapabilityTier.FULL_AI)
        every { gemmaService.isAvailable } returns true
        coEvery { gemmaService.generateResponse(any()) } throws RuntimeException("Model error")

        val result = analyzer().generateInsights(sampleTransactions(), "en")
        assertTrue(result.contains("Financial Summary"))
    }

    @Test
    fun `generateInsights handles empty transaction list`() = runTest {
        every { gemmaService.isAvailable } returns false

        val result = analyzer().generateInsights(emptyList(), "en")
        assertFalse(result.isBlank())
    }
}
