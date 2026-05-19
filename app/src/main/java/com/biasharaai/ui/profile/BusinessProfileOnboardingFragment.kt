package com.biasharaai.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.biasharaai.R
import com.biasharaai.databinding.FragmentBusinessProfileOnboardingBinding
import com.biasharaai.ui.base.BaseFragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BusinessProfileOnboardingFragment : BaseFragment() {

    private var _binding: FragmentBusinessProfileOnboardingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BusinessProfileOnboardingViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentBusinessProfileOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.btnSend.setOnClickListener { submitAnswer() }
        binding.editAnswer.setOnEditorActionListener { _, _, _ ->
            submitAnswer()
            true
        }
        binding.btnSkip.setOnClickListener { viewModel.skipStep() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.textCurrentQuestion.text = getString(state.currentQuestionResId)
                    renderLines(state.lines)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        BusinessProfileOnboardingEvent.Finished -> {
                            Snackbar.make(binding.root, R.string.business_onboarding_complete, Snackbar.LENGTH_LONG).show()
                            findNavController().navigateUp()
                        }
                        is BusinessProfileOnboardingEvent.ShowMessage ->
                            Snackbar.make(binding.root, event.messageResId, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun submitAnswer() {
        val answer = binding.editAnswer.text?.toString().orEmpty()
        viewModel.submitAnswer(answer)
        binding.editAnswer.text?.clear()
    }

    private var welcomeShown = false

    private fun renderLines(lines: List<OnboardingChatLine>) {
        val container = binding.layoutMessages
        if (!welcomeShown) {
            addBubble(container, fromAgent = true, getString(R.string.business_onboarding_welcome))
            welcomeShown = true
        }
        val chatBubbles = container.childCount - 1
        if (lines.size <= chatBubbles) return
        for (i in chatBubbles until lines.size) {
            val line = lines[i]
            val text = if (line.fromAgent) {
                getString(R.string.business_onboarding_confirmed, line.text)
            } else {
                line.text
            }
            addBubble(container, line.fromAgent, text)
        }
        binding.scrollMessages.post {
            binding.scrollMessages.fullScroll(NestedScrollView.FOCUS_DOWN)
        }
    }

    private fun addBubble(parent: ViewGroup, fromAgent: Boolean, text: String) {
        val card = MaterialCardView(requireContext()).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.screen_edge_padding) / 2
            }
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(
                requireContext().getColor(
                    if (fromAgent) R.color.agent_bubble_background else R.color.user_bubble_background,
                ),
            )
        }
        val tv = TextView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding(24, 16, 24, 16)
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        }
        card.addView(tv)
        parent.addView(card)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
