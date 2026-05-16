package com.biasharaai.skills.builtin

import com.biasharaai.agent.CoPurchaseAnalyser
import com.biasharaai.data.local.db.CoPurchasePair
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillArgsParser
import com.biasharaai.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** X7 — Top product pairs bought on the same receipt ([CoPurchaseAnalyser]). */
@Singleton
class FindCopurchasePairsSkill @Inject constructor(
    private val coPurchaseAnalyser: CoPurchaseAnalyser,
) : BiasharaSkill {
    override val id: String = ID
    override val displayName: String = "Find co-purchase pairs"
    override val parameterSchemaJson: String =
        """{"type":"object","properties":{"days":{"type":"integer","minimum":7,"maximum":365},"minCoCount":{"type":"integer","minimum":2,"maximum":20}}}"""

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        val args = SkillArgsParser.parseObject(argumentsJson).getOrElse {
            return@withContext SkillResult.Failure("INVALID_ARGS", it.message ?: "Invalid JSON")
        }
        val days = SkillArgsParser.intArg(args, "days", default = 90, min = 7, max = 365)
        val minCoCount = SkillArgsParser.intArg(args, "minCoCount", default = 3, min = 2, max = 20)

        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        val pairs = coPurchaseAnalyser.topPairsSince(since, minCoCount)

        SkillResult.successMap(
            mapOf(
                "days" to days,
                "minCoCount" to minCoCount,
                "sinceMillis" to since,
                "pairCount" to pairs.size,
                "pairs" to pairs.map { it.toSkillMap() },
            ),
            summary = when (pairs.size) {
                0 -> "No co-purchase pairs in last ${days}d"
                else -> "${pairs.size} pair(s); top: ${pairs.first().product1} + ${pairs.first().product2}"
            },
        )
    }

    private fun CoPurchasePair.toSkillMap(): Map<String, Any?> = mapOf(
        "product1" to product1,
        "product2" to product2,
        "coCount" to coCount,
    )

    companion object {
        const val ID = "find_copurchase_pairs"
    }
}
