package com.biasharaai.ui.pos

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
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
import com.biasharaai.ai.AudioCaptureHelper
import com.biasharaai.ai.VoiceInputPreferences
import com.biasharaai.ai.VoiceInputProcessor
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.TransactionDao
import com.biasharaai.databinding.FragmentPosBinding
import com.biasharaai.databinding.ItemProductSearchResultBinding
import com.biasharaai.pos.cart.CartItem
import com.biasharaai.pos.cart.CartManager
import com.biasharaai.money.MoneyFormatter
import com.biasharaai.pos.cart.CartRepository
import com.biasharaai.ui.base.BaseFragment
import com.biasharaai.productline.ProductLineManager
import com.biasharaai.productline.showProRequiredSnackbar
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

    @Inject
    lateinit var voiceInputProcessor: VoiceInputProcessor

    @Inject
    lateinit var voiceInputPreferences: VoiceInputPreferences

    @Inject
    lateinit var productLineManager: ProductLineManager

    @Inject
    lateinit var transactionDao: TransactionDao

    private lateinit var productGridAdapter: ProductGridAdapter
    private lateinit var serviceGridAdapter: ServiceGridAdapter
    private lateinit var searchAdapter: PosSearchResultsAdapter

    private var wideCartAdapter: CartAdapter? = null
    private var totalsBarBound = false

    private val posSpeechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val text = matches?.firstOrNull()?.trim().orEmpty()
        if (text.isEmpty()) return@registerForActivityResult
        _binding?.let { b ->
            b.searchView.setQuery(text, false)
            viewModel.setSearchQuery(text)
        }
    }

    private val posMicPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchPosVoiceSearch()
            return@registerForActivityResult
        }
        val root = _binding?.root ?: return@registerForActivityResult
        Snackbar.make(root, R.string.chat_mic_permission_denied, Snackbar.LENGTH_SHORT).show()
    }

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
        setupProPosUi()
        setupTransactionHistoryButton()
        setupPosOverflowMenu()
        setupCartInteractions()
        setupVoiceSearch()
        observeCustomerSuggestions()
        observeViewModel()
        observeScannerResult()
        observeCartAndTotals()
        observeStaffPicker()
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

        serviceGridAdapter = ServiceGridAdapter(
            formatMoney = { moneyFormatter.format(it) },
            onServiceClick = { service -> viewModel.onServiceTapped(service) },
            onServiceLongClick = { service -> showVoucherIssueSheet(service) },
        )
        binding.recyclerServiceGrid?.layoutManager = GridLayoutManager(requireContext(), span)
        binding.recyclerServiceGrid?.adapter = serviceGridAdapter

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
            onRequestProductPriceOverride = { item ->
                CartLinePriceOverrideDialog.show(
                    this,
                    item,
                    moneyFormatter::format,
                ) { cartItem, newPrice ->
                    viewModel.applyLinePriceOverride(cartItem, newPrice)
                }
            },
            onRequestServicePriceOverride = { line ->
                ServiceCartLinePriceOverrideDialog.show(
                    this,
                    line,
                    moneyFormatter::format,
                ) { serviceLine, newPrice ->
                    viewModel.applyServiceLinePriceOverride(serviceLine, newPrice)
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

    private fun setupVoiceSearch() {
        binding.btnVoiceSearch?.setOnClickListener { onVoiceSearchClicked() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                voiceInputPreferences.voiceInputEnabled.collect { enabled ->
                    binding.btnVoiceSearch?.visibility = if (enabled) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun onVoiceSearchClicked() {
        if (!voiceInputPreferences.isVoiceInputEnabled()) return
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchPosVoiceSearch()
        } else {
            posMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun launchPosVoiceSearch() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (voiceInputProcessor.shouldUseOnDeviceAi() &&
                AudioCaptureHelper.hasRecordPermission(requireContext())
            ) {
                val text = runCatching {
                    voiceInputProcessor.transcribeWithAi(
                        Locale.getDefault(),
                        AudioCaptureHelper.DEFAULT_DURATION_MS,
                    )
                }.getOrNull()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    val b = _binding ?: return@launch
                    b.searchView.setQuery(text, false)
                    viewModel.setSearchQuery(text)
                    return@launch
                }
            }
            launchPosSystemSpeechRecognizer()
        }
    }

    private fun launchPosSystemSpeechRecognizer() {
        val b = _binding ?: return
        try {
            val intent = voiceInputProcessor.createSpeechRecognizerIntent(
                locale = Locale.getDefault(),
                prompt = getString(R.string.product_voice_prompt),
            )
            posSpeechLauncher.launch(intent)
        } catch (_: Exception) {
            Snackbar.make(b.root, R.string.chat_mic_unavailable, Snackbar.LENGTH_SHORT).show()
        }
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
        binding.chipCustomer.setOnLongClickListener {
            val customer = viewModel.selectedCustomer.value ?: return@setOnLongClickListener false
            viewLifecycleOwner.lifecycleScope.launch {
                val open = transactionDao.getOpenBalancesForCustomer(customer.id)
                if (open.isEmpty()) {
                    return@launch
                }
                CollectBalanceBottomSheet.newInstance(open.first().id).apply {
                    onSettled = {
                        Snackbar.make(binding.root, R.string.product_saved, Snackbar.LENGTH_SHORT).show()
                    }
                }.show(childFragmentManager, CollectBalanceBottomSheet.TAG)
            }
            true
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
        binding.btnScanServiceToken?.setOnClickListener {
            if (!productLineManager.isProEnabled()) {
                binding.root.showProRequiredSnackbar(productLineManager)
                return@setOnClickListener
            }
            findNavController().navigate(
                R.id.action_posFragment_to_barcodeScannerFragment,
                bundleOf(
                    BarcodeScannerFragment.ARG_SCAN_MODE to ScanMode.SCAN_TO_ADD.name,
                    BarcodeScannerFragment.ARG_RETURN_BARCODE_TO_POS to true,
                ),
            )
        }
    }

    private fun setupProPosUi() {
        val pro = productLineManager.isProEnabled()
        binding.chipGroupPosMode?.visibility = if (pro) View.VISIBLE else View.GONE
        binding.btnScanServiceToken?.visibility = if (pro) View.VISIBLE else View.GONE
        if (!pro) {
            applyCatalogVisibility(PosCatalogMode.PRODUCTS)
            return
        }
        binding.chipPosProducts?.setOnClickListener { viewModel.setCatalogMode(PosCatalogMode.PRODUCTS) }
        binding.chipPosServices?.setOnClickListener { viewModel.setCatalogMode(PosCatalogMode.SERVICES) }
        binding.chipPosBoth?.setOnClickListener { viewModel.setCatalogMode(PosCatalogMode.BOTH) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.catalogMode.collect { applyCatalogVisibility(it) }
                }
                launch {
                    viewModel.allServices.collect { serviceGridAdapter.submitList(it) }
                }
            }
        }
    }

    private fun applyCatalogVisibility(mode: PosCatalogMode) {
        val showProducts = mode == PosCatalogMode.PRODUCTS || mode == PosCatalogMode.BOTH
        val showServices = mode == PosCatalogMode.SERVICES || mode == PosCatalogMode.BOTH
        binding.recyclerProductGrid.visibility = if (showProducts) View.VISIBLE else View.GONE
        binding.recyclerServiceGrid?.visibility = if (showServices) View.VISIBLE else View.GONE
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
                            val openBalance = transactionDao.sumOpenBalanceForCustomer(customer.id)
                            binding.chipCustomer.text = if (openBalance > 0) {
                                getString(
                                    R.string.pos_open_balance,
                                    moneyFormatter.format(openBalance),
                                ) + " · ${customer.name}"
                            } else {
                                customer.name
                            }
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
                launch {
                    viewModel.serviceScanMessage.collect { name ->
                        Snackbar.make(
                            binding.root,
                            getString(R.string.pos_service_added, name),
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
                    cartManager.unifiedLines().collect { lines ->
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

    private fun showVoucherIssueSheet(service: com.biasharaai.data.local.db.ServiceItem) {
        if (!productLineManager.isProEnabled()) {
            binding.root.showProRequiredSnackbar(productLineManager)
            return
        }
        val sheet = VoucherIssueBottomSheet.newInstance(service)
        sheet.onConfirm = { params ->
            viewModel.addVoucherToCart(params)
            Snackbar.make(binding.root, R.string.voucher_added_to_cart, Snackbar.LENGTH_SHORT).show()
        }
        sheet.show(childFragmentManager, VoucherIssueBottomSheet.TAG)
    }

    private fun observeStaffPicker() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pendingStaffPick.collect { service ->
                    val count = viewModel.activeStaffCount.value
                    if (count > 0) {
                        StaffPickerBottomSheet.newInstance(service.name).apply {
                            onSelected = { member ->
                                viewModel.addServiceToCart(service, 1, member?.name)
                            }
                        }.show(childFragmentManager, StaffPickerBottomSheet.TAG)
                    } else {
                        viewModel.addServiceToCart(service, 1, null)
                    }
                }
            }
        }
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
