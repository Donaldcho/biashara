package com.biasharaai.skills.builtin

import com.biasharaai.profile.OnlineBusinessProfileUpdater
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillArgsParser
import com.biasharaai.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateBusinessProfileFromOnlineSkill @Inject constructor(
    private val updater: OnlineBusinessProfileUpdater,
) : BiasharaSkill {

    override val id: String = ID
    override val displayName: String = "Update business profile from online source"
    override val parameterSchemaJson: String = """
        {
          "type": "object",
          "properties": {
            "url": {
              "type": "string",
              "description": "HTTPS page for the business, such as an official site or public profile"
            },
            "apply": {
              "type": "boolean",
              "description": "When false, only preview detected fields; when true, save them to the business profile",
              "default": false
            }
          },
          "required": ["url"]
        }
    """.trimIndent()

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        val args = SkillArgsParser.parseObject(argumentsJson).getOrElse {
            return@withContext SkillResult.Failure("INVALID_ARGS", it.message ?: "Invalid JSON")
        }
        val url = SkillArgsParser.stringArg(args, "url")
            ?: return@withContext SkillResult.Failure("MISSING_URL", "url argument is required")
        val apply = SkillArgsParser.boolArg(args, "apply", default = false)
        val result = if (apply) updater.apply(url) else updater.preview(url)
        result.fold(
            onSuccess = { update ->
                SkillResult.successMap(
                    mapOf(
                        "sourceUrl" to update.sourceUrl,
                        "detectedFields" to update.detectedFields,
                        "appliedFields" to update.appliedFields,
                        "applied" to apply,
                    ),
                    summary = if (apply) {
                        "Updated business profile from online source."
                    } else {
                        "Previewed business profile fields from online source."
                    },
                )
            },
            onFailure = { error ->
                SkillResult.Failure("ONLINE_UPDATE_FAILED", error.message ?: "Online update failed")
            },
        )
    }

    companion object {
        const val ID = "update_business_profile_from_online"
    }
}
