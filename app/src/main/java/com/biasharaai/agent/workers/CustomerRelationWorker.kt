package com.biasharaai.agent.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.biasharaai.R
import com.biasharaai.agent.AgentActionBuilder
import com.biasharaai.agent.AgentDecisionEngine
import com.biasharaai.agent.AgentMutex
import com.biasharaai.agent.AgentTypes
import com.biasharaai.agent.CustomerPatternAnalyser
import com.biasharaai.agent.OverdueCustomer
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.GemmaService
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.AgentSetting
import com.biasharaai.data.local.db.AgentSettingDao
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.Customer
import com.biasharaai.data.local.db.CustomerDao
import com.biasharaai.data.local.db.Debt
import com.biasharaai.data.local.db.DebtRepository
import com.biasharaai.locale.LanguagePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/**
 * A6 — Daily customer relations: **overdue credit** (higher priority) vs **visit-gap** “we miss you”
 * when there is no overdue debt. PARTIAL_AI+ drafts SMS via Gemma (same style as Phase 2 debt reminders);
 * RULES_BASED uses templates. Actions use [AgentAction.actionVerb] `SEND_SMS` for owner approval (A9 execution).
 */
class CustomerRelationWorker(
    appContext: Context,
    params: WorkerParameters,
    private val customerPatternAnalyser: CustomerPatternAnalyser,
    private val debtRepository: DebtRepository,
    private val customerDao: CustomerDao,
    private val agentActionDao: AgentActionDao,
    private val agentDecisionEngine: AgentDecisionEngine,
    private val agentSettingDao: AgentSettingDao,
    private val appSettingsDao: AppSettingsDao,
    private val gemmaService: GemmaService,
    private val capabilityTier: CapabilityTier,
    private val agentMutex: AgentMutex,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val startWall = System.currentTimeMillis()
        val settings = agentSettingDao.getSettingsSync() ?: AgentSetting()
        if (!settings.masterSwitch || !settings.customerRelationEnabled) {
            return@withContext Result.success()
        }

        val zone = ZoneId.systemDefault()
        val now = startWall
        val overdueByCustomer = debtRepository.overdueDebtsByCustomerId(now)
        val visitOverdueList = customerPatternAnalyser.findCustomersOverdueByVisitPattern(now, zone)
        val visitByCustomerId = visitOverdueList.associateBy { it.customer.id }
        val allCustomerIds = overdueByCustomer.keys + visitByCustomerId.keys
        if (allCustomerIds.isEmpty()) {
            agentDecisionEngine.buildRunLog(AgentTypes.CUSTOMER_RELATION, startWall, 0, "SUCCESS_EMPTY")
            return@withContext Result.success()
        }

        val app = appSettingsDao.getSettingsSync()
        val currency = app?.currencyCode ?: "KES"
        var actionsInserted = 0

        for (customerId in allCustomerIds) {
            val customer = customerDao.getCustomerById(customerId) ?: continue
            val overdueDebts = overdueByCustomer[customerId].orEmpty()
            val visitInfo = visitByCustomerId[customerId]

            if (agentDecisionEngine.isDuplicateAction(AgentTypes.CUSTOMER_RELATION, customerId)) {
                continue
            }

            val phone = customer.phone?.trim().orEmpty()
            if (phone.isEmpty()) {
                continue
            }

            if (overdueDebts.isNotEmpty()) {
                val maxDays = overdueDebts.maxOf { daysOverdue(it, zone, now) }
                val urgency = when {
                    maxDays >= 14 -> "CRITICAL"
                    maxDays >= 7 -> "HIGH"
                    else -> "MEDIUM"
                }
                val primaryDebt = overdueDebts.maxBy { daysOverdue(it, zone, now) }
                val total = overdueDebts.sumOf { it.amount }
                val draft = if (canUseGemma() && gemmaService.isAvailable) {
                    try {
                        val body = draftDebtSmsWithGemma(customer, primaryDebt, overdueDebts, total, currency, maxDays, zone)
                        body.ifBlank {
                            fallbackDebtSms(customer, primaryDebt, maxDays.toInt(), currency)
                        }
                    } catch (_: Exception) {
                        fallbackDebtSms(customer, primaryDebt, maxDays.toInt(), currency)
                    }
                } else {
                    fallbackDebtSms(customer, primaryDebt, maxDays.toInt(), currency)
                }
                val detail = buildDebtDetail(customer, overdueDebts, currency, maxDays)
                val action = AgentActionBuilder.customerRelationSendSms(
                    urgency = urgency,
                    headline = applicationContext.getString(R.string.agent_customer_relation_debt_headline, customer.name),
                    detail = detail,
                    phone = phone,
                    draftMessage = draft,
                    relatedCustomerId = customerId,
                    debtId = primaryDebt.id,
                    nowMillis = startWall,
                )
                agentActionDao.insertAction(action)
                actionsInserted++
                continue
            }

            if (visitInfo != null &&
                overdueDebts.isEmpty() &&
                visitInfo.daysSinceLastVisit > visitInfo.avgGapDays * 1.5
            ) {
                val draft = if (canUseGemma() && gemmaService.isAvailable) {
                    try {
                        val body = draftVisitSmsWithGemma(customer, visitInfo)
                        body.ifBlank {
                            applicationContext.getString(
                                R.string.agent_customer_visit_fallback_sms,
                                customer.name,
                                visitInfo.daysSinceLastVisit.toInt(),
                            )
                        }
                    } catch (_: Exception) {
                        applicationContext.getString(
                            R.string.agent_customer_visit_fallback_sms,
                            customer.name,
                            visitInfo.daysSinceLastVisit.toInt(),
                        )
                    }
                } else {
                    applicationContext.getString(
                        R.string.agent_customer_visit_fallback_sms,
                        customer.name,
                        visitInfo.daysSinceLastVisit.toInt(),
                    )
                }
                val detail = buildVisitDetail(customer, visitInfo)
                val action = AgentActionBuilder.customerRelationSendSms(
                    urgency = "LOW",
                    headline = applicationContext.getString(R.string.agent_customer_relation_visit_headline, customer.name),
                    detail = detail,
                    phone = phone,
                    draftMessage = draft,
                    relatedCustomerId = customerId,
                    debtId = null,
                    nowMillis = startWall,
                )
                agentActionDao.insertAction(action)
                actionsInserted++
            }
        }

        agentDecisionEngine.buildRunLog(AgentTypes.CUSTOMER_RELATION, startWall, actionsInserted, "SUCCESS")
        Result.success()
    }

    private fun canUseGemma(): Boolean =
        capabilityTier == CapabilityTier.PARTIAL_AI || capabilityTier == CapabilityTier.FULL_AI

    private suspend fun draftDebtSmsWithGemma(
        customer: Customer,
        primaryDebt: Debt,
        allOverdue: List<Debt>,
        totalAmount: Double,
        currency: String,
        maxDaysOverdue: Long,
        zone: ZoneId,
    ): String {
        val customerLanguage = languageNameForPrompt()
        val dueFmt = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
        val lines = allOverdue.joinToString("\n") { d ->
            val due = d.dueDate?.let { dueFmt.format(Date(it)) } ?: "—"
            "- ${money(d.amount)} $currency, due $due"
        }
        val primaryDueTxt = primaryDebt.dueDate?.let { dueFmt.format(Date(it)) }
        val prompt = """
Write a short, polite, friendly SMS reminder in $customerLanguage.
The message is from a small shop owner to a customer.
Customer name: ${customer.name}.
Total overdue balance: ${money(totalAmount)} $currency.
Most overdue note (${money(primaryDebt.amount)} $currency)${if (primaryDueTxt != null) " was due on $primaryDueTxt." else "."}
Days past due (worst note): $maxDaysOverdue.
${if (allOverdue.size > 1) "Breakdown:\n$lines" else ""}
Keep the message under 60 words. Do not use formal legal language.
        """.trimIndent()
        return agentMutex.mutex.withLock {
            gemmaService.generateResponse(prompt).trim()
        }
    }

    private suspend fun draftVisitSmsWithGemma(customer: Customer, visit: OverdueCustomer): String {
        val customerLanguage = languageNameForPrompt()
        val prompt = """
Write a short, warm SMS in $customerLanguage from a small shop to a regular customer named ${customer.name}.
They have not visited for about ${visit.daysSinceLastVisit} days; their usual gap between visits is about ${"%.0f".format(Locale.US, visit.avgGapDays)} days.
Keep under 45 words. Friendly and respectful, not pushy sales language.
        """.trimIndent()
        return agentMutex.mutex.withLock {
            gemmaService.generateResponse(prompt).trim()
        }
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

    private fun buildDebtDetail(customer: Customer, debts: List<Debt>, currency: String, worstDays: Long): String =
        buildString {
            append(customer.name).append(": overdue credit follow-up. Worst days past due: ").append(worstDays).append(". ")
            val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
            debts.forEach { d ->
                append(money(d.amount)).append(" ").append(currency)
                d.dueDate?.let { append(" due ").append(fmt.format(Date(it))) }
                append("; ")
            }
        }

    private fun buildVisitDetail(customer: Customer, visit: OverdueCustomer): String =
        "${customer.name}: gap vs usual cadence (~${"%.1f".format(Locale.US, visit.avgGapDays)} day avg between visits); " +
            "${visit.daysSinceLastVisit} days since last recorded visit."

    private fun money(v: Double): String = String.format(Locale.US, "%.2f", v)

    private fun daysOverdue(debt: Debt, zone: ZoneId, nowMillis: Long): Long {
        val due = debt.dueDate ?: return 0L
        val dueDay = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(dueDay, today).coerceAtLeast(0L)
    }

    private fun fallbackDebtSms(customer: Customer, primaryDebt: Debt, daysOverdue: Int, currency: String): String =
        applicationContext.getString(
            R.string.credit_remind_fallback_sms,
            customer.name,
            currency,
            primaryDebt.amount,
            daysOverdue,
        )
}
