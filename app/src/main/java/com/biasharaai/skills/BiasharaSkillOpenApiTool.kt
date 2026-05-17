package com.biasharaai.skills

import com.biasharaai.agent.AgentToolCallRecord
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * LiteRT-LM [OpenApiTool] adapter — routes model tool calls to [SkillExecutor].
 */
class BiasharaSkillOpenApiTool(
    private val definition: SkillToolDefinition,
    private val skillExecutor: SkillExecutor,
    private val executionLog: MutableList<AgentToolCallRecord>? = null,
) : OpenApiTool {

    override fun getToolDescriptionJsonString(): String =
        buildOpenApiDescription(definition.skillId, definition.displayName, definition.schemaJson)

    override fun execute(paramsJsonString: String): String = runBlocking(Dispatchers.IO) {
        val result = skillExecutor.execute(definition.skillId, paramsJsonString)
        executionLog?.add(
            AgentToolCallRecord(
                skillId = definition.skillId,
                argumentsJson = paramsJsonString,
                success = result.isSuccess,
                outputJson = when (result) {
                    is SkillResult.Success -> result.outputJson
                    is SkillResult.Failure -> """{"error":{"code":"${result.code}","message":${gson.toJson(result.message)}}}"""
                },
            ),
        )
        when (result) {
            is SkillResult.Success -> result.outputJson
            is SkillResult.Failure -> gson.toJson(
                mapOf("error" to mapOf("code" to result.code, "message" to result.message)),
            )
        }
    }

    companion object {
        private val gson = Gson()

        fun buildOpenApiDescription(skillId: String, displayName: String, parametersSchemaJson: String): String =
            """
            {
              "name": ${gson.toJson(skillId)},
              "description": ${gson.toJson(displayName)},
              "parameters": $parametersSchemaJson
            }
            """.trimIndent()
    }
}
