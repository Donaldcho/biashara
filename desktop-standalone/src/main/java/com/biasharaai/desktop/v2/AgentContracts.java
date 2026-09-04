package com.biasharaai.desktop.v2;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;

enum AgentToolAccess {
    READ_ONLY
}

record AgentDefinition(
    String id,
    String name,
    String description,
    String objective,
    String icon,
    List<String> allowedTools,
    List<String> defaultTools
) {
    AgentDefinition {
        allowedTools = List.copyOf(allowedTools);
        defaultTools = List.copyOf(defaultTools);
    }
}

record AgentToolDescriptor(
    String name,
    String description,
    JsonNode inputSchema,
    AgentToolAccess access
) {}

interface AgentTool {
    AgentToolDescriptor descriptor();

    JsonNode execute(JsonNode arguments, BusinessSnapshot snapshot);
}

record AgentRequestedTool(
    String callId,
    String name,
    JsonNode arguments
) {}

record AgentModelMessage(
    String role,
    String content,
    String toolCallId,
    String name,
    List<AgentRequestedTool> toolCalls
) {
    AgentModelMessage {
        content = content == null ? "" : content;
        toolCallId = toolCallId == null ? "" : toolCallId;
        name = name == null ? "" : name;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    static AgentModelMessage user(String content) {
        return new AgentModelMessage("user", content, "", "", List.of());
    }

    static AgentModelMessage assistant(String content, List<AgentRequestedTool> toolCalls) {
        return new AgentModelMessage("assistant", content, "", "", toolCalls);
    }

    static AgentModelMessage tool(String callId, String name, String content) {
        return new AgentModelMessage("tool", content, callId, name, List.of());
    }
}

record AgentModelTurn(
    String content,
    List<AgentRequestedTool> toolCalls
) {
    AgentModelTurn {
        content = content == null ? "" : content.trim();
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}

interface AgentLanguageModel {
    AgentModelTurn nextTurn(
        Settings settings,
        AgentDefinition agent,
        List<AgentModelMessage> messages,
        List<AgentToolDescriptor> tools
    ) throws IOException;
}

record AgentToolTrace(
    String toolName,
    AgentToolAccess access,
    String status,
    long startedAtMillis,
    long durationMillis,
    JsonNode arguments,
    JsonNode result,
    String error
) {}

record AgentRun(
    String id,
    String agentId,
    String agentName,
    String provider,
    String status,
    String summary,
    long startedAtMillis,
    long completedAtMillis,
    List<AgentToolTrace> toolTraces,
    String error
) {
    AgentRun {
        toolTraces = List.copyOf(toolTraces);
        summary = summary == null ? "" : summary;
        error = error == null ? "" : error;
    }
}

record AgentCenterState(
    List<AgentDefinition> agents,
    List<AgentRun> recentRuns
) {}

interface AgentRunRepository {
    void save(AgentRun run) throws IOException;

    List<AgentRun> recent(int limit) throws IOException;
}
