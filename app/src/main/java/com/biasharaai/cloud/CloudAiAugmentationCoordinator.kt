package com.biasharaai.cloud

import android.util.Log
import com.biasharaai.ai.GemmaOutputSanitizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class CloudAiAugmentationAnswer(
    val answer: String,
    val providerLabel: String,
    val model: String?,
    val usedInternet: Boolean,
    val sources: List<CloudAiGatewaySource>,
) {
    fun toChatText(): String = buildString {
        append(GemmaOutputSanitizer.finalAnswer(answer))
        if (sources.isNotEmpty()) {
            append("\n\nSources:")
            sources.forEach { source ->
                append("\n- ")
                append(source.title.ifBlank { source.url.ifBlank { "Source" } })
                if (source.url.isNotBlank()) {
                    append(" - ")
                    append(source.url)
                }
            }
        }
    }.trim()
}

@Singleton
class CloudAiAugmentationCoordinator @Inject constructor(
    private val settingsStore: CloudAiAugmentationSettingsStore,
    private val gatewayClient: CloudAiGatewayClient,
) {
    @Volatile
    private var cooldownGatewayUrl: String? = null

    @Volatile
    private var cooldownUntilMs: Long = 0L

    suspend fun maybeAnswer(
        userQuestion: String,
        languageName: String,
        visualSummary: String,
        localModelAvailable: Boolean,
        businessContextProvider: suspend () -> String,
    ): CloudAiAugmentationAnswer? = withContext(Dispatchers.IO) {
        val settings = settingsStore.load()
        val question = userQuestion.trim()
        if (!settings.enabled || question.length < 3) return@withContext null
        if (settings.gatewayUrl.isBlank()) return@withContext null
        if (isGatewayCoolingDown(settings.gatewayUrl)) return@withContext null

        val needsInternet = CloudAiQueryClassifier.needsInternet(question)
        val shouldUseCloud =
            settings.useForGeneralChat ||
                !localModelAvailable ||
                (settings.internetResearchEnabled && needsInternet)
        if (!shouldUseCloud) return@withContext null

        val businessContext = if (settings.sendBusinessContext) {
            runCatching { businessContextProvider().take(MAX_BUSINESS_CONTEXT_CHARS) }
                .getOrElse {
                    Log.w(TAG, "Could not build business context for cloud AI", it)
                    ""
                }
        } else {
            ""
        }

        try {
            val request = CloudAiGatewayRequest(
                gatewayUrl = settings.gatewayUrl,
                bearerToken = settingsStore.gatewayTokenOrNull(),
                userQuestion = question,
                languageName = languageName,
                allowInternetResearch = settings.internetResearchEnabled &&
                    (needsInternet || settings.useForGeneralChat),
                businessContext = businessContext.ifBlank { null },
                visualSummary = visualSummary.ifBlank { null },
                providerLabel = settings.providerLabel,
            )
            val result = withTimeoutOrNull(CLOUD_AI_TIMEOUT_MS) {
                gatewayClient.ask(request)
            } ?: run {
                recordGatewayFailure(settings.gatewayUrl, "timeout")
                return@withContext null
            }
            result.fold(
                onSuccess = {
                    clearGatewayFailure(settings.gatewayUrl)
                    CloudAiAugmentationAnswer(
                        answer = it.answer,
                        providerLabel = it.provider ?: settings.providerLabel,
                        model = it.model,
                        usedInternet = it.usedInternet,
                        sources = it.sources,
                    )
                },
                onFailure = {
                    recordGatewayFailure(settings.gatewayUrl, it.message ?: it.javaClass.simpleName)
                    Log.w(TAG, "Cloud AI augmentation failed; local path will continue", it)
                    null
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            Log.w(TAG, "Cloud AI augmentation crashed; local path will continue", t)
            null
        }
    }

    private fun isGatewayCoolingDown(gatewayUrl: String): Boolean {
        val until = cooldownUntilMs
        return cooldownGatewayUrl == gatewayUrl && until > System.currentTimeMillis()
    }

    private fun recordGatewayFailure(gatewayUrl: String, reason: String) {
        cooldownGatewayUrl = gatewayUrl
        cooldownUntilMs = System.currentTimeMillis() + GATEWAY_FAILURE_COOLDOWN_MS
        Log.w(TAG, "Cloud AI gateway cooling down after failure: $reason")
    }

    private fun clearGatewayFailure(gatewayUrl: String) {
        if (cooldownGatewayUrl == gatewayUrl) {
            cooldownGatewayUrl = null
            cooldownUntilMs = 0L
        }
    }

    private object CloudAiQueryClassifier {
        fun needsInternet(question: String): Boolean {
            val q = question.lowercase(Locale.ROOT)
            return INTERNET_KEYWORDS.any { q.contains(it) } ||
                MARKET_KEYWORDS.any { q.contains(it) } ||
                AFRICA_SME_KEYWORDS.any { q.contains(it) }
        }

        private val INTERNET_KEYWORDS = listOf(
            "latest",
            "current",
            "today",
            "news",
            "online",
            "internet",
            "research",
            "source",
            "sources",
            "market price",
            "exchange rate",
            "regulation",
            "tax rule",
            "law",
            "policy",
            "weather",
            "supplier",
            "competitor",
        )

        private val MARKET_KEYWORDS = listOf(
            "commodity",
            "wholesale price",
            "retail price",
            "inflation",
            "fuel price",
            "import duty",
            "mobile money fee",
            "mtn momo",
            "orange money fee",
            "mpesa fee",
        )

        private val AFRICA_SME_KEYWORDS = listOf(
            "cameroon",
            "nigeria",
            "kenya",
            "ghana",
            "tanzania",
            "uganda",
            "zambia",
            "xaf",
            "fcfa",
            "naira",
            "shilling",
        )
    }

    private companion object {
        private const val TAG = "CloudAiCoordinator"
        private const val MAX_BUSINESS_CONTEXT_CHARS = 12_000
        private const val CLOUD_AI_TIMEOUT_MS = 38_000L
        private const val GATEWAY_FAILURE_COOLDOWN_MS = 60_000L
    }
}
