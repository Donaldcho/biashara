package com.biasharaai.ui.negotiation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.databinding.FragmentSupplierNegotiationBinding
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SupplierNegotiationFragment : BaseFragment() {

    private var _binding: FragmentSupplierNegotiationBinding? = null
    private val binding get() = _binding!!

    private val negotiationViewModel: NegotiationViewModel by activityViewModels()

    @Inject
    lateinit var capabilityTier: CapabilityTier

    @Inject
    lateinit var productDao: ProductDao

    private val selectedProductIds = mutableSetOf<Long>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSupplierNegotiationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (showNegotiationTierBlockedDialogIfNeeded(capabilityTier)) {
            findNavController().navigateUp()
            return
        }

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        if (binding.inputCountry.text.isNullOrBlank()) {
            binding.inputCountry.setText(Locale.getDefault().displayCountry)
        }

        binding.btnSelectProducts.setOnClickListener { showProductMultiSelect() }
        updateSelectedProductsSummary()

        binding.btnGenerateScript.setOnClickListener { submitForm() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                negotiationViewModel.generateState.collect { state ->
                    binding.progressGenerate.isVisible = state is NegotiationGenerateState.Loading
                    binding.btnGenerateScript.isEnabled = state !is NegotiationGenerateState.Loading
                    when (state) {
                        is NegotiationGenerateState.Success -> {
                            findNavController().navigate(
                                R.id.action_supplierNegotiationFragment_to_negotiationGuideFragment,
                            )
                            negotiationViewModel.consumeGenerationSuccess()
                        }
                        is NegotiationGenerateState.Error -> {
                            val msg = when (state.message) {
                                "NO_ITEMS" -> getString(R.string.negotiation_error_no_items)
                                "BAD_BUDGET" -> getString(R.string.negotiation_error_budget)
                                "MODEL_UNAVAILABLE" -> getString(R.string.negotiation_error_model)
                                else -> state.message
                            }
                            Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun showProductMultiSelect() {
        viewLifecycleOwner.lifecycleScope.launch {
            val products = withContext(Dispatchers.IO) { productDao.getProductsList() }
                .sortedBy { it.name.lowercase(Locale.getDefault()) }
            if (products.isEmpty()) {
                Snackbar.make(binding.root, R.string.negotiation_no_products_inventory, Snackbar.LENGTH_SHORT).show()
                return@launch
            }
            val labels = products.map { it.name }.toTypedArray()
            val checked = BooleanArray(products.size) { i -> products[i].id in selectedProductIds }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.negotiation_select_products)
                .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                    val id = products[which].id
                    if (isChecked) selectedProductIds.add(id) else selectedProductIds.remove(id)
                }
                .setPositiveButton(android.R.string.ok) { _, _ -> updateSelectedProductsSummary() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun updateSelectedProductsSummary() {
        binding.textSelectedProducts.text = if (selectedProductIds.isEmpty()) {
            getString(R.string.negotiation_products_none_selected)
        } else {
            resources.getQuantityString(
                R.plurals.negotiation_products_selected_count,
                selectedProductIds.size,
                selectedProductIds.size,
            )
        }
    }

    private fun submitForm() {
        val input = NegotiationFormInput(
            supplierName = binding.inputSupplierName.text?.toString().orEmpty(),
            selectedProductIds = selectedProductIds.toSet(),
            extraItemsText = binding.inputExtraItems.text?.toString().orEmpty(),
            budgetRaw = binding.inputBudget.text?.toString().orEmpty(),
            city = binding.inputCity.text?.toString().orEmpty(),
            country = binding.inputCountry.text?.toString().orEmpty(),
        )
        negotiationViewModel.submitNegotiationForm(input)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
