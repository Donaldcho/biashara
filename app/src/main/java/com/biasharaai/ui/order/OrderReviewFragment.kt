package com.biasharaai.ui.order

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.biasharaai.R
import com.biasharaai.data.local.db.Product
import com.biasharaai.databinding.FragmentOrderReviewBinding
import com.biasharaai.money.MoneyFormatter
import com.biasharaai.pos.cart.CartRepository
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OrderReviewFragment : BaseFragment() {

    private var _binding: FragmentOrderReviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OrderParserViewModel by activityViewModels()

    private lateinit var adapter: OrderReviewAdapter

    @Inject
    lateinit var moneyFormatter: MoneyFormatter

    @Inject
    lateinit var cartRepository: CartRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentOrderReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = OrderReviewAdapter(
            moneyFormatter = moneyFormatter,
            onFindProduct = { line -> showProductSearchDialog(line) },
        )
        binding.recyclerOrderLines.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerOrderLines.adapter = adapter

        binding.btnRecordSale.setOnClickListener {
            viewModel.recordSale()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.orderLines.collect { lines ->
                        adapter.submitList(lines)
                    }
                }
                launch {
                    cartRepository.activeSettings
                        .map { it?.currencyCode }
                        .distinctUntilChanged()
                        .collect {
                            if (::adapter.isInitialized && adapter.itemCount > 0) {
                                adapter.notifyDataSetChanged()
                            }
                        }
                }
            }
        }
    }

    private fun showProductSearchDialog(line: OrderLineItem) {
        val ctx = requireContext()
        val pad = resources.getDimensionPixelSize(R.dimen.screen_edge_padding)
        val density = resources.displayMetrics.density
        val input = EditText(ctx).apply {
            hint = getString(R.string.order_parser_search_hint)
            setText(line.parsedName)
        }
        val rv = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (240 * density).toInt(),
            )
        }
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(input)
            addView(rv)
        }
        lateinit var dialogRef: androidx.appcompat.app.AlertDialog
        val pickAdapter = ProductPickAdapter { product ->
            viewModel.setManualProduct(line.rowKey, product)
            dialogRef.dismiss()
        }
        rv.adapter = pickAdapter
        dialogRef = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.order_parser_find_product)
            .setView(wrap)
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        var searchJob: Job? = null
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                val q = s?.toString().orEmpty()
                searchJob = lifecycleScope.launch {
                    delay(220)
                    val list = viewModel.searchProducts(q)
                    pickAdapter.submitList(list)
                }
            }
        })
        lifecycleScope.launch {
            val list = viewModel.searchProducts(input.text?.toString().orEmpty())
            pickAdapter.submitList(list)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerOrderLines.adapter = null
        _binding = null
    }

    private class ProductPickAdapter(
        private val onPick: (Product) -> Unit,
    ) : RecyclerView.Adapter<ProductPickAdapter.VH>() {

        private var items: List<Product> = emptyList()

        fun submitList(list: List<Product>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                val p = (16 * resources.displayMetrics.density).toInt()
                setPadding(p, p, p, p)
                textSize = 16f
            }
            return VH(tv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = items[position]
            holder.textView.text = p.name
            holder.textView.setOnClickListener { onPick(p) }
        }

        class VH(val textView: TextView) : RecyclerView.ViewHolder(textView)
    }
}
