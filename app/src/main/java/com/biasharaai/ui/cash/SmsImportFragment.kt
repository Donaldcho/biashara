package com.biasharaai.ui.cash

import android.content.ClipboardManager
import android.content.Context
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
import com.biasharaai.cash.RegexParser
import com.biasharaai.databinding.FragmentSmsImportBinding
import com.biasharaai.ui.base.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SmsImportFragment : BaseFragment() {

    private var _binding: FragmentSmsImportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SmsImportViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSmsImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkClipboard()

        binding.btnPasteClipboard.setOnClickListener { pasteFromClipboard() }
        binding.btnParse.setOnClickListener { parseAndNavigate() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val b = _binding ?: return@collect
                    b.progressParsing.isVisible = state is SmsImportUiState.Parsing
                    b.btnParse.isEnabled = state !is SmsImportUiState.Parsing
                    when (state) {
                        is SmsImportUiState.Ready -> {
                            findNavController().navigate(
                                R.id.action_smsImportFragment_to_confirmationFragment,
                                bundleOf(
                                    ConfirmationFragment.ARG_DIRECTION to (state.parsed.suggestedDirection?.name
                                        ?: "MONEY_IN"),
                                    ConfirmationFragment.ARG_PARSED_AMOUNT to (state.parsed.amount?.toString() ?: ""),
                                    ConfirmationFragment.ARG_PARSED_REFERENCE to (state.parsed.reference ?: ""),
                                    ConfirmationFragment.ARG_PARSED_COUNTERPARTY to (state.parsed.counterparty ?: ""),
                                    ConfirmationFragment.ARG_RAW_TEXT to state.rawText,
                                    ConfirmationFragment.ARG_PROOF_TYPE to state.parsed.proofType.name,
                                    ConfirmationFragment.ARG_CONFIDENCE to state.parsed.confidence,
                                    ConfirmationFragment.ARG_CAPTURE_METHOD to "SMS_IMPORT",
                                    ConfirmationFragment.ARG_PARSER_ENGINE to state.parsed.engine.name,
                                ),
                            )
                            viewModel.resetToIdle()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun checkClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return
        val upper = text.uppercase()
        val looksLikeMpesa = (upper.contains("M-PESA") || upper.contains("MPESA") || upper.contains("CONFIRMED")) &&
            upper.contains("KSH")
        if (looksLikeMpesa) {
            _binding?.cardClipboard?.isVisible = true
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return
        binding.etSms.setText(text)
        binding.cardClipboard.isVisible = false
    }

    private fun parseAndNavigate() {
        val text = binding.etSms.text?.toString()?.trim() ?: ""
        if (text.isBlank()) {
            binding.tilSms.error = "Paste an SMS first"
            return
        }
        binding.tilSms.error = null
        viewModel.parse(text)
    }
}
