package com.biasharaai.ui.insights

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.biasharaai.R
import com.biasharaai.databinding.FragmentCashFlowInsightsBinding
import com.biasharaai.ui.base.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/**
 * Displays cash flow insights with a visual chart and AI-generated narrative.
 *
 * Replaces the placeholder InsightsFragment.
 */
@AndroidEntryPoint
class CashFlowInsightsFragment : BaseFragment() {

    private var _binding: FragmentCashFlowInsightsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CashFlowInsightsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCashFlowInsightsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        observeState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_refresh -> {
                    viewModel.refresh()
                    true
                }
                else -> false
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    bindState(state)
                }
            }
        }
    }

    private fun bindState(state: CashFlowInsightsViewModel.UiState) {
        val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())

        // Period
        binding.textPeriod.text = state.periodLabel

        // Chart bars — animate widths as percentage of the larger value
        val maxAmount = maxOf(state.totalIncome, state.totalExpenses, 1.0)

        val incomePercent = (state.totalIncome / maxAmount).toFloat()
        val expensePercent = (state.totalExpenses / maxAmount).toFloat()

        // Set bar widths using LayoutParams weight trick on the parent FrameLayout
        binding.barIncome.post {
            val parentWidth = (binding.barIncome.parent as View).width
            val params = binding.barIncome.layoutParams
            params.width = (parentWidth * incomePercent).toInt().coerceAtLeast(4)
            binding.barIncome.layoutParams = params
        }

        binding.barExpenses.post {
            val parentWidth = (binding.barExpenses.parent as View).width
            val params = binding.barExpenses.layoutParams
            params.width = (parentWidth * expensePercent).toInt().coerceAtLeast(4)
            binding.barExpenses.layoutParams = params
        }

        // Amounts
        binding.textIncomeAmount.text = formatter.format(state.totalIncome)
        binding.textExpensesAmount.text = formatter.format(state.totalExpenses)

        // Net cash flow
        val netFormatted = formatter.format(abs(state.netCashFlow))
        binding.textNetCashFlow.text = if (state.netCashFlow >= 0) {
            getString(R.string.insights_net_positive, netFormatted)
        } else {
            getString(R.string.insights_net_negative, netFormatted)
        }

        // Loading / narrative
        if (state.isLoading) {
            binding.progressInsights.visibility = View.VISIBLE
            binding.textInsightsNarrative.visibility = View.GONE
        } else {
            binding.progressInsights.visibility = View.GONE
            binding.textInsightsNarrative.visibility = View.VISIBLE
            binding.textInsightsNarrative.text = state.insightsText
        }
    }
}
