package com.biasharaai.agent

import com.biasharaai.profile.BusinessProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Prepends structured [BusinessProfile] context to agent system and legacy prompts. */
@Singleton
class AgentPromptComposer @Inject constructor(
    private val businessProfileRepository: BusinessProfileRepository,
) {

    suspend fun enrichSystemPrompt(base: String): String =
        prependContext(base)

    suspend fun enrichLegacyPrompt(base: String): String =
        prependContext(base)

    private suspend fun prependContext(base: String): String {
        val header = businessProfileRepository.buildAgentContextHeader() ?: return base
        return "$header\n\n$base"
    }
}
