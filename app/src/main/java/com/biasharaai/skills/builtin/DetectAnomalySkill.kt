package com.biasharaai.skills.builtin

import com.biasharaai.agent.FraudRuleEngine
import com.biasharaai.data.local.db.AgentAction
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** X7 — Room-backed fraud / anomaly heuristics ([FraudRuleEngine]); read-only for the agent loop. */
@Singleton
class DetectAnomalySkill @Inject constructor(
    private val fraudRuleEngine: FraudRuleEngine,
) : BiasharaSkill {
    override val id: String = ID
    override val displayName: String = "Detect anomaly"
    override val parameterSchemaJson: String =
        """{"type":"object","properties":{}}"""

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val signals = fraudRuleEngine.detectAll(now)
        val anomalies = signals.map { it.toAnomalyMap() }

        SkillResult.successMap(
            mapOf(
                "checkedAtMillis" to now,
                "count" to anomalies.size,
                "anomalies" to anomalies,
            ),
            summary = when (anomalies.size) {
                0 -> "No anomalies detected"
                1 -> "1 anomaly: ${anomalies.first()["headline"]}"
                else -> "${anomalies.size} anomalies detected"
            },
        )
    }

    private fun AgentAction.toAnomalyMap(): Map<String, Any?> = mapOf(
        "headline" to headline,
        "detail" to detail,
        "urgency" to urgency,
        "agentType" to agentType,
        "relatedTransactionId" to relatedEntityId,
        "actionVerb" to actionVerb,
    )

    companion object {
        const val ID = "detect_anomaly"
    }
}
