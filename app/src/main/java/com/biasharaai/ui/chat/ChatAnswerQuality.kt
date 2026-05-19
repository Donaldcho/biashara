package com.biasharaai.ui.chat

import java.util.Locale

data class ChatAnswerMetadata(
    val sourceTags: List<String>,
    val confidenceLabel: String,
    val actionHint: String?,
)

enum class ChatAnswerSource {
    STRUCTURED_LOCAL_DATA,
    LOCAL_RULES,
    ON_DEVICE_AI,
    IMAGE_PLUS_AI,
    APP_KNOWLEDGE,
    ONLINE_SOURCE,
}

object ChatAnswerQuality {

    fun promptGuidance(languageName: String): String = buildString {
        append("Answer in $languageName. ")
        append("Be concise, factual, and action-oriented: summary first, key numbers second, next step last. ")
        append("If the local data does not store a field, say exactly what is missing instead of guessing. ")
        append("Use Cameroon-first defaults when no shop setting says otherwise: FCFA/XAF for money, MTN MoMo before M-Pesa, and Cameroon business examples. ")
        append("Never claim that a price, debt, sale, or stock record was changed unless a tool or app action actually changed it. ")
    }

    fun metadataFor(
        question: String,
        source: ChatAnswerSource,
        hasImage: Boolean = false,
    ): ChatAnswerMetadata {
        val tags = linkedSetOf<String>()
        val q = question.lowercase(Locale.ROOT)

        when (source) {
            ChatAnswerSource.STRUCTURED_LOCAL_DATA -> {
                tags += "Local data"
                tags += "Deterministic"
            }
            ChatAnswerSource.LOCAL_RULES -> {
                tags += "Local rules"
                tags += "Offline"
            }
            ChatAnswerSource.ON_DEVICE_AI -> {
                tags += "On-device AI"
                tags += "Local facts"
            }
            ChatAnswerSource.IMAGE_PLUS_AI -> {
                tags += "Image analysis"
                tags += "On-device AI"
            }
            ChatAnswerSource.APP_KNOWLEDGE -> {
                tags += "App guide"
                tags += "Offline"
            }
            ChatAnswerSource.ONLINE_SOURCE -> {
                tags += "Online source"
                tags += "Profile update"
            }
        }

        if (hasImage) tags += "Photo"
        if (q.hasAny("sale", "sales", "revenue", "income", "profit", "sold", "mauzo", "mapato")) tags += "Sales"
        if (q.hasAny("stock", "inventory", "product", "price", "cost", "reorder", "bidhaa")) tags += "Inventory"
        if (q.hasAny("debt", "credit", "owe", "owes", "customer", "wateja")) tags += "Customers"
        if (q.hasAny("cash", "ledger", "expense", "money out", "momo", "mtn", "orange money", "mpesa")) tags += "Ledger"

        val confidence = when (source) {
            ChatAnswerSource.STRUCTURED_LOCAL_DATA -> "High confidence"
            ChatAnswerSource.LOCAL_RULES -> "Medium confidence"
            ChatAnswerSource.ON_DEVICE_AI -> "Check figures before acting"
            ChatAnswerSource.IMAGE_PLUS_AI -> "Review extracted image details"
            ChatAnswerSource.APP_KNOWLEDGE -> "App guide"
            ChatAnswerSource.ONLINE_SOURCE -> "Review saved fields"
        }

        return ChatAnswerMetadata(
            sourceTags = tags.take(5),
            confidenceLabel = confidence,
            actionHint = actionHintFor(q, source),
        )
    }

    private fun actionHintFor(q: String, source: ChatAnswerSource): String? = when {
        q.hasAny("low stock", "reorder", "restock", "stock out") ->
            "Next: open Inventory to update stock or prepare a supplier order."
        q.hasAny("debt", "credit", "owe", "owes") ->
            "Next: open Credit to review balances or draft a reminder."
        q.hasAny("momo", "mtn", "orange money", "mpesa", "mobile money") ->
            "Next: verify the mobile money reference against the receipt or ledger entry."
        q.hasAny("cash", "ledger", "expense", "money out") ->
            "Next: open Ledger to inspect entries or add proof."
        q.hasAny("sale", "sales", "receipt", "transaction") ->
            "Next: open Sales history to review the matching receipts."
        source == ChatAnswerSource.IMAGE_PLUS_AI ->
            "Next: review the extracted photo details before saving business records."
        source == ChatAnswerSource.APP_KNOWLEDGE ->
            "Next: follow the steps in the matching app screen."
        source == ChatAnswerSource.ONLINE_SOURCE ->
            "Next: open Settings > Business profile to review the imported fields."
        else -> null
    }

    private fun String.hasAny(vararg needles: String): Boolean =
        needles.any { contains(it, ignoreCase = true) }
}
