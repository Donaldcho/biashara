package com.biasharaai.ui.insights

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.biasharaai.R
import com.biasharaai.databinding.FragmentInsightsOverviewBinding
import com.biasharaai.money.MoneyFormatter
import com.biasharaai.pos.cart.CartRepository
import com.biasharaai.ui.base.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class InsightsOverviewFragment : BaseFragment() {

    private var _binding: FragmentInsightsOverviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CashFlowInsightsViewModel by viewModels(ownerProducer = { requireParentFragment() })

    @Inject
    lateinit var moneyFormatter: MoneyFormatter

    @Inject
    lateinit var cartRepository: CartRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentInsightsOverviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPeriodSelector()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.uiState,
                    cartRepository.activeSettings,
                ) { state, _ -> state }.collect { state -> bindState(state) }
            }
        }
    }

    private fun setupPeriodSelector() {
        binding.chipToday.setOnClickListener {
            viewModel.selectPeriod(CashFlowInsightsViewModel.Period.TODAY)
        }
        binding.chipSevenDays.setOnClickListener {
            viewModel.selectPeriod(CashFlowInsightsViewModel.Period.LAST_7_DAYS)
        }
        binding.chipThirtyDays.setOnClickListener {
            viewModel.selectPeriod(CashFlowInsightsViewModel.Period.LAST_30_DAYS)
        }
        binding.chipMonth.setOnClickListener {
            viewModel.selectPeriod(CashFlowInsightsViewModel.Period.THIS_MONTH)
        }
    }

    private fun bindState(state: CashFlowInsightsViewModel.UiState) {
        binding.textPeriod.text = state.periodLabel
        binding.chipToday.isChecked = state.selectedPeriod == CashFlowInsightsViewModel.Period.TODAY
        binding.chipSevenDays.isChecked = state.selectedPeriod == CashFlowInsightsViewModel.Period.LAST_7_DAYS
        binding.chipThirtyDays.isChecked = state.selectedPeriod == CashFlowInsightsViewModel.Period.LAST_30_DAYS
        binding.chipMonth.isChecked = state.selectedPeriod == CashFlowInsightsViewModel.Period.THIS_MONTH

        binding.textTelemetryIncomeValue.text = moneyFormatter.format(state.totalIncome)
        binding.textTelemetryExpenseValue.text = moneyFormatter.format(state.totalExpenses)
        binding.textTelemetryNetValue.text = moneyFormatter.format(state.netCashFlow)
        binding.textTelemetryTransactionValue.text = state.transactionCount.toString()
        binding.textTelemetryAverageValue.text = moneyFormatter.format(state.averageSale)
        binding.chartTelemetry.submitPoints(state.telemetryPoints)

        binding.textLastUpdated.text = if (state.lastUpdatedMillis > 0L) {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(state.lastUpdatedMillis))
            getString(R.string.insights_updated_format, time)
        } else {
            ""
        }

        val maxAmount = maxOf(state.totalIncome, state.totalExpenses, 1.0)
        val incomePercent = normalizedBarPercent(state.totalIncome, maxAmount)
        val expensePercent = normalizedBarPercent(state.totalExpenses, maxAmount)

        binding.barIncome.post {
            val parentWidth = (binding.barIncome.parent as View).width
            val params = binding.barIncome.layoutParams
            params.width = barWidth(parentWidth, incomePercent)
            binding.barIncome.layoutParams = params
        }
        binding.barExpenses.post {
            val parentWidth = (binding.barExpenses.parent as View).width
            val params = binding.barExpenses.layoutParams
            params.width = barWidth(parentWidth, expensePercent)
            binding.barExpenses.layoutParams = params
        }

        binding.textIncomeAmount.text = moneyFormatter.format(state.totalIncome)
        binding.textExpensesAmount.text = moneyFormatter.format(state.totalExpenses)

        val netFormatted = moneyFormatter.format(abs(state.netCashFlow))
        binding.textNetCashFlow.text = if (state.netCashFlow >= 0) {
            getString(R.string.insights_net_positive, netFormatted)
        } else {
            getString(R.string.insights_net_negative, netFormatted)
        }

        if (state.isLoading) {
            binding.progressInsights.visibility = View.VISIBLE
            binding.textInsightsNarrative.visibility = View.GONE
        } else {
            binding.progressInsights.visibility = View.GONE
            binding.textInsightsNarrative.visibility = View.VISIBLE
            binding.textInsightsNarrative.text = state.insightsText
        }

        bindPerformers(state.productRevenue, state.serviceRevenue, state.topProducts, state.topServices)
    }

    private fun bindPerformers(
        productRevenue: Double,
        serviceRevenue: Double,
        products: List<CashFlowInsightsViewModel.TopProduct>,
        services: List<CashFlowInsightsViewModel.TopService>,
    ) {
        val hasData = productRevenue > 0 || serviceRevenue > 0 || products.isNotEmpty() || services.isNotEmpty()
        binding.cardTopProducts.visibility = if (hasData) View.VISIBLE else View.GONE
        if (!hasData) return

        // Revenue split stacked bar
        val total = productRevenue + serviceRevenue
        val serviceWeight = if (total > 0) (serviceRevenue / total * 100).toFloat() else 0f
        val productWeight = if (total > 0) (productRevenue / total * 100).toFloat() else 0f
        (binding.barServicesSegment.layoutParams as LinearLayout.LayoutParams).weight = serviceWeight
        binding.barServicesSegment.requestLayout()
        (binding.barProductsSegment.layoutParams as LinearLayout.LayoutParams).weight = productWeight
        binding.barProductsSegment.requestLayout()
        binding.textRevenueServicesAmount.text = moneyFormatter.format(serviceRevenue)
        binding.textRevenueProductsAmount.text = moneyFormatter.format(productRevenue)

        // Top services
        binding.labelTopServices.visibility = if (services.isEmpty()) View.GONE else View.VISIBLE
        binding.containerTopServices.removeAllViews()
        services.forEachIndexed { index, service ->
            binding.containerTopServices.addView(makeRankRow(
                rank = index + 1,
                text = getString(R.string.insights_top_service_row, service.name,
                    moneyFormatter.format(service.revenue), service.sessions),
                index = index,
            ))
        }

        // Top products
        binding.labelTopProducts.visibility = if (products.isEmpty()) View.GONE else View.VISIBLE
        binding.containerTopProducts.removeAllViews()
        products.forEachIndexed { index, product ->
            binding.containerTopProducts.addView(makeRankRow(
                rank = index + 1,
                text = getString(R.string.insights_top_product_row, product.name,
                    moneyFormatter.format(product.revenue), product.qty),
                index = index,
            ))
        }
    }

    private fun makeRankRow(rank: Int, text: String, index: Int): TextView {
        val dp4 = (4 * resources.displayMetrics.density).toInt()
        val row = TextView(requireContext())
        row.setPadding(0, if (index == 0) 0 else dp4, 0, 0)
        row.textSize = 13f
        row.gravity = Gravity.START
        row.text = "$rank. $text"
        return row
    }

    private fun normalizedBarPercent(amount: Double, maxAmount: Double): Float =
        (amount.coerceAtLeast(0.0) / maxAmount).toFloat()

    private fun barWidth(parentWidth: Int, percent: Float): Int =
        if (percent <= 0f) 0 else (parentWidth * percent).toInt().coerceAtLeast(4)
}
