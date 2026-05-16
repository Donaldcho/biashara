package com.biasharaai.ui.pos

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.biasharaai.R
import com.biasharaai.data.local.db.Product
import com.biasharaai.databinding.FragmentPosBinding
import com.biasharaai.databinding.ItemProductSearchResultBinding
import com.biasharaai.pos.cart.CartItem
import com.biasharaai.pos.cart.CartManager
import com.biasharaai.money.MoneyFormatter
import com.biasharaai.pos.cart.CartRepository
import com.biasharaai.ui.base.BaseFragment
import com.biasharaai.ui.scanner.BarcodeScannerFragment
import com.biasharaai.ui.scanner.ScanMode
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class PosFragment : BaseFragment() {

    private var _binding: FragmentPosBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PosViewModel by viewModels()

    @Inject
    lateinit var cartManager: CartManager

    @Inject
    lateinit var cartRepository: CartRepository

    @Inject
    lateinit var moneyFormatter: MoneyFormatter

    private lateinit var productGridAdapter: ProductGridAdapter
    private lateinit var searchAdapter: PosSearchResultsAdapter

    private var wideCartAdapter: CartAdapter? = null
    private var totalsBarBound = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupSearchView()
        setupCustomerChip()
        setupScanButton()
        setupTransactionHistoryButton()
        setupPosOverflowMenu()
        setupCartInteractions()
        observeCustomerSuggestions()
        observeViewModel()
        observeScannerResult()
        observeCartAndTotals()
        applyLayoutMode()
    }

    private fun setupRecyclerViews() {
        val span = gridSpanCount()
        binding.recyclerProductGrid.layoutManager = GridLayoutManager(requireContext(), span)
        productGridAdapter = ProductGridAdapter(
            onProductClick = { viewModel.addProductToCart(it, 1) },
            onProductLongClick = { showQuantityDialog(it) },
            formatMoney = { moneyFormatter.format(it) },
        )
        binding.recyclerProductGrid.adapter = productGridAdapter

        binding.recyclerSearchResults.layoutManager = LinearLayoutManager(requireContext())
        searchAdapter = PosSearchResultsAdapter(
            formatMoney = { moneyFormatter.format(it) },
            onPick = { product ->
                viewModel.addProductToCart(product, 1)
                viewModel.clearSearch()
                binding.searchView.setQuery("", false)
                binding.recyclerSearchResults.visibility = View.GONE
            },
        )
        binding.recyclerSearchResults.adapter = searchAdapter

        setupWideCartPanelIfNeeded()
    }

    private fun setupWideCartPanelIfNeeded() {
        if (!isSideCartVisible()) return
        if (wideCartAdapter != null) return
        wideCartAdapter = CartAdapter(
            cartManager = cartManager,
            formatMoney = { moneyFormatter.format(it) },
            allowPriceOverride = cartRepository.activeSettings.value?.allowPriceOverride != false,
            onRequestPriceOverride = { item ->
                CartLinePriceOverrideDialog.show(
                    this,
                    item,
                    moneyFormatter::format,
                ) { cartItem, newPrice ->
                    viewModel.applyLinePriceOverride(cartItem, newPrice)
                }
            },
        )
        binding.recyclerCartLines.adapter = wideCartAdapter
        CartAdapter.attachSwipeToRemove(binding.recyclerCartLines, wideCartAdapter!!, cartManager)
    }

    private fun gridSpanCount(): Int {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val wide = resources.configuration.smallestScreenWidthDp >= 600
        return if (land || wide) 3 else 2
    }

    private fun applyLayoutMode() {
        val sideCart = isSideCartVisible()
        binding.textCartPanelTitle.visibility = if (sideCart) View.VISIBLE else View.GONE
        binding.recyclerCartLines.visibility = if (sideCart) View.VISIBLE else View.GONE
        binding.cartSummaryTapArea.isClickable = !sideCart
        binding.cartSummaryTapArea.isFocusable = !sideCart
        ensureTotalsBarBound()
        setupWideCartPanelIfNeeded()
    }

    private fun ensureTotalsBarBound() {
        if (totalsBarBound || !isSideCartVisible()) return
        totalsBarBound = true
        binding.totalsBar.bind(viewLifecycleOwner, cartRepository, moneyFormatter)
    }

    private fun isSideCartVisible(): Boolean {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val wide = resources.configuration.smallestScreenWidthDp >= 600
        return land || wide
    }

    private fun setupCartInteractions() {
        binding.cartSummaryTapArea.setOnClickListener {
            if (isSideCartVisible()) return@setOnClickListener
            CartBottomSheetFragment.newInstance().show(childFragmentManager, CartBottomSheetFragment.TAG)
        }

        val payListener = View.OnClickListener {
            findNavController().navigate(R.id.action_posFragment_to_paymentDialogFragment)
        }
        binding.btnPayCompact.setOnClickListener(payListener)
        binding.btnPayWide.setOnClickListener(payListener)
    }

    private fun setupSearchView() {
        val sv = binding.searchView
        sv.setIconifiedByDefault(false)
        sv.isIconified = false
        sv.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.setSearchQuery(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText.orEmpty())
                return true
            }
        })
    }

    private fun observeCustomerSuggestions() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.customerSuggestionsUi, cartManager.items) { ui, lines ->
                    ui to lines.map { it.product.id }.toSet()
                }.collect { (ui, cartIds) ->
                    val visible = ui.products.filter { it.id !in cartIds }
                    binding.chipGroupSuggestions.removeAllViews()
                    binding.scrollSuggestions.visibility =
                        if (visible.isEmpty()) View.GONE else View.VISIBLE
                    val ctx = requireContext()
                    for (p in visible) {
                        val chip = Chip(ctx).apply {
                            text = p.name
                            isCheckable = false
                            isClickable = true
                            contentDescription = getString(R.string.pos_suggestion_chip_cd, p.name)
                            setOnClickListener { viewModel.addProductToCart(p, 1) }
                        }
                        binding.chipGroupSuggestions.addView(chip)
                    }
                    val sub = ui.gemmaSubtitle
                    if (sub.isNullOrBlank()) {
                        binding.textSuggestionSubtitle.visibility = View.GONE
                    } else {
                        binding.textSuggestionSubtitle.text = sub
                        binding.textSuggestionSubtitle.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun setupCustomerChip() {
        binding.chipCustomer.setOnClickListener {
            CustomerSelectorBottomSheet.newInstance().apply {
                onCustomerPicked = { customer ->
                    if (customer == null) {
                        viewModel.selectWalkInCustomer()
                    } else {
                        viewModel.selectCustomer(customer)
                    }
                }
            }.show(childFragmentManager, "customer_selector")
        }
    }

    private fun setupScanButton() {
        binding.btnScan.setOnClickListener {
            findNavController().navigate(
                R.id.action_posFragment_to_barcodeScannerFragment,
                bundleOf(
                    BarcodeScannerFragment.ARG_SCAN_MODE to ScanMode.SCAN_TO_ADD.name,
                    BarcodeScannerFragment.ARG_RETURN_BARCODE_TO_POS to true,
                ),
            )
        }
    }

    private fun setupTransactionHistoryButton() {
        binding.btnTransactionHistory.setOnClickListener {
            findNavController().navigate(R.id.action_posFragment_to_transactionHistoryFragment)
        }
    }

    private fun setupPosOverflowMenu() {
        binding.btnPosMenu.setOnClickListener { anchor ->
            PopupMenu(requireContext(), anchor).apply {
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.pos_close_day))
                setOnMenuItemClickListener {
                    findNavController().navigate(R.id.action_posFragment_to_endOfDayFragment)
                    true
                }
                show()
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.searchResults.collect { list ->
                        searchAdapter.submitList(list)
                        binding.recyclerSearchResults.visibility =
                            if (list.isEmpty()) View.GONE else View.VISIBLE
                    }
                }
                launch {
                    viewModel.allProducts.collect { list ->
                        productGridAdapter.submitList(list)
                    }
                }
                launch {
                    viewModel.selectedCustomer.collect { customer ->
                        val walkInColor = ContextCompat.getColor(requireContext(), R.color.biashara_forest_dark)
                        val customerColor = ContextCompat.getColor(requireContext(), R.color.biashara_forest)
                        if (customer == null) {
                            binding.chipCustomer.text = getString(R.string.pos_walk_in_customer)
                            binding.chipCustomer.setTextColor(walkInColor)
                        } else {
                            binding.chipCustomer.text = customer.name
                            binding.chipCustomer.setTextColor(customerColor)
                        }
                    }
                }
                launch {
                    viewModel.priceWarning.collect { ev ->
                        Snackbar.make(binding.root, ev.message, Snackbar.LENGTH_LONG)
                            .setAction(R.string.pos_price_warning_undo) {
                                viewModel.undoPriceOverride(ev.productId, ev.previousOverride)
                            }
                            .show()
                    }
                }
                launch {
                    viewModel.unknownBarcode.collect { code ->
                        Snackbar.make(
                            binding.root,
                            getString(R.string.pos_barcode_not_found, code),
                            Snackbar.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

    private fun observeScannerResult() {
        val handle = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        handle.getLiveData<String>(RESULT_KEY_SCANNED_BARCODE)
            .observe(viewLifecycleOwner) { code ->
                if (!code.isNullOrBlank()) {
                    viewModel.onScannedBarcode(code)
                    handle.remove<String>(RESULT_KEY_SCANNED_BARCODE)
                }
            }
    }

    private fun observeCartAndTotals() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    cartManager.items.collect { lines ->
                        val n = lines.sumOf { it.quantity }
                        binding.textCartBadge.text = if (n == 1) {
                            getString(R.string.pos_cart_items_count_one)
                        } else {
                            getString(R.string.pos_cart_items_count, n)
                        }
                        if (isSideCartVisible()) {
                            wideCartAdapter?.submitList(lines)
                        }
                    }
                }
                launch {
                    combine(
                        cartRepository.grandTotal,
                        cartRepository.activeSettings,
                    ) { total, _ -> total }.collect { total ->
                        binding.textCartGrandTotal.text = moneyFormatter.format(total)
                    }
                }
                launch {
                    cartRepository.activeSettings
                        .map { it?.allowPriceOverride != false }
                        .distinctUntilChanged()
                        .collect { allow ->
                            wideCartAdapter?.setAllowPriceOverride(allow)
                            wideCartAdapter?.notifyDataSetChanged()
                        }
                }
            }
        }
    }

    private fun showQuantityDialog(product: Product) {
        val input = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.pos_qty_dialog_hint)
            setText("1")
        }
        val padding = resources.getDimensionPixelSize(R.dimen.pos_dialog_padding)
        val container = android.widget.FrameLayout(requireContext()).apply {
            setPadding(padding, padding, padding, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.pos_qty_dialog_title))
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.pos_qty_dialog_positive) { _, _ ->
                val qty = input.text?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                viewModel.addProductToCart(product, qty)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        totalsBarBound = false
        wideCartAdapter = null
        _binding = null
    }

    companion object {
        const val RESULT_KEY_SCANNED_BARCODE = "pos_scanned_barcode"
    }
}

private class PosSearchResultsAdapter(
    private val formatMoney: (Double) -> String,
    private val onPick: (Product) -> Unit,
) : ListAdapter<Product, PosSearchResultsAdapter.VH>(SEARCH_DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemProductSearchResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(
        private val row: ItemProductSearchResultBinding,
    ) : RecyclerView.ViewHolder(row.root) {

        fun bind(product: Product) {
            row.textName.text = product.name
            val stock = product.stockQuantity
            row.textMeta.text = row.root.context.getString(
                R.string.pos_search_row_meta,
                formatMoney(product.price),
                row.root.context.getString(R.string.pos_stock_badge, stock),
            )
            row.root.setOnClickListener { onPick(product) }
        }
    }

    companion object {
        private val SEARCH_DIFF = object : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(oldItem: Product, newItem: Product): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Product, newItem: Product): Boolean =
                oldItem == newItem
        }
    }
}
