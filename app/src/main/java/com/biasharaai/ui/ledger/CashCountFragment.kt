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
import com.biasharaai.databinding.FragmentCashCountBinding
import com.biasharaai.money.MoneyFormatter
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CashCountFragment : BaseFragment() {

    private var _binding: FragmentCashCountBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LedgerViewModel by viewModels()

    @Inject
    lateinit var moneyFormatter: MoneyFormatter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCashCountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.textExpected.text = getString(
                        R.string.ledger_expected_cash,
                        moneyFormatter.format(state.runningBalance),
                    )
                }
            }
        }

        binding.btnSave.setOnClickListener {
            val actual = binding.editActual.text?.toString()?.toDoubleOrNull()
            if (actual == null || actual < 0) {
                Snackbar.make(binding.root, R.string.ledger_invalid_amount, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val notes = binding.editNotes.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            viewModel.submitCashCount(actual, notes) {
                Snackbar.make(binding.root, R.string.ledger_cash_count_saved, Snackbar.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
