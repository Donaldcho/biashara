package com.biasharaai.ui.ledger

import androidx.lifecycle.viewModelScope
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.LedgerEntry
import com.biasharaai.data.local.db.LedgerEntryDao
import com.biasharaai.data.local.db.LedgerRepository
import com.biasharaai.enterprise.EnterprisePermissionRepository
import com.biasharaai.enterprise.EnterpriseRolePermissions
import com.biasharaai.ledger.LedgerReportExporter
import com.biasharaai.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val ledgerEntryDao: LedgerEntryDao,
    private val ledgerRepository: LedgerRepository,
    private val appSettingsDao: AppSettingsDao,
    private val reportExporter: LedgerReportExporter,
    private val enterprisePermissionRepository: EnterprisePermissionRepository,
) : BaseViewModel() {

    data class UiState(
        val periodLabel: String = "",
        val runningBalance: Double = 0.0,
        val moneyIn: Double = 0.0,
        val moneyOut: Double = 0.0,
        val pendingCredit: Double = 0.0,
        val entries: List<LedgerEntry> = emptyList(),
        val searchQuery: String = "",
    )

    private val periodRange = MutableStateFlow(monthRange())
    private val searchQuery = MutableStateFlow("")
    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    val uiState: StateFlow<UiState> = kotlinx.coroutines.flow.combine(
        periodRange,
        searchQuery,
    ) { range, query -> range to query }
        .flatMapLatest { (range, query) ->
            val (from, to, label) = range
            val entriesFlow = if (query.isBlank()) {
                ledgerEntryDao.getEntriesForPeriod(from, to)
            } else {
                ledgerEntryDao.search(query)
            }
            entriesFlow.mapLatest { entries ->
                UiState(
                    periodLabel = label,
                    runningBalance = ledgerEntryDao.getCurrentBalance() ?: 0.0,
                    moneyIn = ledgerEntryDao.getTotalForDirection(
                        LedgerDirection.MONEY_IN.name,
                        from,
                        to,
                    ) ?: 0.0,
                    moneyOut = ledgerEntryDao.getTotalForDirection(
                        LedgerDirection.MONEY_OUT.name,
                        from,
                        to,
                    ) ?: 0.0,
                    pendingCredit = ledgerEntryDao.getPendingCreditTotal() ?: 0.0,
                    entries = entries,
                    searchQuery = query,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setSearchQuery(query: String) {
        searchQuery.value = query.trim()
    }

    fun refreshPeriod() {
        periodRange.value = monthRange()
    }

    fun submitManualEntry(
        direction: LedgerDirection,
        amount: Double,
        description: String,
        notes: String?,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val permissionCheck = enterprisePermissionRepository.requirePermission(
                permission = EnterpriseRolePermissions.PERMISSION_EDIT_LEDGER,
                action = "LEDGER_MANUAL_ENTRY",
                entityType = "LEDGER_ENTRY",
                summary = "Manual ledger entry blocked",
                metadata = "direction=${direction.name}; amount=$amount",
            )
            if (!permissionCheck.allowed) {
                val operator = permissionCheck.operator
                _events.emit(
                    Event.PermissionDenied(
                        operatorName = operator?.name.orEmpty(),
                        operatorRole = operator?.role.orEmpty(),
                    ),
                )
                return@launch
            }
            ledgerRepository.recordManualEntry(direction, amount, description, notes)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun submitCashCount(
        actualBalance: Double,
        notes: String?,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val permissionCheck = enterprisePermissionRepository.requirePermission(
                permission = EnterpriseRolePermissions.PERMISSION_EDIT_LEDGER,
                action = "LEDGER_CASH_COUNT",
                entityType = "CASH_COUNT",
                summary = "Cash count blocked",
                metadata = "actualBalance=$actualBalance",
            )
            if (!permissionCheck.allowed) {
                val operator = permissionCheck.operator
                _events.emit(
                    Event.PermissionDenied(
                        operatorName = operator?.name.orEmpty(),
                        operatorRole = operator?.role.orEmpty(),
                    ),
                )
                return@launch
            }
            val expected = ledgerEntryDao.getCurrentBalance() ?: 0.0
            ledgerRepository.recordCashCount(expected, actualBalance, notes)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    suspend fun exportCsv(): String? = withContext(Dispatchers.IO) {
        val permissionCheck = enterprisePermissionRepository.requirePermission(
            permission = EnterpriseRolePermissions.PERMISSION_EXPORT_DATA,
            action = "LEDGER_CSV_EXPORT",
            entityType = "LEDGER_REPORT",
            summary = "Ledger CSV export blocked",
        )
        if (!permissionCheck.allowed) {
            val operator = permissionCheck.operator
            _events.emit(
                Event.PermissionDenied(
                    operatorName = operator?.name.orEmpty(),
                    operatorRole = operator?.role.orEmpty(),
                ),
            )
            return@withContext null
        }
        val (from, to, _) = periodRange.value
        val business = appSettingsDao.getSettingsSync()?.businessName?.ifBlank { "My shop" } ?: "My shop"
        reportExporter.buildCsvReport(from, to, business)
    }

    private fun monthRange(): Triple<Long, Long, String> {
        val cal = Calendar.getInstance()
        val label = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
            .format(cal.time)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val from = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val to = cal.timeInMillis - 1
        return Triple(from, to, label)
    }

    sealed class Event {
        data class PermissionDenied(val operatorName: String, val operatorRole: String) : Event()
    }
}
