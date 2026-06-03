package com.biasharaai.ui.ledger

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.biasharaai.R
import com.biasharaai.data.local.db.MoneyDraft
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
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class LedgerFragment : BaseFragment() {

    private var _binding: FragmentLedgerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LedgerViewModel by viewModels()
    private lateinit var adapter: LedgerEntryAdapter
    private lateinit var draftAdapter: MoneyDraftAdapter
    private var currentView = MoneyView.INBOX
    private var lastState: LedgerViewModel.UiState? = null

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
        draftAdapter = MoneyDraftAdapter(
            moneyFormatter = moneyFormatter,
            onApprove = { draft -> showDraftReviewDialog(draft) },
            onReject = { draft -> viewModel.rejectDraft(draft.id) },
        )
        binding.recyclerEntries.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerEntries.adapter = adapter
        binding.recyclerDrafts.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerDrafts.adapter = draftAdapter

        binding.editSearch.doAfterTextChanged {
            viewModel.setSearchQuery(it?.toString().orEmpty())
        }
        binding.toggleMoneyView.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            currentView = when (checkedId) {
                R.id.btn_money_ledger -> MoneyView.LEDGER
                R.id.btn_money_review -> MoneyView.CASH_REVIEW
                else -> MoneyView.INBOX
            }
            lastState?.let { bindMode(it) }
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
                    CashQuickActionBar.CashDestination.SmsImport ->
                        requireParentFragment().findNavController().navigate(
                            R.id.action_global_sms_import,
                        )
                    is CashQuickActionBar.CashDestination.QrGenerator ->
                        requireParentFragment().findNavController().navigate(
                            R.id.action_global_qr_generator,
                        )
                }
            }
        }

        binding.btnCashCount.setOnClickListener {
            findNavController().navigate(R.id.action_insightsFragment_to_cashCountFragment)
        }
        binding.fabActions.setOnClickListener { showActionsMenu() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state -> bindState(state) }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is LedgerViewModel.Event.PermissionDenied -> {
                                com.google.android.material.snackbar.Snackbar.make(
                                    binding.root,
                                    getString(
                                        R.string.settings_enterprise_permission_denied,
                                        event.operatorName,
                                        event.operatorRole,
                                    ),
                                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG,
                                ).show()
                            }
                            LedgerViewModel.Event.DraftApproved -> {
                                com.google.android.material.snackbar.Snackbar.make(
                                    binding.root,
                                    R.string.money_draft_approved,
                                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                                ).show()
                            }
                            LedgerViewModel.Event.DraftRejected -> {
                                com.google.android.material.snackbar.Snackbar.make(
                                    binding.root,
                                    R.string.money_draft_rejected,
                                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                                ).show()
                            }
                            is LedgerViewModel.Event.Error -> {
                                com.google.android.material.snackbar.Snackbar.make(
                                    binding.root,
                                    event.message,
                                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG,
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun bindState(state: LedgerViewModel.UiState) {
        lastState = state
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
        draftAdapter.submitList(state.pendingDrafts)
        binding.btnMoneyInbox.text = if (state.pendingDrafts.isEmpty()) {
            getString(R.string.money_tab_inbox)
        } else {
            getString(R.string.money_tab_inbox_count, state.pendingDrafts.size)
        }
        binding.textReviewExpected.text = getString(
            R.string.money_review_expected,
            moneyFormatter.format(state.runningBalance),
        )
        binding.textReviewLatestCount.text = state.latestCashCount?.let { count ->
            getString(
                R.string.money_review_latest_count,
                moneyFormatter.format(count.actualBalance),
                moneyFormatter.format(count.difference),
            )
        } ?: getString(R.string.money_review_no_count)
        binding.textEmpty.isVisible = state.entries.isEmpty()
        binding.recyclerEntries.isVisible = state.entries.isNotEmpty()
        binding.textEmptyDrafts.isVisible = state.pendingDrafts.isEmpty()
        binding.recyclerDrafts.isVisible = state.pendingDrafts.isNotEmpty()
        bindMode(state)
    }

    private fun bindMode(state: LedgerViewModel.UiState) {
        binding.layoutMoneyInbox.isVisible = currentView == MoneyView.INBOX
        binding.layoutLedgerList.isVisible = currentView == MoneyView.LEDGER
        binding.layoutCashReview.isVisible = currentView == MoneyView.CASH_REVIEW
        binding.textEmpty.isVisible = currentView == MoneyView.LEDGER && state.entries.isEmpty()
        binding.recyclerEntries.isVisible = currentView == MoneyView.LEDGER && state.entries.isNotEmpty()
        binding.textEmptyDrafts.isVisible = currentView == MoneyView.INBOX && state.pendingDrafts.isEmpty()
        binding.recyclerDrafts.isVisible = currentView == MoneyView.INBOX && state.pendingDrafts.isNotEmpty()
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

    private fun showDraftReviewDialog(draft: MoneyDraft) {
        val ctx = requireContext()
        val pad = resources.getDimensionPixelSize(R.dimen.pos_dialog_padding)
        val amountInput = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.ledger_amount_hint)
            setText(String.format(Locale.US, "%.2f", draft.amount))
            selectAll()
        }
        val descriptionInput = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            hint = getString(R.string.ledger_description_hint)
            setText(draft.description)
        }
        val notesInput = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            hint = getString(R.string.ledger_notes_hint)
            minLines = 2
            setText(draft.notes.orEmpty())
        }
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(amountInput)
            addView(descriptionInput)
            addView(notesInput)
        }
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.money_draft_review_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.money_draft_approve, null)
            .show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val amount = amountInput.text?.toString()?.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                amountInput.error = getString(R.string.ledger_invalid_amount)
                return@setOnClickListener
            }
            viewModel.approveDraft(
                draftId = draft.id,
                amount = amount,
                description = descriptionInput.text?.toString(),
                notes = notesInput.text?.toString(),
            )
            dialog.dismiss()
        }
    }

    private fun shareReport() {
        viewLifecycleOwner.lifecycleScope.launch {
            val csv = viewModel.exportCsv() ?: return@launch
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
        binding.recyclerDrafts.adapter = null
        super.onDestroyView()
        _binding = null
    }

    private enum class MoneyView {
        INBOX,
        LEDGER,
        CASH_REVIEW,
    }
}
