package com.biasharaai.ui.ledger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.databinding.FragmentManualLedgerEntryBinding
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ManualLedgerEntryFragment : BaseFragment() {

    private var _binding: FragmentManualLedgerEntryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LedgerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentManualLedgerEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toggleDirection.check(R.id.btn_money_in)
        observeEvents()

        binding.btnSave.setOnClickListener {
            val amount = binding.editAmount.text?.toString()?.toDoubleOrNull()
            val description = binding.editDescription.text?.toString()?.trim().orEmpty()
            if (amount == null || amount <= 0) {
                Snackbar.make(binding.root, R.string.ledger_invalid_amount, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (description.isEmpty()) {
                Snackbar.make(binding.root, R.string.ledger_invalid_description, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val direction = if (binding.toggleDirection.checkedButtonId == R.id.btn_money_in) {
                LedgerDirection.MONEY_IN
            } else {
                LedgerDirection.MONEY_OUT
            }
            val notes = binding.editNotes.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            viewModel.submitManualEntry(direction, amount, description, notes) {
                Snackbar.make(binding.root, R.string.ledger_saved, Snackbar.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is LedgerViewModel.Event.PermissionDenied -> {
                            Snackbar.make(
                                binding.root,
                                getString(
                                    R.string.settings_enterprise_permission_denied,
                                    event.operatorName,
                                    event.operatorRole,
                                ),
                                Snackbar.LENGTH_LONG,
                            ).show()
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
