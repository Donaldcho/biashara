package com.biasharaai.agent

import com.biasharaai.data.local.db.AgentAction
import com.google.gson.Gson

/**
 * Factory helpers for [AgentAction] rows — keeps defaults and required fields consistent per agent.
 * (A3+ agents call these when enqueueing user-visible actions.)
 */
object AgentActionBuilder {

    private const val ENTITY_PRODUCT = "PRODUCT"
    private const val ENTITY_CUSTOMER = "CUSTOMER"
    private val gson = Gson()

    fun stockAlert(
        productId: Long,
        productName: String,
        urgency: String,
        daysRemaining: Double,
        currencySymbol: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): AgentAction {
        val daysInt = kotlin.math.max(0, kotlin.math.floor(daysRemaining).toInt())
        val detail =
            "~$daysInt days of stock left (7-day sales pace). Currency: $currencySymbol"
        return AgentAction(
            agentType = AgentTypes.STOCK_GUARDIAN,
            urgency = urgency,
            executionType = "REQUIRES_APPROVAL",
            headline = "$productName: $daysInt days of stock left",
            detail = detail,
            actionPayload = null,
            actionVerb = "REVIEW_STOCK",
            status = "PENDING",
            createdAt = nowMillis,
            expiresAt = null,
            relatedEntityId = productId,
            relatedEntityType = ENTITY_PRODUCT,
        )
    }

    fun fraudSignal(
        headline: String,
        detail: String,
        relatedTransactionId: Long?,
        nowMillis: Long = System.currentTimeMillis(),
    ): AgentAction = AgentAction(
        agentType = AgentTypes.FRAUD_SENTINEL,
        urgency = "CRITICAL",
        executionType = "REQUIRES_APPROVAL",
        headline = headline,
        detail = detail,
        actionPayload = null,
        actionVerb = "REVIEW_TRANSACTION",
        status = "PENDING",
        createdAt = nowMillis,
        expiresAt = null,
        relatedEntityId = relatedTransactionId,
        relatedEntityType = if (relatedTransactionId != null) "TRANSACTION" else null,
    )

    fun cashFlowDailySummary(
        urgency: String,
        headline: String,
        detail: String,
        dayStartMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): AgentAction = AgentAction(
        agentType = AgentTypes.CASH_FLOW,
        urgency = urgency,
        executionType = "REQUIRES_APPROVAL",
        headline = headline,
        detail = detail,
        actionPayload = null,
        actionVerb = "REVIEW_CASH_FLOW",
        status = "PENDING",
        createdAt = nowMillis,
        expiresAt = null,
        relatedEntityId = dayStartMillis,
        relatedEntityType = "DAY",
    )

    fun pricingSuggestion(productId: Long, productName: String, body: String, nowMillis: Long = System.currentTimeMillis()): AgentAction =
        AgentAction(
            agentType = AgentTypes.PRICING_AGENT,
            urgency = "MEDIUM",
            executionType = "REQUIRES_APPROVAL",
            headline = "Pricing: $productName",
            detail = body,
            actionPayload = null,
            actionVerb = "REVIEW_PRICE",
            status = "PENDING",
            createdAt = nowMillis,
            relatedEntityId = productId,
            relatedEntityType = ENTITY_PRODUCT,
        )

    /**
     * Draft SMS for owner review (A6 / A9). Payload: `phone`, `draftMessage`, optional `debtId`.
     */
    fun customerRelationSendSms(
        urgency: String,
        headline: String,
        detail: String,
        phone: String,
        draftMessage: String,
        relatedCustomerId: Long,
        debtId: Long? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): AgentAction {
        val payloadMap = buildMap<String, Any?> {
            put("phone", phone)
            put("draftMessage", draftMessage)
            put("debtId", debtId)
        }
        return AgentAction(
            agentType = AgentTypes.CUSTOMER_RELATION,
            urgency = urgency,
            executionType = "REQUIRES_APPROVAL",
            headline = headline,
            detail = detail,
            actionPayload = gson.toJson(payloadMap),
            actionVerb = "SEND_SMS",
            status = "PENDING",
            createdAt = nowMillis,
            expiresAt = null,
            relatedEntityId = relatedCustomerId,
            relatedEntityType = ENTITY_CUSTOMER,
        )
    }

    private const val ENTITY_WEEK = "WEEK"

    fun weeklyReviewChipsJson(chips: List<Pair<String, String>>): String =
        gson.toJson(
            mapOf(
                "chips" to chips.map { mapOf("label" to it.first, "value" to it.second) },
            ),
        )

    /** A7 — [executionType] is [AUTO_EXECUTE]: owner feed shows narrative + stat chips (no approval queue). */
    fun weeklyReviewShowReview(
        headline: String,
        narrativeDetail: String,
        chipsPayloadJson: String,
        weekStartMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): AgentAction = AgentAction(
        agentType = AgentTypes.WEEKLY_REVIEW,
        urgency = "LOW",
        executionType = "AUTO_EXECUTE",
        headline = headline,
        detail = narrativeDetail,
        actionPayload = chipsPayloadJson,
        actionVerb = "SHOW_REVIEW",
        status = "PENDING",
        createdAt = nowMillis,
        expiresAt = null,
        relatedEntityId = weekStartMillis,
        relatedEntityType = ENTITY_WEEK,
    )

    fun opportunitySpotter(
        headline: String,
        detail: String,
        weekStartMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): AgentAction = AgentAction(
        agentType = AgentTypes.OPPORTUNITY_SPOTTER,
        urgency = "MEDIUM",
        executionType = "REQUIRES_APPROVAL",
        headline = headline,
        detail = detail,
        actionPayload = null,
        actionVerb = "REVIEW_OPPORTUNITIES",
        status = "PENDING",
        createdAt = nowMillis,
        expiresAt = null,
        relatedEntityId = weekStartMillis,
        relatedEntityType = ENTITY_WEEK,
    )
}
