package com.biasharaai.ui.negotiation

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.databinding.FragmentNegotiationGuideBinding
import com.biasharaai.databinding.ItemNegotiationSectionBinding
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NegotiationGuideFragment : BaseFragment() {

    private var _binding: FragmentNegotiationGuideBinding? = null
    private val binding get() = _binding!!

    private val negotiationViewModel: NegotiationViewModel by activityViewModels()

    @Inject
    lateinit var capabilityTier: CapabilityTier

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentNegotiationGuideBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (showNegotiationTierBlockedDialogIfNeeded(capabilityTier)) {
            findNavController().navigateUp()
            return
        }

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.btnRegenerate.setOnClickListener {
            negotiationViewModel.regenerate()
        }

        binding.fabShareScript.setOnClickListener {
            val text = negotiationViewModel.fullScript.value
            if (text.isBlank()) return@setOnClickListener
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(
                Intent.createChooser(send, getString(R.string.negotiation_share_chooser_title)),
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    negotiationViewModel.sections.collect { sections ->
                        renderSections(sections)
                    }
                }
                launch {
                    negotiationViewModel.generateState.collect { state ->
                        binding.progressRegenerate.isVisible = state is NegotiationGenerateState.Loading
                        binding.btnRegenerate.isEnabled = state !is NegotiationGenerateState.Loading
                        binding.fabShareScript.isEnabled = state !is NegotiationGenerateState.Loading
                        when (state) {
                            is NegotiationGenerateState.Success -> {
                                negotiationViewModel.consumeGenerationSuccess()
                            }
                            is NegotiationGenerateState.Error -> {
                                val msg = when (state.message) {
                                    "NO_ITEMS" -> getString(R.string.negotiation_error_no_items)
                                    "BAD_BUDGET" -> getString(R.string.negotiation_error_budget)
                                    "MODEL_UNAVAILABLE" -> getString(R.string.negotiation_error_model)
                                    else -> state.message
                                }
                                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun renderSections(sections: List<NegotiationSectionUi>) {
        binding.containerSections.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        for (section in sections) {
            val cardBinding = ItemNegotiationSectionBinding.inflate(inflater, binding.containerSections, false)
            cardBinding.textSectionTitle.text = section.title
            cardBinding.textSectionBody.text = section.body
            val bg = ContextCompat.getColor(requireContext(), section.colorRes)
            cardBinding.root.setCardBackgroundColor(bg)
            binding.containerSections.addView(cardBinding.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
