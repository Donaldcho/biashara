package com.biasharaai.ai

import android.content.Context
import android.util.Log
import com.biasharaai.data.local.db.Transaction
import com.biasharaai.data.local.db.TransactionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device cash flow analysis.
 *
 * **Mid-range devices (typically [CapabilityTier.PARTIAL_AI])** use a fast rules-only
 * narrative so the shared LLM stays available for Chat and other interactive flows.
 *
 * **High-end devices ([CapabilityTier.FULL_AI])** can optionally enhance the narrative
 * with Gemma; callers should show [generateRulesInsights] first, then call
 * [tryEnhanceInsightsWithAi] so the UI is responsive while the model runs.
 */
@Singleton
class CashFlowAnalyzer @Inject constructor(
    private val gemmaService: GemmaService,
    @ApplicationContext private val appContext: Context,
    private val modelDownloadManager: ModelDownloadManager,
) {
    private fun effectiveTier(): CapabilityTier =
        DeviceCapabilityChecker.evaluate(
            appContext,
            modelPresentOnDisk = modelDownloadManager.isModelDownloaded,
        ).tier
    companion object {
        private const val TAG = "CashFlowAnalyzer"

        /** Language display names used in the prompt. */
        private val LANGUAGE_NAMES = mapOf(
            "en" to "English",
            "sw" to "Swahili",
            "ha" to "Hausa",
            "yo" to "Yoruba",
            "am" to "Amharic",
        )
    }

    private data class CashFlowStats(
        val totalIncome: Double,
        val totalExpenses: Double,
        val topExpenses: List<Map.Entry<String, Double>>,
    )

    private fun aggregate(transactions: List<Transaction>): CashFlowStats {
        val totalIncome = transactions
            .filter { it.type == TransactionType.INCOME }
            .sumOf { it.amount }

        val totalExpenses = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }

        val topExpenses = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.description.trim().lowercase() }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }
            .entries
            .sortedByDescending { it.value }
            .take(3)

        return CashFlowStats(totalIncome, totalExpenses, topExpenses)
    }

    /**
     * Fast, offline-friendly summary — does not use the LLM.
     */
    fun generateRulesInsights(
        transactions: List<Transaction>,
        periodLabel: String,
    ): String {
        val stats = aggregate(transactions)
        val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
        return generateWithRules(
            stats.totalIncome,
            stats.totalExpenses,
            stats.topExpenses,
            formatter,
            periodLabel,
        )
    }

    /**
     * Optional LLM polish for insights — only on [CapabilityTier.FULL_AI] when the model is present.
     * Returns `null` if skipped, failed, or not applicable (caller keeps rules text).
     */
    suspend fun tryEnhanceInsightsWithAi(
        transactions: List<Transaction>,
        language: String = Locale.getDefault().language,
        periodLabel: String = "this period",
    ): String? {
        if (effectiveTier() != CapabilityTier.FULL_AI || !gemmaService.isAvailable) {
            return null
        }
        val stats = aggregate(transactions)
        val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
        return try {
            generateAiNarrative(
                stats.totalIncome,
                stats.totalExpenses,
                stats.topExpenses,
                formatter,
                language,
                periodLabel,
            )
        } catch (e: Exception) {
            Log.w(TAG, "AI insight enhancement failed", e)
            null
        }
    }

    /**
     * Generate financial insights for the given transactions.
     *
     * On [CapabilityTier.FULL_AI] with a downloaded model, uses Gemma; otherwise rules only.
     */
    suspend fun generateInsights(
        transactions: List<Transaction>,
        language: String = Locale.getDefault().language,
        periodLabel: String = "this period",
    ): String {
        val rules = generateRulesInsights(transactions, periodLabel)
        if (effectiveTier() != CapabilityTier.FULL_AI || !gemmaService.isAvailable) {
            return rules
        }
        return try {
            val stats = aggregate(transactions)
            val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
            generateAiNarrative(
                stats.totalIncome,
                stats.totalExpenses,
                stats.topExpenses,
                formatter,
                language,
                periodLabel,
            )
        } catch (e: Exception) {
            Log.w(TAG, "AI insights failed, falling back to rules", e)
            rules
        }
    }

    private suspend fun generateAiNarrative(
        totalIncome: Double,
        totalExpenses: Double,
        topExpenses: List<Map.Entry<String, Double>>,
        formatter: NumberFormat,
        language: String,
        periodLabel: String,
    ): String = withContext(Dispatchers.IO) {
        val langName = LANGUAGE_NAMES[language] ?: "English"

        val topExpenseLines = topExpenses.joinToString(", ") { (cat, amt) ->
            "${cat.replaceFirstChar { it.uppercase() }}: ${formatter.format(amt)}"
        }.ifBlank { "None" }

        val innerPrompt = buildString {
            append("You are a friendly business advisor for a small African business. ")
            append("Respond in $langName. Keep your response under 120 words. Be direct.\n\n")
            append("Financial summary for $periodLabel:\n")
            append("Total income: ${formatter.format(totalIncome)}. ")
            append("Total expenses: ${formatter.format(totalExpenses)}.\n")
            append("Top expenses: $topExpenseLines.\n\n")
            append("Provide 2-3 short, actionable insights.")
        }
        // LiteRT-LM applies the model's chat template internally; pass plain text only.
        val response = gemmaService.generateResponse(innerPrompt)
        Log.d(TAG, "AI insights generated (${response.length} chars)")
        response.trim()
    }

    private fun generateWithRules(
        totalIncome: Double,
        totalExpenses: Double,
        topExpenses: List<Map.Entry<String, Double>>,
        formatter: NumberFormat,
        periodLabel: String,
    ): String {
        val netCashFlow = totalIncome - totalExpenses
        val netLabel = if (netCashFlow >= 0) "Profit" else "Loss"

        val sb = StringBuilder()
        sb.appendLine("📊 Financial Summary — $periodLabel")
        sb.appendLine()
        sb.appendLine("Income: ${formatter.format(totalIncome)}")
        sb.appendLine("Expenses: ${formatter.format(totalExpenses)}")
        sb.appendLine("$netLabel: ${formatter.format(kotlin.math.abs(netCashFlow))}")

        if (topExpenses.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Top expenses:")
            topExpenses.forEach { (category, amount) ->
                sb.appendLine("  • ${category.replaceFirstChar { it.uppercase() }}: ${formatter.format(amount)}")
            }
        }

        if (totalExpenses > totalIncome && totalIncome > 0) {
            sb.appendLine()
            sb.appendLine("⚠ Your expenses exceed your income. Consider reviewing your top spending categories.")
        } else if (totalIncome > 0) {
            val savingsRate = ((totalIncome - totalExpenses) / totalIncome * 100).toInt()
            sb.appendLine()
            sb.appendLine("✅ You're saving $savingsRate% of your income. Keep it up!")
        }

        return sb.toString()
    }
}
