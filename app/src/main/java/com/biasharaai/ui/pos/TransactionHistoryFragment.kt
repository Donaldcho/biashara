package com.biasharaai.ui.pos

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
import com.biasharaai.databinding.FragmentTransactionHistoryBinding
import com.biasharaai.money.MoneyFormatter
import com.biasharaai.pos.cart.CartRepository
import com.biasharaai.ui.base.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.text.DateFormat
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class TransactionHistoryFragment : BaseFragment() {

    private var _binding: FragmentTransactionHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransactionHistoryViewModel by viewModels()

    @Inject
    lateinit var moneyFormatter: MoneyFormatter

    @Inject
    lateinit var cartRepository: CartRepository

    private val dateFormat: DateFormat =
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())

    private lateinit var adapter: TransactionHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTransactionHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        adapter = TransactionHistoryAdapter(
            moneyFormatter = moneyFormatter,
            dateFormat = dateFormat,
            onClick = { tx ->
                findNavController().navigate(
                    R.id.action_transactionHistoryFragment_to_receiptFragment,
                    bundleOf(ReceiptViewModel.ARG_TRANSACTION_ID to tx.id),
                )
            },
        )
        binding.recyclerTransactions.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.completedSales.collect { list ->
                        adapter.submitList(list)
                        binding.textEmpty.visibility =
                            if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    combine(
                        viewModel.todayPosSalesSummary,
                        cartRepository.activeSettings,
                    ) { summary, _ -> summary }.collect { summary ->
                        binding.textTodaySalesSummary.text = getString(
                            R.string.transactions_today_summary,
                            moneyFormatter.format(summary.totalAmount),
                            summary.transactionCount,
                        )
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
