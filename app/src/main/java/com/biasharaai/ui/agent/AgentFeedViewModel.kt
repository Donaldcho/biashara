package com.biasharaai.ui.agent

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.R
import com.biasharaai.agent.AgentActionExecutor
import com.biasharaai.agent.ExecutionResult
import com.biasharaai.agent.AgentOrchestrator
import com.biasharaai.data.local.db.AgentAction
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.AppSettingsDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class AgentFeedUiState(
    val greeting: String,
    val dateLine: String,
    val attentionLabel: String,
    val rows: List<AgentActionCardUiModel>,
)

sealed interface AgentFeedEvent {
    data class ApproveFailed(val action: AgentAction, val message: String) : AgentFeedEvent
    data class ApproveSuccess(val actionId: Long) : AgentFeedEvent
    /** [ExecutionResult.RequiresNavigation] — UI should navigate, then call [markExecutedAfterNavigation]. */
    data class ApproveNeedsNavigation(val action: AgentAction) : AgentFeedEvent
}

@HiltViewModel
class AgentFeedViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val agentActionDao: AgentActionDao,
    private val appSettingsDao: AppSettingsDao,
    private val agentOrchestrator: AgentOrchestrator,
    private val agentActionExecutor: AgentActionExecutor,
) : ViewModel() {

    private val executingId = MutableStateFlow<Long?>(null)

    private val _events = MutableSharedFlow<AgentFeedEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<AgentFeedUiState> = combine(
        agentActionDao.observePendingActionsForFeed(),
        appSettingsDao.getSettings(),
        executingId,
    ) { actions, settings, execId ->
        val sorted = sortByUrgencyThenDate(actions)
        val rows = sorted.map { AgentActionCardUiModel(it, it.id == execId) }
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
        val n = sorted.size
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
            rows = rows,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AgentFeedUiState(
            greeting = "",
            dateLine = "",
            attentionLabel = "",
            rows = emptyList(),
        ),
    )

    fun refreshAgents() {
        agentOrchestrator.runAllNow()
    }

    fun approve(action: AgentAction) {
        viewModelScope.launch {
            executingId.value = action.id
            val result = withContext(Dispatchers.IO) {
                agentActionExecutor.execute(action)
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
}
