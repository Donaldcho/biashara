package com.biasharaai.agent

import com.biasharaai.data.local.db.AgentAction
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.AgentAdviceFeedback
import com.biasharaai.data.local.db.AgentAdviceFeedbackDao
import com.biasharaai.data.local.db.AgentRunLog
import com.biasharaai.data.local.db.AgentRunLogDao
import com.biasharaai.data.local.db.AgentSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentDecisionEngine @Inject constructor(
    private val agentActionDao: AgentActionDao,
    private val agentAdviceFeedbackDao: AgentAdviceFeedbackDao,
    private val agentRunLogDao: AgentRunLogDao,
) {

    /**
     * True when a new alert should **not** be inserted: pending duplicate, owner already reviewed
     * the same entity/headline, or owner feedback marked similar advice as handled.
     */
    suspend fun shouldSkipInserting(action: AgentAction): Boolean = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() - REVIEW_SUPPRESSION_WINDOW_MS
        val entityId = action.relatedEntityId
        if (entityId != null) {
            if (agentActionDao.countPendingForAgentAndEntity(action.agentType, entityId) > 0) return@withContext true
            if (agentActionDao.countHandledForAgentAndEntitySince(action.agentType, entityId, since) > 0) {
                return@withContext true
            }
        } else {
            if (agentActionDao.countPendingWithExactHeadline(action.agentType, action.headline) > 0) {
                return@withContext true
            }
            if (agentActionDao.countHandledWithHeadlineSince(action.agentType, action.headline, since) > 0) {
                return@withContext true
            }
        }
        val hash = AgentActionHasher.contentHash(action)
        agentAdviceFeedbackDao.countReviewedHashSince(hash, since) > 0
    }

    /**
     * True when a **PENDING** [AgentAction] already exists for this [agentType] and [relatedEntityId].
     * Prefer [shouldSkipInserting] for workers — this also respects owner-reviewed alerts.
     */
    suspend fun isDuplicateAction(agentType: String, relatedEntityId: Long?): Boolean {
        if (relatedEntityId == null) return false
        return shouldSkipInserting(
            AgentAction(
                agentType = agentType,
                urgency = "INFO",
                headline = "",
                createdAt = System.currentTimeMillis(),
                relatedEntityId = relatedEntityId,
            ),
        )
    }

    /** Pending duplicate or recently reviewed by exact headline. */
    suspend fun isDuplicatePendingHeadline(agentType: String, headline: String): Boolean =
        shouldSkipInserting(
            AgentAction(
                agentType = agentType,
                urgency = "INFO",
                headline = headline,
                createdAt = System.currentTimeMillis(),
            ),
        )

    /** Persist owner review so workers stop re-alerting on the same sale/product/headline. */
    suspend fun recordOwnerReview(action: AgentAction, vote: Int) {
        withContext(Dispatchers.IO) {
            agentAdviceFeedbackDao.upsert(
                AgentAdviceFeedback(
                    agentActionId = action.id,
                    agentType = action.agentType,
                    contentHash = AgentActionHasher.contentHash(action),
                    headline = action.headline,
                    detail = action.detail,
                    vote = vote,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * Whether outbound **notifications** should be suppressed (quiet hours), using wall-clock
     * hour-of-day in the **default** device timezone.
     *
     * [AgentSetting.quietHoursStart] / [AgentSetting.quietHoursEnd] are **0–23** inclusive.
     * When start &gt; end, the quiet window crosses midnight (e.g. 22 → 7).
     */
    fun shouldSuppressNotification(settings: AgentSetting, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!settings.masterSwitch) return true
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val start = settings.quietHoursStart
        val end = settings.quietHoursEnd
        if (start == end) return false
        return if (start < end) {
            hour in start until end
        } else {
            hour >= start || hour < end
        }
    }

    /**
     * Persists a completed agent run for telemetry (see [AgentRunLog]).
     * Prompt A2 name: **buildRunLog** — inserts the row.
     */
    suspend fun buildRunLog(
        agentType: String,
        startTimeMs: Long,
        actionsGenerated: Int,
        outcome: String,
    ) {
        val end = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            agentRunLogDao.insertLog(
                AgentRunLog(
                    agentType = agentType,
                    ranAt = end,
                    durationMs = (end - startTimeMs).coerceAtLeast(0L),
                    actionsGenerated = actionsGenerated,
                    outcome = outcome,
                ),
            )
        }
    }

    companion object {
        /** How long owner dismiss/approve blocks repeat alerts for the same entity or headline. */
        const val REVIEW_SUPPRESSION_WINDOW_MS = 21L * 24 * 60 * 60 * 1000
    }
}
