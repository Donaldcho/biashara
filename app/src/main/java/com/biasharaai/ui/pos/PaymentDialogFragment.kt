package com.biasharaai.ui.pos

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.biasharaai.pos.cart.CartRepository
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
import java.text.NumberFormat
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

    private val currencyFormat: NumberFormat =
        NumberFormat.getCurrencyInstance(Locale.getDefault())

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
                R.id.net_airtel -> "AIRTEL"
                R.id.net_other -> "OTHER"
                else -> "MPESA"
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

        binding.btnSelectCustomerCredit.setOnClickListener { showCustomerPicker() }

        binding.btnCreditDueDate.setOnClickListener { openDueDatePicker() }
        binding.btnCreditClearDue.setOnClickListener { viewModel.setCreditDueDate(null) }

        binding.btnCancel.setOnClickListener { findNavController().navigateUp() }
        binding.btnConfirmSale.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                binding.btnConfirmSale.isEnabled = false
                val result = withContext(Dispatchers.IO) {
                    viewModel.commitCurrentSale()
                }
                when (result) {
                    is SaleCommitResult.Success -> {
                        findNavController().navigate(
                            R.id.action_paymentDialogFragment_to_receiptFragment,
                            bundleOf(ReceiptViewModel.ARG_TRANSACTION_ID to result.transactionId),
                            NavOptions.Builder()
                                .setPopUpTo(R.id.posFragment, false)
                                .build(),
                        )
                    }
                    is SaleCommitResult.EmptyCart -> {
                        Snackbar.make(binding.root, R.string.payment_commit_empty_cart, Snackbar.LENGTH_SHORT).show()
                        binding.btnConfirmSale.isEnabled = viewModel.confirmSaleEnabled.value
                    }
                    is SaleCommitResult.Failure -> {
                        Snackbar.make(binding.root, result.message, Snackbar.LENGTH_LONG).show()
                        binding.btnConfirmSale.isEnabled = viewModel.confirmSaleEnabled.value
                    }
                }
            }
        }

        observeFlows()
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
                        binding.textGrandTotal.text = currencyFormat.format(g)
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
                            } else {
                                binding.panelCash.visibility = View.GONE
                                binding.panelMobile.visibility = View.GONE
                                binding.panelCredit.visibility = View.GONE
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
                            getString(R.string.payment_credit_outstanding, currencyFormat.format(bal))
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
                                getString(R.string.payment_change_due, currencyFormat.format(change))
                        }
                    }
                }
                launch {
                    viewModel.splitLine2DisplayAmount.collect { r ->
                        binding.textSplitRemainder.text = getString(
                            R.string.payment_split_remainder,
                            currencyFormat.format(r ?: 0.0),
                        )
                    }
                }
                launch {
                    viewModel.confirmSaleEnabled.collect { ok -> binding.btnConfirmSale.isEnabled = ok }
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
            }
        }
    }

    private fun syncToggle(tab: PrimaryPaymentTab) {
        val id = when (tab) {
            PrimaryPaymentTab.CASH -> R.id.tab_cash
            PrimaryPaymentTab.MOBILE_MONEY -> R.id.tab_mobile
            PrimaryPaymentTab.CREDIT -> R.id.tab_credit
        }
        if (binding.togglePaymentTabs.checkedButtonId != id) {
            binding.togglePaymentTabs.check(id)
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
