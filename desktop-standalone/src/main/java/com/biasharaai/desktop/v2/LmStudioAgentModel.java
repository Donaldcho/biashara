package com.biasharaai.desktop.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class LmStudioAgentModel implements AgentLanguageModel {
    private final LmStudioClient client;
    private final ObjectMapper mapper;

    LmStudioAgentModel(LmStudioClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public AgentModelTurn nextTurn(
        Settings settings,
        AgentDefinition agent,
        List<AgentModelMessage> messages,
        List<AgentToolDescriptor> tools
    ) throws IOException {
        String model = client.resolveModelId(settings);
        if (model.isBlank()) {
            throw new IOException("LM Studio is reachable, but no model is loaded.");
        }

        ObjectNode request = mapper.createObjectNode();
        boolean hasToolEvidence = messages.stream().anyMatch(message -> "tool".equals(message.role()));
        request.put("model", model);
        request.put("temperature", 0.1);
        request.put("max_tokens", hasToolEvidence ? 360 : 160);
        request.put("stream", false);
        request.put("parallel_tool_calls", false);
        ArrayNode requestMessages = request.putArray("messages");
        requestMessages.addObject()
            .put("role", "system")
            .put("content", systemPrompt(agent, settings));
        messages.forEach(message -> appendMessage(requestMessages, message));

        ArrayNode requestTools = request.putArray("tools");
        for (AgentToolDescriptor tool : tools) {
            ObjectNode function = requestTools.addObject().put("type", "function").putObject("function");
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.set("parameters", tool.inputSchema());
        }
        request.put("tool_choice", hasToolEvidence ? "auto" : "required");

        JsonNode response = mapper.readTree(client.completeAgentTurn(settings, mapper.writeValueAsString(request)));
        JsonNode message = response.path("choices").path(0).path("message");
        if (message.isMissingNode()) {
            throw new IOException("LM Studio returned an invalid agent response.");
        }
        String content = message.path("content").isTextual() ? message.path("content").asText() : "";
        List<AgentRequestedTool> calls = new ArrayList<>();
        for (JsonNode call : message.path("tool_calls")) {
            String id = call.path("id").asText();
            String name = call.path("function").path("name").asText();
            String arguments = call.path("function").path("arguments").asText("{}");
            JsonNode parsedArguments;
            try {
                parsedArguments = mapper.readTree(arguments);
            } catch (Exception ignored) {
                parsedArguments = mapper.createObjectNode();
            }
            if (!name.isBlank()) {
                calls.add(new AgentRequestedTool(id.isBlank() ? "call-" + (calls.size() + 1) : id, name, parsedArguments));
            }
        }
        return new AgentModelTurn(content, calls);
    }

    private void appendMessage(ArrayNode messages, AgentModelMessage message) {
        ObjectNode target = messages.addObject();
        target.put("role", message.role());
        if (!message.content().isBlank() || "tool".equals(message.role())) {
            target.put("content", message.content());
        }
        if ("tool".equals(message.role())) {
            target.put("tool_call_id", message.toolCallId());
            target.put("name", message.name());
        }
        if (!message.toolCalls().isEmpty()) {
            ArrayNode calls = target.putArray("tool_calls");
            for (AgentRequestedTool call : message.toolCalls()) {
                ObjectNode toolCall = calls.addObject();
                toolCall.put("id", call.callId());
                toolCall.put("type", "function");
                ObjectNode function = toolCall.putObject("function");
                function.put("name", call.name());
                function.put("arguments", compact(call.arguments()));
            }
        }
    }

    private String compact(JsonNode node) {
        try {
            return mapper.writeValueAsString(node == null ? mapper.createObjectNode() : node);
        } catch (IOException ignored) {
            return "{}";
        }
    }

    private String systemPrompt(AgentDefinition agent, Settings settings) {
        return "You are the " + agent.name() + " inside Biashara AI Pro Desktop. "
            + "Objective: " + agent.objective() + " "
            + "Use the supplied tools before reaching conclusions. Use only tool results; never invent business records. "
            + "All available tools are read-only. Do not claim that you changed prices, stock, ledger entries, services, customers, or messages. "
            + "Return a concise owner-facing report with Evidence, Risks, and Recommended next actions. "
            + "Use currency " + safe(settings.currency) + ". Do not reveal chain-of-thought.";
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "configured by the business" : value.trim();
    }
}
