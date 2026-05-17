package com.biasharaai.skills

import android.util.Log
import com.biasharaai.data.local.db.SkillDescriptorDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 6 X4 — dispatches tool calls to registered [BiasharaSkill] implementations.
 * Updates execution stats in Room on success.
 */
@Singleton
class SkillExecutor @Inject constructor(
    private val skillRegistry: SkillRegistry,
    private val skillDescriptorDao: SkillDescriptorDao,
) {
    companion object {
        private const val TAG = "SkillExecutor"

        const val CODE_NOT_FOUND = "NOT_FOUND"
        const val CODE_DISABLED = "DISABLED"
        const val CODE_NOT_IMPLEMENTED = "NOT_IMPLEMENTED"
        const val CODE_EXECUTION_ERROR = "EXECUTION_ERROR"
    }

    /**
     * @param argumentsJson JSON object matching the skill's schema (use `{}` when empty).
     */
    suspend fun execute(skillId: String, argumentsJson: String = "{}"): SkillResult =
        withContext(Dispatchers.IO) {
            val descriptor = skillDescriptorDao.getById(skillId)
            if (descriptor != null && !descriptor.isEnabled) {
                return@withContext SkillResult.Failure(
                    CODE_DISABLED,
                    "Skill '$skillId' is disabled in settings.",
                )
            }

            val skill = skillRegistry.get(skillId)
            if (skill == null) {
                val inCatalogue = skillRegistry.catalogue().skills.any { it.skillId == skillId }
                return@withContext if (inCatalogue) {
                    SkillResult.Failure(
                        CODE_NOT_IMPLEMENTED,
                        "Skill '$skillId' is registered in the catalogue but not yet implemented.",
                    )
                } else {
                    SkillResult.Failure(CODE_NOT_FOUND, "Unknown skill: $skillId")
                }
            }

            try {
                val result = skill.execute(argumentsJson)
                if (result is SkillResult.Success) {
                    skillDescriptorDao.recordExecution(skillId, System.currentTimeMillis())
                }
                result
            } catch (e: Exception) {
                Log.e(TAG, "Skill $skillId failed", e)
                SkillResult.Failure(
                    CODE_EXECUTION_ERROR,
                    e.message ?: "Skill execution failed",
                )
            }
        }

    /** JSON schemas for all catalogue skills (for future Engine tool registration in X8). */
    suspend fun toolDefinitionsForLlm(): List<SkillToolDefinition> = withContext(Dispatchers.IO) {
        skillDescriptorDao.getAll()
            .filter { it.isEnabled }
            .map { desc ->
                SkillToolDefinition(
                    skillId = desc.skillId,
                    displayName = desc.displayName,
                    schemaJson = desc.schemaJson,
                    implemented = skillRegistry.isImplemented(desc.skillId),
                )
            }
    }
}

/** LiteRT-LM / function-calling metadata (X8 will register these on the Engine). */
data class SkillToolDefinition(
    val skillId: String,
    val displayName: String,
    val schemaJson: String,
    val implemented: Boolean,
)
