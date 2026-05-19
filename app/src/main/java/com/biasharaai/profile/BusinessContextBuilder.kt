package com.biasharaai.profile

import com.biasharaai.data.local.db.BusinessProfile
import com.biasharaai.money.MoneyFormatter

object BusinessContextBuilder {

    fun build(profile: BusinessProfile, moneyFormatter: MoneyFormatter): String {
        val offer = profile.whatTheyOffer() ?: "not specified"
        val target = profile.targetCustomer?.takeIf { it.isNotBlank() } ?: "general market"
        val location = profile.location?.takeIf { it.isNotBlank() } ?: "not specified"
        val open = listOfNotNull(
            profile.openDays?.takeIf { it.isNotBlank() },
            profile.openHours?.takeIf { it.isNotBlank() },
        ).joinToString(", ").ifBlank { "not specified" }
        val staff = profile.staffCount?.toString() ?: "owner only"
        val suppliers = profile.mainSuppliers?.takeIf { it.isNotBlank() } ?: "not recorded"
        val monthlyTarget = profile.monthlyRevenueTarget?.let { moneyFormatter.format(it) } ?: "not set"
        val goal = profile.businessGoal?.takeIf { it.isNotBlank() } ?: "not specified"
        val owner = profile.ownerName.takeIf { it.isNotBlank() } ?: "the owner"
        val tone = profile.agentTone?.takeIf { it.isNotBlank() } ?: "encouraging"
        val specialisation = profile.specialisation?.takeIf { it.isNotBlank() } ?: "general"

        val servicesNote = profile.primaryServices?.takeIf { it.isNotBlank() }?.let {
            "\n- Services offered (owner): $it"
        } ?: ""

        return """
BUSINESS CONTEXT (read before reasoning):
- Name: ${profile.businessName}
- Owner: $owner
- Type: ${profile.businessType}
- What they offer: $offer$servicesNote
- Specialisation: $specialisation
- Target customers: $target
- Location: $location
- Open: $open
- Staff: $staff
- Suppliers: $suppliers
- Monthly target: $monthlyTarget
- Owner's goal: $goal
- Preferred tone: $tone
Use this context to personalise all advice. Refer to the business by name.
Speak to the owner as $owner.
        """.trimIndent()
    }
}
