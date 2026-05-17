package com.biasharaai.agent

import com.biasharaai.data.local.db.AgentActionDao
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
    private val agentRunLogDao: AgentRunLogDao,
) {

    /**
     * True when a **PENDING** [com.biasharaai.data.local.db.AgentAction] already exists for this
     * [agentType] and [relatedEntityId] (prevents feed flooding).
     */
    suspend fun isDuplicateAction(agentType: String, relatedEntityId: Long?): Boolean {
        if (relatedEntityId == null) return false
        return withContext(Dispatchers.IO) {
            agentActionDao.countPendingForAgentAndEntity(agentType, relatedEntityId) > 0
        }
    }

    /** Pending duplicate by exact headline (e.g. aggregate fraud signals with no related entity). */
    suspend fun isDuplicatePendingHeadline(agentType: String, headline: String): Boolean =
        withContext(Dispatchers.IO) {
            agentActionDao.countPendingWithExactHeadline(agentType, headline) > 0
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
}
