package com.biasharaai.desktop.v2;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

final class FileAgentRunRepository implements AgentRunRepository {
    private final Path file;
    private final ObjectMapper mapper;

    FileAgentRunRepository(Path file, ObjectMapper mapper) {
        this.file = file;
        this.mapper = mapper;
    }

    @Override
    public synchronized void save(AgentRun run) throws IOException {
        Files.createDirectories(file.getParent());
        String line = mapper.writeValueAsString(run) + System.lineSeparator();
        Files.writeString(
            file,
            line,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        );
    }

    @Override
    public synchronized List<AgentRun> recent(int limit) throws IOException {
        int safeLimit = Math.max(1, Math.min(100, limit));
        if (!Files.exists(file)) {
            return List.of();
        }
        Deque<AgentRun> latest = new ArrayDeque<>(safeLimit);
        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            lines.filter(line -> !line.isBlank()).forEach(line -> {
                try {
                    AgentRun run = mapper.readValue(line, AgentRun.class);
                    if (latest.size() == safeLimit) {
                        latest.removeFirst();
                    }
                    latest.addLast(run);
                } catch (IOException ignored) {
                    // A damaged audit line must not hide later valid agent runs.
                }
            });
        }
        List<AgentRun> result = new ArrayList<>(latest);
        Collections.reverse(result);
        return result;
    }
}
