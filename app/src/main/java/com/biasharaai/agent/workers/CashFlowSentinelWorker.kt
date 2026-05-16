package com.biasharaai.agent.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.biasharaai.agent.AgentActionBuilder
import com.biasharaai.agent.AgentDecisionEngine
import com.biasharaai.agent.AgentLoopRunner
import com.biasharaai.agent.AgentSystemPrompts
import com.biasharaai.agent.AgentTypes
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.AgentSetting
import com.biasharaai.data.local.db.AgentSettingDao
import com.biasharaai.data.local.db.AppSettings
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.TransactionDao
import com.biasharaai.locale.LanguagePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * A5 — Daily cash-flow digest (PARTIAL_AI+ uses Gemma under [AgentMutex]; rules-only narrative
 * otherwise). Scheduled ~once per day near [com.biasharaai.data.local.db.AgentSetting.dailySummaryHour].
 */
class CashFlowSentinelWorker(
    appContext: Context,
    params: WorkerParameters,
    private val transactionDao: TransactionDao,
    private val agentActionDao: AgentActionDao,
    private val agentDecisionEngine: AgentDecisionEngine,
    private val agentSettingDao: AgentSettingDao,
    private val appSettingsDao: AppSettingsDao,
    private val agentLoopRunner: AgentLoopRunner,
    private val capabilityTier: CapabilityTier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = agentSettingDao.getSettingsSync() ?: AgentSetting()
        if (!settings.masterSwitch || !settings.cashFlowEnabled) {
            return@withContext Result.success()
        }

        val startWall = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val historyStart = today.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()

        val revenue = transactionDao.sumIncomeAmountBetween(startOfToday, startOfTomorrow)
        val expenses = transactionDao.sumExpenseAmountBetween(startOfToday, startOfTomorrow)
        val credit = transactionDao.sumCreditIncomeAmountBetween(startOfToday, startOfTomorrow)
        val net = revenue - expenses

        val sumRev30 = transactionDao.sumIncomeAmountBetween(historyStart, startOfToday)
        val sumExp30 = transactionDao.sumExpenseAmountBetween(historyStart, startOfToday)
        val avgDailyRev = sumRev30 / 30.0
        val avgDailyExp = sumExp30 / 30.0

        val urgency = when {
            net < 0 -> "CRITICAL"
            avgDailyRev > 0 && revenue < 0.5 * avgDailyRev -> "HIGH"
            avgDailyRev > 0 && revenue > 1.2 * avgDailyRev -> "LOW"
            else -> "MEDIUM"
        }

        val app = appSettingsDao.getSettingsSync() ?: AppSettings()
        val currency = app.currencySymbol
        val businessName = app.businessName.ifBlank { "My shop" }
        val localeTag = LanguagePreferences.getPersistedLocaleTag(applicationContext) ?: "en"
        val languageKey = localeTag.substringBefore(',').substringBefore('-').lowercase(Locale.ROOT)
        val language = LANGUAGE_FOR_PROMPT[languageKey] ?: "English"

        val baseDetail = buildString {
            append("Revenue: ").append(currency).append(money(revenue))
            append(". Expenses: ").append(currency).append(money(expenses))
            append(". Net: ").append(currency).append(money(net))
            append(". Credit extended: ").append(currency).append(money(credit))
            append(". 30-day avg daily revenue: ").append(currency).append(money(avgDailyRev))
            append(". 30-day avg daily expenses: ").append(currency).append(money(avgDailyExp))
            append(".")
        }

        val headline = when (urgency) {
            "CRITICAL" -> "Cash flow: spending exceeded revenue today"
            "HIGH" -> "Cash flow: today’s revenue is well below your usual pace"
            "LOW" -> "Cash flow: strong revenue vs your 30-day average"
            else -> "Cash flow: daily summary"
        }

        val legacyPrompt = """
            You are a business advisor for a small shop in Africa.
            Respond in $language. Under 80 words. Warm, practical tone.
            Today's trading summary for $businessName:
            Revenue: $currency${money(revenue)}. Expenses: $currency${money(expenses)}.
            Net: $currency${money(net)}. Credit extended: $currency${money(credit)}.
            30-day average daily revenue: $currency${money(avgDailyRev)}.
            Write one paragraph summarising today and give one practical tip.
        """.trimIndent()
        val userMessage = """
            Summarise today's cash flow for $businessName.
            Revenue $currency${money(revenue)}, expenses $currency${money(expenses)}, net $currency${money(net)}.
            Credit extended $currency${money(credit)}. 30-day avg daily revenue $currency${money(avgDailyRev)}.
            You may call query_sales or calculate_profit to verify before answering.
        """.trimIndent()
        val system = AgentSystemPrompts.withLanguage(AgentSystemPrompts.CASH_FLOW, language)

        val narrative = if (canUseGemma()) {
            try {
                val raw = agentLoopRunner.runOrSendPrompt(userMessage, system, legacyPrompt)
                raw.ifBlank {
                    rulesOnlyNarrative(revenue, expenses, net, credit, avgDailyRev, urgency, currency)
                }
            } catch (_: Exception) {
                rulesOnlyNarrative(revenue, expenses, net, credit, avgDailyRev, urgency, currency)
            }
        } else {
            rulesOnlyNarrative(revenue, expenses, net, credit, avgDailyRev, urgency, currency)
        }

        val fullDetail = baseDetail + "\n\n" + narrative

        if (agentDecisionEngine.isDuplicateAction(AgentTypes.CASH_FLOW, startOfToday)) {
            agentDecisionEngine.buildRunLog(AgentTypes.CASH_FLOW, startWall, 0, "SKIPPED_DUPLICATE")
            return@withContext Result.success()
        }

        val action = AgentActionBuilder.cashFlowDailySummary(
            urgency = urgency,
            headline = headline,
            detail = fullDetail,
            dayStartMillis = startOfToday,
            nowMillis = startWall,
        )
        agentActionDao.insertAction(action)
        agentDecisionEngine.buildRunLog(AgentTypes.CASH_FLOW, startWall, 1, "SUCCESS")
        Result.success()
    }

    private fun canUseGemma(): Boolean =
        capabilityTier == CapabilityTier.PARTIAL_AI || capabilityTier == CapabilityTier.FULL_AI

    private fun money(v: Double): String = String.format(Locale.US, "%.2f", v)

    private fun rulesOnlyNarrative(
        revenue: Double,
        expenses: Double,
        net: Double,
        credit: Double,
        avgDailyRev: Double,
        urgency: String,
        currency: String,
    ): String = buildString {
        append("Today’s net is ").append(currency).append(money(net)).append(". ")
        when (urgency) {
            "CRITICAL" -> append("Expenses exceeded revenue — trim discretionary spend and chase collections. ")
            "HIGH" -> append("Revenue is unusually soft versus your recent average — run a quick promotion or follow up on debtors. ")
            "LOW" -> append("Sales are ahead of your recent average — consider topping up fast movers. ")
            else -> append("Numbers look in line with recent norms — keep tracking receipts and expenses daily. ")
        }
        if (credit > 0) {
            append("Credit sales today: ").append(currency).append(money(credit)).append(". ")
        }
        append("30-day avg daily revenue reference: ").append(currency).append(money(avgDailyRev)).append(".")
    }

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
