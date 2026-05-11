package com.biasharaai.ui.inventory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.databinding.FragmentInventoryListBinding
import com.biasharaai.ui.base.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class InventoryListFragment : BaseFragment() {

    private var _binding: FragmentInventoryListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InventoryListViewModel by viewModels()

    private lateinit var productAdapter: ProductAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentInventoryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupToolbar()
        setupFab()
        observeProducts()
    }

    private fun setupRecyclerView() {
        productAdapter = ProductAdapter { product ->
            // Navigate to AddEditProductFragment for editing (defined in a future prompt)
            findNavController().navigate(
                R.id.action_inventoryListFragment_to_addEditProductFragment,
                bundleOf(ARG_PRODUCT_ID to product.id),
            )
        }
        binding.recyclerProducts.adapter = productAdapter
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_scan -> {
                    // Navigate to BarcodeScannerFragment in lookup mode (Prompt 4b)
                    findNavController().navigate(
                        R.id.action_inventoryListFragment_to_barcodeScannerFragment,
                        bundleOf(
                            "scan_mode" to "SCAN_FOR_LOOKUP",
                        ),
                    )
                    true
                }
                else -> false
            }
        }
    }

    private fun setupFab() {
        binding.fabAddProduct.setOnClickListener {
            // Navigate to AddEditProductFragment for creating a new product.
            // Passing 0L signals "new product" to the destination.
            findNavController().navigate(
                R.id.action_inventoryListFragment_to_addEditProductFragment,
                bundleOf(ARG_PRODUCT_ID to 0L),
            )
        }
    }

    private fun observeProducts() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.products.collect { products ->
                        productAdapter.submitList(products)
                        binding.textEmpty.visibility = if (products.isEmpty()) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                    }
                }
                launch {
                    viewModel.forecasts.collect { forecastMap ->
                        productAdapter.submitForecasts(forecastMap)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerProducts.adapter = null
        _binding = null
    }

    companion object {
        /** Bundle key for the product ID passed to AddEditProductFragment. */
        const val ARG_PRODUCT_ID = "product_id"
    }
}
