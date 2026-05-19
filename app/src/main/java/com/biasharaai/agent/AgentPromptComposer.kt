package com.biasharaai.agent

import com.biasharaai.data.local.db.BusinessMemoryEntryDao
import com.biasharaai.profile.BusinessProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Prepends structured business context and learned memory to agent system and legacy prompts. */
@Singleton
class AgentPromptComposer @Inject constructor(
    private val businessProfileRepository: BusinessProfileRepository,
    private val businessMemoryEntryDao: BusinessMemoryEntryDao,
) {

    suspend fun enrichSystemPrompt(base: String): String =
        prependContext(base)

    suspend fun enrichLegacyPrompt(base: String): String =
        prependContext(base)

    private suspend fun prependContext(base: String): String {
        val sections = buildList {
            businessProfileRepository.buildAgentContextHeader()?.let { add(it) }
            buildBusinessMemoryHeader()?.let { add(it) }
        }
        if (sections.isEmpty()) return base
        return sections.joinToString("\n\n") + "\n\n" + base
    }

    private suspend fun buildBusinessMemoryHeader(): String? {
        val entries = businessMemoryEntryDao.getActive()
            .sortedWith(
                compareByDescending<com.biasharaai.data.local.db.BusinessMemoryEntry> { memoryPriority(it.type) }
                    .thenByDescending { it.createdAt },
            )
            .take(MAX_MEMORY_ENTRIES)

        if (entries.isEmpty()) return null

        return buildString {
            appendLine("Business memory from prior weeks and owner chats:")
            entries.forEach { entry ->
                append("- ")
                append(entry.content.take(MAX_MEMORY_LINE_CHARS))
                appendLine()
            }
            append("Use this memory only when relevant; do not claim it as a guarantee.")
        }.trimEnd()
    }

    private fun memoryPriority(type: String): Int = when (type) {
        "goal" -> 4
        "preference" -> 3
        "pattern" -> 2
        "kpi_week" -> 1
        else -> 0
    }

    companion object {
        private const val MAX_MEMORY_ENTRIES = 6
        private const val MAX_MEMORY_LINE_CHARS = 220
    }
}
