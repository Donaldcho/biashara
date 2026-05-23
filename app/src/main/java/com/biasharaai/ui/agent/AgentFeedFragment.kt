package com.biasharaai.ui.agent

import android.os.Bundle
import android.util.Log
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
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.biasharaai.R
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.data.local.db.AgentAction
import com.biasharaai.databinding.FragmentAgentFeedBinding
import com.biasharaai.locale.LanguagePreferences
import com.biasharaai.ui.base.BaseFragment
import com.biasharaai.ui.negotiation.NegotiationViewModel
import com.biasharaai.ui.negotiation.showNegotiationTierBlockedDialogIfNeeded
import com.biasharaai.ui.insights.CashFlowInsightsFragment
import com.biasharaai.ui.inventory.InventoryListFragment
import com.biasharaai.ui.pos.ReceiptViewModel
import com.biasharaai.voice.BiasharaTtsEngine
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AgentFeedFragment : BaseFragment() {

    private var _binding: FragmentAgentFeedBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AgentFeedViewModel by viewModels()
    private val negotiationViewModel: NegotiationViewModel by activityViewModels()

    @Inject
    lateinit var capabilityTier: CapabilityTier

    @Inject
    lateinit var biasharaTtsEngine: BiasharaTtsEngine

    private var lastAutoReadCriticalId: Long? = null

    private val adapter by lazy {
        AgentActionCardAdapter(
            onReview = { navigateReview(it) },
            onApprove = { viewModel.approve(it) },
            onReject = { viewModel.reject(it) },
            onSnooze = { viewModel.snooze(it) },
            onDismiss = { viewModel.dismiss(it) },
            onView = { navigateReview(it) },
            onFeedback = { action, helpful -> viewModel.submitFeedback(action, helpful) },
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
                { _binding?.swipeRefresh?.isRefreshing = false },
                1400L,
            )
        }

        binding.buttonPrepareSupplierVisit.setOnClickListener {
            if (showNegotiationTierBlockedDialogIfNeeded(capabilityTier)) return@setOnClickListener
            negotiationViewModel.resetScriptOutput()
            navigateSafely { navigate(R.id.action_agentFeedFragment_to_supplierNegotiationFragment) }
        }

        binding.buttonOpenLedger.setOnClickListener {
            navigateSafely {
                navigate(
                    R.id.action_agentFeedFragment_to_insightsFragment,
                    bundleOf(CashFlowInsightsFragment.ARG_INITIAL_TAB to CashFlowInsightsFragment.TAB_LEDGER),
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        val b = _binding ?: return@collect
                        b.textGreeting.text = state.greeting
                        b.textDate.text = state.dateLine
                        b.chipAttention.text = state.attentionLabel
                        b.textAiBriefTitle.text = state.brief.title
                        b.textAiBriefBody.text = state.brief.body
                        adapter.submitList(state.rows)
                        val empty = state.rows.isEmpty()
                        b.recyclerActions.isVisible = !empty
                        b.emptyState.isVisible = empty
                        maybeAutoReadCriticalAlert(state)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is AgentFeedEvent.ApproveSuccess -> flashApproveSuccessCard(event.actionId)
                            is AgentFeedEvent.FeedbackSaved -> {
                                val root = _binding?.root ?: return@collect
                                val message = if (event.hidesSimilarReports) {
                                    R.string.agent_feedback_saved_hide_similar
                                } else {
                                    R.string.agent_feedback_saved
                                }
                                Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show()
                            }
                            is AgentFeedEvent.ApproveNeedsNavigation -> {
                                if (navigateReviewIfPossible(event.action)) {
                                    viewModel.markExecutedAfterNavigation(event.action.id)
                                    flashApproveSuccessCard(event.action.id)
                                }
                            }
                            is AgentFeedEvent.ApproveFailed -> {
                                val root = _binding?.root ?: return@collect
                                Snackbar.make(root, event.message, Snackbar.LENGTH_LONG)
                                    .setAction(R.string.agent_action_retry) { viewModel.approve(event.action) }
                                    .show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun maybeAutoReadCriticalAlert(state: AgentFeedUiState) {
        if (!state.ttsEnabled || !state.ttsAutoReadCriticalAlerts) {
            lastAutoReadCriticalId = null
            return
        }
        val critical = state.rows.firstOrNull {
            it.action.urgency == "CRITICAL" && it.action.status == "PENDING"
        }?.action
        if (critical == null) {
            lastAutoReadCriticalId = null
            return
        }
        if (critical.id == lastAutoReadCriticalId) return
        lastAutoReadCriticalId = critical.id
        val text = sequenceOf(critical.headline.trim(), critical.detail.trim())
            .filter { it.isNotEmpty() }
            .joinToString(". ")
        if (text.isBlank()) return
        viewLifecycleOwner.lifecycleScope.launch {
            delay(1_000)
            if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
            try {
                biasharaTtsEngine.speak(text, preferredTtsLanguageCode())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                Log.w(TAG, "Auto-read critical alert failed", t)
            }
        }
    }

    private fun preferredTtsLanguageCode(): String? {
        val ctx = context ?: return null
        LanguagePreferences.getPersistedLocaleTag(ctx)?.let { tag ->
            val lang = tag.substringBefore('-', missingDelimiterValue = tag).lowercase(Locale.US)
            if (lang.isNotBlank()) return lang
        }
        return ctx.resources.configuration.locales[0]?.language?.lowercase(Locale.US)
    }

    private fun flashApproveSuccessCard(actionId: Long) {
        _binding?.recyclerActions?.post {
            val b = _binding ?: return@post
            if (!isAdded) return@post
            val i = adapter.currentList.indexOfFirst { it.action.id == actionId }
            if (i < 0) return@post
            val v = b.recyclerActions
                .findViewHolderForAdapterPosition(i)
                ?.itemView as? MaterialCardView
            val ctx = context ?: return@post
            v?.setCardBackgroundColor(
                ContextCompat.getColor(ctx, R.color.biashara_success_light),
            )
            v?.animate()?.alpha(0f)?.scaleY(0.9f)?.setDuration(320)?.start()
        }
    }

    private fun navigateReview(action: AgentAction) {
        navigateReviewIfPossible(action)
    }

    /** @return true when a navigation was started (so the caller may mark the action executed). */
    private fun navigateReviewIfPossible(action: AgentAction): Boolean {
        if (action.actionVerb == "EXPLORE_SERVICES") {
            return navigateSafely {
                navigate(
                    R.id.inventoryListFragment,
                    bundleOf(InventoryListFragment.ARG_INITIAL_TAB to InventoryListFragment.TAB_SERVICES),
                )
            }
        }
        when (action.relatedEntityType?.uppercase()) {
            "DAY", "WEEK" -> {
                return navigateSafely { navigate(R.id.action_agentFeedFragment_to_insightsFragment) }
            }
        }
        val id = action.relatedEntityId ?: run {
            _binding?.root?.let { Snackbar.make(it, R.string.agent_review_no_target, Snackbar.LENGTH_SHORT).show() }
            return false
        }
        return when (action.relatedEntityType?.uppercase()) {
            "PRODUCT" -> navigateSafely {
                navigate(
                    R.id.action_agentFeedFragment_to_addEditProductFragment,
                    bundleOf("product_id" to id),
                )
            }
            "TRANSACTION" -> navigateSafely {
                navigate(
                    R.id.action_agentFeedFragment_to_receiptFragment,
                    bundleOf(ReceiptViewModel.ARG_TRANSACTION_ID to id),
                )
            }
            "CUSTOMER" -> navigateSafely {
                navigate(R.id.action_agentFeedFragment_to_chatFragment)
            }
            else -> {
                _binding?.root?.let {
                    Snackbar.make(it, R.string.agent_review_no_target, Snackbar.LENGTH_SHORT).show()
                }
                false
            }
        }
    }

    private fun navigateSafely(block: NavController.() -> Unit): Boolean {
        return runCatching {
            findNavController().block()
            true
        }.getOrElse {
            Log.w(TAG, "Agent feed navigation failed", it)
            false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "AgentFeedFragment"
    }
}
