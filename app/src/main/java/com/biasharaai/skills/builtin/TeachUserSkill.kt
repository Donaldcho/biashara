package com.biasharaai.skills.builtin

import com.biasharaai.knowledge.LessonLibrary
import com.biasharaai.knowledge.TeachingEngine
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillArgsParser
import com.biasharaai.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * K4 — Provides a step-by-step micro-lesson for a given feature, or suggests
 * the next recommended lesson based on the owner's mastery profile.
 */
@Singleton
class TeachUserSkill @Inject constructor(
    private val teachingEngine: TeachingEngine,
    private val lessonLibrary: LessonLibrary,
) : BiasharaSkill {

    override val id: String = ID
    override val displayName: String = "Teach user"
    override val parameterSchemaJson: String = """
        {
          "type": "object",
          "properties": {
            "featureId": {"type": "string", "description": "Feature to teach, e.g. 'pos_sale'. Omit to get the next recommended lesson."},
            "languageCode": {"type": "string", "default": "en"}
          }
        }
    """.trimIndent()

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        val args = SkillArgsParser.parseObject(argumentsJson).getOrElse {
            return@withContext SkillResult.Failure("INVALID_ARGS", it.message ?: "Invalid JSON")
        }
        val featureId = SkillArgsParser.stringArg(args, "featureId")
        val lang = SkillArgsParser.stringArg(args, "languageCode") ?: "en"

        if (featureId != null) {
            val lesson = lessonLibrary.firstLessonForFeature(featureId, lang)
                ?: return@withContext SkillResult.Failure("NO_LESSON", "No lesson found for feature: $featureId")
            return@withContext SkillResult.successMap(
                mapOf(
                    "lessonId" to lesson.lessonId,
                    "featureId" to lesson.featureId,
                    "title" to lesson.title,
                    "estimatedMinutes" to lesson.estimatedMinutes,
                    "stepCount" to lesson.steps.size,
                    "steps" to lesson.steps.map { step ->
                        mapOf(
                            "step" to step.stepNumber,
                            "instruction" to step.instruction,
                            "navigationHint" to step.navigationHint,
                            "actionType" to step.actionType.name,
                        )
                    },
                ),
                summary = "Lesson: ${lesson.title} (${lesson.steps.size} steps)",
            )
        }

        val suggestion = teachingEngine.nextSuggestion(lang)
            ?: return@withContext SkillResult.successMap(
                mapOf("allMastered" to true),
                summary = "All features mastered!",
            )

        val lesson = lessonLibrary.lessonById(suggestion.lessonId)
            ?: return@withContext SkillResult.Failure("LESSON_NOT_FOUND", "Lesson ${suggestion.lessonId} missing")

        SkillResult.successMap(
            mapOf(
                "lessonId" to lesson.lessonId,
                "featureId" to lesson.featureId,
                "title" to lesson.title,
                "reason" to suggestion.reason,
                "estimatedMinutes" to lesson.estimatedMinutes,
                "stepCount" to lesson.steps.size,
                "steps" to lesson.steps.map { step ->
                    mapOf(
                        "step" to step.stepNumber,
                        "instruction" to step.instruction,
                        "navigationHint" to step.navigationHint,
                        "actionType" to step.actionType.name,
                    )
                },
            ),
            summary = "Recommended: ${lesson.title} — ${suggestion.reason}",
        )
    }

    companion object {
        const val ID = "teach_user"
    }
}
