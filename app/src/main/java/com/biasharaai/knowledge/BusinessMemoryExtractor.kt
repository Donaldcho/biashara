package com.biasharaai.knowledge

import android.util.Log
import com.biasharaai.agent.WeeklyReviewBuilder
import com.biasharaai.ai.EmbeddingEngine
import com.biasharaai.data.local.db.BusinessMemoryEntry
import com.biasharaai.data.local.db.BusinessMemoryEntryDao
import com.biasharaai.data.local.db.ChatSessionDao
import com.biasharaai.data.local.db.ChatSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts structured facts from two sources and persists them as [BusinessMemoryEntry] rows:
 *
 * 1. **Weekly KPI narrative** â€” converts [WeeklyReviewBuilder.WeeklyReviewStats] into a
 *    human-readable memory entry so the AI can compare across many weeks.
 *
 * 2. **Chat sessions** â€” scans recent user messages for goal and preference statements
 *    (regex heuristics) so the AI personalises advice to this specific owner.
 *
 * Entries are optionally embedded via [EmbeddingEngine] (MiniLM-L6-v2) for semantic
 * retrieval; keyword index used as fallback when the model is not loaded.
 */
@Singleton
class BusinessMemoryExtractor @Inject constructor(
    private val memoryDao: BusinessMemoryEntryDao,
    private val chatSessionDao: ChatSessionDao,
    private val embeddingEngine: EmbeddingEngine,
) {
    companion object {
        private const val TAG = "BusinessMemoryExtractor"
        private const val SOURCE_WEEKLY = "weekly_review"
        private const val SOURCE_CHAT = "chat_extract"
        private const val TYPE_KPI = "kpi_week"
        private const val TYPE_GOAL = "goal"
        private const val TYPE_PREFERENCE = "preference"
        private val KPI_TTL_MS = TimeUnit.DAYS.toMillis(90)

        private val GOAL_PATTERNS = listOf(
            Regex("""(?:i want|i'd like|i would like|my goal is|i'm trying|i plan|i need|we want|we need)\s+to\s+[^.!?\n]{10,150}""", RegexOption.IGNORE_CASE),
            Regex("""(?:i want|we want)\s+(?:to\s+)?(?:grow|expand|increase|focus|improve)\s+[^.!?\n]{5,120}""", RegexOption.IGNORE_CASE),
        )
        private val PREFERENCE_PATTERNS = listOf(
            Regex("""(?:i prefer|i usually|i always|i never|i like|i don't like|we prefer)\s+[^.!?\n]{10,150}""", RegexOption.IGNORE_CASE),
        )
    }

    // â”€â”€ Step 1: weekly KPI memory â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    suspend fun persistWeeklyKpiMemory(
        stats: WeeklyReviewBuilder.WeeklyReviewStats,
    ) = withContext(Dispatchers.IO) {
        // Keep weekly KPI entries until expiry so prompts can reason across multiple weeks.
        memoryDao.purgeExpired()

        val dateLabel = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            .format(Date(stats.weekStartMillis))

        val revenueChange = when {
            stats.lastWeekRevenue <= 0 -> "first week recorded"
            else -> {
                val pct = ((stats.weekRevenue - stats.lastWeekRevenue) / stats.lastWeekRevenue * 100).toInt()
                if (pct >= 0) "+$pct%" else "$pct%"
            }
        }

        val content = buildString {
            append("Week of $dateLabel: revenue ${stats.currencySymbol}${fmt(stats.weekRevenue)} ($revenueChange vs prior week). ")
            append("Products: ${stats.currencySymbol}${fmt(stats.productRevenue)}. ")
            if (stats.serviceSalesRevenue > 0) {
                append("Services: ${stats.currencySymbol}${fmt(stats.serviceSalesRevenue)}. ")
            }
            append("${stats.txCount} transactions. ")
            if (stats.topProduct != "â€”") {
                append("Top product: ${stats.topProduct} (${stats.topQty} units, ${stats.currencySymbol}${fmt(stats.topRevenue)}). ")
            }
            append("Best day: ${stats.bestDay}, best hour: ${stats.bestHour}:00. ")
            if (stats.newCustomers > 0) append("New customers: ${stats.newCustomers}. ")
            if (stats.debtCustomerCount > 0) {
                append("Credit outstanding: ${stats.currencySymbol}${fmt(stats.totalCredit)} across ${stats.debtCustomerCount} debtors. ")
            }
            stats.serviceStats?.takeIf { it.hasActivity() }?.let { s ->
                append("Top service: ${s.topServiceName} (${s.deliveryCount} sessions, ${s.utilisationPct}% utilisation). ")
            }
        }.trimEnd()

        val sourceKey = "$SOURCE_WEEKLY:${kotlin.math.abs(content.hashCode())}"
        memoryDao.deleteByTypeAndSource(TYPE_KPI, sourceKey)
        saveEntry(
            TYPE_KPI,
            content,
            sourceKey,
            expiresAt = System.currentTimeMillis() + KPI_TTL_MS,
            computeEmbedding = true,
        )
        Log.d(TAG, "Persisted weekly KPI memory for $dateLabel")
    }

    // â”€â”€ Step 4: chat goal / preference extraction â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    suspend fun extractFromRecentChats() = withContext(Dispatchers.IO) {
        val weekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)

        // Skip if we already extracted this week to avoid duplication
        val alreadyDone = memoryDao.countRecentByTypeAndSource(TYPE_GOAL, SOURCE_CHAT, weekAgo) +
            memoryDao.countRecentByTypeAndSource(TYPE_PREFERENCE, SOURCE_CHAT, weekAgo)
        if (alreadyDone >= 5) {
            Log.d(TAG, "Chat extraction skipped â€” already done this week ($alreadyDone entries)")
            return@withContext
        }

        var extracted = 0
        val sessions = chatSessionDao.listSessions()

        for (session in sessions) {
            val messages = chatSessionDao.messagesForSession(session.id)
            for (msg in messages) {
                if (msg.role != ChatSessionRepository.ROLE_USER) continue
                if (msg.createdAt < weekAgo) continue
                val text = msg.body.trim()
                if (text.length < 15) continue

                findMatch(GOAL_PATTERNS, text)?.let { snippet ->
                    saveEntry(TYPE_GOAL, "Owner stated: $snippet", SOURCE_CHAT)
                    extracted++
                }
                findMatch(PREFERENCE_PATTERNS, text)?.let { snippet ->
                    saveEntry(TYPE_PREFERENCE, "Owner preference: $snippet", SOURCE_CHAT)
                    extracted++
                }
            }
        }
        Log.d(TAG, "Extracted $extracted memory entries from recent chat sessions")
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private suspend fun saveEntry(
        type: String,
        content: String,
        source: String,
        expiresAt: Long? = null,
        computeEmbedding: Boolean = false,
    ) {
        val keywords = buildKeywords(content)
        // Only compute embeddings when explicitly requested (e.g., KPI memory).
        // Chat-extracted entries rely on keyword search to avoid competing with the
        // chat path for the shared TFLite interpreter during background processing.
        val embeddingBlob = if (computeEmbedding) {
            runCatching {
                if (embeddingEngine.isLoaded) embeddingEngine.serialize(embeddingEngine.embed(content)) else null
            }.getOrNull()
        } else null

        memoryDao.insert(
            BusinessMemoryEntry(
                type = type,
                content = content,
                keywords = keywords,
                embeddingBlob = embeddingBlob,
                source = source,
                expiresAt = expiresAt,
            ),
        )
    }

    private fun findMatch(patterns: List<Regex>, text: String): String? {
        for (pattern in patterns) {
            val snippet = pattern.find(text)?.value?.trim()?.take(220) ?: continue
            if (snippet.length >= 12) return snippet
        }
        return null
    }

    private fun buildKeywords(content: String): String =
        content.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
            .distinct()
            .take(30)
            .joinToString(" ")

    private fun fmt(v: Double) = String.format(Locale.US, "%.0f", v)
}

