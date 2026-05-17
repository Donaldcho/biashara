package com.biasharaai.ui.ledger

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.biasharaai.R
import com.biasharaai.databinding.FragmentLedgerBinding
import com.biasharaai.money.MoneyFormatter
import androidx.core.os.bundleOf
import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.ui.base.BaseFragment
import com.biasharaai.ui.cash.CashQuickActionBar
import com.biasharaai.ui.cash.CashScanFragment
import com.biasharaai.ui.cash.ManualEntryFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LedgerFragment : BaseFragment() {

    private var _binding: FragmentLedgerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LedgerViewModel by viewModels()
    private lateinit var adapter: LedgerEntryAdapter

    @Inject
    lateinit var moneyFormatter: MoneyFormatter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLedgerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = LedgerEntryAdapter(moneyFormatter)
        binding.recyclerEntries.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerEntries.adapter = adapter

        binding.editSearch.doAfterTextChanged {
            viewModel.setSearchQuery(it?.toString().orEmpty())
        }

        binding.cashQuickActionBar.wireNavigation { destination ->
            runCatching {
                when (destination) {
                    is CashQuickActionBar.CashDestination.Scan ->
                        requireParentFragment().findNavController().navigate(
                            R.id.action_global_cash_scan,
                            bundleOf(CashScanFragment.ARG_DIRECTION to destination.direction.name),
                        )
                    is CashQuickActionBar.CashDestination.Manual ->
                        requireParentFragment().findNavController().navigate(
                            R.id.action_global_manual_entry,
                            bundleOf(ManualEntryFragment.ARG_DIRECTION to destination.direction.name),
                        )
                    is CashQuickActionBar.CashDestination.QrGenerator ->
                        requireParentFragment().findNavController().navigate(
                            R.id.action_global_qr_generator,
                        )
                }
            }
        }

        binding.fabActions.setOnClickListener { showActionsMenu() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> bindState(state) }
            }
        }
    }

    private fun bindState(state: LedgerViewModel.UiState) {
        binding.textPeriod.text = state.periodLabel
        binding.textRunningBalance.text = getString(
            R.string.ledger_running_balance,
            moneyFormatter.format(state.runningBalance),
        )
        binding.textMoneyIn.text = getString(
            R.string.ledger_money_in,
            moneyFormatter.format(state.moneyIn),
        )
        binding.textMoneyOut.text = getString(
            R.string.ledger_money_out,
            moneyFormatter.format(state.moneyOut),
        )
        binding.textPendingCredit.text = getString(
            R.string.ledger_pending_credit,
            moneyFormatter.format(state.pendingCredit),
        )
        adapter.submitList(state.entries)
        binding.textEmpty.isVisible = state.entries.isEmpty()
        binding.recyclerEntries.isVisible = state.entries.isNotEmpty()
    }

    private fun showActionsMenu() {
        val items = arrayOf(
            getString(R.string.ledger_action_manual),
            getString(R.string.ledger_action_cash_count),
            getString(R.string.ledger_action_export),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.ledger_fab_actions)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> findNavController().navigate(R.id.action_insightsFragment_to_manualLedgerEntryFragment)
                    1 -> findNavController().navigate(R.id.action_insightsFragment_to_cashCountFragment)
                    2 -> shareReport()
                }
            }
            .show()
    }

    private fun shareReport() {
        viewLifecycleOwner.lifecycleScope.launch {
            val csv = viewModel.exportCsv()
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.ledger_export_subject))
                putExtra(Intent.EXTRA_TEXT, csv)
            }
            startActivity(Intent.createChooser(send, getString(R.string.ledger_export_chooser)))
        }
    }

    override fun onDestroyView() {
        binding.recyclerEntries.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
