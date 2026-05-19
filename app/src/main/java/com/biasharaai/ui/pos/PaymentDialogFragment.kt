package com.biasharaai.ui.pos

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.databinding.FragmentPaymentDialogBinding
import com.biasharaai.money.MoneyFormatter
import com.biasharaai.pos.cart.CartRepository
import com.biasharaai.pos.payment.MixedPaymentPlan
import com.biasharaai.pos.payment.PrimaryPaymentTab
import com.biasharaai.pos.payment.SplitLineMethod
import com.google.android.material.color.MaterialColors
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class PaymentDialogFragment : DialogFragment() {

    private var _binding: FragmentPaymentDialogBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PaymentViewModel by viewModels()

    @Inject
    lateinit var cartRepository: CartRepository

    @Inject
    lateinit var moneyFormatter: MoneyFormatter

    private val dateFormat: DateFormat =
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())

    override fun getTheme(): Int = R.style.Theme_BiasharaAI_FullScreenPaymentDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPaymentDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.togglePaymentTabs.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.tab_cash -> viewModel.selectTab(PrimaryPaymentTab.CASH)
                R.id.tab_mobile -> viewModel.selectTab(PrimaryPaymentTab.MOBILE_MONEY)
                R.id.tab_credit -> viewModel.selectTab(PrimaryPaymentTab.CREDIT)
                R.id.tab_voucher -> viewModel.selectTab(PrimaryPaymentTab.VOUCHER)
            }
        }

        binding.btnSplitToggle.setOnClickListener {
            val next = !viewModel.splitMode.value
            viewModel.setSplitMode(next)
            binding.btnSplitToggle.setText(
                if (next) R.string.payment_split_hide else R.string.payment_split_toggle,
            )
        }

        binding.inputAmountTendered.setText(viewModel.amountTenderedText.value)
        binding.inputAmountTendered.addTextWatcher { viewModel.setAmountTenderedText(it) }

        binding.inputMobileRef.setText(viewModel.mobileMoneyRef.value)
        binding.inputMobileRef.addTextWatcher { viewModel.setMobileMoneyRef(it) }

        binding.inputSplitAmount1.setText(viewModel.splitLine1AmountText.value)
        binding.inputSplitAmount1.addTextWatcher { viewModel.setSplitLine1AmountText(it) }

        binding.groupMobileNetwork.setOnCheckedChangeListener { _, checkedId ->
            val code = when (checkedId) {
                R.id.net_mtn -> "MTN"
                R.id.net_orange -> "ORANGE"
                R.id.net_airtel -> "AIRTEL"
                R.id.net_mpesa -> "MPESA"
                else -> "OTHER"
            }
            viewModel.setMobileMoneyNetwork(code)
        }

        binding.splitRow1Method.setOnCheckedChangeListener { _, checkedId ->
            viewModel.setSplitLine1Method(
                if (checkedId == R.id.split1_mobile) SplitLineMethod.MOBILE_MONEY else SplitLineMethod.CASH,
            )
        }
        binding.splitRow2Method.setOnCheckedChangeListener { _, checkedId ->
            viewModel.setSplitLine2Method(
                if (checkedId == R.id.split2_cash) SplitLineMethod.CASH else SplitLineMethod.MOBILE_MONEY,
            )
        }

        binding.btnPasteSms.setOnClickListener { showPasteSmsDialog() }

        binding.inputVoucherId.addTextWatcher { viewModel.lookupVoucher(it) }
        binding.layoutVoucherId.setEndIconOnClickListener {
            findNavController().navigate(
                R.id.action_paymentDialogFragment_to_barcodeScannerFragment,
                androidx.core.os.bundleOf(
                    com.biasharaai.ui.scanner.BarcodeScannerFragment.ARG_SCAN_MODE to "SCAN_TO_ADD",
                    com.biasharaai.ui.scanner.BarcodeScannerFragment.ARG_RETURN_BARCODE_TO_POS to true,
                ),
            )
        }
        observeVoucherScanResult()
        setupMixedPaymentPlan()

        binding.btnSelectCustomerCredit.setOnClickListener { showCustomerPicker() }

        binding.btnCreditDueDate.setOnClickListener { openDueDatePicker() }
        binding.btnCreditClearDue.setOnClickListener { viewModel.setCreditDueDate(null) }

        binding.btnCancel.setOnClickListener { findNavController().navigateUp() }

        binding.btnConfirmPaid.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                binding.btnConfirmPaid.isEnabled = false
                binding.btnOnCredit.isEnabled = false
                val result = withContext(Dispatchers.IO) {
                    viewModel.commitCurrentSale()
                }
                handleCommitResult(result)
                binding.btnConfirmPaid.isEnabled = viewModel.paidSaleConfirmEnabled.value
                binding.btnOnCredit.isEnabled = viewModel.onCreditEnabled.value
            }
        }

        binding.btnOnCredit.setOnClickListener { showOnCreditDialog() }

        observeFlows()
    }

    private fun handleCommitResult(result: SaleCommitResult) {
        when (result) {
            is SaleCommitResult.Success -> {
                val bundle = bundleOf(
                    ReceiptViewModel.ARG_TRANSACTION_ID to result.transactionId,
                    ReceiptViewModel.ARG_ISSUED_VOUCHER_IDS to result.issuedVoucherIds.toTypedArray(),
                )
                findNavController().navigate(
                    R.id.action_paymentDialogFragment_to_receiptFragment,
                    bundle,
                    NavOptions.Builder()
                        .setPopUpTo(R.id.posFragment, false)
                        .build(),
                )
            }
            is SaleCommitResult.EmptyCart -> {
                Snackbar.make(binding.root, R.string.payment_commit_empty_cart, Snackbar.LENGTH_SHORT).show()
            }
            is SaleCommitResult.Failure -> {
                Snackbar.make(binding.root, result.message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun showOnCreditDialog() {
        val ctx = requireContext()
        val grand = viewModel.grandTotal.value
        var dueMillis: Long? = viewModel.creditDueDateMillis.value
        val amountInput = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(Locale.getDefault(), "%.2f", grand))
        }
        val noteInput = EditText(ctx).apply {
            minLines = 2
            hint = getString(R.string.payment_credit_dialog_note_hint)
        }
        val dueText = TextView(ctx).apply {
            textSize = 14f
        }
        fun syncDueLabel() {
            dueText.text = if (dueMillis == null) {
                getString(R.string.payment_credit_due_unset)
            } else {
                getString(R.string.payment_credit_due_set, dateFormat.format(Date(dueMillis!!)))
            }
        }
        syncDueLabel()
        val dueBtn = com.google.android.material.button.MaterialButton(ctx).apply {
            text = getString(R.string.payment_credit_set_due)
            setOnClickListener {
                val picker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText(R.string.payment_credit_set_due)
                    .build()
                picker.addOnPositiveButtonClickListener { sel ->
                    dueMillis = sel
                    syncDueLabel()
                }
                picker.show(childFragmentManager, "credit_due_on_credit_dialog")
            }
        }
        val clearDueBtn = com.google.android.material.button.MaterialButton(ctx).apply {
            text = getString(R.string.payment_credit_clear_due)
            setOnClickListener {
                dueMillis = null
                syncDueLabel()
            }
        }
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val p = resources.getDimensionPixelSize(R.dimen.pos_dialog_padding)
            setPadding(p, p, p, p)
            addView(TextView(ctx).apply { text = getString(R.string.payment_on_credit_amount_label) })
            addView(amountInput)
            addView(TextView(ctx).apply {
                text = getString(R.string.payment_credit_note_label)
                setPadding(0, 12, 0, 0)
            })
            addView(noteInput)
            addView(dueBtn)
            addView(clearDueBtn)
            addView(dueText)
        }
        val scroll = ScrollView(ctx).apply { addView(inner) }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.payment_on_credit_dialog_title)
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.payment_confirm) { _, _ ->
                val parsed = PaymentViewModel.parseAmount(amountInput.text?.toString().orEmpty())
                if (!PaymentViewModel.amountsMatch(parsed, grand)) {
                    Snackbar.make(binding.root, R.string.payment_credit_amount_must_match, Snackbar.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    binding.btnConfirmPaid.isEnabled = false
                    binding.btnOnCredit.isEnabled = false
                    val result = withContext(Dispatchers.IO) {
                        viewModel.commitOnCreditSale(dueMillis, noteInput.text?.toString().orEmpty())
                    }
                    handleCommitResult(result)
                    binding.btnConfirmPaid.isEnabled = viewModel.paidSaleConfirmEnabled.value
                    binding.btnOnCredit.isEnabled = viewModel.onCreditEnabled.value
                }
            }
            .show()
    }

    private fun showCustomerPicker() {
        CustomerSelectorBottomSheet.newInstance().apply {
            onCustomerPicked = { customer ->
                cartRepository.setSelectedCustomer(customer)
            }
        }.show(childFragmentManager, "payment_customer")
    }

    private fun showPasteSmsDialog() {
        val input = EditText(requireContext()).apply {
            minLines = 3
            hint = getString(R.string.payment_paste_sms_hint)
        }
        val pad = resources.getDimensionPixelSize(R.dimen.pos_dialog_padding)
        val wrap = android.widget.FrameLayout(requireContext()).apply {
            setPadding(pad, pad, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.payment_paste_sms_title)
            .setView(wrap)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.payment_paste_apply) { _, _ ->
                viewModel.parseMobileMoneyConfirmationSms(input.text?.toString().orEmpty())
            }
            .show()
    }

    private fun setupMixedPaymentPlan() {
        binding.groupMixedPlan.setOnCheckedChangeListener { _, checkedId ->
            val plan = when (checkedId) {
                R.id.plan_credit_services -> MixedPaymentPlan.CREDIT_SERVICES
                R.id.plan_credit_products -> MixedPaymentPlan.CREDIT_PRODUCTS
                R.id.plan_deposit -> MixedPaymentPlan.DEPOSIT
                else -> MixedPaymentPlan.PAY_ALL
            }
            viewModel.setMixedPaymentPlan(plan)
            val showDeposit = plan == MixedPaymentPlan.DEPOSIT
            binding.layoutDepositAmount.visibility = if (showDeposit) View.VISIBLE else View.GONE
        }
        binding.inputDepositAmount.addTextWatcher { viewModel.setDepositAmountText(it) }
    }

    private fun openDueDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.payment_credit_set_due)
            .build()
        picker.addOnPositiveButtonClickListener { selection -> viewModel.setCreditDueDate(selection) }
        picker.show(childFragmentManager, "credit_due")
    }

    private fun observeFlows() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.grandTotal.collect { g ->
                        binding.textGrandTotal.text = moneyFormatter.format(g)
                    }
                }
                launch {
                    combine(viewModel.isMixedCart, viewModel.cartBreakdown) { mixed, breakdown ->
                        mixed to breakdown
                    }.collect { (mixed, breakdown) ->
                        binding.panelMixedPlan.visibility = if (mixed) View.VISIBLE else View.GONE
                        if (mixed) {
                            binding.textCartBreakdown.visibility = View.VISIBLE
                            binding.textCartBreakdown.text = getString(
                                R.string.payment_cart_breakdown,
                                moneyFormatter.format(breakdown.productSubtotal),
                                moneyFormatter.format(
                                    breakdown.serviceSubtotal + breakdown.voucherSubtotal,
                                ),
                            )
                        } else {
                            binding.textCartBreakdown.visibility = View.GONE
                        }
                    }
                }
                launch {
                    combine(viewModel.amountDueNow, viewModel.paymentSplit, viewModel.grandTotal) { due, split, grand ->
                        Triple(due, split, grand)
                    }.collect { (due, split, grand) ->
                        val partial = due + 0.01 < grand
                        binding.textDueNow.visibility = if (partial) View.VISIBLE else View.GONE
                        if (partial) {
                            binding.textDueNow.text = getString(
                                R.string.payment_due_now,
                                moneyFormatter.format(due),
                            )
                        }
                        binding.textBalanceAfter.visibility =
                            if (split.balanceDue > 0) View.VISIBLE else View.GONE
                        if (split.balanceDue > 0) {
                            binding.textBalanceAfter.text = getString(
                                R.string.payment_balance_due,
                                moneyFormatter.format(split.balanceDue),
                            )
                        }
                    }
                }
                launch {
                    combine(viewModel.splitMode, viewModel.primaryTab) { split, tab -> split to tab }
                        .collect { (split, tab) ->
                            binding.panelSplit.visibility = if (split) View.VISIBLE else View.GONE
                            binding.togglePaymentTabs.visibility = if (split) View.GONE else View.VISIBLE
                            binding.rowPasteSms.visibility =
                                if (!split && tab == PrimaryPaymentTab.MOBILE_MONEY && viewModel.showMobileMoneyPasteAi) {
                                    View.VISIBLE
                                } else {
                                    View.GONE
                                }
                            if (!split) {
                                syncToggle(tab)
                                binding.panelCash.visibility = tabVisibility(tab, PrimaryPaymentTab.CASH)
                                binding.panelMobile.visibility = tabVisibility(tab, PrimaryPaymentTab.MOBILE_MONEY)
                                binding.panelCredit.visibility = tabVisibility(tab, PrimaryPaymentTab.CREDIT)
                                binding.panelVoucher.visibility = tabVisibility(tab, PrimaryPaymentTab.VOUCHER)
                            } else {
                                binding.panelCash.visibility = View.GONE
                                binding.panelMobile.visibility = View.GONE
                                binding.panelCredit.visibility = View.GONE
                                binding.panelVoucher.visibility = View.GONE
                            }
                        }
                }
                launch {
                    combine(viewModel.selectedCustomer, viewModel.primaryTab, viewModel.splitMode) { c, tab, split ->
                        Triple(c, tab, split)
                    }.collect { (customer, tab, split) ->
                        val showCredit = !split && tab == PrimaryPaymentTab.CREDIT
                        binding.panelCreditNoCustomer.visibility =
                            if (showCredit && customer == null) View.VISIBLE else View.GONE
                        binding.panelCreditWithCustomer.visibility =
                            if (showCredit && customer != null) View.VISIBLE else View.GONE
                        if (customer != null) {
                            binding.textCreditCustomerName.text = customer.name
                        }
                    }
                }
                launch {
                    viewModel.creditOutstanding.collect { bal ->
                        binding.textCreditOutstanding.text =
                            getString(R.string.payment_credit_outstanding, moneyFormatter.format(bal))
                    }
                }
                launch {
                    viewModel.creditDueDateMillis.collect { millis ->
                        binding.textCreditDue.text = if (millis == null) {
                            getString(R.string.payment_credit_due_unset)
                        } else {
                            getString(R.string.payment_credit_due_set, dateFormat.format(Date(millis)))
                        }
                    }
                }
                launch {
                    combine(viewModel.changeDue, viewModel.tenderedAmountParsed) { change, tender ->
                        change to tender
                    }.collect { (change, tender) ->
                        if (tender <= 0.0) {
                            binding.textChangeDue.visibility = View.GONE
                            return@collect
                        }
                        binding.textChangeDue.visibility = View.VISIBLE
                        if (change == null || change < 0) {
                            binding.textChangeDue.setTextColor(
                                MaterialColors.getColor(
                                    binding.textChangeDue,
                                    com.google.android.material.R.attr.colorError,
                                    Color.RED,
                                ),
                            )
                            binding.textChangeDue.text = getString(R.string.payment_change_insufficient)
                        } else {
                            binding.textChangeDue.setTextColor(
                                ContextCompat.getColor(requireContext(), R.color.pos_customer_selected),
                            )
                            binding.textChangeDue.text =
                                getString(R.string.payment_change_due, moneyFormatter.format(change))
                        }
                    }
                }
                launch {
                    viewModel.splitLine2DisplayAmount.collect { r ->
                        binding.textSplitRemainder.text = getString(
                            R.string.payment_split_remainder,
                            moneyFormatter.format(r ?: 0.0),
                        )
                    }
                }
                launch {
                    viewModel.paidSaleConfirmEnabled.collect { ok ->
                        binding.btnConfirmPaid.isEnabled = ok
                    }
                }
                launch {
                    viewModel.onCreditEnabled.collect { ok ->
                        binding.btnOnCredit.isEnabled = ok
                    }
                }
                launch {
                    viewModel.smsParseInProgress.collect { loading ->
                        binding.progressSmsParse.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.smsParseError.collect { err ->
                        if (err != null) {
                            Snackbar.make(binding.root, R.string.payment_sms_parse_failed, Snackbar.LENGTH_SHORT).show()
                            viewModel.clearSmsParseError()
                        }
                    }
                }
                launch {
                    combine(viewModel.resolvedVoucher, viewModel.voucherError) { v, e -> v to e }
                        .collect { (voucher, error) ->
                            binding.textVoucherStatus.visibility =
                                if (voucher != null || error != null) View.VISIBLE else View.GONE
                            binding.textVoucherStatus.text = when {
                                voucher != null -> getString(
                                    R.string.payment_voucher_valid,
                                    voucher.remainingUses,
                                )
                                error == "NOT_FOUND" -> getString(R.string.payment_voucher_not_found)
                                error == "EXHAUSTED" -> getString(R.string.payment_voucher_exhausted)
                                error == "EXPIRED" -> getString(R.string.payment_voucher_expired)
                                else -> ""
                            }
                            val color = if (voucher != null) {
                                androidx.core.content.ContextCompat.getColor(
                                    requireContext(), R.color.pos_customer_selected,
                                )
                            } else {
                                com.google.android.material.color.MaterialColors.getColor(
                                    binding.textVoucherStatus,
                                    com.google.android.material.R.attr.colorError,
                                    android.graphics.Color.RED,
                                )
                            }
                            binding.textVoucherStatus.setTextColor(color)
                        }
                }
            }
        }
    }

    private fun syncToggle(tab: PrimaryPaymentTab) {
        val id = when (tab) {
            PrimaryPaymentTab.CASH -> R.id.tab_cash
            PrimaryPaymentTab.MOBILE_MONEY -> R.id.tab_mobile
            PrimaryPaymentTab.CREDIT -> R.id.tab_credit
            PrimaryPaymentTab.VOUCHER -> R.id.tab_voucher
        }
        if (binding.togglePaymentTabs.checkedButtonId != id) {
            binding.togglePaymentTabs.check(id)
        }
    }

    private fun observeVoucherScanResult() {
        val handle = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        handle.getLiveData<String>(com.biasharaai.ui.pos.PosFragment.RESULT_KEY_SCANNED_BARCODE)
            .observe(viewLifecycleOwner) { scanned ->
                if (!scanned.isNullOrBlank()) {
                    binding.inputVoucherId.setText(scanned)
                    handle.remove<String>(com.biasharaai.ui.pos.PosFragment.RESULT_KEY_SCANNED_BARCODE)
                }
            }
    }

    private fun tabVisibility(current: PrimaryPaymentTab, panel: PrimaryPaymentTab): Int =
        if (current == panel) View.VISIBLE else View.GONE

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun EditText.addTextWatcher(on: (String) -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                on(s?.toString().orEmpty())
            }
        })
    }
}
