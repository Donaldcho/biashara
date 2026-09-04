package com.biasharaai.desktop.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class AgentApplicationService {
    private static final int MAX_MODEL_TURNS = 5;
    private static final int MAX_TOOL_CALLS = 8;

    private final BusinessAgentToolCatalog toolCatalog;
    private final AgentLanguageModel languageModel;
    private final AgentRunRepository repository;
    private final ObjectMapper mapper;
    private final List<AgentDefinition> definitions;

    AgentApplicationService(
        BusinessAgentToolCatalog toolCatalog,
        AgentLanguageModel languageModel,
        AgentRunRepository repository,
        ObjectMapper mapper
    ) {
        this(toolCatalog, languageModel, repository, mapper, defaultDefinitions());
    }

    AgentApplicationService(
        BusinessAgentToolCatalog toolCatalog,
        AgentLanguageModel languageModel,
        AgentRunRepository repository,
        ObjectMapper mapper,
        List<AgentDefinition> definitions
    ) {
        this.toolCatalog = toolCatalog;
        this.languageModel = languageModel;
        this.repository = repository;
        this.mapper = mapper;
        this.definitions = List.copyOf(definitions);
    }

    AgentCenterState centerState() throws IOException {
        return new AgentCenterState(definitions, repository.recent(40));
    }

    AgentRun run(String agentId, BusinessSnapshot snapshot, Settings settings) throws IOException {
        AgentDefinition agent = definitions.stream()
            .filter(item -> item.id().equals(agentId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown agent: " + agentId));
        long startedAt = System.currentTimeMillis();
        List<AgentToolTrace> traces = new ArrayList<>();
        String provider = "RULES";
        String status = "FALLBACK";
        String summary = "";
        String modelError = "";

        if ("LM_STUDIO".equalsIgnoreCase(settings.aiProvider)) {
            provider = "LM_STUDIO";
            try {
                summary = runWithModel(agent, snapshot, settings, traces);
                status = "COMPLETED";
            } catch (Exception ex) {
                modelError = safeMessage(ex);
            }
        }

        if (summary.isBlank()) {
            executeDefaults(agent, snapshot, traces);
            summary = fallbackSummary(agent, traces, snapshot, modelError);
        }

        AgentRun run = new AgentRun(
            "ARN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT),
            agent.id(),
            agent.name(),
            provider,
            status,
            summary,
            startedAt,
            System.currentTimeMillis(),
            traces,
            modelError
        );
        repository.save(run);
        return run;
    }

    private String runWithModel(
        AgentDefinition agent,
        BusinessSnapshot snapshot,
        Settings settings,
        List<AgentToolTrace> traces
    ) throws IOException {
        List<AgentModelMessage> messages = new ArrayList<>();
        messages.add(AgentModelMessage.user(
            "Run this agent now against the current local business snapshot. Investigate the objective, cite exact tool evidence, and give prioritized next actions."
        ));
        List<AgentToolDescriptor> descriptors = toolCatalog.descriptors(agent.allowedTools());
        int toolCalls = 0;
        for (int turnNumber = 0; turnNumber < MAX_MODEL_TURNS; turnNumber++) {
            AgentModelTurn turn = languageModel.nextTurn(settings, agent, List.copyOf(messages), descriptors);
            messages.add(AgentModelMessage.assistant(turn.content(), turn.toolCalls()));
            if (turn.toolCalls().isEmpty()) {
                if (traces.stream().noneMatch(trace -> "COMPLETED".equals(trace.status()))) {
                    throw new IOException("The local model returned a report without inspecting any approved business tool.");
                }
                if (turn.content().isBlank()) {
                    throw new IOException("The local model returned an empty agent report.");
                }
                return turn.content();
            }
            for (AgentRequestedTool call : turn.toolCalls()) {
                toolCalls++;
                if (toolCalls > MAX_TOOL_CALLS) {
                    throw new IOException("The local model exceeded the agent tool-call limit.");
                }
                AgentToolTrace trace = executeRequestedTool(agent, call, snapshot);
                traces.add(trace);
                JsonNode toolResponse = "COMPLETED".equals(trace.status())
                    ? trace.result()
                    : errorNode(trace.error());
                messages.add(AgentModelMessage.tool(call.callId(), call.name(), compact(toolResponse)));
            }
        }
        throw new IOException("The local model did not finish the agent run within the turn limit.");
    }

    private AgentToolTrace executeRequestedTool(
        AgentDefinition agent,
        AgentRequestedTool call,
        BusinessSnapshot snapshot
    ) {
        long startedAt = System.currentTimeMillis();
        JsonNode arguments = call.arguments() == null ? mapper.createObjectNode() : call.arguments();
        if (!agent.allowedTools().contains(call.name())) {
            return new AgentToolTrace(
                call.name(),
                AgentToolAccess.READ_ONLY,
                "DENIED",
                startedAt,
                System.currentTimeMillis() - startedAt,
                arguments,
                mapper.createObjectNode(),
                "Tool is not allowed for " + agent.name() + "."
            );
        }
        AgentTool tool = toolCatalog.find(call.name()).orElse(null);
        if (tool == null) {
            return new AgentToolTrace(
                call.name(),
                AgentToolAccess.READ_ONLY,
                "DENIED",
                startedAt,
                System.currentTimeMillis() - startedAt,
                arguments,
                mapper.createObjectNode(),
                "Tool is not registered."
            );
        }
        try {
            JsonNode result = tool.execute(arguments, snapshot);
            return new AgentToolTrace(
                call.name(),
                tool.descriptor().access(),
                "COMPLETED",
                startedAt,
                System.currentTimeMillis() - startedAt,
                arguments,
                result,
                ""
            );
        } catch (Exception ex) {
            return new AgentToolTrace(
                call.name(),
                tool.descriptor().access(),
                "FAILED",
                startedAt,
                System.currentTimeMillis() - startedAt,
                arguments,
                mapper.createObjectNode(),
                safeMessage(ex)
            );
        }
    }

    private void executeDefaults(AgentDefinition agent, BusinessSnapshot snapshot, List<AgentToolTrace> traces) {
        Set<String> completed = traces.stream()
            .filter(trace -> "COMPLETED".equals(trace.status()))
            .map(AgentToolTrace::toolName)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (String toolName : agent.defaultTools()) {
            if (completed.contains(toolName)) continue;
            traces.add(executeRequestedTool(
                agent,
                new AgentRequestedTool("fallback-" + toolName, toolName, mapper.createObjectNode()),
                snapshot
            ));
        }
    }

    private String fallbackSummary(
        AgentDefinition agent,
        List<AgentToolTrace> traces,
        BusinessSnapshot snapshot,
        String modelError
    ) {
        String report = switch (agent.id()) {
            case "business-manager" -> businessManagerSummary(traces, snapshot);
            case "inventory-planner" -> inventorySummary(traces);
            case "ledger-auditor" -> ledgerSummary(traces);
            case "service-coordinator" -> serviceSummary(traces);
            case "credit-collector" -> creditSummary(traces);
            case "sync-reconciliation" -> syncSummary(traces);
            default -> "The approved local tools completed. Review the evidence in the tool trace before taking action.";
        };
        if (!modelError.isBlank()) {
            return report + "\n\nLocal AI note: LM Studio could not complete this run, so the deterministic evidence report was used. " + modelError;
        }
        return report;
    }

    private String businessManagerSummary(List<AgentToolTrace> traces, BusinessSnapshot snapshot) {
        JsonNode overview = resultFor(traces, "business_overview");
        JsonNode sync = resultFor(traces, "sync_health");
        return "Business review for " + displayBusiness(snapshot) +
            ": today revenue is " + overview.path("todayRevenue").asText("0") +
            ", seven-day revenue is " + overview.path("sevenDayRevenue").asText("0") +
            ", outstanding credit is " + overview.path("creditOutstanding").asText("0") +
            ", and " + overview.path("lowStockProducts").asInt(0) + " product(s) need stock attention. " +
            "There are " + overview.path("openServiceTickets").asInt(0) + " open service ticket(s). " +
            "Phone sync status is " + sync.path("status").asText("UNKNOWN") +
            ". Prioritize stock exceptions, overdue service work, and customer credit before discretionary changes.";
    }

    private String inventorySummary(List<AgentToolTrace> traces) {
        JsonNode risks = resultFor(traces, "inventory_risks");
        JsonNode velocity = resultFor(traces, "sales_velocity");
        return "Inventory review found " + risks.path("riskCount").asInt(0) + " product risk(s), including " +
            risks.path("lowStockCount").asInt(0) + " low-stock item(s), " +
            risks.path("missingCostCount").asInt(0) + " missing cost value(s), and " +
            risks.path("missingImageCount").asInt(0) + " missing catalog image(s). " +
            velocity.path("reorderCount").asInt(0) + " product(s) have a positive reorder suggestion for " +
            velocity.path("targetCoverDays").asInt(14) + " days of cover. Review: " + names(risks.path("items"), 3) + ".";
    }

    private String ledgerSummary(List<AgentToolTrace> traces) {
        JsonNode totals = resultFor(traces, "ledger_summary");
        JsonNode anomalies = resultFor(traces, "ledger_anomalies");
        return "Ledger review covered " + totals.path("transactionCount").asInt(0) + " transaction(s). Money in is " +
            totals.path("moneyIn").asText("0") + ", money out is " + totals.path("moneyOut").asText("0") +
            ", and net cash flow is " + totals.path("net").asText("0") + ". " +
            anomalies.path("anomalyCount").asInt(0) + " item(s) require review. These are review signals, not automatic corrections.";
    }

    private String serviceSummary(List<AgentToolTrace> traces) {
        JsonNode queue = resultFor(traces, "service_queue");
        return "The service queue has " + queue.path("openCount").asInt(0) + " open ticket(s): " +
            queue.path("bookedCount").asInt(0) + " booked and " + queue.path("inProgressCount").asInt(0) +
            " in progress. " + queue.path("unassignedCount").asInt(0) +
            " ticket(s) have no technician. Assign the oldest unassigned work first and confirm payment balances before completion.";
    }

    private String creditSummary(List<AgentToolTrace> traces) {
        JsonNode credit = resultFor(traces, "customer_credit");
        return "Customer credit review found " + credit.path("debtorCount").asInt(0) + " account(s) owing " +
            credit.path("totalOutstanding").asText("0") + ". Prioritize the largest balances and review each customer record before sending a reminder. " +
            "Phone numbers remain masked in the agent audit trail.";
    }

    private String syncSummary(List<AgentToolTrace> traces) {
        JsonNode sync = resultFor(traces, "sync_health");
        return "Mobile reconciliation status is " + sync.path("status").asText("UNKNOWN") + ". The desktop has " +
            sync.path("productSyncRecords").asInt(0) + " catalog sync record(s), " +
            sync.path("stockIntakeRecords").asInt(0) + " stock intake record(s), and " +
            sync.path("scanEvents").asInt(0) + " scanner event(s). " +
            "Keep both devices on the same network and run reconciliation before starting sales on a second device.";
    }

    private JsonNode resultFor(List<AgentToolTrace> traces, String toolName) {
        return traces.stream()
            .filter(trace -> toolName.equals(trace.toolName()) && "COMPLETED".equals(trace.status()))
            .map(AgentToolTrace::result)
            .findFirst()
            .orElse(mapper.createObjectNode());
    }

    private String names(JsonNode items, int limit) {
        List<String> names = new ArrayList<>();
        if (items.isArray()) {
            for (JsonNode item : items) {
                String name = item.path("name").asText();
                if (!name.isBlank()) names.add(name);
                if (names.size() >= limit) break;
            }
        }
        return names.isEmpty() ? "no named exceptions" : String.join(", ", names);
    }

    private String displayBusiness(BusinessSnapshot snapshot) {
        return snapshot.businessName().isBlank() ? "the business" : snapshot.businessName();
    }

    private ObjectNode errorNode(String message) {
        ObjectNode error = mapper.createObjectNode();
        error.put("error", message);
        return error;
    }

    private String compact(JsonNode node) throws IOException {
        String value = mapper.writeValueAsString(node);
        return value.length() <= 12_000 ? value : value.substring(0, 12_000) + "...";
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message.trim();
    }

    private static List<AgentDefinition> defaultDefinitions() {
        return List.of(
            new AgentDefinition(
                "business-manager",
                "Business manager",
                "Prioritizes the day using revenue, credit, stock, services, and device health.",
                "Produce an evidence-based operating review and identify the three highest-value actions for the owner.",
                "briefcase",
                List.of("business_overview", "inventory_risks", "ledger_summary", "service_queue", "customer_credit", "sync_health"),
                List.of("business_overview", "sync_health")
            ),
            new AgentDefinition(
                "inventory-planner",
                "Inventory planner",
                "Finds stock, margin, cost, image, and replenishment risks.",
                "Inspect inventory risk and recent product velocity, then recommend a prioritized reorder review without changing stock.",
                "package",
                List.of("inventory_risks", "sales_velocity"),
                List.of("inventory_risks", "sales_velocity")
            ),
            new AgentDefinition(
                "ledger-auditor",
                "Ledger auditor",
                "Reviews cash flow and suspicious ledger patterns without changing records.",
                "Inspect recent cash flow, duplicate-looking records, large expenses, and negative-flow conditions.",
                "shield",
                List.of("ledger_summary", "ledger_anomalies", "customer_credit"),
                List.of("ledger_summary", "ledger_anomalies")
            ),
            new AgentDefinition(
                "service-coordinator",
                "Service coordinator",
                "Prioritizes booked, in-progress, unassigned, and unpaid service work.",
                "Inspect the live service queue and identify operational bottlenecks for the cashier and technicians.",
                "clipboard",
                List.of("service_queue"),
                List.of("service_queue")
            ),
            new AgentDefinition(
                "credit-collector",
                "Credit collection",
                "Prioritizes outstanding customer balances while protecting customer data.",
                "Inspect customer credit exposure and recommend a responsible follow-up order without contacting anyone.",
                "users",
                List.of("customer_credit", "ledger_summary"),
                List.of("customer_credit")
            ),
            new AgentDefinition(
                "sync-reconciliation",
                "Sync reconciliation",
                "Checks whether phone pairing and recent synchronization activity are healthy.",
                "Inspect pairing and synchronization freshness, then explain any action needed before multi-device selling.",
                "refresh",
                List.of("sync_health", "business_overview"),
                List.of("sync_health")
            )
        );
    }
}
