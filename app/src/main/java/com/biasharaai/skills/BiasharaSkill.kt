package com.biasharaai.skills

/**
 * Phase 6 — executable tool/skill invoked by [SkillExecutor] (and later [AgentLoopRunner] in X8).
 *
 * Implementations live under `com.biasharaai.skills.builtin` (shipped) or are loaded from skill packs (X11).
 */
interface BiasharaSkill {
    val id: String
    val displayName: String

    /** JSON Schema for function-calling arguments (stored in Room `skill_descriptors.schemaJson`). */
    val parameterSchemaJson: String

    /**
     * Run the skill with JSON arguments matching [parameterSchemaJson].
     * Must not throw — return [SkillResult.Failure] on error.
     */
    suspend fun execute(argumentsJson: String): SkillResult
}
