package com.biasharaai.ui.chat

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.viewModelScope
import com.biasharaai.R
import com.biasharaai.ai.GemmaService
import com.biasharaai.ai.InferenceSettingsStore
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.data.local.db.TransactionDao
import com.biasharaai.data.local.db.TransactionType
import com.biasharaai.locale.LanguagePreferences
import com.biasharaai.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeoutException
import javax.inject.Inject

/**
 * ViewModel for the AI Chat feature.
 *
 * Injects business context (inventory and transaction summaries) into
 * every prompt so Gemma can give relevant, data-driven answers about
 * the user's business in the **language they chose** at onboarding.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val gemmaService: GemmaService,
    private val productDao: ProductDao,
    private val transactionDao: TransactionDao,
    @ApplicationContext private val appContext: Context,
    private val inferenceSettingsStore: InferenceSettingsStore,
) : BaseViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    /** True while the model is actively generating; toggles the send button into a stop button. */
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private var generationJob: Job? = null

    init {
        // Pre-load the engine the moment the user opens chat so the first message doesn't
        // pay the cold engine init cost.
        gemmaService.warmUp()
    }

    /** Whether the AI model is available for chat. */
    val isModelAvailable: Boolean
        get() = gemmaService.isAvailable

    /** Language code from app settings (en, sw, ha, yo, am, …). */
    private fun resolveUserLanguageCode(): String {
        val tag = LanguagePreferences.getPersistedLocaleTag(appContext)
        if (!tag.isNullOrBlank()) return tag.substringBefore("-").lowercase()
        val locales = AppCompatDelegate.getApplicationLocales()
        for (i in 0 until locales.size()) {
            val l = locales[i] ?: continue
            if (l.language.isNotBlank()) return l.language.lowercase()
        }
        return Locale.getDefault().language.lowercase().ifBlank { "en" }
    }

    /** [Context] with the user's locale so [Context.getString] returns the right translations. */
    private fun localizedContext(): Context {
        val code = resolveUserLanguageCode()
        val loc = Locale.forLanguageTag(code)
        val cfg = Configuration(appContext.resources.configuration)
        cfg.setLocale(loc)
        return appContext.createConfigurationContext(cfg)
    }

    private fun languageNameForPrompt(code: String): String = when (code.lowercase()) {
        "en" -> "English"
        "sw" -> "Swahili"
        "ha" -> "Hausa"
        "yo" -> "Yoruba"
        "am" -> "Amharic"
        else -> Locale.forLanguageTag(code).getDisplayName(Locale.ENGLISH)
    }

    /**
     * Send a user message and get an AI response.
     */
    fun sendMessage(userText: String) {
        if (userText.isBlank()) return
        if (_isGenerating.value) return

        val userMessage = ChatMessage(text = userText.trim(), isUser = true)
        _messages.value = _messages.value + userMessage

        generationJob = viewModelScope.launch(Dispatchers.IO) {
            _isThinking.value = true
            _isGenerating.value = true

            val placeholder = ChatMessage(text = "", isUser = false)
            _messages.value = _messages.value + placeholder
            val placeholderIndex = _messages.value.lastIndex
            val streamed = StringBuilder()

            try {
                if (!gemmaService.isAvailable) {
                    replacePlaceholder(placeholderIndex, generateFallbackResponse(userText.trim()))
                    return@launch
                }

                val context = buildBusinessContext(userText.trim())
                val prompt = buildPrompt(context, userText.trim())

                val startMs = System.currentTimeMillis()
                var firstTokenMs = 0L

                try {
                    gemmaService.generateStreaming(prompt) { delta, done ->
                        if (delta.isNotEmpty()) {
                            if (firstTokenMs == 0L) {
                                firstTokenMs = System.currentTimeMillis()
                                Log.d(TAG, "First token after ${firstTokenMs - startMs}ms (prompt=${prompt.length} chars)")
                            }
                            streamed.append(delta)
                            updatePlaceholder(placeholderIndex, streamed.toString())
                            if (_isThinking.value) _isThinking.value = false
                        }
                        if (done) {
                            _isThinking.value = false
                        }
                    }
                    val totalMs = System.currentTimeMillis() - startMs
                    val decodeMs = if (firstTokenMs > 0L) {
                        System.currentTimeMillis() - firstTokenMs
                    } else totalMs
                    val approxTokens = streamed.length / 4
                    val tps = if (decodeMs > 0) approxTokens * 1000.0 / decodeMs else 0.0
                    Log.d(
                        TAG,
                        "AI streaming OK: ${streamed.length} chars (~$approxTokens tok), " +
                            "first=${if (firstTokenMs > 0L) firstTokenMs - startMs else -1}ms, " +
                            "decode=${decodeMs}ms, ${"%.1f".format(tps)} tok/s",
                    )
                } catch (_: TimeoutException) {
                    Log.w(TAG, "AI first-token timed out — showing offline summary")
                    val fb = generateFallbackResponse(userText.trim())
                    replacePlaceholder(
                        placeholderIndex,
                        localizedContext().getString(R.string.chat_ai_timeout_with_summary, fb),
                    )
                    return@launch
                } catch (cancelled: CancellationException) {
                    Log.d(TAG, "AI generation cancelled by user")
                    val partial = streamed.toString().trim()
                    val suffix = "\n\n" + localizedContext().getString(R.string.chat_stopped_marker)
                    val finalText = if (partial.isEmpty()) {
                        localizedContext().getString(R.string.chat_stopped_marker)
                    } else {
                        partial + suffix
                    }
                    updatePlaceholder(placeholderIndex, finalText)
                    throw cancelled
                } catch (aiError: Exception) {
                    Log.w(TAG, "AI inference failed, falling back to rules", aiError)
                    replacePlaceholder(placeholderIndex, generateFallbackResponse(userText.trim()))
                    return@launch
                }

                if (streamed.isBlank()) {
                    Log.w(TAG, "AI returned blank, falling back to rules")
                    replacePlaceholder(placeholderIndex, generateFallbackResponse(userText.trim()))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Chat response failed completely", e)
                replacePlaceholder(
                    placeholderIndex,
                    localizedContext().getString(R.string.chat_error_generic),
                )
            } finally {
                _isThinking.value = false
                _isGenerating.value = false
            }
        }
    }

    /** Stop generation in flight. The bubble keeps whatever text was already streamed. */
    fun stopGeneration() {
        if (!_isGenerating.value) return
        gemmaService.cancelGeneration()
        generationJob?.cancel()
    }

    /** Clear the current conversation and drop the LLM session so the next reply starts fresh. */
    fun startNewChat() {
        stopGeneration()
        _messages.value = emptyList()
        gemmaService.resetSession()
    }

    /** Localized list of starter questions shown on the empty state. */
    fun suggestedPrompts(): List<String> {
        val L = localizedContext()
        return listOf(
            L.getString(R.string.chat_suggest_profit),
            L.getString(R.string.chat_suggest_low_stock),
            L.getString(R.string.chat_suggest_top_expenses),
            L.getString(R.string.chat_suggest_inventory_count),
        )
    }

    override fun onCleared() {
        super.onCleared()
        gemmaService.cancelGeneration()
        generationJob?.cancel()
    }

    private fun updatePlaceholder(index: Int, text: String) {
        val list = _messages.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(text = text)
            _messages.value = list
        }
    }

    private fun replacePlaceholder(index: Int, text: String) {
        val safeText = text.ifBlank {
            localizedContext().getString(R.string.chat_fallback_generic)
        }
        updatePlaceholder(index, safeText)
    }

    /**
     * Build only the slice of business data that looks relevant to [userQuestion].
     *
     * Shorter prompts → faster prefill → much shorter time-to-first-token on `tasks-genai`.
     * If we can't classify the question, we fall back to a small summary instead of the full dump.
     */
    private suspend fun buildBusinessContext(userQuestion: String): String {
        val q = userQuestion.lowercase()
        val wantsInventory = q.containsAny(
            "stock", "inventory", "product", "bidhaa", "kayayyaki", "hisa", "soko",
            "kiasi", "akiba", "duka",
        )
        val wantsLowStock = q.containsAny("low stock", "reorder", "restock", "out of stock", "finished")
        val wantsFinance = q.containsAny(
            "profit", "revenue", "income", "expense", "cost", "spend", "spending", "money",
            "cash", "faida", "mapato", "matumizi", "hasara",
        )
        val needAll = !(wantsInventory || wantsLowStock || wantsFinance)

        val sb = StringBuilder()

        if (wantsInventory || wantsLowStock || needAll) {
            try {
                val products = productDao.getProductsList()
                if (products.isNotEmpty()) {
                    val totalStockValue = products.sumOf { it.price * it.stockQuantity }
                    val lowStock = products.filter { it.stockQuantity < 10 }
                    sb.appendLine("Inventory: ${products.size} products. Total stock value ${formatCurrency(totalStockValue)}.")
                    val productListing = if (wantsInventory && !needAll) {
                        // Detailed product listing only when explicitly asked.
                        products.joinToString(", ") {
                            "${it.name} (${it.stockQuantity} in stock at ${formatCurrency(it.price)})"
                        }
                    } else {
                        // Brief listing for general/profit questions: top 5 by stock value.
                        products
                            .sortedByDescending { it.price * it.stockQuantity }
                            .take(5)
                            .joinToString(", ") { "${it.name} (${it.stockQuantity})" }
                    }
                    sb.appendLine("Products: $productListing.")
                    if (lowStock.isNotEmpty()) {
                        sb.appendLine(
                            "Low stock items: ${lowStock.joinToString(", ") { "${it.name} (${it.stockQuantity} left)" }}.",
                        )
                    }
                } else {
                    sb.appendLine("Inventory: no products added yet.")
                }
            } catch (e: Exception) {
                sb.appendLine("Inventory: data unavailable.")
            }
        }

        if (wantsFinance || needAll) {
            try {
                val transactions = transactionDao.getTransactionsList()
                if (transactions.isNotEmpty()) {
                    val totalIncome = transactions
                        .filter { it.type == TransactionType.INCOME }
                        .sumOf { it.amount }
                    val totalExpenses = transactions
                        .filter { it.type == TransactionType.EXPENSE }
                        .sumOf { it.amount }
                    val profit = totalIncome - totalExpenses

                    sb.appendLine(
                        "Finances: total income ${formatCurrency(totalIncome)}, total expenses " +
                            "${formatCurrency(totalExpenses)}, net profit ${formatCurrency(profit)}.",
                    )

                    if (wantsFinance) {
                        val topExpenses = transactions
                            .filter { it.type == TransactionType.EXPENSE }
                            .groupBy { it.description }
                            .mapValues { (_, txns) -> txns.sumOf { it.amount } }
                            .entries
                            .sortedByDescending { it.value }
                            .take(3)
                        if (topExpenses.isNotEmpty()) {
                            sb.appendLine(
                                "Top expense categories: ${topExpenses.joinToString(", ") { "${it.key} ${formatCurrency(it.value)}" }}.",
                            )
                        }
                    }
                } else {
                    sb.appendLine("Finances: no transactions recorded yet.")
                }
            } catch (e: Exception) {
                sb.appendLine("Finances: data unavailable.")
            }
        }

        return sb.toString()
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { this.contains(it) }

    /**
     * Build the user-turn content sent to the LLM.
     *
     * After the migration to LiteRT-LM (`com.google.ai.edge.litertlm`) the runtime applies
     * the model's chat template internally and injects the `<start_of_turn>user` /
     * `<end_of_turn>` markers itself. We must therefore pass **plain text only** — emitting
     * those markers manually used to make the model hallucinate fake follow-up turns.
     *
     * Conversation history is also not re-emitted: the underlying `Conversation` object in
     * [com.biasharaai.ai.GemmaService] keeps the KV cache warm across sends, so prior
     * messages are automatically in scope. [com.biasharaai.ai.GemmaService.resetSession]
     * clears it when the user taps "New chat".
     */
    private fun buildPrompt(businessContext: String, userQuestion: String): String {
        val langName = languageNameForPrompt(resolveUserLanguageCode())
        return buildString {
            append("Answer in $langName. ")
            if (businessContext.isNotBlank()) {
                append("Use these business facts as truth:\n\n")
                append(businessContext.trim())
                append("\n\n")
            }
            append("Question: ")
            append(userQuestion.trim())
        }
    }

    /**
     * Rule-based answer used when the LLM is unavailable, errors out, or returns blank.
     *
     * The goal: never echo the canned "Try asking about one of these" message. Always look at
     * the user's actual question and return something useful, even if it's just a quick
     * business summary.
     */
    private suspend fun generateFallbackResponse(question: String): String {
        val q = question.lowercase().trim()
        val L = localizedContext()
        return try {
            val products = productDao.getProductsList()
            val transactions = transactionDao.getTransactionsList()
            val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val totalExpenses = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

            // 1. Product-name lookup: "do we have milo", "do you have x", "is X in stock",
            //    "find X", "search X", "looking for X", "got any X", "any X".
            val needle = extractProductSearchTerm(q)
            val productLookup: String? = if (needle != null && products.isNotEmpty()) {
                val matches = products.filter { it.name.lowercase().contains(needle) }
                if (matches.isNotEmpty()) {
                    L.getString(
                        R.string.chat_fallback_product_found,
                        matches.joinToString(", ") {
                            "${it.name} (${it.stockQuantity} in stock at ${formatCurrency(it.price)})"
                        },
                    )
                } else {
                    L.getString(
                        R.string.chat_fallback_product_not_found,
                        needle,
                        products.joinToString(", ") { it.name }.take(120),
                    )
                }
            } else null

            productLookup ?: when {
                // Profit / income
                q.containsAny("profit", "revenue", "income", "faida", "mapato") -> {
                    L.getString(
                        R.string.chat_fallback_profit,
                        formatCurrency(totalIncome),
                        formatCurrency(totalExpenses),
                        formatCurrency(totalIncome - totalExpenses),
                    )
                }
                // Expenses
                q.containsAny("expense", "cost", "spend", "spending", "matumizi", "hasara") -> {
                    val topExpense = transactions.filter { it.type == TransactionType.EXPENSE }
                        .groupBy { it.description }.maxByOrNull { (_, v) -> v.sumOf { it.amount } }
                    L.getString(
                        R.string.chat_fallback_expenses,
                        formatCurrency(totalExpenses),
                        topExpense?.key ?: "—",
                    )
                }
                // Low stock
                q.containsAny("low stock", "reorder", "restock", "out of stock", "finished") -> {
                    val low = products.filter { it.stockQuantity < 10 }
                    if (low.isEmpty()) {
                        L.getString(R.string.chat_fallback_all_stocked)
                    } else {
                        L.getString(
                            R.string.chat_fallback_low_stock,
                            low.joinToString(", ") { "${it.name} (${it.stockQuantity})" },
                        )
                    }
                }
                // Inventory overview
                q.containsAny(
                    "stock", "inventory", "product", "products", "bidhaa", "kayayyaki",
                    "duka", "akiba",
                ) -> {
                    if (products.isEmpty()) {
                        L.getString(R.string.chat_fallback_no_products)
                    } else {
                        L.getString(
                            R.string.chat_fallback_inventory_summary,
                            products.size,
                            formatCurrency(products.sumOf { it.price * it.stockQuantity }),
                        )
                    }
                }
                // Sales / how much
                q.containsAny("sale", "sales", "sold", "selling", "uza", "uuzaji") -> {
                    val incomeTx = transactions.filter { it.type == TransactionType.INCOME }
                    if (incomeTx.isEmpty()) {
                        L.getString(R.string.chat_fallback_no_sales)
                    } else {
                        L.getString(
                            R.string.chat_fallback_sales_summary,
                            incomeTx.size,
                            formatCurrency(totalIncome),
                        )
                    }
                }
                // Default: useful overview instead of "try one of these"
                else -> buildBusinessOverview(products, transactions, totalIncome, totalExpenses)
            }
        } catch (e: Exception) {
            Log.w(TAG, "generateFallbackResponse failed", e)
            localizedContext().getString(R.string.chat_fallback_generic)
        }
    }

    /**
     * Try to extract a product name to look up from the user's question. Returns the lower-cased
     * search term, or `null` if the question doesn't look like a product lookup.
     */
    private fun extractProductSearchTerm(q: String): String? {
        val patterns = listOf(
            Regex("""do\s+(we|you|i)\s+(have|got|stock|carry|sell)\s+(?:any\s+|some\s+|the\s+)?(.+?)[?.!]*$"""),
            Regex("""(?:is|are)\s+there\s+(?:any\s+|some\s+|the\s+)?(.+?)\s+(?:in\s+stock|available|left)?[?.!]*$"""),
            Regex("""(?:find|search\s+for|search|looking\s+for|got\s+any|any|sell|stock)\s+(?:the\s+|some\s+|any\s+)?(.+?)[?.!]*$"""),
            Regex("""have\s+we\s+(?:got\s+|any\s+)?(.+?)[?.!]*$"""),
        )
        for (p in patterns) {
            val m = p.find(q) ?: continue
            // Last capture group is the name in every pattern above.
            val raw = m.groupValues.last().trim()
            if (raw.isBlank()) continue
            val cleaned = raw
                .removePrefix("a ").removePrefix("an ").removePrefix("the ")
                .removeSuffix(" in stock")
                .removeSuffix(" available")
                .removeSuffix(" left")
                .trim()
            if (cleaned.length in 2..40) return cleaned
        }
        return null
    }

    private fun buildBusinessOverview(
        products: List<com.biasharaai.data.local.db.Product>,
        transactions: List<com.biasharaai.data.local.db.Transaction>,
        totalIncome: Double,
        totalExpenses: Double,
    ): String {
        val L = localizedContext()
        val pieces = mutableListOf<String>()
        if (products.isNotEmpty()) {
            pieces += L.getString(
                R.string.chat_fallback_inventory_summary,
                products.size,
                formatCurrency(products.sumOf { it.price * it.stockQuantity }),
            )
        }
        if (transactions.isNotEmpty()) {
            pieces += L.getString(
                R.string.chat_fallback_profit,
                formatCurrency(totalIncome),
                formatCurrency(totalExpenses),
                formatCurrency(totalIncome - totalExpenses),
            )
        }
        return if (pieces.isEmpty()) {
            L.getString(R.string.chat_fallback_no_data)
        } else {
            pieces.joinToString("\n\n")
        }
    }

    private fun formatCurrency(amount: Double): String {
        val loc = Locale.forLanguageTag(resolveUserLanguageCode())
        return String.format(loc, "%,.0f", amount)
    }
}
