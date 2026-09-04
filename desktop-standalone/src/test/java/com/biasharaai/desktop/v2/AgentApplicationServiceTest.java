package com.biasharaai.desktop.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentApplicationServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void modelUsesAllowedToolBeforeCompletingReport() throws Exception {
        AtomicInteger turns = new AtomicInteger();
        AgentLanguageModel model = (settings, agent, messages, tools) -> turns.getAndIncrement() == 0
            ? new AgentModelTurn("", List.of(new AgentRequestedTool("call-1", "inventory_risks", mapper.createObjectNode())))
            : new AgentModelTurn("Evidence confirms one low-stock product.", List.of());
        InMemoryRepository repository = new InMemoryRepository();
        AgentApplicationService service = service(model, repository);
        Settings settings = lmStudioSettings();

        AgentRun run = service.run("inventory-planner", AgentTestFixtures.snapshot(), settings);

        assertEquals("COMPLETED", run.status());
        assertEquals("LM_STUDIO", run.provider());
        assertEquals("inventory_risks", run.toolTraces().get(0).toolName());
        assertEquals("COMPLETED", run.toolTraces().get(0).status());
        assertEquals(1, repository.runs.size());
    }

    @Test
    void disallowedModelToolIsDeniedAndFallsBackToApprovedDefaults() throws Exception {
        AtomicInteger turns = new AtomicInteger();
        AgentLanguageModel model = (settings, agent, messages, tools) -> turns.getAndIncrement() == 0
            ? new AgentModelTurn("", List.of(new AgentRequestedTool("call-1", "ledger_summary", mapper.createObjectNode())))
            : new AgentModelTurn("Unsupported conclusion", List.of());
        InMemoryRepository repository = new InMemoryRepository();
        AgentApplicationService service = service(model, repository);

        AgentRun run = service.run("inventory-planner", AgentTestFixtures.snapshot(), lmStudioSettings());

        assertEquals("FALLBACK", run.status());
        assertTrue(run.toolTraces().stream().anyMatch(trace -> "ledger_summary".equals(trace.toolName()) && "DENIED".equals(trace.status())));
        assertTrue(run.toolTraces().stream().anyMatch(trace -> "inventory_risks".equals(trace.toolName()) && "COMPLETED".equals(trace.status())));
        assertTrue(run.toolTraces().stream().allMatch(trace -> trace.access() == AgentToolAccess.READ_ONLY));
    }

    private AgentApplicationService service(AgentLanguageModel model, AgentRunRepository repository) {
        return new AgentApplicationService(
            new BusinessAgentToolCatalog(mapper),
            model,
            repository,
            mapper
        );
    }

    private Settings lmStudioSettings() {
        Settings settings = new Settings();
        settings.aiProvider = "LM_STUDIO";
        settings.currency = "KES";
        return settings;
    }

    private static final class InMemoryRepository implements AgentRunRepository {
        private final List<AgentRun> runs = new ArrayList<>();

        @Override
        public void save(AgentRun run) {
            runs.add(run);
        }

        @Override
        public List<AgentRun> recent(int limit) {
            return runs.stream().limit(limit).toList();
        }
    }
}
