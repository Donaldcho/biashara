package com.biasharaai.ui.negotiation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.ai.GemmaService
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.locale.LanguagePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

/** Snapshot of the supplier negotiation form — kept for **Regenerate** on the guide screen. */
data class NegotiationFormInput(
    val supplierName: String,
    val selectedProductIds: Set<Long>,
    val extraItemsText: String,
    val budgetRaw: String,
    val city: String,
    val country: String,
)

data class NegotiationSectionUi(
    val title: String,
    val body: String,
    val colorRes: Int,
)

sealed interface NegotiationGenerateState {
    data object Idle : NegotiationGenerateState
    data object Loading : NegotiationGenerateState
    data class Success(val fullScript: String) : NegotiationGenerateState
    data class Error(val message: String) : NegotiationGenerateState
}

@HiltViewModel
class NegotiationViewModel @Inject constructor(
    private val gemmaService: GemmaService,
    private val appSettingsDao: AppSettingsDao,
    private val productDao: ProductDao,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _generateState = MutableStateFlow<NegotiationGenerateState>(NegotiationGenerateState.Idle)
    val generateState: StateFlow<NegotiationGenerateState> = _generateState.asStateFlow()

    private val _sections = MutableStateFlow<List<NegotiationSectionUi>>(emptyList())
    val sections: StateFlow<List<NegotiationSectionUi>> = _sections.asStateFlow()

    private val _fullScript = MutableStateFlow("")
    val fullScript: StateFlow<String> = _fullScript.asStateFlow()

    private var lastSubmittedForm: NegotiationFormInput? = null

    /** Clears script output so a new home/inventory entry starts clean (Prompt U7). */
    fun resetScriptOutput() {
        _sections.value = emptyList()
        _fullScript.value = ""
        _generateState.value = NegotiationGenerateState.Idle
        lastSubmittedForm = null
    }

    fun consumeGenerationSuccess() {
        if (_generateState.value is NegotiationGenerateState.Success) {
            _generateState.value = NegotiationGenerateState.Idle
        }
    }

    fun submitNegotiationForm(input: NegotiationFormInput) {
        lastSubmittedForm = input
        runGeneration(input)
    }

    fun regenerate() {
        val f = lastSubmittedForm ?: return
        runGeneration(f)
    }

    private fun runGeneration(input: NegotiationFormInput) {
        if (_generateState.value is NegotiationGenerateState.Loading) return
        viewModelScope.launch {
            _generateState.value = NegotiationGenerateState.Loading
            val settings = withContext(Dispatchers.IO) { appSettingsDao.getSettingsSync() }
            val currency = settings?.currencyCode ?: "KES"
            val language = languageLabelForPrompt()
            val itemsList = buildItemsList(input.selectedProductIds, input.extraItemsText)
            if (itemsList.isBlank()) {
                _generateState.value = NegotiationGenerateState.Error("NO_ITEMS")
                return@launch
            }
            val budget = input.budgetRaw.trim().replace(",", "").toDoubleOrNull()
            if (budget == null || budget <= 0.0) {
                _generateState.value = NegotiationGenerateState.Error("BAD_BUDGET")
                return@launch
            }
            val cityTrim = input.city.trim().ifBlank { "not specified" }
            val countryTrim = input.country.trim().ifBlank { "not specified" }
            val supplier = input.supplierName.trim().ifBlank { null }
            val prompt = """
You are helping a small business owner in $cityTrim, $countryTrim
negotiate with a supplier. Respond in $language.
Use a warm, confident, respectful tone appropriate for local business culture.

They want to buy: $itemsList.
Their total budget is: $budget $currency.
Supplier name: ${supplier ?: "not specified"}.

Write a negotiation script with these four clearly labelled sections:
1. OPENING — a friendly greeting and introduction
2. MAIN ASK — state what they want and their target price
3. IF PUSHED BACK — how to respond if the supplier says no
4. CLOSING — how to end the conversation positively

Keep the total script under 250 words.
            """.trimIndent()
            try {
                if (!gemmaService.isAvailable) {
                    _generateState.value = NegotiationGenerateState.Error("MODEL_UNAVAILABLE")
                    return@launch
                }
                val raw = withContext(Dispatchers.IO) {
                    gemmaService.generateResponse(prompt)
                }
                val trimmed = raw.trim()
                _fullScript.value = trimmed
                _sections.value = parseNegotiationSections(trimmed)
                _generateState.value = NegotiationGenerateState.Success(trimmed)
            } catch (e: Exception) {
                _generateState.value = NegotiationGenerateState.Error(e.message ?: "GENERATION_FAILED")
            }
        }
    }

    private fun languageLabelForPrompt(): String {
        val tag = LanguagePreferences.getPersistedLocaleTag(appContext)
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

    private suspend fun buildItemsList(selectedProductIds: Set<Long>, extraItemsText: String): String {
        val parts = mutableListOf<String>()
        if (selectedProductIds.isNotEmpty()) {
            val all = withContext(Dispatchers.IO) { productDao.getProductsList() }
            val byId = all.associateBy { it.id }
            selectedProductIds.sorted().forEach { id ->
                byId[id]?.name?.let { parts.add(it) }
            }
        }
        extraItemsText.trim().lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { parts.add(it) }
        return parts.joinToString(", ")
    }

    companion object {
        private val SECTION_HEADER = Regex(
            """(?im)^\s*\d+\.\s*(OPENING|MAIN ASK|IF PUSHED BACK|CLOSING)\b[^\n]*""",
        )

        private val SECTION_COLORS = intArrayOf(
            com.biasharaai.R.color.biashara_mint,
            com.biasharaai.R.color.biashara_blue_light,
            com.biasharaai.R.color.biashara_amber_light,
            com.biasharaai.R.color.biashara_success_light,
        )

        fun parseNegotiationSections(raw: String): List<NegotiationSectionUi> {
            val matches = SECTION_HEADER.findAll(raw).toList()
            if (matches.size < 2) {
                return listOf(
                    NegotiationSectionUi(
                        title = "Script",
                        body = raw.trim(),
                        colorRes = com.biasharaai.R.color.biashara_mint,
                    ),
                )
            }
            return matches.mapIndexed { index, match ->
                val title = match.value.trim()
                val bodyStart = match.range.last + 1
                val bodyEnd = matches.getOrNull(index + 1)?.range?.first ?: raw.length
                val body = raw.substring(bodyStart, bodyEnd).trim()
                NegotiationSectionUi(
                    title = title,
                    body = body,
                    colorRes = SECTION_COLORS[index % SECTION_COLORS.size],
                )
            }
        }
    }
}
