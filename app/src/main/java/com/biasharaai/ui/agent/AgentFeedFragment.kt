package com.biasharaai.ui.agent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.biasharaai.R
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.data.local.db.AgentAction
import com.biasharaai.databinding.FragmentAgentFeedBinding
import com.biasharaai.ui.base.BaseFragment
import com.biasharaai.ui.negotiation.NegotiationViewModel
import com.biasharaai.ui.negotiation.showNegotiationTierBlockedDialogIfNeeded
import com.biasharaai.ui.pos.ReceiptViewModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AgentFeedFragment : BaseFragment() {

    private var _binding: FragmentAgentFeedBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AgentFeedViewModel by viewModels()
    private val negotiationViewModel: NegotiationViewModel by activityViewModels()

    @Inject
    lateinit var capabilityTier: CapabilityTier

    private val adapter by lazy {
        AgentActionCardAdapter(
            onReview = { navigateReview(it) },
            onApprove = { viewModel.approve(it) },
            onReject = { viewModel.reject(it) },
            onSnooze = { viewModel.snooze(it) },
            onDismiss = { viewModel.dismiss(it) },
            onView = { navigateReview(it) },
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAgentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerActions.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerActions.adapter = adapter
        (binding.recyclerActions.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshAgents()
            binding.swipeRefresh.postDelayed(
                { binding.swipeRefresh.isRefreshing = false },
                1400L,
            )
        }

        binding.buttonPrepareSupplierVisit.setOnClickListener {
            if (showNegotiationTierBlockedDialogIfNeeded(capabilityTier)) return@setOnClickListener
            negotiationViewModel.resetScriptOutput()
            findNavController().navigate(R.id.action_agentFeedFragment_to_supplierNegotiationFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.textGreeting.text = state.greeting
                        binding.textDate.text = state.dateLine
                        binding.chipAttention.text = state.attentionLabel
                        adapter.submitList(state.rows)
                        val empty = state.rows.isEmpty()
                        binding.recyclerActions.isVisible = !empty
                        binding.emptyState.isVisible = empty
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is AgentFeedEvent.ApproveSuccess -> flashApproveSuccessCard(event.actionId)
                            is AgentFeedEvent.ApproveNeedsNavigation -> {
                                if (navigateReviewIfPossible(event.action)) {
                                    viewModel.markExecutedAfterNavigation(event.action.id)
                                    flashApproveSuccessCard(event.action.id)
                                }
                            }
                            is AgentFeedEvent.ApproveFailed -> {
                                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG)
                                    .setAction(R.string.agent_action_retry) { viewModel.approve(event.action) }
                                    .show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun flashApproveSuccessCard(actionId: Long) {
        binding.recyclerActions.post {
            val i = adapter.currentList.indexOfFirst { it.action.id == actionId }
            if (i < 0) return@post
            val v = binding.recyclerActions
                .findViewHolderForAdapterPosition(i)
                ?.itemView as? MaterialCardView
            v?.setCardBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.biashara_success_light,
                ),
            )
            v?.animate()?.alpha(0f)?.scaleY(0.9f)?.setDuration(320)?.start()
        }
    }

    private fun navigateReview(action: AgentAction) {
        navigateReviewIfPossible(action)
    }

    /** @return true when a navigation was started (so the caller may mark the action executed). */
    private fun navigateReviewIfPossible(action: AgentAction): Boolean {
        val nav = findNavController()
        when (action.relatedEntityType?.uppercase()) {
            "DAY", "WEEK" -> {
                nav.navigate(R.id.action_agentFeedFragment_to_insightsFragment)
                return true
            }
        }
        val id = action.relatedEntityId ?: run {
            Snackbar.make(binding.root, R.string.agent_review_no_target, Snackbar.LENGTH_SHORT).show()
            return false
        }
        return when (action.relatedEntityType?.uppercase()) {
            "PRODUCT" -> {
                nav.navigate(
                    R.id.action_agentFeedFragment_to_addEditProductFragment,
                    bundleOf("product_id" to id),
                )
                true
            }
            "TRANSACTION" -> {
                nav.navigate(
                    R.id.action_agentFeedFragment_to_receiptFragment,
                    bundleOf(ReceiptViewModel.ARG_TRANSACTION_ID to id),
                )
                true
            }
            "CUSTOMER" -> {
                nav.navigate(R.id.action_agentFeedFragment_to_chatFragment)
                true
            }
            else -> {
                Snackbar.make(binding.root, R.string.agent_review_no_target, Snackbar.LENGTH_SHORT).show()
                false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
