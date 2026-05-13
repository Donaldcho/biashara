package com.biasharaai.ui.inventory

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.data.local.db.Product
import com.biasharaai.databinding.FragmentInventoryListBinding
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
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
        productAdapter = ProductAdapter(
            onItemClick = { product ->
                findNavController().navigate(
                    R.id.action_inventoryListFragment_to_addEditProductFragment,
                    bundleOf(ARG_PRODUCT_ID to product.id),
                )
            },
            onItemLongClick = { product, anchor ->
                PopupMenu(requireContext(), anchor).apply {
                    menuInflater.inflate(R.menu.menu_inventory_product_context, menu)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.action_edit_product -> {
                                findNavController().navigate(
                                    R.id.action_inventoryListFragment_to_addEditProductFragment,
                                    bundleOf(ARG_PRODUCT_ID to product.id),
                                )
                                true
                            }
                            R.id.action_remove_stock -> {
                                showRemoveStockDialog(product)
                                true
                            }
                            R.id.action_delete_product -> {
                                showDeleteProductDialog(product)
                                true
                            }
                            else -> false
                        }
                    }
                }.show()
                true
            },
        )
        binding.recyclerProducts.adapter = productAdapter
    }

    private fun showRemoveStockDialog(product: Product) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.inventory_remove_stock_hint)
        }
        val pad = resources.getDimensionPixelSize(R.dimen.pos_dialog_padding)
        val wrap = FrameLayout(requireContext()).apply {
            setPadding(pad, pad, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.inventory_remove_stock_title, product.name))
            .setView(wrap)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.inventory_remove_stock_apply) { _, _ ->
                val qty = input.text?.toString()?.trim()?.toIntOrNull() ?: 0
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        viewModel.removeStockUnits(product.id, qty)
                        Snackbar.make(
                            binding.root,
                            R.string.inventory_removed_stock_snackbar,
                            Snackbar.LENGTH_SHORT,
                        ).show()
                    } catch (e: Exception) {
                        Snackbar.make(
                            binding.root,
                            getString(R.string.inventory_action_failed, e.message ?: ""),
                            Snackbar.LENGTH_LONG,
                        ).show()
                    }
                }
            }
            .show()
    }

    private fun showDeleteProductDialog(product: Product) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.inventory_delete_product_title)
            .setMessage(getString(R.string.inventory_delete_product_message, product.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.inventory_delete_confirm) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.deleteProduct(product)
                    Snackbar.make(
                        binding.root,
                        R.string.inventory_deleted_product_snackbar,
                        Snackbar.LENGTH_SHORT,
                    ).show()
                }
            }
            .show()
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

    private var speedDialOpen: Boolean = false

    private fun setupFab() {
        binding.fabMain.shrink()
        binding.fabMain.setOnClickListener {
            speedDialOpen = !speedDialOpen
            if (speedDialOpen) openSpeedDial() else closeSpeedDial()
        }
        binding.fabAddManual.setOnClickListener {
            closeSpeedDial()
            findNavController().navigate(
                R.id.action_inventoryListFragment_to_addEditProductFragment,
                bundleOf(ARG_PRODUCT_ID to 0L),
            )
        }
        binding.fabScanBarcode.setOnClickListener {
            closeSpeedDial()
            findNavController().navigate(
                R.id.action_inventoryListFragment_to_barcodeScannerFragment,
                bundleOf("scan_mode" to "SCAN_FOR_LOOKUP"),
            )
        }
        binding.fabScanReceipt.setOnClickListener {
            closeSpeedDial()
            findNavController().navigate(
                R.id.action_inventoryListFragment_to_receiptScanFragment,
            )
        }
    }

    private fun openSpeedDial() {
        binding.fabScanReceipt.visibility = View.VISIBLE
        binding.fabScanBarcode.visibility = View.VISIBLE
        binding.fabAddManual.visibility = View.VISIBLE
        binding.fabMain.setIconResource(R.drawable.ic_close)
        binding.fabMain.contentDescription = getString(R.string.inventory_fab_close_desc)
        binding.fabMain.shrink()
    }

    private fun closeSpeedDial() {
        speedDialOpen = false
        binding.fabScanReceipt.visibility = View.GONE
        binding.fabScanBarcode.visibility = View.GONE
        binding.fabAddManual.visibility = View.GONE
        binding.fabMain.setIconResource(R.drawable.ic_add)
        binding.fabMain.contentDescription = getString(R.string.inventory_fab_expand_desc)
        binding.fabMain.shrink()
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
