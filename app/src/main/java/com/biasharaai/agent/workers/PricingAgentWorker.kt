package com.biasharaai.agent.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.biasharaai.agent.AgentActionBuilder
import com.biasharaai.agent.AgentDecisionEngine
import com.biasharaai.agent.AgentMutex
import com.biasharaai.agent.AgentTypes
import com.biasharaai.agent.PricingRuleEngine
import com.biasharaai.agent.PricingRuleKind
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.GemmaService
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.AgentSetting
import com.biasharaai.data.local.db.AgentSettingDao
import com.biasharaai.locale.LanguagePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/**
 * A5 — Daily pricing rules + optional Gemma one-liner (PARTIAL_AI+ under [AgentMutex]).
 */
class PricingAgentWorker(
    appContext: Context,
    params: WorkerParameters,
    private val pricingRuleEngine: PricingRuleEngine,
    private val agentActionDao: AgentActionDao,
    private val agentDecisionEngine: AgentDecisionEngine,
    private val agentSettingDao: AgentSettingDao,
    private val gemmaService: GemmaService,
    private val capabilityTier: CapabilityTier,
    private val agentMutex: AgentMutex,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = agentSettingDao.getSettingsSync() ?: AgentSetting()
        if (!settings.masterSwitch || !settings.pricingAgentEnabled) {
            return@withContext Result.success()
        }

        val startWall = System.currentTimeMillis()
        val hits = pricingRuleEngine.detectHits(startWall)
        var inserted = 0

        val localeTag = LanguagePreferences.getPersistedLocaleTag(applicationContext) ?: "en"
        val languageKey = localeTag.substringBefore(',').substringBefore('-').lowercase(Locale.ROOT)
        val language = LANGUAGE_FOR_PROMPT[languageKey] ?: "English"

        for (hit in hits) {
            val pid = hit.product.id
            if (agentDecisionEngine.isDuplicateAction(AgentTypes.PRICING_AGENT, pid)) {
                continue
            }

            val ruleLabel = when (hit.kind) {
                PricingRuleKind.VELOCITY_SPIKE -> "Velocity spike"
                PricingRuleKind.DEAD_STOCK -> "Dead stock"
                PricingRuleKind.MARGIN_ALERT -> "Tight margin"
            }

            val rationale = if (canUseGemma() && gemmaService.isAvailable) {
                try {
                    val prompt = """
                        You are a pricing advisor for a small shop in Africa.
                        Respond in $language. Exactly one sentence. No bullet points.
                        Product: ${hit.product.name}. ${hit.factLine}
                        Give one practical pricing rationale.
                    """.trimIndent()
                    val raw = agentMutex.mutex.withLock {
                        gemmaService.generateResponse(prompt).trim()
                    }
                    val line = raw.lineSequence().firstOrNull { it.isNotBlank() } ?: raw
                    if (line.isBlank()) {
                        ruleLabel + ": " + hit.factLine
                    } else {
                        line
                    }
                } catch (_: Exception) {
                    ruleLabel + ": " + hit.factLine
                }
            } else {
                ruleLabel + ": " + hit.factLine
            }

            val detail = hit.factLine + "\n\n" + rationale
            val action = AgentActionBuilder.pricingSuggestion(
                productId = pid,
                productName = hit.product.name,
                body = detail,
                nowMillis = startWall,
            )
            agentActionDao.insertAction(action)
            inserted++
        }

        agentDecisionEngine.buildRunLog(AgentTypes.PRICING_AGENT, startWall, inserted, "SUCCESS")
        Result.success()
    }

    private fun canUseGemma(): Boolean =
        capabilityTier == CapabilityTier.PARTIAL_AI || capabilityTier == CapabilityTier.FULL_AI

    companion object {
        private val LANGUAGE_FOR_PROMPT = mapOf(
            "en" to "English",
            "sw" to "Swahili",
            "ha" to "Hausa",
            "yo" to "Yoruba",
            "am" to "Amharic",
        )
    }
}
