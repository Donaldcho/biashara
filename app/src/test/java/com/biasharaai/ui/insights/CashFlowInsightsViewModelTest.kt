package com.biasharaai.ui.insights

import android.content.Context
import com.biasharaai.ai.CashFlowAnalyzer
import com.biasharaai.ai.CapabilityResult
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.DeviceCapabilityChecker
import com.biasharaai.ai.GemmaService
import com.biasharaai.ai.ModelDownloadManager
import com.biasharaai.data.local.db.Transaction
import com.biasharaai.data.local.db.TransactionDao
import com.biasharaai.data.local.db.TransactionRepository
import com.biasharaai.data.local.db.TransactionType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CashFlowInsightsViewModel].
 *
 * Note: The ViewModel dispatches work on [Dispatchers.IO] which
 * runs on real threads in JVM unit tests. We use [Thread.sleep]
 * to allow those coroutines to settle before asserting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CashFlowInsightsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var transactionDao: TransactionDao
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var gemmaService: GemmaService
    private lateinit var cashFlowAnalyzer: CashFlowAnalyzer

    private val sampleTransactions = listOf(
        Transaction(id = 1, type = TransactionType.INCOME, amount = 10000.0, description = "Sales", date = System.currentTimeMillis()),
        Transaction(id = 2, type = TransactionType.INCOME, amount = 5000.0, description = "Services", date = System.currentTimeMillis()),
        Transaction(id = 3, type = TransactionType.EXPENSE, amount = 3000.0, description = "Rent", date = System.currentTimeMillis()),
        Transaction(id = 4, type = TransactionType.EXPENSE, amount = 2000.0, description = "Transport", date = System.currentTimeMillis()),
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
        } returns CapabilityResult(
            tier = CapabilityTier.PARTIAL_AI,
            apiLevel = 34,
            totalRamMb = 4096L,
            freeStorageMb = 8000L,
            meetsApiRequirement = true,
            meetsStorageRequirement = true,
        )

        transactionDao = mockk(relaxed = true)
        transactionRepository = TransactionRepository(transactionDao)
        gemmaService = mockk(relaxed = true)
        every { gemmaService.isAvailable } returns false
        val appContext = mockk<Context>(relaxed = true)
        val modelDm = mockk<ModelDownloadManager>(relaxed = true)
        every { modelDm.isModelDownloaded } returns true
        cashFlowAnalyzer = CashFlowAnalyzer(gemmaService, appContext, modelDm)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(android.util.Log::class)
        unmockkObject(DeviceCapabilityChecker)
    }

    /** Allow Dispatchers.IO coroutines to complete. */
    private fun awaitIoCompletion() {
        Thread.sleep(500)
    }

    @Test
    fun `after loading completes state is not loading`() {
        every { transactionDao.getTransactionsByPeriod(any(), any()) } returns flowOf(sampleTransactions)

        val viewModel = CashFlowInsightsViewModel(transactionRepository, cashFlowAnalyzer)
        awaitIoCompletion()

        assertFalse("Should not be loading after completion", viewModel.uiState.value.isLoading)
    }

    @Test
    fun `calculates correct totals from transactions`() {
        every { transactionDao.getTransactionsByPeriod(any(), any()) } returns flowOf(sampleTransactions)

        val viewModel = CashFlowInsightsViewModel(transactionRepository, cashFlowAnalyzer)
        awaitIoCompletion()

        val state = viewModel.uiState.value
        assertEquals(15000.0, state.totalIncome, 0.01)
        assertEquals(5000.0, state.totalExpenses, 0.01)
        assertEquals(10000.0, state.netCashFlow, 0.01)
    }

    @Test
    fun `insights text is not empty after loading`() {
        every { transactionDao.getTransactionsByPeriod(any(), any()) } returns flowOf(sampleTransactions)

        val viewModel = CashFlowInsightsViewModel(transactionRepository, cashFlowAnalyzer)
        awaitIoCompletion()

        assertTrue(
            "Insights text should not be blank",
            viewModel.uiState.value.insightsText.isNotBlank(),
        )
    }

    @Test
    fun `period label is set after loading`() {
        every { transactionDao.getTransactionsByPeriod(any(), any()) } returns flowOf(sampleTransactions)

        val viewModel = CashFlowInsightsViewModel(transactionRepository, cashFlowAnalyzer)
        awaitIoCompletion()

        assertTrue(
            "Period label should not be blank",
            viewModel.uiState.value.periodLabel.isNotBlank(),
        )
    }

    @Test
    fun `refresh reloads insights`() {
        every { transactionDao.getTransactionsByPeriod(any(), any()) } returns flowOf(sampleTransactions)

        val viewModel = CashFlowInsightsViewModel(transactionRepository, cashFlowAnalyzer)
        awaitIoCompletion()

        val firstInsights = viewModel.uiState.value.insightsText

        viewModel.refresh()
        awaitIoCompletion()

        assertEquals(firstInsights, viewModel.uiState.value.insightsText)
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
