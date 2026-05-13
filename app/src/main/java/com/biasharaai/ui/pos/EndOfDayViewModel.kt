package com.biasharaai.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.pos.cart.CartRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class EndOfDayUiState(
    val loading: Boolean = true,
    val stats: EndOfDayStats? = null,
    val narrative: String = "",
    val businessName: String = "",
    val currencySymbol: String = "",
)

@HiltViewModel
class EndOfDayViewModel @Inject constructor(
    private val posAiAdvisor: PosAiAdvisor,
    private val cartRepository: CartRepository,
    private val capabilityTier: CapabilityTier,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EndOfDayUiState())
    val uiState: StateFlow<EndOfDayUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = EndOfDayUiState(loading = true)
            val settings = cartRepository.activeSettings.value
            val businessName = settings?.businessName?.takeIf { it.isNotBlank() } ?: "My Business"
            val currency = settings?.currencySymbol?.takeIf { it.isNotBlank() } ?: ""
            val language = Locale.getDefault().displayLanguage
            val stats = posAiAdvisor.loadTodayStats()
            val narrative = posAiAdvisor.generateEndOfDaySummary(
                stats = stats,
                businessName = businessName,
                language = language,
                currency = currency,
                tier = capabilityTier,
            )
            _uiState.value = EndOfDayUiState(
                loading = false,
                stats = stats,
                narrative = narrative,
                businessName = businessName,
                currencySymbol = currency,
            )
        }
    }

    fun shareText(): String {
        val s = _uiState.value.stats ?: return _uiState.value.narrative
        val cur = _uiState.value.currencySymbol
        return buildString {
            appendLine(_uiState.value.businessName)
            appendLine("— End of day —")
            appendLine("Sales: ${s.totalSales} $cur · ${s.transactionCount} transactions")
            appendLine("Top: ${s.topProductName} (${s.topProductQty} units)")
            appendLine("Cash ${s.cashPct}% · Mobile ${s.mobilePct}% · Credit ${s.creditPct}%")
            appendLine()
            append(_uiState.value.narrative)
        }
    }
}
