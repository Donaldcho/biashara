package com.biasharaai.voice

import androidx.core.os.bundleOf
import androidx.navigation.NavController
import com.biasharaai.R
import com.biasharaai.ui.insights.CashFlowInsightsFragment

/**
 * Maps [VoiceNavigationTarget] from voice commands to [NavController] destinations.
 */
fun NavController.navigateFromVoiceTarget(
    target: VoiceNavigationTarget,
    onUnknownHint: (String) -> Unit,
    onAlreadyAtChat: () -> Unit = {},
) {
    when (target) {
        VoiceNavigationTarget.AgentFeed -> navigate(R.id.agentFeedFragment)
        VoiceNavigationTarget.InventoryList -> navigate(R.id.inventoryListFragment)
        VoiceNavigationTarget.Pos -> navigate(R.id.posFragment)
        VoiceNavigationTarget.Chat -> onAlreadyAtChat()
        VoiceNavigationTarget.Insights -> navigate(R.id.insightsFragment)
        VoiceNavigationTarget.Ledger -> navigate(
            R.id.insightsFragment,
            bundleOf(CashFlowInsightsFragment.ARG_INITIAL_TAB to CashFlowInsightsFragment.TAB_LEDGER),
        )
        is VoiceNavigationTarget.Unknown -> onUnknownHint(target.rawHint)
    }
}
