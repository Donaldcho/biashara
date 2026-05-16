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

/**
 * A7 — **FULL_AI + model only.** Weekly narrative via Gemma (long-form prompt); [AgentMutex] serialises inference.
 * Inserts [AUTO_EXECUTE] / [SHOW_REVIEW] feed rows (see [AgentActionBuilder.weeklyReviewShowReview]).
 */
class WeeklyReviewWorker(
    appContext: Context,
    params: WorkerParameters,
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
        if (!settings.masterSwitch || !settings.weeklyReviewEnabled) {
            return@withContext Result.success()
        }
        if (capabilityTier != CapabilityTier.FULL_AI || !activeModelStore.isAvailable) {
            agentDecisionEngine.buildRunLog(AgentTypes.WEEKLY_REVIEW, startWall, 0, "SKIPPED_FULL_AI_ONLY")
            return@withContext Result.success()
        }

        val zone = ZoneId.systemDefault()
        val stats = weeklyReviewBuilder.buildLastCompletedIsoWeek(zone, Locale.getDefault())

        if (agentDecisionEngine.isDuplicateAction(AgentTypes.WEEKLY_REVIEW, stats.weekStartMillis)) {
            agentDecisionEngine.buildRunLog(AgentTypes.WEEKLY_REVIEW, startWall, 0, "SKIPPED_DUPLICATE")
            return@withContext Result.success()
        }

        val language = languageNameForPrompt()
        val currency = stats.currencySymbol
        val weekRevenue = money(stats.weekRevenue)
        val lastWeekRevenue = money(stats.lastWeekRevenue)
        val topRevenue = money(stats.topRevenue)
        val totalCredit = money(stats.totalCredit)

        val legacyPrompt = """
You are the business advisor for ${stats.businessName}, a small shop.
Respond in $language. Write a friendly weekly business review.
Be specific, encouraging, and practical. Under 200 words.

This week's data:
Total revenue: $currency$weekRevenue (vs last week: $currency$lastWeekRevenue)
Total transactions: ${stats.txCount}
Top selling product: ${stats.topProduct} (${stats.topQty} units, $currency$topRevenue)
Slowest product: ${stats.slowProduct} (${stats.slowQty} units)
New customers: ${stats.newCustomers}
Returning customers: ${stats.returningCustomers}
Outstanding credit: $currency$totalCredit across ${stats.debtCustomerCount} customers
Best trading day: ${stats.bestDay}
Best trading hour: ${stats.bestHour}:00

Structure your response as:
1. One sentence celebrating a win from this week
2. One sentence noting something to watch
3. One specific, actionable recommendation for next week
        """.trimIndent()
        val userMessage = """
Weekly review for ${stats.businessName}. Use query_sales, calculate_profit, or query_customers if needed.
Revenue $currency$weekRevenue vs last week $currency$lastWeekRevenue; ${stats.txCount} transactions.
Top: ${stats.topProduct} (${stats.topQty} units). Slow: ${stats.slowProduct}. Credit outstanding $currency$totalCredit.
        """.trimIndent()
        val system = AgentSystemPrompts.withLanguage(AgentSystemPrompts.WEEKLY_REVIEW, language)

        val narrative = try {
            agentLoopRunner.runOrSendPrompt(userMessage, system, legacyPrompt)
        } catch (_: Exception) {
            agentDecisionEngine.buildRunLog(AgentTypes.WEEKLY_REVIEW, startWall, 0, "GEMMA_FAILED")
            return@withContext Result.success()
        }

        val chipsJson = AgentActionBuilder.weeklyReviewChipsJson(stats.chipsForPayload)
        val action = AgentActionBuilder.weeklyReviewShowReview(
            headline = applicationContext.getString(R.string.agent_weekly_review_headline),
            narrativeDetail = narrative.ifBlank { applicationContext.getString(R.string.agent_weekly_review_fallback) },
            chipsPayloadJson = chipsJson,
            weekStartMillis = stats.weekStartMillis,
            nowMillis = startWall,
        )
        agentActionDao.insertAction(action)
        agentDecisionEngine.buildRunLog(AgentTypes.WEEKLY_REVIEW, startWall, 1, "SUCCESS")
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

    private fun money(v: Double): String = String.format(Locale.US, "%.2f", v)
}
