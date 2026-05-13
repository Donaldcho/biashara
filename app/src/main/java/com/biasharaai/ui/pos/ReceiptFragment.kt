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
import com.biasharaai.ui.base.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@AndroidEntryPoint
class ReceiptFragment : BaseFragment() {

    private var _binding: FragmentReceiptBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReceiptViewModel by viewModels()

    private val currencyFormat: NumberFormat =
        NumberFormat.getCurrencyInstance(Locale.getDefault())

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    bindReceipt(state)
                }
            }
        }
    }

    private fun bindReceipt(state: com.biasharaai.ui.pos.ReceiptUiState) {
        val tx = state.transaction
        if (tx == null) {
            binding.textBusinessName.text = ""
            return
        }

        val isReturn = tx.type == TransactionType.RETURN
        binding.textReturnBanner.isVisible = isReturn

        val symbol = state.settings?.currencySymbol
            ?: currencyFormat.currency?.symbol
            ?: ""

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
            subtotal += line.lineTotal

            row.textLineTitle.text = getString(
                R.string.receipt_line_format,
                qty,
                line.productName,
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

        val totalDisplay = abs(tx.amount)
        binding.textGrandTotal.text = getString(
            R.string.receipt_total,
            formatMoney(isReturn, totalDisplay, symbol),
        )

        binding.textFooter.text = state.settings?.receiptFooter.orEmpty()

        val showReturn = tx.type == TransactionType.INCOME &&
            state.lines.any { it.quantity > 0 }
        binding.btnReturnItems.isVisible = showReturn
    }

    private fun formatMoney(isReturn: Boolean, value: Double, symbol: String): String {
        val core = currencyFormat.format(abs(value))
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
