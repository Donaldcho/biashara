package com.biasharaai.ui.pos

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.databinding.FragmentEndOfDayBinding
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@AndroidEntryPoint
class EndOfDayFragment : BaseFragment() {

    private var _binding: FragmentEndOfDayBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EndOfDayViewModel by viewModels()

    private val moneyFormat: NumberFormat =
        NumberFormat.getCurrencyInstance(Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentEndOfDayBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.btnShare.setOnClickListener {
            val text = viewModel.shareText()
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(send, getString(R.string.end_of_day_share)))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progress.isVisible = state.loading
                    binding.textNarrative.text = state.narrative
                    binding.chipGroupStats.removeAllViews()
                    val stats = state.stats
                    if (stats != null && !state.loading) {
                        fun chip(label: String): Chip = Chip(requireContext()).apply {
                            text = label
                            isCheckable = false
                            isClickable = false
                        }
                        val salesLabel = if (state.currencySymbol.isNotBlank()) {
                            "${moneyFormat.format(stats.totalSales)} ${state.currencySymbol}".trim()
                        } else {
                            moneyFormat.format(stats.totalSales)
                        }
                        binding.chipGroupStats.addView(
                            chip(getString(R.string.end_of_day_stat_sales, salesLabel)),
                        )
                        binding.chipGroupStats.addView(
                            chip(getString(R.string.end_of_day_stat_tx, stats.transactionCount)),
                        )
                        binding.chipGroupStats.addView(
                            chip(getString(R.string.end_of_day_stat_top, stats.topProductName, stats.topProductQty)),
                        )
                        binding.chipGroupStats.addView(
                            chip(
                                getString(
                                    R.string.end_of_day_stat_pay,
                                    stats.cashPct,
                                    stats.mobilePct,
                                    stats.creditPct,
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
