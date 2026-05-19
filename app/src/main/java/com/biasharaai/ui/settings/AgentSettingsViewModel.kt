package com.biasharaai.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.R
import com.biasharaai.agent.AgentTypes
import com.biasharaai.productline.ProductLineManager
import com.biasharaai.data.local.db.AgentRunLog
import com.biasharaai.data.local.db.AgentRunLogDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Locale
import javax.inject.Inject

data class AgentSettingsRow(
    val agentType: String,
    val title: String,
    val summary: String,
    val expanded: Boolean,
    val historyText: String,
)

@HiltViewModel
class AgentSettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val agentRunLogDao: AgentRunLogDao,
    private val productLineManager: ProductLineManager,
) : ViewModel() {

    private val expanded = MutableStateFlow<Set<String>>(emptySet())

    private val _rows = MutableStateFlow<List<AgentSettingsRow>>(emptyList())
    val rows: StateFlow<List<AgentSettingsRow>> = _rows.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val ex = expanded.value
            val list = withContext(Dispatchers.IO) {
                displayOrderForUi().map { type ->
                    val last = agentRunLogDao.getLastRunForAgent(type)
                    val history =
                        if (ex.contains(type)) agentRunLogDao.getRunHistoryForAgent(type, 5) else emptyList()
                    AgentSettingsRow(
                        agentType = type,
                        title = titleFor(type),
                        summary = buildSummary(last),
                        expanded = ex.contains(type),
                        historyText = buildHistoryText(history, ex.contains(type)),
                    )
                }
            }
            _rows.value = list
        }
    }

    fun toggleAgent(agentType: String) {
        expanded.value =
            if (agentType in expanded.value) expanded.value - agentType else expanded.value + agentType
        refresh()
    }

    private fun titleFor(agentType: String): String =
        when (agentType) {
            AgentTypes.STOCK_GUARDIAN -> appContext.getString(R.string.agent_settings_name_stock_guardian)
            AgentTypes.FRAUD_SENTINEL -> appContext.getString(R.string.agent_settings_name_fraud_sentinel)
            AgentTypes.PRICING_AGENT -> appContext.getString(R.string.agent_settings_name_pricing_agent)
            AgentTypes.CASH_FLOW -> appContext.getString(R.string.agent_settings_name_cash_flow)
            AgentTypes.CUSTOMER_RELATION -> appContext.getString(R.string.agent_settings_name_customer_relation)
            AgentTypes.WEEKLY_REVIEW -> appContext.getString(R.string.agent_settings_name_weekly_review)
            AgentTypes.OPPORTUNITY_SPOTTER -> appContext.getString(R.string.agent_settings_name_opportunity_spotter)
            AgentTypes.UTILISATION_AGENT -> appContext.getString(R.string.agent_settings_name_utilisation)
            AgentTypes.NO_SHOW_TRACKER -> appContext.getString(R.string.agent_settings_name_no_show)
            AgentTypes.SERVICE_PRICING_AGENT -> appContext.getString(R.string.agent_settings_name_service_pricing)
            AgentTypes.VOUCHER_EXPIRY -> appContext.getString(R.string.agent_settings_name_voucher_expiry)
            else -> agentType
        }

    private fun displayOrderForUi(): List<String> = buildList {
        addAll(AgentDisplayOrder)
        if (productLineManager.isProEnabled()) {
            add(AgentTypes.UTILISATION_AGENT)
            add(AgentTypes.NO_SHOW_TRACKER)
            add(AgentTypes.SERVICE_PRICING_AGENT)
            add(AgentTypes.VOUCHER_EXPIRY)
        }
    }

    private fun buildSummary(last: AgentRunLog?): String {
        val ago = if (last == null) {
            appContext.getString(R.string.agent_settings_last_run_never)
        } else {
            formatAgo(last.ranAt)
        }
        val actions = last?.actionsGenerated ?: 0
        val gen = appContext.resources.getQuantityString(
            R.plurals.agent_settings_generated_actions,
            actions,
            actions,
        )
        return appContext.getString(R.string.agent_settings_row_summary, ago, gen)
    }

    private fun buildHistoryText(history: List<AgentRunLog>, isExpanded: Boolean): String {
        if (!isExpanded) return ""
        if (history.isEmpty()) return appContext.getString(R.string.agent_settings_history_empty)
        return history.joinToString("\n") { formatHistoryLine(it) }
    }

    private fun formatHistoryLine(log: AgentRunLog): String {
        val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
        val time = df.format(log.ranAt)
        val dur = formatDuration(log.durationMs)
        return appContext.getString(R.string.agent_settings_history_line, time, dur, log.outcome)
    }

    private fun formatAgo(ranAt: Long): String {
        val diff = (System.currentTimeMillis() - ranAt).coerceAtLeast(0L)
        return when {
            diff < 60_000L -> appContext.getString(R.string.agent_settings_last_run_just_now)
            diff < 3_600_000L -> {
                val m = (diff / 60_000L).toInt().coerceAtLeast(1)
                appContext.resources.getQuantityString(R.plurals.agent_settings_minutes_ago, m, m)
            }
            diff < 86_400_000L -> {
                val h = (diff / 3_600_000L).toInt().coerceAtLeast(1)
                appContext.resources.getQuantityString(R.plurals.agent_settings_hours_ago, h, h)
            }
            else -> {
                val d = (diff / 86_400_000L).toInt().coerceAtLeast(1)
                appContext.resources.getQuantityString(R.plurals.agent_settings_days_ago, d, d)
            }
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val ms = durationMs.coerceAtLeast(0L)
        return when {
            ms < 60_000L ->
                appContext.getString(
                    R.string.agent_settings_duration_seconds,
                    (ms / 1000L).toInt().coerceAtLeast(0),
                )
            ms < 3_600_000L ->
                appContext.getString(R.string.agent_settings_duration_minutes, (ms / 60_000L).toInt())
            else ->
                appContext.getString(R.string.agent_settings_duration_hours, (ms / 3_600_000L).toInt())
        }
    }

    companion object {
        val AgentDisplayOrder = listOf(
            AgentTypes.STOCK_GUARDIAN,
            AgentTypes.FRAUD_SENTINEL,
            AgentTypes.PRICING_AGENT,
            AgentTypes.CASH_FLOW,
            AgentTypes.CUSTOMER_RELATION,
            AgentTypes.WEEKLY_REVIEW,
            AgentTypes.OPPORTUNITY_SPOTTER,
        )
    }
}
