package com.biasharaai.skills.builtin

import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillResult
import javax.inject.Inject
import javax.inject.Singleton

/** Built-in no-op skill for registry / executor smoke tests. */
@Singleton
class PingSkill @Inject constructor() : BiasharaSkill {
    override val id: String = ID
    override val displayName: String = "Health check"
    override val parameterSchemaJson: String =
        """{"type":"object","properties":{},"additionalProperties":false}"""

    override suspend fun execute(argumentsJson: String): SkillResult =
        SkillResult.successMap(
            mapOf(
                "ok" to true,
                "skillId" to ID,
            ),
            summary = "pong",
        )

    companion object {
        const val ID = "ping"
    }
}
