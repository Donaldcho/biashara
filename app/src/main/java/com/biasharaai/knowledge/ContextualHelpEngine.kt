package com.biasharaai.knowledge

import com.biasharaai.data.local.db.MasteryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Maps the current screen/context to the most relevant help topic and lesson. */
@Singleton
class ContextualHelpEngine @Inject constructor(
    private val masteryRepository: FeatureMasteryRepository,
    private val lessonLibrary: LessonLibrary,
    private val retriever: KnowledgeRetriever,
) {
    data class ContextualHelp(
        val featureId: String,
        val lessonId: String?,
        val helpTitle: String,
        val helpSnippet: String,
        val shouldShowProactively: Boolean,
    )

    /** Returns help relevant to the current screen context. */
    suspend fun helpForScreen(screenId: String, languageCode: String = "en"): ContextualHelp? =
        withContext(Dispatchers.IO) {
            val featureId = screenToFeatureId(screenId) ?: return@withContext null
            val mastery = masteryRepository.getMasteryLevel(featureId)
            val lesson = if (mastery != MasteryLevel.MASTERED) {
                lessonLibrary.nextLessonForMastery(
                    com.biasharaai.data.local.db.FeatureMastery(
                        featureId = featureId,
                        masteryLevel = mastery.name,
                        firstSeenAt = 0L,
                        lastPracticedAt = 0L,
                    ),
                    languageCode,
                )
            } else null

            val chunks = retriever.retrieve(query = featureId.replace('_', ' '), languageCode = languageCode, topK = 1)
            val snippet = chunks.firstOrNull()?.chunk?.contentText?.take(200) ?: helpTextForFeature(featureId)

            ContextualHelp(
                featureId = featureId,
                lessonId = lesson?.lessonId,
                helpTitle = featureDisplayName(featureId),
                helpSnippet = snippet,
                shouldShowProactively = mastery == MasteryLevel.UNDISCOVERED || mastery == MasteryLevel.DISCOVERED,
            )
        }

    /** Returns help snippets matching a free-text query from the user. */
    suspend fun searchHelp(query: String, languageCode: String = "en"): List<RetrievedChunk> =
        withContext(Dispatchers.IO) {
            retriever.retrieve(query = query, languageCode = languageCode, topK = 5)
        }

    private fun screenToFeatureId(screenId: String): String? = when (screenId) {
        "inventory", "product_list" -> "add_product"
        "add_edit_product" -> "add_product"
        "pos", "pos_cart" -> "pos_sale"
        "transactions" -> "transactions"
        "customers" -> "customers"
        "debts" -> "debts"
        "chat" -> "chat"
        "agent_feed" -> "agent_feed"
        "agent_settings" -> "agent_settings"
        "settings" -> "settings"
        "voice_settings" -> "voice_settings"
        "model_settings" -> "model_settings"
        "ledger" -> "ledger"
        "cash_count" -> "cash_count"
        "receipt_scan" -> "receipt_scan"
        else -> null
    }

    private fun featureDisplayName(featureId: String): String = when (featureId) {
        "add_product" -> "Adding Products"
        "pos_sale" -> "Making a Sale"
        "customers" -> "Customer Management"
        "debts" -> "Debt Tracking"
        "transactions" -> "Transaction History"
        "agent_feed" -> "AI Agent Feed"
        "chat" -> "AI Chat"
        "voice_commands" -> "Voice Commands"
        "ledger" -> "Business Ledger"
        "cash_count" -> "Cash Count"
        "settings" -> "App Settings"
        else -> featureId.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    private fun helpTextForFeature(featureId: String): String = when (featureId) {
        "add_product" -> "Tap '+' in Inventory to add a new product with name, price, and stock."
        "pos_sale" -> "Open POS, add items to the cart, then tap Charge to complete the sale."
        "customers" -> "Manage your customer database and track who owes you money."
        "chat" -> "Ask the AI assistant questions about your business in plain language."
        "agent_feed" -> "The AI agent monitors your business and sends you proactive suggestions."
        else -> "Tap the help icon for step-by-step guidance."
    }
}
