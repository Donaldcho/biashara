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
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.databinding.FragmentReturnBinding
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReturnFragment : BaseFragment() {

    private var _binding: FragmentReturnBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReturnViewModel by viewModels()

    private lateinit var adapter: ReturnLineAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentReturnBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        adapter = ReturnLineAdapter(
            onCheckedChange = { id, checked -> viewModel.setChecked(id, checked) },
            onQtyChange = { id, qty -> viewModel.setReturnQty(id, qty) },
        )
        binding.recyclerReturnLines.adapter = adapter

        binding.btnProcessReturn.setOnClickListener {
            viewModel.commitReturn { returnTxId ->
                Snackbar.make(binding.root, R.string.return_success, Snackbar.LENGTH_SHORT).show()
                findNavController().navigate(
                    R.id.receiptFragment,
                    bundleOf(ReceiptViewModel.ARG_TRANSACTION_ID to returnTxId),
                    NavOptions.Builder()
                        .setPopUpTo(R.id.receiptFragment, true)
                        .build(),
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.rows.collect { adapter.submitList(it) }
                }
                launch {
                    viewModel.error.collect { err ->
                        if (err == null) return@collect
                        val msg = when (err) {
                            ReturnViewModel.ERROR_NONE ->
                                getString(R.string.return_none_selected)
                            ReturnViewModel.ERROR_QTY ->
                                getString(R.string.return_invalid_qty)
                            ReturnViewModel.ERROR_GENERIC ->
                                getString(R.string.return_failed)
                            else -> err
                        }
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
                launch {
                    viewModel.busy.collect { binding.btnProcessReturn.isEnabled = !it }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
