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
import com.biasharaai.databinding.FragmentInsightsOverviewBinding
import com.biasharaai.money.MoneyFormatter
import com.biasharaai.pos.cart.CartRepository
import com.biasharaai.ui.base.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
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
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.uiState,
                    cartRepository.activeSettings,
                ) { state, _ -> state }.collect { state -> bindState(state) }
            }
        }
    }

    private fun bindState(state: CashFlowInsightsViewModel.UiState) {
        binding.textPeriod.text = state.periodLabel

        val maxAmount = maxOf(state.totalIncome, state.totalExpenses, 1.0)
        val incomePercent = (state.totalIncome / maxAmount).toFloat()
        val expensePercent = (state.totalExpenses / maxAmount).toFloat()

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
    }
}
