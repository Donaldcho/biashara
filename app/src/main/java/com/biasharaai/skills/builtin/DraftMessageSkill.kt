package com.biasharaai.skills.builtin

import android.content.Context
import com.biasharaai.agent.AgentMutex
import com.biasharaai.ai.ActiveModelStore
import com.biasharaai.locale.LanguagePreferences
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillArgsParser
import com.biasharaai.skills.SkillResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** X6 — Draft SMS / short message (Gemma when available; template fallback). */
@Singleton
class DraftMessageSkill @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activeModelStore: ActiveModelStore,
    private val agentMutex: AgentMutex,
) : BiasharaSkill {
    override val id: String = ID
    override val displayName: String = "Draft message"
    override val parameterSchemaJson: String =
        """{"type":"object","properties":{"purpose":{"type":"string"},"customerName":{"type":"string"},"context":{"type":"string"},"maxWords":{"type":"integer"}},"required":["purpose","customerName"]}"""

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        val args = SkillArgsParser.parseObject(argumentsJson).getOrElse {
            return@withContext SkillResult.Failure("INVALID_ARGS", it.message ?: "Invalid JSON")
        }
        val purpose = SkillArgsParser.stringArg(args, "purpose")
            ?: return@withContext SkillResult.Failure("INVALID_ARGS", "purpose is required")
        val customerName = SkillArgsParser.stringArg(args, "customerName")
            ?: return@withContext SkillResult.Failure("INVALID_ARGS", "customerName is required")
        val extraContext = SkillArgsParser.stringArg(args, "context").orEmpty()
        val maxWords = SkillArgsParser.intArg(args, "maxWords", default = 60, min = 20, max = 120)

        val language = languageNameForPrompt()
        val body = if (activeModelStore.isAvailable) {
            val prompt = buildPrompt(purpose, customerName, extraContext, maxWords, language)
            runCatching {
                agentMutex.mutex.withLock {
                    activeModelStore.sendPrompt(prompt).trim()
                }
            }.getOrElse { templateMessage(purpose, customerName) }
        } else {
            templateMessage(purpose, customerName)
        }

        if (body.isBlank()) {
            return@withContext SkillResult.Failure("EMPTY", "Message draft was empty.")
        }

        SkillResult.successMap(
            mapOf(
                "purpose" to purpose,
                "customerName" to customerName,
                "draftMessage" to body,
                "usedAi" to activeModelStore.isAvailable,
            ),
            summary = body.take(80),
        )
    }

    private fun buildPrompt(
        purpose: String,
        customerName: String,
        context: String,
        maxWords: Int,
        language: String,
    ): String = buildString {
        append("Write a short, polite SMS in $language from a small shop owner to customer ")
        append(customerName).append(". Purpose: ").append(purpose).append(". ")
        if (context.isNotBlank()) append("Context: ").append(context).append(". ")
        append("Under $maxWords words. Friendly tone, not legal language. Reply with only the message text.")
    }.trim()

    private fun templateMessage(purpose: String, customerName: String): String = when {
        purpose.contains("debt", ignoreCase = true) ->
            "Hi $customerName, this is a friendly reminder about your outstanding balance. Please visit when you can. Thank you."
        purpose.contains("visit", ignoreCase = true) ->
            "Hi $customerName, we have not seen you in a while and would love to serve you again!"
        else ->
            "Hi $customerName, thank you for shopping with us. We appreciate your business."
    }

    private fun languageNameForPrompt(): String {
        val tag = LanguagePreferences.getPersistedLocaleTag(context)
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

    companion object {
        const val ID = "draft_message"
    }
}
