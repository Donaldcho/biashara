package com.biasharaai.desktop.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileAgentRunRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsAppendOnlyRunsAndReturnsNewestFirst() throws Exception {
        FileAgentRunRepository repository = new FileAgentRunRepository(tempDir.resolve("agent-runs.jsonl"), new ObjectMapper());
        repository.save(run("RUN-1", 100));
        repository.save(run("RUN-2", 200));

        List<AgentRun> recent = repository.recent(10);

        assertEquals(List.of("RUN-2", "RUN-1"), recent.stream().map(AgentRun::id).toList());
    }

    private AgentRun run(String id, long completedAt) {
        return new AgentRun(
            id,
            "inventory-planner",
            "Inventory planner",
            "RULES",
            "FALLBACK",
            "Summary",
            completedAt - 10,
            completedAt,
            List.of(),
            ""
        );
    }
}
