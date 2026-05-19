package com.biasharaai.ui.insights

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.databinding.FragmentCashFlowInsightsBinding
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint

/**
 * Insights host: **Cash flow** tab + **Credit** tab (Prompt U6).
 */
@AndroidEntryPoint
class CashFlowInsightsFragment : BaseFragment() {

    private var _binding: FragmentCashFlowInsightsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CashFlowInsightsViewModel by viewModels()

    private var tabMediator: TabLayoutMediator? = null

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
        binding.pagerInsights.adapter = InsightsPagerAdapter(this)
        val initialTab = arguments?.getInt(ARG_INITIAL_TAB, TAB_FLOW)?.coerceIn(TAB_FLOW, TAB_LEDGER)
            ?: TAB_FLOW
        binding.pagerInsights.setCurrentItem(initialTab, false)
        tabMediator = TabLayoutMediator(
            binding.tabInsights,
            binding.pagerInsights,
        ) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.insights_tab_flow)
                1 -> getString(R.string.insights_tab_credit)
                else -> getString(R.string.insights_tab_ledger)
            }
        }.also { it.attach() }
    }

    override fun onDestroyView() {
        tabMediator?.detach()
        tabMediator = null
        binding.pagerInsights.adapter = null
        super.onDestroyView()
        _binding = null
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_refresh -> {
                    viewModel.refresh()
                    true
                }
                R.id.action_transactions -> {
                    findNavController().navigate(
                        R.id.action_insightsFragment_to_transactionHistoryFragment,
                    )
                    true
                }
                else -> false
            }
        }
    }

    companion object {
        const val ARG_INITIAL_TAB = "initialTab"
        const val TAB_FLOW = 0
        const val TAB_CREDIT = 1
        const val TAB_LEDGER = 2
    }
}
