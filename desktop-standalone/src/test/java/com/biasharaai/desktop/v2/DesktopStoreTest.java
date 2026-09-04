package com.biasharaai.desktop.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void migratesLegacyFilesToSqliteAndKeepsRollbackMirror() throws Exception {
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve("products.tsv"), "PRD-1\tSoap\tHand soap\tSKU-1\t123\tCare\t\tret-1\t\t\t250\t100\t8\n");

        AppState restored = new DesktopStore(dataDir).load();

        assertEquals(1, restored.products.size());
        assertEquals("Soap", restored.products.get(0).name);
        assertTrue(Files.isRegularFile(dataDir.resolve(SqliteDesktopStateRepository.DATABASE_FILE)));
        assertTrue(Files.isRegularFile(dataDir.resolve("legacy-tsv-before-sqlite.zip")));
        assertTrue(Files.readString(dataDir.resolve("products.tsv")).contains("Soap"));
    }

    @Test
    void rollsBackWholeSnapshotWhenAnyRowViolatesConstraint() {
        DesktopStore store = new DesktopStore(dataDir);
        AppState state = new AppState(dataDir);
        state.products.add(product("PRD-1", "Original", 10));
        store.save(state);

        state.products.get(0).name = "Changed but not committed";
        state.products.add(product("PRD-1", "Duplicate", 99));
        assertThrows(DesktopPersistenceException.class, () -> store.save(state));

        AppState restored = new DesktopStore(dataDir).load();
        assertEquals(1, restored.products.size());
        assertEquals("Original", restored.products.get(0).name);
        assertEquals(10, restored.products.get(0).stock);
    }

    @Test
    void roundTripsSyncInboxAndStockMovement() {
        DesktopStore store = new DesktopStore(dataDir);
        AppState state = new AppState(dataDir);
        state.products.add(product("PRD-1", "Soap", 9));
        state.syncInboxEntries.add(new SyncInboxEntry(
            "mobile-transaction:phone:42",
            "TRANSACTION_SYNC",
            "Phone",
            Instant.parse("2026-09-04T10:00:00Z"),
            "abc123",
            200,
            "{\"accepted\":true}"
        ));
        state.stockMovements.add(new StockMovement(
            "MOV-1",
            "mobile-transaction:phone:42",
            Instant.parse("2026-09-04T10:00:00Z"),
            "Phone",
            "PRD-1",
            -1,
            9,
            "MOBILE_SALE",
            "MOB-42"
        ));

        store.save(state);
        AppState restored = store.load();

        assertEquals(1, restored.syncInboxEntries.size());
        assertEquals("abc123", restored.syncInboxEntries.get(0).payloadHash);
        assertEquals(1, restored.stockMovements.size());
        assertEquals(-1, restored.stockMovements.get(0).quantityDelta);
    }

    @Test
    void backupContainsConsistentDatabaseAndCompatibilityFiles() throws Exception {
        DesktopStore store = new DesktopStore(dataDir);
        AppState state = new AppState(dataDir);
        state.products.add(product("PRD-1", "Soap", 10));
        store.save(state);

        Path backup = store.exportBackup(dataDir.resolve("exports").resolve("backup.zip"));
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(backup))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.add(entry.getName());
            }
        }

        assertTrue(entries.contains(SqliteDesktopStateRepository.DATABASE_FILE));
        assertTrue(entries.contains("products.tsv"));
        assertTrue(entries.contains("settings.properties"));
    }

    @Test
    void handlesFiveThousandProductsWithoutLosingOrderOrStock() {
        DesktopStore store = new DesktopStore(dataDir);
        AppState state = new AppState(dataDir);
        for (int index = 0; index < 5_000; index++) {
            state.products.add(product("PRD-" + index, "Product " + index, index));
        }

        long started = System.nanoTime();
        store.save(state);
        AppState restored = store.load();
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

        assertEquals(5_000, restored.products.size());
        assertEquals("Product 0", restored.products.get(0).name);
        assertEquals(4_999, restored.products.get(4_999).stock);
        assertTrue(elapsedMillis < 20_000, "5,000-product round trip took " + elapsedMillis + " ms");
    }

    @Test
    void refusesToOpenDatabaseFromANewerSchema() throws Exception {
        Files.createDirectories(dataDir);
        Class.forName("org.sqlite.JDBC");
        Path database = dataDir.resolve(SqliteDesktopStateRepository.DATABASE_FILE);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = 99");
        }

        DesktopPersistenceException error = assertThrows(
            DesktopPersistenceException.class,
            () -> new DesktopStore(dataDir).load()
        );
        assertTrue(error.getMessage().contains("Could not load"));
    }

    private Product product(String id, String name, int stock) {
        return new Product(id, name, "", "BAR-" + id, "General", 100, 50, stock);
    }
}
