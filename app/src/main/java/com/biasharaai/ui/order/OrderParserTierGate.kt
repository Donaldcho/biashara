package com.biasharaai.ui.order

import androidx.fragment.app.Fragment
import com.biasharaai.R
import com.biasharaai.ai.CapabilityTier
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Prompt U10: text-order parser expects on-device Gemma; **RULES_BASED** devices cannot run it.
 *
 * @return `true` if the flow should stop (dialog shown).
 */
fun Fragment.showOrderParserTierBlockedDialogIfNeeded(tier: CapabilityTier): Boolean {
    if (tier != CapabilityTier.RULES_BASED) return false
    MaterialAlertDialogBuilder(requireContext())
        .setMessage(R.string.order_parser_tier_rules_only_message)
        .setPositiveButton(android.R.string.ok, null)
        .show()
    return true
}
