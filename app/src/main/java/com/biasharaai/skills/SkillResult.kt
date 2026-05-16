package com.biasharaai.skills

import com.google.gson.Gson

/**
 * Outcome of [SkillExecutor] / [BiasharaSkill.execute].
 */
sealed class SkillResult {
    data class Success(
        val outputJson: String,
        val summary: String? = null,
    ) : SkillResult()

    data class Failure(
        val code: String,
        val message: String,
    ) : SkillResult()

    val isSuccess: Boolean get() = this is Success

    companion object {
        private val gson = Gson()

        fun successMap(data: Map<String, Any?>, summary: String? = null): Success =
            Success(gson.toJson(data), summary)

        fun successText(text: String): Success = Success(
            outputJson = gson.toJson(mapOf("text" to text)),
            summary = text,
        )
    }
}
