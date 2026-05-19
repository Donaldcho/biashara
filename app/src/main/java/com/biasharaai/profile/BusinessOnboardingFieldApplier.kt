package com.biasharaai.profile

import com.biasharaai.data.local.db.BusinessProfile

object BusinessOnboardingFieldApplier {

    fun apply(profile: BusinessProfile, stepIndex: Int, answer: String): BusinessProfile {
        if (answer.isBlank()) return profile
        return when (stepIndex) {
            0 -> profile.copy(businessName = answer)
            1 -> applyOffer(profile, answer)
            2 -> profile.copy(targetCustomer = answer)
            3 -> applyHours(profile, answer)
            4 -> profile.copy(location = answer)
            5 -> profile.copy(ownerName = answer)
            6 -> profile.copy(mainSuppliers = answer)
            7 -> applyGoal(profile, answer)
            else -> profile
        }
    }

    private fun applyOffer(profile: BusinessProfile, answer: String): BusinessProfile {
        val lower = answer.lowercase()
        val businessType = when {
            SERVICE_KEYWORDS.any { lower.contains(it) } && PRODUCT_KEYWORDS.any { lower.contains(it) } ->
                "mixed"
            SERVICE_KEYWORDS.any { lower.contains(it) } -> "salon"
            PRODUCT_KEYWORDS.any { lower.contains(it) } -> "duka"
            else -> inferBusinessType(answer)
        }
        val isService = businessType == "salon" || SERVICE_KEYWORDS.any { lower.contains(it) }
        return profile.copy(
            businessType = businessType,
            description = answer,
            primaryServices = if (isService) answer else profile.primaryServices,
            primaryProducts = if (!isService) answer else profile.primaryProducts,
            specialisation = extractSpecialisation(lower),
        )
    }

    private fun applyHours(profile: BusinessProfile, answer: String): BusinessProfile {
        val (days, hours) = splitDaysAndHours(answer)
        return profile.copy(openDays = days, openHours = hours ?: profile.openHours)
    }

    private fun applyGoal(profile: BusinessProfile, answer: String): BusinessProfile {
        val target = parseRevenueTarget(answer)
        return profile.copy(
            businessGoal = answer,
            monthlyRevenueTarget = target ?: profile.monthlyRevenueTarget,
            agentTone = profile.agentTone ?: "encouraging",
        )
    }

    private fun inferBusinessType(answer: String): String {
        val lower = answer.lowercase()
        return when {
            lower.contains("salon") || lower.contains("barber") || lower.contains("spa") -> "salon"
            lower.contains("mechanic") || lower.contains("garage") -> "mechanic"
            lower.contains("restaurant") || lower.contains("food") -> "restaurant"
            else -> "mixed"
        }
    }

    private fun extractSpecialisation(lower: String): String? {
        val markers = listOf("only", "speciali", "natural", "no chemical", "hakuna")
        return if (markers.any { lower.contains(it) }) {
            lower.lines().firstOrNull()?.take(200)
        } else {
            null
        }
    }

    private fun splitDaysAndHours(answer: String): Pair<String, String?> {
        val hourMarkers = listOf("am", "pm", "saa", "asubuhi", "jioni", "hour", "hrs", ":")
        val lower = answer.lowercase()
        val splitAt = hourMarkers.firstNotNullOfOrNull { marker ->
            val idx = lower.indexOf(marker)
            if (idx > 0) idx else null
        }
        return if (splitAt != null && splitAt > 3) {
            answer.substring(0, splitAt).trim().trimEnd(',', ';') to answer.substring(splitAt).trim()
        } else {
            answer to null
        }
    }

    private fun parseRevenueTarget(answer: String): Double? {
        val regex = Regex("""(\d[\d,.\s]*\d|\d+)""")
        val matches = regex.findAll(answer).mapNotNull {
            it.value.replace(",", "").replace(" ", "").toDoubleOrNull()
        }.toList()
        return matches.maxOrNull()?.takeIf { it > 0 }
    }

    private val SERVICE_KEYWORDS = listOf(
        "hair", "braid", "salon", "nywele", "service", "wash", "cut", "massage",
        "repair", "fix", "locs", "twist", "relaxed",
    )

    private val PRODUCT_KEYWORDS = listOf(
        "sell", "stock", "duka", "shop", "maize", "flour", "oil", "sugar", "product",
        "bidhaa", "uza",
    )
}
