package com.biasharaai.ui.negotiation

import androidx.fragment.app.Fragment
import com.biasharaai.R
import com.biasharaai.ai.CapabilityTier
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Prompt U7: supplier negotiation script is **FULL_AI** only (not PARTIAL_AI / RULES_BASED).
 *
 * @return `true` if navigation should be blocked (dialog shown).
 */
fun Fragment.showNegotiationTierBlockedDialogIfNeeded(tier: CapabilityTier): Boolean {
    if (tier == CapabilityTier.FULL_AI) return false
    MaterialAlertDialogBuilder(requireContext())
        .setMessage(R.string.negotiation_tier_required_message)
        .setPositiveButton(android.R.string.ok, null)
        .show()
    return true
}
