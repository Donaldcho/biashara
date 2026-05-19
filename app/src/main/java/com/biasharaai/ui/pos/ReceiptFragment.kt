package com.biasharaai.ui.pos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.data.local.db.TransactionType
import com.biasharaai.databinding.FragmentReceiptBinding
import com.biasharaai.databinding.ItemReceiptLineBinding
import com.biasharaai.money.MoneyFormatter
import com.biasharaai.pos.receipt.PosReceiptLine
import com.biasharaai.ui.base.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class ReceiptFragment : BaseFragment() {

    private var _binding: FragmentReceiptBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReceiptViewModel by viewModels()

    @Inject
    lateinit var moneyFormatter: MoneyFormatter

    private val dateFormat: DateFormat =
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentReceiptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.btnReturnItems.setOnClickListener {
            val tx = viewModel.uiState.value.transaction ?: return@setOnClickListener
            findNavController().navigate(
                R.id.action_receiptFragment_to_returnFragment,
                bundleOf(ReceiptViewModel.ARG_TRANSACTION_ID to tx.id),
            )
        }

        binding.btnViewVoucherQr.setOnClickListener {
            val voucherId = viewModel.uiState.value.voucherIds.firstOrNull() ?: return@setOnClickListener
            findNavController().navigate(
                R.id.action_receiptFragment_to_voucherReceiptFragment,
                VoucherReceiptFragment.args(voucherId),
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    bindReceipt(state)
                }
            }
        }
    }

    private fun bindReceipt(state: ReceiptUiState) {
        val tx = state.transaction
        if (tx == null) {
            binding.textBusinessName.text = ""
            return
        }

        val isReturn = tx.type == TransactionType.RETURN
        binding.textReturnBanner.isVisible = isReturn

        val symbol = state.settings?.currencySymbol?.takeIf { it.isNotBlank() }
            ?: moneyFormatter.numberFormat().currency?.symbol.orEmpty()

        binding.textBusinessName.text =
            state.settings?.businessName.orEmpty().ifBlank { getString(R.string.app_name) }

        val receiptLabel = tx.receiptNumber?.takeIf { it.isNotBlank() }
            ?: getString(R.string.transactions_receipt_fallback, tx.id)
        binding.textReceiptMeta.text = getString(
            R.string.receipt_meta_format,
            receiptLabel,
            dateFormat.format(Date(tx.date)),
        )

        binding.textPaymentMethod.text = PaymentMethodLabels.label(requireContext(), tx.paymentMethod)

        binding.containerLines.removeAllViews()
        val inflater = layoutInflater
        var subtotal = 0.0
        for (line in state.lines) {
            val row = ItemReceiptLineBinding.inflate(inflater, binding.containerLines, true)
            val qty = abs(line.quantity)
            val unit = abs(line.unitPrice)
            val lineTotal = abs(line.lineTotal)
            subtotal += if (isReturn) -lineTotal else lineTotal

            val kindSuffix = when (line.kind) {
                PosReceiptLine.Kind.PRODUCT -> ""
                PosReceiptLine.Kind.SERVICE -> getString(R.string.receipt_line_kind_service)
                PosReceiptLine.Kind.VOUCHER -> getString(R.string.receipt_line_kind_voucher)
            }
            val displayName = line.name + kindSuffix

            row.textLineTitle.text = getString(
                R.string.receipt_line_format,
                qty,
                displayName,
                formatMoney(isReturn, unit, symbol),
            )
            row.textLineTotal.text = formatMoney(isReturn, lineTotal, symbol)
        }

        val taxLabel = state.settings?.taxLabel?.takeIf { it.isNotBlank() } ?: getString(R.string.pos_totals_tax_default)
        val taxRate = tx.taxRate
        val taxAmount = tx.taxAmount
        if (!isReturn && taxAmount > 0) {
            binding.textTax.isVisible = true
            binding.textTax.text = getString(
                R.string.receipt_tax,
                getString(R.string.pos_totals_tax_label, taxLabel, taxRate),
                formatMoney(false, taxAmount, symbol),
            )
        } else {
            binding.textTax.isVisible = false
        }

        binding.textSubtotal.text = getString(
            R.string.receipt_subtotal,
            formatMoney(isReturn, abs(subtotal), symbol),
        )

        if (!isReturn && tx.amountPaid > 0 && tx.balanceDue > 0.01) {
            binding.textAmountPaid.isVisible = true
            binding.textBalanceDue.isVisible = true
            binding.textAmountPaid.text = getString(
                R.string.receipt_amount_paid,
                formatMoney(false, tx.amountPaid, symbol),
            )
            binding.textBalanceDue.text = getString(
                R.string.receipt_balance_due,
                formatMoney(false, tx.balanceDue, symbol),
            )
        } else {
            binding.textAmountPaid.isVisible = false
            binding.textBalanceDue.isVisible = false
        }

        val totalDisplay = abs(tx.amount)
        binding.textGrandTotal.text = getString(
            R.string.receipt_total,
            formatMoney(isReturn, totalDisplay, symbol),
        )

        binding.textFooter.text = state.settings?.receiptFooter.orEmpty()

        val showReturn = tx.type == TransactionType.INCOME &&
            state.lines.any { it.kind == PosReceiptLine.Kind.PRODUCT && it.quantity > 0 }
        binding.btnReturnItems.isVisible = showReturn

        val voucherCount = state.voucherIds.size
        binding.btnViewVoucherQr.isVisible = !isReturn && voucherCount > 0
        binding.btnViewVoucherQr.text = if (voucherCount == 1) {
            getString(R.string.receipt_view_voucher_qr_one)
        } else {
            getString(R.string.receipt_view_voucher_qr_many, voucherCount)
        }
    }

    private fun formatMoney(isReturn: Boolean, value: Double, symbol: String): String {
        val core = moneyFormatter.format(abs(value))
        return if (isReturn) {
            val trimmed = core.trimStart('+')
            "-$trimmed"
        } else {
            core
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
