package com.biasharaai.ui.cash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
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
import com.biasharaai.databinding.FragmentManualEntryBinding
import com.biasharaai.productline.ProductLineManager
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ManualEntryFragment : BaseFragment() {

    private var _binding: FragmentManualEntryBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var productLineManager: ProductLineManager

    private val viewModel: ConfirmationViewModel by viewModels()

    private var direction = LedgerDirection.MONEY_IN
    private val inItems by lazy { buildInItems() }
    private val outItems by lazy { buildOutItems() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentManualEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        direction = arguments?.getString(ARG_DIRECTION)
            ?.let { runCatching { LedgerDirection.valueOf(it) }.getOrDefault(LedgerDirection.MONEY_IN) }
            ?: LedgerDirection.MONEY_IN

        when (direction) {
            LedgerDirection.MONEY_IN -> binding.chipMoneyIn.isChecked = true
            LedgerDirection.MONEY_OUT -> binding.chipMoneyOut.isChecked = true
            else -> binding.chipMoneyIn.isChecked = true
        }
        refreshCategories(direction)

        binding.chipGroupDirection.setOnCheckedStateChangeListener { _, ids ->
            direction = if (R.id.chip_money_in in ids) LedgerDirection.MONEY_IN else LedgerDirection.MONEY_OUT
            refreshCategories(direction)
        }

        binding.btnSave.setOnClickListener { doSave() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currencySymbol.collect { symbol ->
                    _binding?.tilAmount?.prefixText = "$symbol "
                }
            }
        }

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun refreshCategories(dir: LedgerDirection) {
        val items = if (dir == LedgerDirection.MONEY_IN) inItems else outItems
        val labels = items.map { it.first }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
        binding.actvCategory.setAdapter(adapter)
        binding.actvCategory.setText(labels.firstOrNull() ?: "", false)
        binding.actvCategory.tag = items
    }

    @Suppress("UNCHECKED_CAST")
    private fun doSave() {
        val b = _binding ?: return
        val amount = b.etAmount.text?.toString()?.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            b.tilAmount.error = "Enter a valid amount"
            return
        }
        b.tilAmount.error = null

        val ref = b.etReference.text?.toString()?.trim()?.uppercase()?.takeIf { it.isNotBlank() }

        if (direction == LedgerDirection.MONEY_OUT && ref.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.cash_unverified_toast, Toast.LENGTH_LONG).show()
        }

        val categoryLabel = b.actvCategory.text?.toString() ?: ""
        val items = b.actvCategory.tag as? List<Pair<String, LedgerEntryType>>
        val type = items?.firstOrNull { it.first == categoryLabel }?.second ?: LedgerEntryType.OTHER_INCOME

        viewModel.save(
            direction = direction,
            type = type,
            amount = amount,
            description = categoryLabel,
            notes = b.etNotes.text?.toString()?.takeIf { it.isNotBlank() },
            captureMethod = CaptureMethod.MANUAL,
            proofType = ProofType.UNKNOWN,
            rawText = null,
            parsedReference = ref,
            parsedCounterparty = b.etCounterparty.text?.toString()?.trim()?.takeIf { it.isNotBlank() },
            parsedDate = null,
            parserConfidence = 0f,
            parserEngine = ParserEngine.MANUAL,
        )
    }

    private fun buildInItems() = buildList {
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

    private fun buildOutItems() = listOf(
        getString(R.string.cash_category_expense) to LedgerEntryType.EXPENSE,
        getString(R.string.cash_category_stock_purchase) to LedgerEntryType.STOCK_PURCHASE,
        getString(R.string.cash_category_refund) to LedgerEntryType.REFUND,
        getString(R.string.cash_category_salary) to LedgerEntryType.EXPENSE,
        getString(R.string.cash_category_utility) to LedgerEntryType.EXPENSE,
        getString(R.string.cash_category_transport) to LedgerEntryType.EXPENSE,
        getString(R.string.cash_category_rent) to LedgerEntryType.EXPENSE,
        getString(R.string.cash_category_owner_draw) to LedgerEntryType.EXPENSE,
    )

    companion object {
        const val ARG_DIRECTION = "direction"
    }
}
