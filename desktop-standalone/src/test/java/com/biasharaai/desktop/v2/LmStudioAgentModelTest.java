package com.biasharaai.desktop.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LmStudioAgentModelTest {
    @Test
    void parsesOpenAiCompatibleToolCalls() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,"
                + "\"tool_calls\":[{\"id\":\"call-7\",\"type\":\"function\",\"function\":{"
                + "\"name\":\"sync_health\",\"arguments\":\"{}\"}}]}}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            ObjectMapper mapper = new ObjectMapper();
            Settings settings = new Settings();
            settings.currency = "KES";
            settings.lmStudioModel = "test-model";
            settings.lmStudioBaseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            AgentDefinition agent = new AgentDefinition(
                "sync-reconciliation",
                "Sync reconciliation",
                "Checks sync.",
                "Inspect sync health.",
                "refresh",
                List.of("sync_health"),
                List.of("sync_health")
            );
            BusinessAgentToolCatalog tools = new BusinessAgentToolCatalog(mapper);
            LmStudioAgentModel model = new LmStudioAgentModel(new LmStudioClient(), mapper);

            AgentModelTurn turn = model.nextTurn(
                settings,
                agent,
                List.of(AgentModelMessage.user("Inspect sync.")),
                tools.descriptors(agent.allowedTools())
            );

            assertEquals(1, turn.toolCalls().size());
            assertEquals("call-7", turn.toolCalls().get(0).callId());
            assertEquals("sync_health", turn.toolCalls().get(0).name());
            assertEquals("required", mapper.readTree(requestBody.get()).path("tool_choice").asText());
            assertEquals(160, mapper.readTree(requestBody.get()).path("max_tokens").asInt());
        } finally {
            server.stop(0);
        }
    }
}
