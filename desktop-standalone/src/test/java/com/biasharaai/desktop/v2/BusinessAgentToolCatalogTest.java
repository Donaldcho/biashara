package com.biasharaai.desktop.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessAgentToolCatalogTest {
    private ObjectMapper mapper;
    private BusinessAgentToolCatalog catalog;
    private BusinessSnapshot snapshot;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        catalog = new BusinessAgentToolCatalog(mapper);
        snapshot = AgentTestFixtures.snapshot();
    }

    @Test
    void inventoryToolsCalculateRisksAndReorderQuantity() {
        JsonNode risks = execute("inventory_risks", mapper.createObjectNode().put("limit", 10));
        ObjectNode velocityInput = mapper.createObjectNode();
        velocityInput.put("periodDays", 10);
        velocityInput.put("targetDays", 14);
        JsonNode velocity = execute("sales_velocity", velocityInput);

        assertEquals(2, risks.path("riskCount").asInt());
        assertEquals(1, risks.path("lowStockCount").asInt());
        assertEquals("Fast Item", risks.path("items").path(0).path("name").asText());
        assertEquals(13, velocity.path("items").path(0).path("suggestedReorder").asInt());
    }

    @Test
    void operationalToolsReturnLedgerCreditServiceAndSyncEvidence() {
        JsonNode ledger = execute("ledger_summary", mapper.createObjectNode().put("periodDays", 30));
        JsonNode credit = execute("customer_credit", mapper.createObjectNode());
        JsonNode services = execute("service_queue", mapper.createObjectNode());
        JsonNode sync = execute("sync_health", mapper.createObjectNode());

        assertEquals(20_000, ledger.path("moneyInCents").asLong());
        assertEquals(2_000, ledger.path("moneyOutCents").asLong());
        assertEquals(5_000, credit.path("totalOutstandingCents").asLong());
        assertEquals("***1234", credit.path("customers").path(0).path("maskedPhone").asText());
        assertEquals(1, services.path("openCount").asInt());
        assertEquals(1, services.path("unassignedCount").asInt());
        assertEquals("HEALTHY", sync.path("status").asText());
        assertTrue(catalog.descriptors(List.of("sync_health")).stream()
            .allMatch(tool -> tool.access() == AgentToolAccess.READ_ONLY));
    }

    private JsonNode execute(String name, JsonNode arguments) {
        return catalog.find(name).orElseThrow().execute(arguments, snapshot);
    }
}
