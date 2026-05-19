package com.biasharaai.ui.cash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.data.local.db.CaptureMethod
import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.LedgerEntryType
import com.biasharaai.data.local.db.ParserEngine
import com.biasharaai.data.local.db.ProofType
import com.biasharaai.databinding.FragmentConfirmationBinding
import com.biasharaai.productline.ProductLineManager
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ConfirmationFragment : BaseFragment() {

    @Inject
    lateinit var productLineManager: ProductLineManager

    private var _binding: FragmentConfirmationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConfirmationViewModel by viewModels()

    private var direction = LedgerDirection.MONEY_IN
    private var rawTextVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentConfirmationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyArgs()
        setupCategoryDropdown()
        applyQrLabel()
        setupListeners()
        observeState()
        observeCurrencySymbol()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun applyArgs() {
        val args = arguments ?: return
        direction = args.getString(ARG_DIRECTION)
            ?.let { runCatching { LedgerDirection.valueOf(it) }.getOrDefault(LedgerDirection.MONEY_IN) }
            ?: LedgerDirection.MONEY_IN

        when (direction) {
            LedgerDirection.MONEY_IN -> binding.chipMoneyIn.isChecked = true
            LedgerDirection.MONEY_OUT -> binding.chipMoneyOut.isChecked = true
            else -> binding.chipMoneyIn.isChecked = true
        }

        args.getString(ARG_PARSED_AMOUNT)?.toDoubleOrNull()?.let { amt ->
            binding.etAmount.setText(String.format("%.2f", amt))
        }
        args.getString(ARG_PARSED_REFERENCE)?.takeIf { it.isNotBlank() }?.let {
            binding.etReference.setText(it)
        }
        args.getString(ARG_PARSED_COUNTERPARTY)?.takeIf { it.isNotBlank() }?.let {
            binding.etCounterparty.setText(it)
        }

        val rawText = args.getString(ARG_RAW_TEXT) ?: ""
        if (rawText.isNotBlank()) {
            binding.tvRawText.text = rawText.take(800)
            binding.btnToggleRaw.isVisible = true
        }

        val confidence = args.getFloat(ARG_CONFIDENCE, 0f)
        val confPct = (confidence * 100).toInt()
        binding.tvConfidenceLabel.text = getString(R.string.cash_evidence_confidence, confPct)
        binding.progressConfidence.progress = confPct
        binding.tvConfidenceLabel.isVisible = confidence > 0f
        binding.progressConfidence.isVisible = confidence > 0f
    }

    private fun applyQrLabel() {
        arguments?.getString(ARG_QR_LABEL)?.takeIf { it.isNotBlank() }?.let { label ->
            binding.actvCategory.setText(label, false)
        }
    }

    private fun setupCategoryDropdown() {
        val inItems = buildList {
            add(getString(R.string.cash_category_sale_product) to LedgerEntryType.SALE_PRODUCT)
            if (productLineManager.isProEnabled()) {
                add(getString(R.string.cash_category_sale_service) to LedgerEntryType.SALE_SERVICE)
            }
            add(getString(R.string.cash_category_debt_repaid) to LedgerEntryType.DEBT_REPAID)
            add(getString(R.string.cash_category_other_income) to LedgerEntryType.OTHER_INCOME)
            add(getString(R.string.cash_category_loan_received) to LedgerEntryType.OTHER_INCOME)
            add(getString(R.string.cash_category_grant) to LedgerEntryType.OTHER_INCOME)
            add(getString(R.string.cash_category_owner_injection) to LedgerEntryType.OTHER_INCOME)
        }
        val outItems = listOf(
            getString(R.string.cash_category_expense) to LedgerEntryType.EXPENSE,
            getString(R.string.cash_category_stock_purchase) to LedgerEntryType.STOCK_PURCHASE,
            getString(R.string.cash_category_refund) to LedgerEntryType.REFUND,
            getString(R.string.cash_category_salary) to LedgerEntryType.EXPENSE,
            getString(R.string.cash_category_utility) to LedgerEntryType.EXPENSE,
            getString(R.string.cash_category_transport) to LedgerEntryType.EXPENSE,
            getString(R.string.cash_category_rent) to LedgerEntryType.EXPENSE,
            getString(R.string.cash_category_owner_draw) to LedgerEntryType.EXPENSE,
        )

        fun refreshCategories(dir: LedgerDirection) {
            val items = if (dir == LedgerDirection.MONEY_IN) inItems else outItems
            val labels = items.map { it.first }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
            binding.actvCategory.setAdapter(adapter)
            binding.actvCategory.setText(labels.firstOrNull() ?: "", false)
            binding.actvCategory.tag = items
        }
        refreshCategories(direction)

        binding.chipGroupDirection.setOnCheckedStateChangeListener { _, ids ->
            direction = if (R.id.chip_money_in in ids) LedgerDirection.MONEY_IN else LedgerDirection.MONEY_OUT
            refreshCategories(direction)
        }
    }

    private fun setupListeners() {
        binding.btnToggleRaw.setOnClickListener {
            rawTextVisible = !rawTextVisible
            binding.cardRawText.isVisible = rawTextVisible
        }
        binding.btnSave.setOnClickListener { doSave() }
    }

    private fun observeCurrencySymbol() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currencySymbol.collect { symbol ->
                    _binding?.tilAmount?.prefixText = "$symbol "
                }
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val b = _binding ?: return@collect
                    b.progressSaving.isVisible = state is ConfirmationUiState.Saving
                    b.btnSave.isEnabled = state !is ConfirmationUiState.Saving
                    when (state) {
                        is ConfirmationUiState.Saved -> findNavController().navigateUp()
                        is ConfirmationUiState.Error ->
                            Snackbar.make(b.root, state.message, Snackbar.LENGTH_LONG).show()
                        else -> Unit
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun doSave() {
        val b = _binding ?: return
        val amountStr = b.etAmount.text?.toString() ?: ""
        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            b.tilAmount.error = "Enter a valid amount"
            return
        }
        b.tilAmount.error = null

        val categoryLabel = b.actvCategory.text?.toString() ?: ""
        val items = b.actvCategory.tag as? List<Pair<String, LedgerEntryType>>

        val args = arguments
        val qrCategoryType = args?.getString(ARG_CATEGORY_TYPE)
            ?.let { runCatching { LedgerEntryType.valueOf(it) }.getOrNull() }
        val type = qrCategoryType
            ?: items?.firstOrNull { it.first == categoryLabel }?.second
            ?: LedgerEntryType.OTHER_INCOME

        val rawText = args?.getString(ARG_RAW_TEXT)
        val proofType = args?.getString(ARG_PROOF_TYPE)
            ?.let { runCatching { ProofType.valueOf(it) }.getOrDefault(ProofType.UNKNOWN) }
            ?: ProofType.UNKNOWN
        val confidence = args?.getFloat(ARG_CONFIDENCE, 0f) ?: 0f
        val captureMethod = args?.getString(ARG_CAPTURE_METHOD)
            ?.let { runCatching { CaptureMethod.valueOf(it) }.getOrDefault(CaptureMethod.MANUAL) }
            ?: CaptureMethod.MANUAL
        val parserEngine = args?.getString(ARG_PARSER_ENGINE)
            ?.let { runCatching { ParserEngine.valueOf(it) }.getOrDefault(ParserEngine.MANUAL) }
            ?: ParserEngine.MANUAL

        viewModel.save(
            direction = direction,
            type = type,
            amount = amount,
            description = categoryLabel,
            notes = b.etNotes.text?.toString()?.takeIf { it.isNotBlank() },
            captureMethod = captureMethod,
            proofType = proofType,
            rawText = rawText,
            parsedReference = b.etReference.text?.toString()?.trim()?.uppercase()?.takeIf { it.isNotBlank() },
            parsedCounterparty = b.etCounterparty.text?.toString()?.trim()?.takeIf { it.isNotBlank() },
            parsedDate = null,
            parserConfidence = confidence,
            parserEngine = parserEngine,
        )
    }

    companion object {
        const val ARG_DIRECTION = "direction"
        const val ARG_PARSED_AMOUNT = "parsed_amount"
        const val ARG_PARSED_REFERENCE = "parsed_reference"
        const val ARG_PARSED_COUNTERPARTY = "parsed_counterparty"
        const val ARG_RAW_TEXT = "raw_text"
        const val ARG_PROOF_TYPE = "proof_type"
        const val ARG_CONFIDENCE = "confidence"
        const val ARG_CAPTURE_METHOD = "capture_method"
        const val ARG_PARSER_ENGINE = "parser_engine"
        const val ARG_CATEGORY_TYPE = "category_type"
        const val ARG_QR_LABEL = "qr_label"
    }
}
