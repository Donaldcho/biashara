package com.biasharaai.skills.builtin

import com.biasharaai.profile.BusinessContextBuilder
import com.biasharaai.profile.BusinessProfileRepository
import com.biasharaai.money.MoneyFormatter
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillArgsParser
import com.biasharaai.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueryBusinessProfileSkill @Inject constructor(
    private val businessProfileRepository: BusinessProfileRepository,
    private val moneyFormatter: MoneyFormatter,
) : BiasharaSkill {

    override val id: String = ID
    override val displayName: String = "Query business profile"
    override val parameterSchemaJson: String = """
        {
          "type": "object",
          "properties": {
            "includeContextBlock": {
              "type": "boolean",
              "description": "When true, include the full agent context header text",
              "default": true
            }
          }
        }
    """.trimIndent()

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        val args = SkillArgsParser.parseObject(argumentsJson).getOrElse {
            return@withContext SkillResult.Failure("INVALID_ARGS", it.message ?: "Invalid JSON")
        }
        val includeContext = SkillArgsParser.boolArg(args, "includeContextBlock", default = true)
        val profile = businessProfileRepository.getOrCreate()
        if (!profile.hasIdentity()) {
            return@withContext SkillResult.successMap(
                mapOf(
                    "found" to false,
                    "onboardingComplete" to profile.onboardingComplete,
                ),
                summary = "Business profile not set — owner has not completed onboarding.",
            )
        }
        val contextBlock = if (includeContext) {
            BusinessContextBuilder.build(profile, moneyFormatter)
        } else {
            ""
        }
        SkillResult.successMap(
            mapOf(
                "found" to true,
                "onboardingComplete" to profile.onboardingComplete,
                "businessName" to profile.businessName,
                "ownerName" to profile.ownerName,
                "businessType" to profile.businessType,
                "whatTheyOffer" to profile.whatTheyOffer(),
                "targetCustomer" to profile.targetCustomer,
                "location" to profile.location,
                "monthlyRevenueTarget" to profile.monthlyRevenueTarget,
                "businessGoal" to profile.businessGoal,
                "contextBlock" to contextBlock,
            ),
            summary = "Profile: ${profile.businessName}",
        )
    }

    companion object {
        const val ID = "query_business_profile"
    }
}
