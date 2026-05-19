package com.biasharaai.ui.pos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import com.biasharaai.R
import com.biasharaai.data.local.db.ServiceItem
import com.biasharaai.databinding.FragmentVoucherIssueBinding
import com.biasharaai.money.MoneyFormatter
import com.biasharaai.pos.cart.VoucherCartItem
import com.biasharaai.service.voucherValidDays
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class VoucherIssueBottomSheet : BottomSheetDialogFragment() {

    @Inject lateinit var moneyFormatter: MoneyFormatter

    var onConfirm: ((VoucherIssueParams) -> Unit)? = null

    private var _binding: FragmentVoucherIssueBinding? = null
    private val binding get() = _binding!!

    private var uses = 5
    private lateinit var service: ServiceItem

    data class VoucherIssueParams(
        val serviceItem: ServiceItem,
        val uses: Int,
        val pricePerUse: Double,
        val customerId: Long? = null,
        val customerName: String? = null,
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentVoucherIssueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val serviceId = arguments?.getLong(ARG_SERVICE_ID) ?: run {
            dismiss()
            return
        }
        val serviceName = arguments?.getString(ARG_SERVICE_NAME).orEmpty()
        val basePrice = arguments?.getDouble(ARG_BASE_PRICE) ?: 0.0
        service = ServiceItem(
            id = serviceId,
            name = serviceName,
            basePrice = basePrice,
            catalogueToken = "",
        )

        binding.textServiceName.text = service.name
        binding.textBasePrice.text = getString(R.string.voucher_base_price, moneyFormatter.format(basePrice))
        binding.inputPricePerUse.setText(formatPrice(basePrice))

        binding.btnUsesMinus.setOnClickListener {
            uses = (uses - 1).coerceIn(MIN_USES, MAX_USES)
            refreshSummary()
        }
        binding.btnUsesPlus.setOnClickListener {
            uses = (uses + 1).coerceIn(MIN_USES, MAX_USES)
            refreshSummary()
        }
        binding.inputPricePerUse.setOnFocusChangeListener { _, _ -> refreshSummary() }
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSell.setOnClickListener {
            val price = binding.inputPricePerUse.text?.toString()?.toDoubleOrNull() ?: basePrice
            onConfirm?.invoke(
                VoucherIssueParams(
                    serviceItem = service,
                    uses = uses,
                    pricePerUse = price,
                ),
            )
            dismiss()
        }
        refreshSummary()
    }

    private fun refreshSummary() {
        binding.textUses.text = uses.toString()
        val price = binding.inputPricePerUse.text?.toString()?.toDoubleOrNull() ?: service.basePrice
        val total = uses * price
        binding.textSummary.text = getString(
            R.string.voucher_issue_summary,
            moneyFormatter.format(total),
            service.voucherValidDays,
        )
    }

    private fun formatPrice(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "VoucherIssueBottomSheet"
        private const val ARG_SERVICE_ID = "service_id"
        private const val ARG_SERVICE_NAME = "service_name"
        private const val ARG_BASE_PRICE = "base_price"
        private const val MIN_USES = 1
        private const val MAX_USES = 20

        fun newInstance(service: ServiceItem): VoucherIssueBottomSheet =
            VoucherIssueBottomSheet().apply {
                arguments = bundleOf(
                    ARG_SERVICE_ID to service.id,
                    ARG_SERVICE_NAME to service.name,
                    ARG_BASE_PRICE to service.basePrice,
                )
            }
    }
}
