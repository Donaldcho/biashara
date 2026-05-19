package com.biasharaai.ui.agent

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.R
import com.biasharaai.agent.AgentActionExecutor
import com.biasharaai.agent.AgentOrchestrator
import com.biasharaai.agent.ExecutionResult
import com.biasharaai.data.local.db.AgentAction
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.AgentAdviceFeedback
import com.biasharaai.data.local.db.AgentAdviceFeedbackDao
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.BusinessKpiSnapshot
import com.biasharaai.data.local.db.BusinessKpiSnapshotDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class AgentTodayBriefUiModel(
    val title: String = "",
    val body: String = "",
)

data class AgentFeedUiState(
    val greeting: String,
    val dateLine: String,
    val attentionLabel: String,
    val brief: AgentTodayBriefUiModel = AgentTodayBriefUiModel(),
    val rows: List<AgentActionCardUiModel>,
    val ttsEnabled: Boolean = false,
    val ttsAutoReadCriticalAlerts: Boolean = false,
)

sealed interface AgentFeedEvent {
    data class ApproveFailed(val action: AgentAction, val message: String) : AgentFeedEvent
    data class ApproveSuccess(val actionId: Long) : AgentFeedEvent
    data class FeedbackSaved(val hidesSimilarReports: Boolean) : AgentFeedEvent
    data class ApproveNeedsNavigation(val action: AgentAction) : AgentFeedEvent
}

