package com.biasharaai.ui.pos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.databinding.FragmentServiceReceiptScanBinding
import com.biasharaai.service.ServiceReceiptCodec
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class ServiceReceiptScanFragment : BaseFragment() {

    private var _binding: FragmentServiceReceiptScanBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ServiceReceiptScanViewModel by viewModels()

    private val dateTimeFormat = SimpleDateFormat("d MMM yyyy · h:mm a", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentServiceReceiptScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        val token = arguments?.getString(ARG_RAW_TOKEN).orEmpty()
        viewModel.verify(token)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ServiceReceiptScanUiState.Loading -> Unit
                        is ServiceReceiptScanUiState.Invalid -> showInvalid(state.message)
                        is ServiceReceiptScanUiState.Verified -> showVerified(state)
                    }
                }
            }
        }

        binding.btnWarrantyClaim.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                when (val result = viewModel.recordWarrantyClaim()) {
                    ServiceReceiptScanViewModel.WarrantyClaimResult.Success ->
                        Snackbar.make(binding.root, R.string.warranty_claim_recorded, Snackbar.LENGTH_SHORT).show()
                    ServiceReceiptScanViewModel.WarrantyClaimResult.Failed ->
                        Snackbar.make(binding.root, R.string.warranty_claim_failed, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showInvalid(message: String) {
        binding.textStatusTitle.text = getString(R.string.bsrc_invalid_title)
        binding.textStatusBody.text = message.ifBlank { getString(R.string.bsrc_invalid_body) }
        binding.chipWarranty.isVisible = false
        binding.btnWarrantyClaim.isVisible = false
    }

    private fun showVerified(state: ServiceReceiptScanUiState.Verified) {
        binding.textStatusTitle.text = getString(R.string.bsrc_verified_title)
        binding.textStatusBody.text = buildString {
            appendLine(getString(R.string.bsrc_business_label, state.businessName))
            state.transactionId?.let { appendLine(getString(R.string.bsrc_receipt_label, it)) }
            appendLine(getString(R.string.bsrc_service_label, state.serviceName))
            state.deliveredAt?.let { appendLine(dateTimeFormat.format(Date(it))) }
        }.trim()
        binding.chipWarranty.isVisible = true
        binding.chipWarranty.text = when (state.warrantyStatus) {
            ServiceReceiptCodec.WarrantyStatus.ACTIVE -> getString(R.string.warranty_active)
            ServiceReceiptCodec.WarrantyStatus.EXPIRED -> getString(R.string.warranty_expired)
            ServiceReceiptCodec.WarrantyStatus.NONE -> getString(R.string.warranty_none)
        }
        binding.btnWarrantyClaim.isVisible = state.warrantyStatus == ServiceReceiptCodec.WarrantyStatus.ACTIVE
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_RAW_TOKEN = "raw_token"

        fun args(rawToken: String) = bundleOf(ARG_RAW_TOKEN to rawToken)
    }
}
