package com.biasharaai.desktop.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DesktopStoreTest {
    @TempDir
    Path dataDir;

    @Test
    void preservesStandardLmStudioEndpointAcrossRestart() {
        DesktopStore store = new DesktopStore(dataDir);
        AppState state = new AppState(dataDir);
        state.settings.aiProvider = "LM_STUDIO";
        state.settings.lmStudioBaseUrl = "http://127.0.0.1:1234/v1";
        state.settings.lmStudioModel = "qwen3-4b-instruct-2507";

        store.save(state);
        AppState restored = store.load();

        assertEquals("LM_STUDIO", restored.settings.aiProvider);
        assertEquals("http://127.0.0.1:1234/v1", restored.settings.lmStudioBaseUrl);
        assertEquals("qwen3-4b-instruct-2507", restored.settings.lmStudioModel);
    }

    @Test
    void persistsBridgeProtocolVersion() {
        DesktopStore store = new DesktopStore(dataDir);

        store.saveBridgeSession("session", "Stock phone", "1.0");

        String[] restored = store.loadBridgeSession();
        assertEquals("session", restored[0]);
        assertEquals("Stock phone", restored[1]);
        assertEquals("1.0", restored[2]);
    }

    @Test
    void loadsLegacyBridgeSessionWithoutRequiringSignedRequests() throws Exception {
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve("bridge-session.tsv"), "legacy-session\tLegacy phone\n");

        String[] restored = new DesktopStore(dataDir).loadBridgeSession();

        assertEquals("legacy-session", restored[0]);
        assertEquals("Legacy phone", restored[1]);
        assertEquals("", restored[2]);
    }
}
