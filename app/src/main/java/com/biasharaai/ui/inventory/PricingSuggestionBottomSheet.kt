package com.biasharaai.ui.inventory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.biasharaai.R
import com.biasharaai.ai.PricingAdvisor
import com.biasharaai.data.local.db.Product
import com.biasharaai.databinding.FragmentPricingSuggestionBinding
import com.biasharaai.money.MoneyFormatter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shows AI or rules-based price suggestion — Prompt U3.
 */
@AndroidEntryPoint
class PricingSuggestionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentPricingSuggestionBinding? = null
    private val binding get() = _binding!!

    private val addEditViewModel: AddEditProductViewModel by viewModels(
        ownerProducer = { requireParentFragment() },
    )

    @Inject
    lateinit var moneyFormatter: MoneyFormatter

    private var parsedPrice: Double? = null

    private fun productFromArgs(args: Bundle): Product =
        Product(
            id = args.getLong(ARG_PRODUCT_ID),
            name = args.getString(ARG_NAME).orEmpty(),
            description = args.getString(ARG_DESCRIPTION).orEmpty().ifBlank { null },
            price = args.getDouble(ARG_PRICE),
            cost = args.getDouble(ARG_COST),
            stockQuantity = args.getInt(ARG_STOCK),
            category = args.getString(ARG_CATEGORY).orEmpty().ifBlank { null },
            barcodeValue = args.getString(ARG_BARCODE).orEmpty().ifBlank { null },
        )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPricingSuggestionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val product = productFromArgs(requireArguments())

        binding.progressPricing.visibility = View.VISIBLE
        binding.layoutContent.visibility = View.GONE
        binding.btnUsePrice.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val response = runCatching { addEditViewModel.suggestSellingPrice(product) }
                .getOrElse { e ->
                    getString(R.string.pricing_suggestion_failed, e.message ?: "")
                }
            binding.progressPricing.visibility = View.GONE
            binding.layoutContent.visibility = View.VISIBLE
            bindResult(response)
        }

        binding.btnKeepPrice.setOnClickListener { dismiss() }
        binding.btnUsePrice.setOnClickListener {
            val p = parsedPrice ?: return@setOnClickListener
            (parentFragment as? AddEditProductFragment)?.applySuggestedPrice(p)
            dismiss()
        }
    }

    private fun bindResult(response: String) {
        parsedPrice = PricingAdvisor.parseSuggestedNumericPrice(response)
        if (parsedPrice != null) {
            val price = parsedPrice!!
            binding.textSuggestedPrice.text = moneyFormatter.format(price)
            binding.textParseHint.visibility = View.GONE
            binding.btnUsePrice.isEnabled = true
            binding.textRationale.text = PricingAdvisor.rationaleText(response).ifBlank {
                response.trim()
            }
        } else {
            binding.textSuggestedPrice.text = getString(R.string.pricing_suggestion_unparsed_title)
            binding.textParseHint.visibility = View.VISIBLE
            binding.btnUsePrice.isEnabled = false
            binding.textRationale.text = response.trim()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_PRODUCT_ID = "product_id"
        private const val ARG_NAME = "name"
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_PRICE = "price"
        private const val ARG_COST = "cost"
        private const val ARG_STOCK = "stock"
        private const val ARG_CATEGORY = "category"
        private const val ARG_BARCODE = "barcode"

        fun newInstance(product: Product): PricingSuggestionBottomSheet =
            PricingSuggestionBottomSheet().apply {
                arguments = bundleOf(
                    ARG_PRODUCT_ID to product.id,
                    ARG_NAME to product.name,
                    ARG_DESCRIPTION to (product.description ?: ""),
                    ARG_PRICE to product.price,
                    ARG_COST to product.cost,
                    ARG_STOCK to product.stockQuantity,
                    ARG_CATEGORY to (product.category ?: ""),
                    ARG_BARCODE to (product.barcodeValue ?: ""),
                )
            }
    }
}