@HiltViewModel
class AgentFeedViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val agentActionDao: AgentActionDao,
    private val agentAdviceFeedbackDao: AgentAdviceFeedbackDao,
    private val appSettingsDao: AppSettingsDao,
    private val businessKpiSnapshotDao: BusinessKpiSnapshotDao,
    private val agentOrchestrator: AgentOrchestrator,
    private val agentActionExecutor: AgentActionExecutor,
) : ViewModel() {

    private val executingId = MutableStateFlow<Long?>(null)
    private val refreshInProgress = MutableStateFlow(false)

    private val _events = MutableSharedFlow<AgentFeedEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<AgentFeedUiState> = combine(
        agentActionDao.observePendingActionsForFeed(),
        appSettingsDao.getSettings(),
        executingId,
        agentAdviceFeedbackDao.observeFeedbackSince(System.currentTimeMillis() - FEEDBACK_SUPPRESSION_WINDOW_MS),
        businessKpiSnapshotDao.observeRecent(BRIEF_KPI_WEEKS),
    ) { actions, settings, execId, feedback, snapshots ->
        val sorted = sortByUrgencyThenDate(actions)
        val feedbackByActionId = feedback.associateBy { it.agentActionId }
        val rejectedHashes = feedback
            .asSequence()
            .filter { it.vote < 0 }
            .map { it.contentHash }
            .toSet()
        val visibleActions = collapseRepeatedActions(sorted)
            .filterNot { action -> contentHashFor(action) in rejectedHashes }
        val hiddenCount = (sorted.size - visibleActions.size).coerceAtLeast(0)
        val limitedActions = visibleActions.take(MAX_FEED_ROWS)
        val ttsOn = settings?.ttsEnabled == true
        val rows = limitedActions.map {
            AgentActionCardUiModel(
                action = it,
                isExecuting = it.id == execId,
                ttsEnabled = ttsOn,
                feedbackVote = feedbackByActionId[it.id]?.vote,
            )
        }
        val biz = settings?.businessName?.takeIf { it.isNotBlank() }
            ?: appContext.getString(R.string.app_name)
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val greetRes = when {
            hour < 12 -> R.string.agent_feed_greeting_morning
            hour < 17 -> R.string.agent_feed_greeting_afternoon
            else -> R.string.agent_feed_greeting_evening
        }
        val greeting = appContext.getString(greetRes, biz)
        val dateLine = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
            .format(Date())
        val n = limitedActions.size
        val attentionLabel = if (n == 0) {
            appContext.getString(R.string.agent_feed_attention_none)
        } else {
            appContext.resources.getQuantityString(
                R.plurals.agent_feed_attention,
                n,
                n,
            )
        }
        AgentFeedUiState(
            greeting = greeting,
            dateLine = dateLine,
            attentionLabel = attentionLabel,
            brief = buildTodayBrief(
                actions = limitedActions,
                snapshots = snapshots,
                hiddenCount = hiddenCount,
                currencySymbol = settings?.currencySymbol?.takeIf { it.isNotBlank() }.orEmpty(),
            ),
            rows = rows,
            ttsEnabled = ttsOn,
            ttsAutoReadCriticalAlerts = settings?.ttsAutoReadAgentAlerts == true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AgentFeedUiState(
            greeting = "",
            dateLine = "",
            attentionLabel = "",
            brief = AgentTodayBriefUiModel(),
            rows = emptyList(),
            ttsEnabled = false,
            ttsAutoReadCriticalAlerts = false,
        ),
    )

    fun refreshAgents() {
        if (refreshInProgress.value) return
        refreshInProgress.value = true
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    agentActionDao.expireOldActions(now)
                    agentAdviceFeedbackDao.deleteOlderThan(now - FEEDBACK_RETENTION_MS)
                }
                agentOrchestrator.runAllNow()
                delay(MIN_REFRESH_INTERVAL_MS)
            } finally {
                refreshInProgress.value = false
            }
        }
    }

    fun approve(action: AgentAction) {
        viewModelScope.launch {
            executingId.value = action.id
            val result = try {
                withContext(Dispatchers.IO) {
                    agentActionExecutor.execute(action)
                }
            } catch (e: Exception) {
                ExecutionResult.Error(e.localizedMessage)
            }
            executingId.value = null
            when (result) {
                ExecutionResult.Success ->
                    _events.emit(AgentFeedEvent.ApproveSuccess(action.id))
                ExecutionResult.RequiresNavigation ->
                    _events.emit(AgentFeedEvent.ApproveNeedsNavigation(action))
                ExecutionResult.UnknownVerb ->
                    _events.emit(
                        AgentFeedEvent.ApproveFailed(
                            action,
                            appContext.getString(R.string.agent_action_unknown_verb),
                        ),
                    )
                is ExecutionResult.Error ->
                    _events.emit(
                        AgentFeedEvent.ApproveFailed(
                            action,
                            result.message ?: appContext.getString(R.string.agent_action_execute_failed),
                        ),
                    )
            }
        }
    }

    fun markExecutedAfterNavigation(actionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            agentActionDao.updateStatus(actionId, "EXECUTED")
        }
    }

    fun reject(action: AgentAction) {
        viewModelScope.launch(Dispatchers.IO) {
            agentActionDao.updateStatus(action.id, "DISMISSED")
        }
    }

    fun submitFeedback(action: AgentAction, helpful: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            agentAdviceFeedbackDao.upsert(
                AgentAdviceFeedback(
                    agentActionId = action.id,
                    agentType = action.agentType,
                    contentHash = contentHashFor(action),
                    headline = action.headline,
                    detail = action.detail,
                    vote = if (helpful) 1 else -1,
                    createdAt = now,
                ),
            )
            agentAdviceFeedbackDao.deleteOlderThan(now - FEEDBACK_RETENTION_MS)
            if (!helpful) {
                agentActionDao.updateStatus(action.id, "DISMISSED")
            }
            _events.emit(AgentFeedEvent.FeedbackSaved(hidesSimilarReports = !helpful))
        }
    }

    fun dismiss(action: AgentAction) {
        viewModelScope.launch(Dispatchers.IO) {
            agentActionDao.updateStatus(action.id, "DISMISSED")
        }
    }

    fun snooze(action: AgentAction) {
        viewModelScope.launch(Dispatchers.IO) {
            val until = System.currentTimeMillis() + 24L * 60 * 60 * 1000
            agentActionDao.updateExpiresAt(action.id, until)
        }
    }

    private fun sortByUrgencyThenDate(actions: List<AgentAction>): List<AgentAction> {
        val rank: (AgentAction) -> Int = { a ->
            when (a.urgency) {
                "CRITICAL" -> 0
                "HIGH" -> 1
                "MEDIUM" -> 2
                "LOW" -> 3
                else -> 4
            }
        }
        return actions.sortedWith(compareBy(rank, { -it.createdAt }))
    }

    private fun collapseRepeatedActions(actions: List<AgentAction>): List<AgentAction> {
        val seen = LinkedHashSet<String>()
        return actions.filter { action ->
            seen.add(dedupeKeyFor(action))
        }
    }

    private fun dedupeKeyFor(action: AgentAction): String =
        listOf(
            action.agentType,
            action.actionVerb.orEmpty(),
            action.relatedEntityType.orEmpty(),
            action.relatedEntityId?.toString().orEmpty(),
            contentHashFor(action),
        ).joinToString("|")

    private fun contentHashFor(action: AgentAction): String {
        val raw = buildString {
            append(action.agentType).append('|')
            append(action.actionVerb.orEmpty()).append('|')
            append(action.relatedEntityType.orEmpty()).append('|')
            append(action.relatedEntityId?.toString().orEmpty()).append('|')
            append(action.headline).append('|')
            append(action.detail.take(CONTENT_HASH_DETAIL_LIMIT))
        }
        val normalized = NORMALIZE_WHITESPACE.replace(
            NORMALIZE_DIGITS.replace(raw.lowercase(Locale.US), "#"),
            " ",
        ).trim()
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.take(CONTENT_HASH_BYTES).joinToString("") { byte ->
            String.format(Locale.US, "%02x", byte.toInt() and 0xff)
        }
    }

    private fun buildTodayBrief(
        actions: List<AgentAction>,
        snapshots: List<BusinessKpiSnapshot>,
        hiddenCount: Int,
        currencySymbol: String,
    ): AgentTodayBriefUiModel {
        val actionLine = actions.firstOrNull()?.let { top ->
            val highPriorityCount = actions.count { it.urgency == "CRITICAL" || it.urgency == "HIGH" }
            if (highPriorityCount > 1) {
                appContext.resources.getQuantityString(
                    R.plurals.agent_feed_brief_priority_count,
                    highPriorityCount,
                    highPriorityCount,
                    top.headline,
                )
            } else {
                appContext.getString(R.string.agent_feed_brief_focus, top.headline)
            }
        } ?: appContext.getString(R.string.agent_feed_brief_clear)
        val kpiLine = kpiBriefLine(snapshots, currencySymbol)
        val hiddenLine = if (hiddenCount > 0) {
            appContext.resources.getQuantityString(
                R.plurals.agent_feed_brief_hidden_repeats,
                hiddenCount,
                hiddenCount,
            )
        } else {
            null
        }
        return AgentTodayBriefUiModel(
            title = appContext.getString(R.string.agent_feed_brief_title),
            body = listOfNotNull(actionLine, kpiLine, hiddenLine).joinToString(" "),
        )
    }

    private fun kpiBriefLine(
        snapshots: List<BusinessKpiSnapshot>,
        currencySymbol: String,
    ): String? {
        val latest = snapshots.firstOrNull() ?: return null
        if (latest.weekRevenue <= 0.0 && latest.txCount <= 0L) return null
        val revenue = formatMoney(currencySymbol, latest.weekRevenue)
        return when {
            latest.serviceRevenue > latest.productRevenue && latest.serviceRevenue > 0.0 ->
                appContext.getString(R.string.agent_feed_brief_kpi_service_lead, revenue)
            latest.topProductName.isNotBlank() ->
                appContext.getString(
                    R.string.agent_feed_brief_kpi_product_lead,
                    revenue,
                    latest.topProductName,
                )
            else ->
                appContext.getString(R.string.agent_feed_brief_kpi_neutral, revenue, latest.txCount)
        }
    }

    private fun formatMoney(currencySymbol: String, amount: Double): String {
        val symbol = currencySymbol.ifBlank { "" }
        return symbol + String.format(Locale.US, "%.0f", amount)
    }

    companion object {
        private const val MAX_FEED_ROWS = 20
        private const val BRIEF_KPI_WEEKS = 4
        private const val MIN_REFRESH_INTERVAL_MS = 3_000L
        private const val FEEDBACK_SUPPRESSION_WINDOW_MS = 21L * 24 * 60 * 60 * 1000
        private const val FEEDBACK_RETENTION_MS = 180L * 24 * 60 * 60 * 1000
        private const val CONTENT_HASH_DETAIL_LIMIT = 360
        private const val CONTENT_HASH_BYTES = 12
        private val NORMALIZE_DIGITS = Regex("\\d+")
        private val NORMALIZE_WHITESPACE = Regex("[^a-z0-9#]+")
    }
}
