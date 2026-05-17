package com.biasharaai.ui.chat

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.viewModelScope
import com.biasharaai.R
import com.biasharaai.ai.GemmaService
import com.biasharaai.ai.InferenceSettingsStore
import com.biasharaai.chat.query.ConversationalQueryLayer
import com.biasharaai.chat.skills.RemoteSkillManifestStore
import com.biasharaai.chat.skills.WikipediaSkillClient
import com.biasharaai.chat.vision.ImageChatAnalyzer
import com.biasharaai.data.ChatActiveSessionStore
import com.biasharaai.data.ChatQueryHistoryStore
import com.biasharaai.data.local.db.ChatMemoryRepository
import com.biasharaai.data.local.db.ChatSessionEntity
import com.biasharaai.data.local.db.ChatSessionRepository
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.data.local.db.SaleLineItemDao
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
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
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
    private val saleLineItemDao: SaleLineItemDao,
    private val conversationalQueryLayer: ConversationalQueryLayer,
    private val chatMemoryRepository: ChatMemoryRepository,
    private val chatSessionRepository: ChatSessionRepository,
    private val imageChatAnalyzer: ImageChatAnalyzer,
    private val wikipediaSkillClient: WikipediaSkillClient,
    private val remoteSkillManifestStore: RemoteSkillManifestStore,
    @ApplicationContext private val appContext: Context,
    private val inferenceSettingsStore: InferenceSettingsStore,
    private val chatQueryHistoryStore: ChatQueryHistoryStore,
) : BaseViewModel() {

    /**
     * After [startNewChat] / cold start, the first Gemma prompt includes a persisted transcript
     * snapshot so the model sees prior turns (LiteRT KV cache is empty). Cleared after the first
     * completed Gemma stream to avoid duplicating history in the prompt.
     */
    private var injectTranscriptIntoNextGemmaPrompt: Boolean = true

    companion object {
        private const val TAG = "ChatViewModel"
        private val TEMP_MESSAGE_ID = AtomicLong(-1L)

        /** Shown so the model knows which facts come from which local tables (no raw SQL). */
        private val DATABASE_SCHEMA_HINT = """
            Local business database (Room/SQLite — facts below are read-only snapshots):
            • products: name, price (list/selling price per unit), cost (your purchase cost per unit),
              stock_quantity, category, barcode_value.
            • transactions: type INCOME | EXPENSE | RETURN, amount, description, date (epoch ms), plus POS fields.
            • sale_line_items: POS line rows — product_id, product_name, unit_price, quantity, line_total, transaction_id.
            • customers: name, phone, notes, created_at, last_visit (address book + visit hints).
            • debts: unpaid amounts linked to a customer (credit tab); repayments update these rows.
            Use "cost" from products for purchase/cost questions; use "price" for shelf/list price.
            If a topic is not listed here, the app may answer from rules only ("not stored") unless facts appear below.
        """.trimIndent()

        /** Glue words when matching OCR tokens from photos to `products.name`. */
        private val CATALOG_OCR_STOPWORDS = setOf(
            "the", "and", "for", "you", "our", "your", "not", "are", "was", "but", "all", "any", "can",
            "has", "have", "with", "from", "its", "one", "two", "new", "now", "more", "high", "may", "way",
            "use", "net", "per", "each", "total", "ingredient", "ingredients",
        )
    }

    /** Kotlin stdlib `containsAny` is not on all AGP/Kotlin combos; keep a tiny local helper. */
    private fun String.containsAnyKeyword(vararg needles: String): Boolean =
        needles.any { contains(it, ignoreCase = true) }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    /** True while the model is actively generating; toggles the send button into a stop button. */
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private var generationJob: Job? = null

    private val _recentQueries = MutableStateFlow(chatQueryHistoryStore.getRecent())
    val recentQueries: StateFlow<List<String>> = _recentQueries.asStateFlow()

    private val _remoteSkillChips = MutableStateFlow(remoteSkillManifestStore.getCachedChips())
    val remoteSkillChips = _remoteSkillChips.asStateFlow()

    private val _sessions = MutableStateFlow<List<ChatSessionEntity>>(emptyList())
    val sessions: StateFlow<List<ChatSessionEntity>> = _sessions.asStateFlow()

    init {
        gemmaService.warmUp()
        viewModelScope.launch(Dispatchers.IO) {
            chatSessionRepository.observeSessions().collect { _sessions.value = it }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val sid = chatSessionRepository.ensureActiveSession(
                appContext.getString(R.string.chat_session_default_title),
            )
            val rows = chatSessionRepository.loadMessagesForUi(sid)
            withContext(Dispatchers.Main) { _messages.value = rows }
        }
    }

    fun getSavedSkillManifestUrl(): String = remoteSkillManifestStore.getManifestUrl()

    fun refreshSkillManifestFromUrl(url: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = remoteSkillManifestStore.fetchFromUrl(url).isSuccess
            if (ok) {
                _remoteSkillChips.value = remoteSkillManifestStore.getCachedChips()
            }
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    /** Open a past session from [com.biasharaai.ui.chat.ChatHistoryFragment]. */
    fun openSession(sessionId: Long) {
        stopGeneration()
        viewModelScope.launch(Dispatchers.IO) {
            chatSessionRepository.setActiveSession(sessionId)
            val rows = chatSessionRepository.loadMessagesForUi(sessionId)
            withContext(Dispatchers.Main) { _messages.value = rows }
            gemmaService.resetSession()
            injectTranscriptIntoNextGemmaPrompt = true
        }
    }

    fun deleteSession(sessionId: Long, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            chatSessionRepository.deleteSession(sessionId)
            val active = chatSessionRepository.ensureActiveSession(
                appContext.getString(R.string.chat_session_default_title),
            )
            val rows = chatSessionRepository.loadMessagesForUi(active)
            withContext(Dispatchers.Main) {
                _messages.value = rows
                onDone()
            }
            gemmaService.resetSession()
            injectTranscriptIntoNextGemmaPrompt = true
        }
    }

    private fun nextTempMessageId(): Long = TEMP_MESSAGE_ID.getAndDecrement()

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
     *
     * @param imageAbsolutePath Optional cached image path (Ask Image — ML Kit summary + text model).
     * @param wikipediaAugment When true, fetches English Wikipedia search snippets (network) for [userText].
     * @param remoteSkillPromptPrefix Optional prefix from URL-loaded skill manifest (Gallery-style).
     */
    fun sendMessage(
        userText: String,
        imageAbsolutePath: String? = null,
        wikipediaAugment: Boolean = false,
        remoteSkillPromptPrefix: String? = null,
    ) {
        val trimmedVisible = userText.trim()
        if (trimmedVisible.isBlank() && imageAbsolutePath.isNullOrBlank()) return
        if (_isGenerating.value) return

        val tempUserId = nextTempMessageId()
        val tempAsstId = nextTempMessageId()
        val photoPlaceholder = appContext.getString(R.string.chat_photo_message_placeholder)
        val bodyForDb = trimmedVisible.ifBlank { photoPlaceholder }
        val userMessage = ChatMessage(
            stableId = tempUserId,
            text = bodyForDb,
            isUser = true,
            imageUri = imageAbsolutePath,
        )
        _messages.value = _messages.value + userMessage

        generationJob = viewModelScope.launch(Dispatchers.IO) {
            _isThinking.value = true
            _isGenerating.value = true

            val placeholder = ChatMessage(stableId = tempAsstId, text = "", isUser = false)
            withContext(Dispatchers.Main.immediate) {
                _messages.value = _messages.value + placeholder
            }
            val placeholderIndex = _messages.value.lastIndex
            val streamed = StringBuilder()
            var sessionId = -1L

            try {
                sessionId = chatSessionRepository.ensureActiveSession(
                    appContext.getString(R.string.chat_session_default_title),
                )

                val visualSummary = if (!imageAbsolutePath.isNullOrBlank()) {
                    imageChatAnalyzer.describeImage(imageAbsolutePath)
                } else {
                    ""
                }

                val wikiBlock = if (wikipediaAugment && trimmedVisible.isNotBlank()) {
                    wikipediaSkillClient.fetchSearchContext(trimmedVisible) ?: ""
                } else {
                    ""
                }

                val transcriptForGemma = if (injectTranscriptIntoNextGemmaPrompt) {
                    chatSessionRepository.buildTranscriptBlockForPrompt(sessionId)
                } else {
                    ""
                }

                val effectiveForModel = buildString {
                    if (!remoteSkillPromptPrefix.isNullOrBlank()) {
                        append(remoteSkillPromptPrefix.trim())
                        append("\n\n")
                    }
                    if (visualSummary.isNotBlank()) {
                        append("VISUAL SUMMARY (on-device ML Kit — approximate): ")
                        append(visualSummary)
                        append("\n\n")
                    }
                    if (wikiBlock.isNotBlank()) {
                        append(wikiBlock)
                        append("\n\n")
                    }
                    append("User question: ")
                    append(
                        trimmedVisible.ifBlank {
                            "Describe what you see and suggest anything useful for a small retail shop."
                        },
                    )
                }

                val catalogQuery = mergeUserTextAndVisualForCatalog(trimmedVisible, visualSummary)
                val structuredQ = trimmedVisible.ifBlank { effectiveForModel }
                val fallbackQ = catalogQuery.ifBlank { structuredQ }

                if (trimmedVisible.isNotBlank()) {
                    chatQueryHistoryStore.recordQuery(trimmedVisible)
                    withContext(Dispatchers.Main.immediate) {
                        _recentQueries.value = chatQueryHistoryStore.getRecent()
                    }
                }

                val langName = languageNameForPrompt(resolveUserLanguageCode())
                chatMemoryRepository.captureMemoryFromUserMessage(structuredQ)

                val uid = chatSessionRepository.appendUserMessage(sessionId, bodyForDb, imageAbsolutePath)
                if (uid > 0L) {
                    withContext(Dispatchers.Main.immediate) {
                        _messages.value = _messages.value.map {
                            if (it.stableId == tempUserId) it.copy(stableId = uid) else it
                        }
                    }
                }
                chatSessionRepository.updateTitleFromFirstUserLine(sessionId, bodyForDb)

                val structuredPrimary = conversationalQueryLayer.tryStructuredAnswer(structuredQ, langName)
                val structured = structuredPrimary
                    ?: if (fallbackQ != structuredQ) {
                        conversationalQueryLayer.tryStructuredAnswer(fallbackQ, langName)
                    } else {
                        null
                    }
                if (structured != null) {
                    replacePlaceholder(placeholderIndex, structured)
                    persistAssistantAndFixRow(sessionId, placeholderIndex, structured)
                    injectTranscriptIntoNextGemmaPrompt = false
                    return@launch
                }

                if (!gemmaService.isAvailable) {
                    val fb = generateFallbackResponse(fallbackQ)
                    replacePlaceholder(placeholderIndex, fb)
                    persistAssistantAndFixRow(sessionId, placeholderIndex, fb)
                    injectTranscriptIntoNextGemmaPrompt = false
                    return@launch
                }

                val memoryBlock = chatMemoryRepository.buildMemoryBlockForPrompt()
                val context = buildBusinessContext(catalogQuery, visualSummary)
                val prompt = buildPrompt(
                    businessContext = context,
                    userQuestion = effectiveForModel,
                    transcriptBlock = transcriptForGemma,
                    memoryBlock = memoryBlock,
                )

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
                    } else {
                        totalMs
                    }
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
                    val fb = generateFallbackResponse(fallbackQ)
                    val msg = localizedContext().getString(R.string.chat_ai_timeout_with_summary, fb)
                    replacePlaceholder(placeholderIndex, msg)
                    persistAssistantAndFixRow(sessionId, placeholderIndex, msg)
                    injectTranscriptIntoNextGemmaPrompt = false
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
                    persistAssistantAndFixRow(sessionId, placeholderIndex, finalText)
                    injectTranscriptIntoNextGemmaPrompt = false
                    throw cancelled
                } catch (aiError: Exception) {
                    Log.w(TAG, "AI inference failed, falling back to rules", aiError)
                    val fb = generateFallbackResponse(fallbackQ)
                    replacePlaceholder(placeholderIndex, fb)
                    persistAssistantAndFixRow(sessionId, placeholderIndex, fb)
                    injectTranscriptIntoNextGemmaPrompt = false
                    return@launch
                }

                val modelText = streamed.toString().trim()
                if (modelText.isNotBlank()) {
                    persistAssistantAndFixRow(sessionId, placeholderIndex, modelText)
                } else {
                    Log.w(TAG, "AI returned blank, falling back to rules")
                    val fb = generateFallbackResponse(fallbackQ)
                    replacePlaceholder(placeholderIndex, fb)
                    persistAssistantAndFixRow(sessionId, placeholderIndex, fb)
                }
                injectTranscriptIntoNextGemmaPrompt = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Chat response failed completely", e)
                val err = localizedContext().getString(R.string.chat_error_generic)
                replacePlaceholder(placeholderIndex, err)
                if (sessionId > 0L) {
                    persistAssistantAndFixRow(sessionId, placeholderIndex, err)
                }
                injectTranscriptIntoNextGemmaPrompt = false
            } finally {
                _isThinking.value = false
                _isGenerating.value = false
            }
        }
    }

    private suspend fun persistAssistantAndFixRow(sessionId: Long, placeholderIndex: Int, body: String) {
        val text = body.trim().ifBlank {
            localizedContext().getString(R.string.chat_fallback_generic)
        }
        val aid = chatSessionRepository.appendAssistantMessage(sessionId, text)
        if (aid > 0L) {
            withContext(Dispatchers.Main.immediate) {
                val list = _messages.value.toMutableList()
                if (placeholderIndex in list.indices) {
                    list[placeholderIndex] = list[placeholderIndex].copy(stableId = aid)
                    _messages.value = list
                }
            }
        }
    }

    /** Persist helpful / not helpful for a persisted assistant bubble (`stableId` > 0). */
    fun submitAssistantFeedback(messageId: Long, vote: Int) {
        if (messageId <= 0L) return
        val normalized = when (vote) {
            1 -> 1
            -1 -> -1
            else -> return
        }
        viewModelScope.launch(Dispatchers.IO) {
            chatSessionRepository.setMessageFeedback(messageId, normalized)
            val sid = chatSessionRepository.getActiveSessionIdFromStore()
            if (sid != ChatActiveSessionStore.NO_SESSION) {
                val rows = chatSessionRepository.loadMessagesForUi(sid)
                withContext(Dispatchers.Main.immediate) { _messages.value = rows }
            }
        }
    }

    /** Stop generation in flight. The bubble keeps whatever text was already streamed. */
    fun stopGeneration() {
        if (!_isGenerating.value) return
        gemmaService.cancelGeneration()
        generationJob?.cancel()
    }

    /** Starts a new chat **session** (Gallery-style thread); previous sessions stay in history. */
    fun startNewChat() {
        stopGeneration()
        viewModelScope.launch(Dispatchers.IO) {
            val title = localizedContext().getString(R.string.chat_new_session_title)
            chatSessionRepository.createSession(title)
            withContext(Dispatchers.Main.immediate) {
                _messages.value = emptyList()
            }
            gemmaService.resetSession()
            injectTranscriptIntoNextGemmaPrompt = true
        }
    }

    /** Localized list of starter questions shown on the empty state. */
    fun suggestedPrompts(): List<String> {
        val L = localizedContext()
        return listOf(
            L.getString(R.string.chat_suggest_today_sales),
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
     *
     * When the question matches at least one inventory/finance/stock keyword ([needAll] is false),
     * we use a **slim** context for Gemma: smaller catalog cap, shorter “today sold” lines, and no
     * 7‑day top-seller paragraph — structured answers still bypass Gemma entirely when they hit first.
     */
    private suspend fun buildBusinessContext(userQuestion: String, visualSummary: String = ""): String {
        val q = userQuestion.lowercase()
        val hasImageContext = visualSummary.isNotBlank()
        val wantsInventory = q.containsAnyKeyword(
            "stock", "inventory", "product", "bidhaa", "kayayyaki", "hisa", "soko",
            "kiasi", "akiba", "duka",
        )
        val wantsLowStock = q.containsAnyKeyword("low stock", "reorder", "restock", "out of stock", "finished")
        val wantsFinance = q.containsAnyKeyword(
            "profit", "revenue", "income", "expense", "cost", "spend", "spending", "money",
            "cash", "faida", "mapato", "matumizi", "hasara",
            "sale", "sales", "sold", "selling", "uza", "uuzaji", "today", "leo", "hii leo",
        )
        val needAll = !(wantsInventory || wantsLowStock || wantsFinance)
        val slimGemmaContext = !needAll
        val maxCatalogChars = if (slimGemmaContext) 2_400 else 6_000
        val shortListProductCount = if (slimGemmaContext) 6 else 8
        /** Per-unit economics: must load products even if the only finance keyword is "cost"/"price". */
        val wantsUnitEconomics = q.containsAnyKeyword(
            "cost", "price", "how much", "bei", "gharama", "per unit", "per item",
            "one ", "unit cost", "selling price", "list price",
        )
        val productFocusRaw = extractProductFocusTerm(q) ?: extractProductSearchTerm(q)
        val includeProducts =
            wantsInventory || wantsLowStock || needAll || wantsUnitEconomics || productFocusRaw != null

        val sb = StringBuilder()
        sb.appendLine(DATABASE_SCHEMA_HINT)
        sb.appendLine()
        if (slimGemmaContext) {
            sb.appendLine("Context mode: focused facts only (question matched specific business keywords).")
            sb.appendLine()
        }

        if (includeProducts) {
            try {
                val products = productDao.getProductsList()
                if (products.isNotEmpty()) {
                    val productFocus = resolveCatalogSearchNeedle(products, productFocusRaw, visualSummary)
                    val totalStockValue = products.sumOf { it.price * it.stockQuantity }
                    val totalCostValue = products.sumOf { it.cost * it.stockQuantity }
                    val lowStock = products.filter { it.stockQuantity < 10 }
                    sb.appendLine(
                        "Inventory: ${products.size} products. Stock at list prices ~ " +
                            "${formatCurrency(totalStockValue)}; stock at your unit costs ~ " +
                            "${formatCurrency(totalCostValue)}.",
                    )
                    val matches = if (productFocus != null) {
                        products.filter { it.name.contains(productFocus, ignoreCase = true) }
                    } else {
                        emptyList()
                    }
                    if (matches.isNotEmpty()) {
                        sb.appendLine(
                            "Products matching \"$productFocus\": " +
                                matches.joinToString(" | ") { formatProductFactLine(it) } + ".",
                        )
                    } else if (productFocus != null) {
                        sb.appendLine(
                            "No product name contains \"$productFocus\". " +
                                "Similar names in catalog: " +
                                products.take(12).joinToString(", ") { it.name } + ".",
                        )
                    }
                    if (hasImageContext) {
                        sb.appendLine(
                            "Photo question: use the product name or label text inferred above; " +
                                "do not treat the business facts block as the user's wording.",
                        )
                    }
                    val detailedListing = when {
                        hasImageContext -> false
                        wantsUnitEconomics -> true
                        productFocus != null -> true
                        wantsInventory && !needAll -> true
                        else -> false
                    }
                    val productListing = if (detailedListing) {
                        val lines = products.joinToString(" | ") { formatProductFactLine(it) }
                        truncateContextLines(lines, maxChars = maxCatalogChars)
                    } else {
                        products
                            .sortedByDescending { it.price * it.stockQuantity }
                            .take(shortListProductCount)
                            .joinToString(", ") { formatProductFactLine(it) }
                    }
                    sb.appendLine("Product catalog: $productListing.")
                    if (lowStock.isNotEmpty()) {
                        val lowStockNote = if (slimGemmaContext) {
                            lowStock.take(6).joinToString(", ") { "${it.name} (${it.stockQuantity} left)" }
                        } else {
                            lowStock.joinToString(", ") { "${it.name} (${it.stockQuantity} left)" }
                        }
                        sb.appendLine("Low stock items: $lowStockNote.")
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
                        .filter { it.type == TransactionType.INCOME || it.type == TransactionType.RETURN }
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

        try {
            val today = loadTodaySalesSnapshot()
            sb.appendLine(
                "Today (${today.localDateIso}, device local calendar): recorded POS-style sales total " +
                    "${formatCurrency(today.total)} from ${today.count} income transaction(s) with this date.",
            )
        } catch (e: Exception) {
            sb.appendLine("Today sales summary: unavailable.")
        }
        try {
            val todaySoldMaxChars = if (slimGemmaContext) 900 else 2_000
            val todaySoldTake = if (slimGemmaContext) 15 else 40
            sb.appendLine(buildTodaySoldProductsFactLine(maxChars = todaySoldMaxChars, take = todaySoldTake))
        } catch (_: Exception) {
            sb.appendLine("Today sold products (detail): unavailable.")
        }

        if (mentionsCalendarToday(q) && (wantsFinance || wantsInventory || wantsLowStock)) {
            try {
                buildDailyPosBriefingLine()?.let { sb.appendLine(it) }
            } catch (_: Exception) {
            }
        }

        if (!slimGemmaContext) {
            try {
                buildLastSevenDaysTopSellersLine()?.let { sb.appendLine(it) }
            } catch (_: Exception) {
            }
        }

        return sb.toString()
    }

    private data class TodaySalesSnapshot(
        val localDateIso: String,
        val total: Double,
        val count: Int,
    )

    private fun todayLocalDateAndMillisRange(): Triple<LocalDate, Long, Long> {
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone)
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return Triple(day, start, end)
    }

    private fun mentionsCalendarToday(qLower: String): Boolean =
        qLower.contains("leo") ||
            qLower.contains("hii leo") ||
            Regex("""\btoday\b""").containsMatchIn(qLower)

    /** One-line yesterday vs today recorded income for “today” questions (POS hero slice). */
    private suspend fun buildDailyPosBriefingLine(): String? {
        val zone = ZoneId.systemDefault()
        val todayDay = LocalDate.now(zone)
        val (_, tStart, tEnd) = todayLocalDateAndMillisRange()
        val yDay = todayDay.minusDays(1)
        val yStart = yDay.atStartOfDay(zone).toInstant().toEpochMilli()
        val yEnd = yDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val todayTxs = transactionDao.getTransactionsBetween(tStart, tEnd)
            .filter { it.type == TransactionType.INCOME && it.amount > 0 }
        val yTxs = transactionDao.getTransactionsBetween(yStart, yEnd)
            .filter { it.type == TransactionType.INCOME && it.amount > 0 }
        val tSum = todayTxs.sumOf { it.amount }
        val ySum = yTxs.sumOf { it.amount }
        val detail = when {
            tSum <= 0.0 && ySum <= 0.0 -> "no positive income transactions on either day."
            ySum <= 0.0 -> "yesterday had none; today ${formatCurrency(tSum)} from ${todayTxs.size} transaction(s)."
            else -> {
                val deltaPct = (tSum - ySum) / ySum * 100.0
                val pctStr = String.format(Locale.US, "%.0f", deltaPct)
                "yesterday ${formatCurrency(ySum)} (${yTxs.size} tx) vs today ${formatCurrency(tSum)} (${todayTxs.size} tx); about $pctStr% vs yesterday."
            }
        }
        return "Same-day POS briefing (local calendar): $detail"
    }

    private suspend fun loadTodaySalesSnapshot(): TodaySalesSnapshot {
        val (day, start, end) = todayLocalDateAndMillisRange()
        val txs = transactionDao.getTransactionsBetween(start, end)
            .filter { it.type == TransactionType.INCOME && it.amount > 0 }
        return TodaySalesSnapshot(
            localDateIso = day.toString(),
            total = txs.sumOf { it.amount },
            count = txs.size,
        )
    }

    /**
     * Aggregates positive sale lines for today (POS) so the model can answer "what did we sell".
     */
    private suspend fun aggregateTodaySoldLines(): List<Triple<String, Int, Double>> {
        val (_, start, end) = todayLocalDateAndMillisRange()
        val lines = saleLineItemDao.saleLinesInPeriod(start, end)
        return lines
            .groupBy { it.productId }
            .map { (_, rows) ->
                val name = rows.first().productName
                val qty = rows.sumOf { it.quantity }
                val revenue = rows.sumOf { it.lineTotal }
                Triple(name, qty, revenue)
            }
            .sortedByDescending { it.second }
    }

    private suspend fun buildTodaySoldProductsFactLine(maxChars: Int = 2_000, take: Int = 40): String {
        val agg = try {
            aggregateTodaySoldLines()
        } catch (_: Exception) {
            return "Today sold products (detail): unavailable."
        }
        if (agg.isEmpty()) {
            return "Today sold products (from POS line items): none recorded for this date."
        }
        val parts = agg.take(take).map { (name, qty, rev) ->
            "$name × $qty (${formatCurrency(rev)})"
        }
        var text = "Today sold products (each name × units sold today, then line revenue): " +
            parts.joinToString("; ") + "."
        if (text.length > maxChars) {
            text = text.take(maxChars) + "…"
        }
        return text
    }

    private suspend fun formatTodaySoldItemsBulletList(): String {
        val agg = try {
            aggregateTodaySoldLines()
        } catch (_: Exception) {
            return ""
        }
        if (agg.isEmpty()) return ""
        return agg.joinToString("\n") { (name, qty, rev) ->
            "• $name × $qty (${formatCurrency(rev)})"
        }
    }

    private suspend fun buildLastSevenDaysTopSellersLine(): String? {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val start = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val lines = try {
            saleLineItemDao.saleLinesInPeriod(start, end)
        } catch (_: Exception) {
            return null
        }
        if (lines.isEmpty()) return null
        val top = lines
            .groupBy { it.productId }
            .map { (_, rows) -> rows.first().productName to rows.sumOf { it.quantity } }
            .sortedByDescending { it.second }
            .take(5)
        return "Best-selling products last 7 days by units: " +
            top.joinToString(", ") { "${it.first} (${it.second})" } + "."
    }

    private fun formatProductFactLine(p: Product): String =
        "${p.name}: stock ${p.stockQuantity}, list ${formatCurrency(p.price)}, unit cost ${formatCurrency(p.cost)}"

    private fun truncateContextLines(text: String, maxChars: Int): String =
        if (text.length <= maxChars) text else text.take(maxChars) + "…"

    private fun mergeUserTextAndVisualForCatalog(userText: String, visualSummary: String): String {
        val u = userText.trim()
        val v = visualSummary.trim()
        return when {
            v.isEmpty() -> u
            u.isEmpty() -> v
            else -> "$u\n\n$v"
        }
    }

    private fun extractQuotedOcrFromVisualSummary(visualSummary: String): String {
        val key = "Text visible in the image: \""
        val start = visualSummary.indexOf(key)
        if (start < 0) return ""
        val from = start + key.length
        val end = visualSummary.indexOf('"', from)
        if (end <= from) return ""
        return visualSummary.substring(from, end)
    }

    private fun extractLabelTokensFromVisualSummary(visualSummary: String): List<String> {
        val key = "Detected items/scene (on-device): "
        val i = visualSummary.indexOf(key)
        if (i < 0) return emptyList()
        val rest = visualSummary.substring(i + key.length)
        val end = rest.indexOf('.')
        val slice = if (end >= 0) rest.substring(0, end) else rest
        return slice.split(',').map { it.trim() }.filter { it.length >= 3 }
    }

    private fun tokenizeOcrHintsForCatalog(ocr: String): List<String> =
        ocr.split(Regex("[^A-Za-z0-9]+"))
            .map { it.trim() }
            .filter { it.length >= 3 }
            .distinct()
            .sortedByDescending { it.length }
            .filter { it.lowercase(Locale.getDefault()) !in CATALOG_OCR_STOPWORDS }

    private fun isDeicticInventoryPhrase(needle: String): Boolean {
        val t = needle.lowercase(Locale.getDefault()).trim()
        if (t.length < 6) return false
        val hasDeictic = Regex("""\b(this|that|these|those)\b""").containsMatchIn(t)
        val hasGeneric = Regex("""\b(product|products|item|items|thing|things)\b""").containsMatchIn(t)
        return hasDeictic && hasGeneric
    }

    /**
     * Maps "this product" + photo OCR to a real catalog substring (e.g. Milo) so we do not dump
     * the entire inventory into the on-device LLM prompt.
     */
    private fun resolveCatalogSearchNeedle(
        products: List<Product>,
        rawFocus: String?,
        visualSummary: String,
    ): String? {
        val cleanedRaw = rawFocus?.trim()?.takeIf { it.length >= 2 }
        val nonDeictic = cleanedRaw?.takeUnless { isDeicticInventoryPhrase(it) }
        if (nonDeictic != null) {
            val n = nonDeictic.trim()
            if (products.any { it.name.contains(n, ignoreCase = true) }) {
                return n.lowercase(Locale.getDefault())
            }
        }
        if (visualSummary.isBlank() || products.isEmpty()) return null
        val ocr = extractQuotedOcrFromVisualSummary(visualSummary)
        if (ocr.isBlank()) return null
        for (tok in tokenizeOcrHintsForCatalog(ocr)) {
            val tl = tok.lowercase(Locale.getDefault())
            if (products.any { it.name.contains(tok, ignoreCase = true) }) {
                return tl
            }
        }
        for (tok in extractLabelTokensFromVisualSummary(visualSummary)) {
            val tl = tok.lowercase(Locale.getDefault())
            if (tl in CATALOG_OCR_STOPWORDS) continue
            if (products.any { it.name.contains(tok, ignoreCase = true) }) {
                return tl
            }
        }
        return null
    }

    /**
     * Extracts a product name or fragment from cost/price questions, e.g. "cost of one wipe".
     */
    private fun extractProductFocusTerm(q: String): String? {
        val ql = q.lowercase().trim().removeSuffix("?").removeSuffix(".").trim()
        val patterns = listOf(
            Regex(
                """(?:what\s+is|what's|whats)\s+the\s+(?:cost|price)\s+of\s+(?:a|an|the|one|1|each)?\s*(.+)$""",
            ),
            Regex("""(?:cost|price)\s+of\s+(?:a|an|the|one|1|each)?\s*(.+)$"""),
            Regex(
                """how\s+much\s+(?:is|does)\s+(?:a|an|the|one|1)?\s*(.+?)(?:\s+cost|\s+sell|\s+go\s+for)?$""",
            ),
            Regex("""how\s+much\s+for\s+(?:a|an|the|one|1)?\s*(.+)$"""),
        )
        for (p in patterns) {
            val m = p.find(ql) ?: continue
            val raw = m.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim() ?: continue
            val cleaned = raw
                .removeSuffix("?")
                .removeSuffix(".")
                .trim()
                .removePrefix("a ")
                .removePrefix("an ")
                .removePrefix("the ")
                .removePrefix("one ")
                .removePrefix("1 ")
                .trim()
            if (cleaned.length in 2..50) return cleaned
        }
        return null
    }

    /**
     * Build the user-turn content sent to the LLM.
     *
     * After the migration to LiteRT-LM (`com.google.ai.edge.litertlm`) the runtime applies
     * the model's chat template internally and injects the `<start_of_turn>user` /
     * `<end_of_turn>` markers itself. We must therefore pass **plain text only** — emitting
     * those markers manually used to make the model hallucinate fake follow-up turns.
     *
     * The underlying `Conversation` keeps a KV cache across sends in one session. We also inject
     * a short persisted transcript on the **first** Gemma prompt after a new chat or process start
     * so history survives restarts; [com.biasharaai.ai.GemmaService.resetSession] clears the cache
     * when the user taps "New chat" (transcript rows are cleared in [startNewChat]).
     */
    private fun buildPrompt(
        businessContext: String,
        userQuestion: String,
        transcriptBlock: String = "",
        memoryBlock: String = "",
    ): String {
        val langName = languageNameForPrompt(resolveUserLanguageCode())
        return buildString {
            append("Answer in $langName. ")
            append(
                "When the user asks about today or today's sales, use the fact line that starts with " +
                    "\"Today (\" and those numbers (not lifetime totals). ",
            )
            append(
                "When they ask which products or items were sold today, list them from the fact line " +
                    "that starts with \"Today sold products\" using the exact names and quantities there. ",
            )
            append(
                "For \"what sells best\" or popularity, use the \"Best-selling products last 7 days\" line if present. ",
            )
            append(
                "For cost, price, or \"how much\" about a product, use the Product catalog lines: " +
                    "\"unit cost\" is your purchase cost per unit; \"list price\" is the selling price. ",
            )
            if (transcriptBlock.isNotBlank()) {
                append(transcriptBlock.trim())
                append("\n")
            }
            if (memoryBlock.isNotBlank()) {
                append(memoryBlock.trim())
                append("\n")
            }
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
            val totalIncome = transactions
                .filter { it.type == TransactionType.INCOME || it.type == TransactionType.RETURN }
                .sumOf { it.amount }
            val totalExpenses = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

            // 1. Product-name lookup: "do we have milo", "do you have x", "is X in stock",
            //    "find X", "search X", "looking for X", "got any X", "any X".
            val visualPart = question.substringAfter("\n\n", "")
            val needleRaw = extractProductFocusTerm(q) ?: extractProductSearchTerm(q)
            val needle = if (products.isNotEmpty()) {
                resolveCatalogSearchNeedle(products, needleRaw, visualPart)
            } else {
                null
            }
            val productLookup: String? = if (needle != null && products.isNotEmpty()) {
                val matches = products.filter { it.name.contains(needle, ignoreCase = true) }
                if (matches.isNotEmpty()) {
                    L.getString(
                        R.string.chat_fallback_product_found,
                        matches.joinToString(", ") {
                            "${it.name} (${it.stockQuantity} in stock, list ${formatCurrency(it.price)}, " +
                                "cost ${formatCurrency(it.cost)})"
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
                // Today's sold items / products (needs line-level POS data)
                q.containsAnyKeyword("today", "leo", "hii leo") &&
                    q.containsAnyKeyword(
                        "item", "items", "product", "products", "sold", "sell", "selling",
                        "list", "which", "what", "name", "anything", "detail", "breakdown",
                        "bidhaa", "tuliuza", "nini",
                    ) -> {
                    val snap = loadTodaySalesSnapshot()
                    val bullets = formatTodaySoldItemsBulletList()
                    if (bullets.isBlank()) {
                        L.getString(R.string.chat_fallback_today_items_none, snap.localDateIso)
                    } else {
                        val header = L.getString(
                            R.string.chat_fallback_today_sales,
                            snap.localDateIso,
                            formatCurrency(snap.total),
                            snap.count,
                        )
                        "$header\n\n${L.getString(R.string.chat_fallback_today_items_intro)}\n$bullets"
                    }
                }
                // Profit / income
                q.containsAnyKeyword("profit", "revenue", "income", "faida", "mapato") -> {
                    L.getString(
                        R.string.chat_fallback_profit,
                        formatCurrency(totalIncome),
                        formatCurrency(totalExpenses),
                        formatCurrency(totalIncome - totalExpenses),
                    )
                }
                // Expenses
                q.containsAnyKeyword("expense", "cost", "spend", "spending", "matumizi", "hasara") -> {
                    val topExpense = transactions.filter { it.type == TransactionType.EXPENSE }
                        .groupBy { it.description }.maxByOrNull { (_, v) -> v.sumOf { it.amount } }
                    L.getString(
                        R.string.chat_fallback_expenses,
                        formatCurrency(totalExpenses),
                        topExpense?.key ?: "—",
                    )
                }
                // Low stock
                q.containsAnyKeyword("low stock", "reorder", "restock", "out of stock", "finished") -> {
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
                q.containsAnyKeyword(
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
                // Today's sales (explicit)
                q.containsAnyKeyword("today", "leo", "hii leo") &&
                    q.containsAnyKeyword("sale", "sales", "sold", "selling", "uza", "uuzaji", "mapato") -> {
                    val snap = loadTodaySalesSnapshot()
                    if (snap.count == 0) {
                        L.getString(R.string.chat_fallback_today_sales_none, snap.localDateIso)
                    } else {
                        val base = L.getString(
                            R.string.chat_fallback_today_sales,
                            snap.localDateIso,
                            formatCurrency(snap.total),
                            snap.count,
                        )
                        val bullets = formatTodaySoldItemsBulletList()
                        if (bullets.isNotBlank()) {
                            "$base\n\n${L.getString(R.string.chat_fallback_today_items_intro)}\n$bullets"
                        } else {
                            base
                        }
                    }
                }
                // Sales / how much (all time)
                q.containsAnyKeyword("sale", "sales", "sold", "selling", "uza", "uuzaji") -> {
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
