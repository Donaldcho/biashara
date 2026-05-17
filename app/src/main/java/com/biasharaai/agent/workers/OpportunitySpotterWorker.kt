package com.biasharaai.agent.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.biasharaai.R
import com.biasharaai.agent.AgentActionBuilder
import com.biasharaai.agent.AgentDecisionEngine
import com.biasharaai.agent.AgentLoopRunner
import com.biasharaai.agent.AgentSystemPrompts
import com.biasharaai.agent.AgentTypes
import com.biasharaai.agent.CoPurchaseAnalyser
import com.biasharaai.agent.WeeklyReviewBuilder
import com.biasharaai.ai.ActiveModelStore
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.AgentSetting
import com.biasharaai.data.local.db.AgentSettingDao
import com.biasharaai.locale.LanguagePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * A7 — **FULL_AI + model only.** Runs **30 minutes** after the weekly review slot (see [AgentOrchestrator]);
 * uses [CoPurchaseAnalyser] + Gemma for bundle / shelf ideas.
 */
class OpportunitySpotterWorker(
    appContext: Context,
    params: WorkerParameters,
    private val coPurchaseAnalyser: CoPurchaseAnalyser,
    private val weeklyReviewBuilder: WeeklyReviewBuilder,
    private val activeModelStore: ActiveModelStore,
    private val agentLoopRunner: AgentLoopRunner,
    private val capabilityTier: CapabilityTier,
    private val agentActionDao: AgentActionDao,
    private val agentDecisionEngine: AgentDecisionEngine,
    private val agentSettingDao: AgentSettingDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val startWall = System.currentTimeMillis()
        val settings = agentSettingDao.getSettingsSync() ?: AgentSetting()
        if (!settings.masterSwitch || !settings.opportunitySpotterEnabled) {
            return@withContext Result.success()
        }
        if (capabilityTier != CapabilityTier.FULL_AI || !activeModelStore.isAvailable) {
            agentDecisionEngine.buildRunLog(AgentTypes.OPPORTUNITY_SPOTTER, startWall, 0, "SKIPPED_FULL_AI_ONLY")
            return@withContext Result.success()
        }

        val zone = ZoneId.systemDefault()
        val stats = weeklyReviewBuilder.buildLastCompletedIsoWeek(zone, Locale.getDefault())
        val weekStart = stats.weekStartMillis

        if (agentDecisionEngine.isDuplicateAction(AgentTypes.OPPORTUNITY_SPOTTER, weekStart)) {
            agentDecisionEngine.buildRunLog(AgentTypes.OPPORTUNITY_SPOTTER, startWall, 0, "SKIPPED_DUPLICATE")
            return@withContext Result.success()
        }

        val since = startWall - TimeUnit.DAYS.toMillis(90)
        val pairs = coPurchaseAnalyser.topPairsSince(since)
        if (pairs.isEmpty()) {
            agentDecisionEngine.buildRunLog(AgentTypes.OPPORTUNITY_SPOTTER, startWall, 0, "SUCCESS_EMPTY_PAIRS")
            return@withContext Result.success()
        }

        val language = languageNameForPrompt()
        val pairLines = pairs.joinToString("\n") { p ->
            "${p.product1} + ${p.product2}: sold together on ${p.coCount} receipts (lookback window)."
        }
        val legacyPrompt = """
You are advising ${stats.businessName}, a small retail shop.
Respond in $language. Under 120 words. Practical and respectful.

These product pairs often appear on the same POS receipt (last ~90 days of sales):
$pairLines

Suggest bundle promotions, shelf adjacency, or cross-sell phrasing staff can use.
Avoid inventing discounts you cannot verify — stay high-level.
        """.trimIndent()
        val userMessage = """
Suggest cross-sell ideas for ${stats.businessName}.
Co-purchase pairs (90d): $pairLines
Call find_copurchase_pairs to verify if needed.
        """.trimIndent()
        val system = AgentSystemPrompts.withLanguage(AgentSystemPrompts.OPPORTUNITY_SPOTTER, language)

        val body = try {
            agentLoopRunner.runOrSendPrompt(userMessage, system, legacyPrompt)
        } catch (_: Exception) {
            agentDecisionEngine.buildRunLog(AgentTypes.OPPORTUNITY_SPOTTER, startWall, 0, "GEMMA_FAILED")
            return@withContext Result.success()
        }

        val headline = applicationContext.getString(R.string.agent_opportunity_headline)
        val detail = buildString {
            append("Co-purchase signals:\n")
            pairs.forEach { p ->
                append("• ").append(p.product1).append(" ↔ ").append(p.product2)
                append(" (").append(p.coCount).append("×)\n")
            }
            append("\n")
            append(body.ifBlank { applicationContext.getString(R.string.agent_opportunity_fallback) })
        }

        val action = AgentActionBuilder.opportunitySpotter(
            headline = headline,
            detail = detail,
            weekStartMillis = weekStart,
            nowMillis = startWall,
        )
        agentActionDao.insertAction(action)
        agentDecisionEngine.buildRunLog(AgentTypes.OPPORTUNITY_SPOTTER, startWall, 1, "SUCCESS")
        Result.success()
    }

    private fun languageNameForPrompt(): String {
        val tag = LanguagePreferences.getPersistedLocaleTag(applicationContext)
            ?.substringBefore("-")
            ?.lowercase(Locale.getDefault())
            ?: Locale.getDefault().language.lowercase(Locale.getDefault())
        return when (tag) {
            "sw" -> "Swahili"
            "ha" -> "Hausa"
            "yo" -> "Yoruba"
            "am" -> "Amharic"
            else -> "English"
        }
    }
}
