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
import com.biasharaai.R
import com.biasharaai.databinding.FragmentCollectBalanceBinding
import com.biasharaai.money.MoneyFormatter
import com.biasharaai.pos.payment.PrimaryPaymentTab
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CollectBalanceBottomSheet : BottomSheetDialogFragment() {

    @Inject lateinit var moneyFormatter: MoneyFormatter

    private var _binding: FragmentCollectBalanceBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CollectBalanceViewModel by viewModels()

    var onSettled: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCollectBalanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        var tab = PrimaryPaymentTab.CASH
        binding.togglePayment.check(R.id.tab_cash)
        binding.togglePayment.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            tab = if (checkedId == R.id.tab_mobile) {
                PrimaryPaymentTab.MOBILE_MONEY
            } else {
                PrimaryPaymentTab.CASH
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sale.collect { sale ->
                    if (sale == null) return@collect
                    binding.textSaleRef.text = getString(
                        R.string.collect_balance_sale,
                        sale.receiptNumber ?: sale.id.toString(),
                    )
                    binding.textBalance.text = getString(
                        R.string.collect_balance_amount,
                        moneyFormatter.format(sale.balanceDue),
                    )
                    binding.inputTendered.setText(sale.balanceDue.toString())
                }
            }
        }

        binding.btnConfirm.setOnClickListener {
            val tender = binding.inputTendered.text?.toString()?.toDoubleOrNull() ?: 0.0
            val sale = viewModel.sale.value ?: return@setOnClickListener
            if (tender + 0.01 < sale.balanceDue) {
                Snackbar.make(binding.root, R.string.payment_change_insufficient, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch {
                binding.btnConfirm.isEnabled = false
                val change = if (tab == PrimaryPaymentTab.CASH) tender - sale.balanceDue else null
                when (
                    val result = viewModel.commitSettlement(
                        tab = tab,
                        cashTendered = if (tab == PrimaryPaymentTab.CASH) tender else null,
                        cashChange = change,
                        mobileNetwork = if (tab == PrimaryPaymentTab.MOBILE_MONEY) "MPESA" else null,
                        mobileRef = null,
                    )
                ) {
                    is SaleCommitResult.Success -> {
                        onSettled?.invoke()
                        dismiss()
                    }
                    is SaleCommitResult.Failure -> {
                        Snackbar.make(binding.root, result.message, Snackbar.LENGTH_LONG).show()
                        binding.btnConfirm.isEnabled = true
                    }
                    else -> binding.btnConfirm.isEnabled = true
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "CollectBalanceBottomSheet"

        fun newInstance(transactionId: Long) = CollectBalanceBottomSheet().apply {
            arguments = bundleOf(CollectBalanceViewModel.ARG_TRANSACTION_ID to transactionId)
        }
    }
}
