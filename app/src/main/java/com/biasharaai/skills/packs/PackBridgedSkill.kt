package com.biasharaai.skills.packs

import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillResult

/**
 * Skill entry from an installed pack — execution may delegate to a built-in [BiasharaSkill].
 */
class PackBridgedSkill(
    val packId: String,
    private val entry: SkillPackManifest.SkillPackSkillEntry,
    private val delegate: BiasharaSkill?,
) : BiasharaSkill {
    override val id: String = entry.skillId
    override val displayName: String = entry.displayName
    override val parameterSchemaJson: String = entry.schemaJson

    override suspend fun execute(argumentsJson: String): SkillResult {
        val target = delegate
            ?: return SkillResult.Failure(
                "NOT_IMPLEMENTED",
                "Pack skill '$id' has no delegateTo handler on this build",
            )
        return target.execute(argumentsJson)
    }
}
