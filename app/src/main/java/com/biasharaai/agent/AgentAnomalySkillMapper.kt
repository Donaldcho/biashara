package com.biasharaai.agent

import com.biasharaai.data.local.db.AgentAction
import com.biasharaai.skills.SkillResult
import com.biasharaai.skills.builtin.DetectAnomalySkill
import com.google.gson.Gson
import com.google.gson.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** Maps [DetectAnomalySkill] JSON output to [AgentAction] fraud feed rows (X9). */
@Singleton
class AgentAnomalySkillMapper @Inject constructor() {

    fun actionsFromSkillResult(result: SkillResult, nowMillis: Long): List<AgentAction> {
        if (result !is SkillResult.Success) return emptyList()
        return actionsFromOutputJson(result.outputJson, nowMillis)
    }

    fun actionsFromOutputJson(outputJson: String, nowMillis: Long): List<AgentAction> {
        val root = runCatching { gson.fromJson(outputJson, JsonObject::class.java) }.getOrNull()
            ?: return emptyList()
        val anomalies = root.getAsJsonArray("anomalies") ?: return emptyList()
        val out = ArrayList<AgentAction>(anomalies.size())
        for (el in anomalies) {
            val obj = el.asJsonObject
            val headline = obj.get("headline")?.asString ?: continue
            val detail = obj.get("detail")?.asString ?: ""
            val txId = obj.get("relatedTransactionId")?.let {
                if (it.isJsonNull) null else it.asLong
            }
            out.add(
                AgentActionBuilder.fraudSignal(
                    headline = headline,
                    detail = detail,
                    relatedTransactionId = txId,
                    nowMillis = nowMillis,
                ),
            )
        }
        return out
    }

    companion object {
        private val gson = Gson()

        const val SKILL_ID = DetectAnomalySkill.ID
    }
}
