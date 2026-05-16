package com.biasharaai.skills

import com.biasharaai.agent.AgentToolCallRecord
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import javax.inject.Inject
import javax.inject.Singleton

/** Builds LiteRT-LM [ToolProvider] instances for enabled, implemented catalogue skills (X8). */
@Singleton
class SkillToolFactory @Inject constructor(
    private val skillExecutor: SkillExecutor,
) {
    suspend fun buildToolProviders(
        executionLog: MutableList<AgentToolCallRecord>? = null,
    ): List<ToolProvider> =
        skillExecutor.toolDefinitionsForLlm()
            .filter { it.implemented }
            .map { def ->
                tool(
                    BiasharaSkillOpenApiTool(
                        definition = def,
                        skillExecutor = skillExecutor,
                        executionLog = executionLog,
                    ),
                )
            }
}
