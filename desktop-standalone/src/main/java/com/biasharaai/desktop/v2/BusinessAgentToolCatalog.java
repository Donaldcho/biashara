package com.biasharaai.desktop.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

final class BusinessAgentToolCatalog {
    private static final long DAY_MILLIS = 86_400_000L;

    private final ObjectMapper mapper;
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    BusinessAgentToolCatalog(ObjectMapper mapper) {
        this.mapper = mapper;
        register("business_overview", "Read current revenue, catalog, credit, service, and cash-flow totals.", objectSchema(), this::businessOverview);
        register("inventory_risks", "Find low stock, missing cost, missing image, and weak-margin products.", limitSchema(), this::inventoryRisks);
        register("sales_velocity", "Calculate recent product unit velocity and suggested reorder quantities.", velocitySchema(), this::salesVelocity);
        register("ledger_summary", "Read money in, money out, net cash flow, and transaction counts for a period.", periodSchema(), this::ledgerSummary);
        register("ledger_anomalies", "Find duplicate-looking entries, unusually large expenses, and negative cash-flow conditions.", periodSchema(), this::ledgerAnomalies);
        register("service_queue", "Read booked and in-progress service tickets, age, assignment, and payment state.", limitSchema(), this::serviceQueue);
        register("customer_credit", "Read outstanding customer credit ordered by business exposure.", limitSchema(), this::customerCredit);
        register("sync_health", "Read phone pairing, catalog sync, stock intake, and scan freshness.", objectSchema(), this::syncHealth);
    }

    Optional<AgentTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    List<AgentToolDescriptor> descriptors(List<String> allowedNames) {
        return allowedNames.stream()
            .map(tools::get)
            .filter(tool -> tool != null)
            .map(AgentTool::descriptor)
            .toList();
    }

    private void register(
        String name,
        String description,
        JsonNode schema,
        BiFunction<JsonNode, BusinessSnapshot, JsonNode> executor
    ) {
        tools.put(name, new RegisteredTool(
            new AgentToolDescriptor(name, description, schema, AgentToolAccess.READ_ONLY),
            executor
        ));
    }

    private JsonNode businessOverview(JsonNode arguments, BusinessSnapshot snapshot) {
        long todayStart = Instant.ofEpochMilli(snapshot.capturedAtMillis())
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();
        long weekStart = snapshot.capturedAtMillis() - 7L * DAY_MILLIS;
        List<BusinessSnapshot.TransactionRecord> today = transactionsSince(snapshot, todayStart);
        List<BusinessSnapshot.TransactionRecord> week = transactionsSince(snapshot, weekStart);

        ObjectNode result = baseResult(snapshot);
        result.put("todayRevenueCents", saleRevenue(today));
        result.put("todayRevenue", money(saleRevenue(today), snapshot));
        result.put("sevenDayRevenueCents", saleRevenue(week));
        result.put("sevenDayRevenue", money(saleRevenue(week), snapshot));
        result.put("sevenDaySales", week.stream().filter(this::isSale).count());
        result.put("productRevenueCents", week.stream().filter(tx -> "SALE".equals(tx.type())).mapToLong(BusinessSnapshot.TransactionRecord::totalCents).sum());
        result.put("serviceRevenueCents", week.stream().filter(tx -> "SERVICE_SALE".equals(tx.type())).mapToLong(BusinessSnapshot.TransactionRecord::totalCents).sum());
        result.put("creditOutstandingCents", snapshot.customers().stream().mapToLong(BusinessSnapshot.CustomerRecord::balanceCents).sum());
        result.put("creditOutstanding", money(snapshot.customers().stream().mapToLong(BusinessSnapshot.CustomerRecord::balanceCents).sum(), snapshot));
        result.put("productCount", snapshot.products().size());
        result.put("serviceCount", snapshot.serviceCount());
        result.put("lowStockProducts", snapshot.products().stream().filter(product -> product.stock() <= 5).count());
        result.put("productsWithoutImages", snapshot.products().stream().filter(product -> !product.hasImage()).count());
        result.put("openServiceTickets", snapshot.serviceTickets().stream().filter(ticket -> !"COMPLETED".equals(ticket.status())).count());
        return result;
    }

    private JsonNode inventoryRisks(JsonNode arguments, BusinessSnapshot snapshot) {
        int limit = intArg(arguments, "limit", 15, 1, 50);
        List<ProductRisk> risks = snapshot.products().stream()
            .map(product -> new ProductRisk(product, riskScore(product), marginPercent(product)))
            .filter(risk -> risk.score() > 0)
            .sorted(Comparator.comparingInt(ProductRisk::score).reversed().thenComparing(risk -> risk.product().stock()))
            .limit(limit)
            .toList();
        ObjectNode result = baseResult(snapshot);
        result.put("riskCount", risks.size());
        result.put("lowStockCount", snapshot.products().stream().filter(product -> product.stock() <= 5).count());
        result.put("missingCostCount", snapshot.products().stream().filter(product -> product.costCents() <= 0).count());
        result.put("missingImageCount", snapshot.products().stream().filter(product -> !product.hasImage()).count());
        ArrayNode items = result.putArray("items");
        for (ProductRisk risk : risks) {
            BusinessSnapshot.ProductRecord product = risk.product();
            ObjectNode item = items.addObject();
            item.put("productId", product.id());
            item.put("name", product.name());
            item.put("category", product.category());
            item.put("stock", product.stock());
            item.put("priceCents", product.priceCents());
            item.put("costCents", product.costCents());
            item.put("marginPercent", risk.marginPercent());
            ArrayNode flags = item.putArray("risks");
            if (product.stock() <= 0) flags.add("out_of_stock");
            else if (product.stock() <= 5) flags.add("low_stock");
            if (product.costCents() <= 0) flags.add("missing_cost");
            else if (risk.marginPercent() < 15.0) flags.add("margin_below_15_percent");
            if (!product.hasImage()) flags.add("missing_catalog_image");
        }
        return result;
    }

    private JsonNode salesVelocity(JsonNode arguments, BusinessSnapshot snapshot) {
        int periodDays = intArg(arguments, "periodDays", 30, 1, 365);
        int targetDays = intArg(arguments, "targetDays", 14, 1, 90);
        long cutoff = snapshot.capturedAtMillis() - periodDays * DAY_MILLIS;
        Map<String, Long> transactionTimes = snapshot.transactions().stream()
            .collect(Collectors.toMap(BusinessSnapshot.TransactionRecord::id, BusinessSnapshot.TransactionRecord::createdAtMillis, Math::max));
        Map<String, Integer> units = new LinkedHashMap<>();
        for (BusinessSnapshot.SaleLineRecord line : snapshot.saleLines()) {
            long transactionTime = transactionTimes.getOrDefault(line.transactionId(), 0L);
            if ("PRODUCT".equals(line.kind()) && transactionTime >= cutoff) {
                units.merge(line.itemId(), Math.max(0, line.quantity()), Integer::sum);
            }
        }
        List<VelocityRow> rows = snapshot.products().stream()
            .map(product -> {
                int sold = units.getOrDefault(product.id(), 0);
                double daily = sold / (double) periodDays;
                int reorder = Math.max(0, (int) Math.ceil(daily * targetDays) - product.stock());
                return new VelocityRow(product, sold, daily, reorder);
            })
            .filter(row -> row.unitsSold() > 0 || row.product().stock() <= 5)
            .sorted(Comparator.comparingInt(VelocityRow::suggestedReorder).reversed()
                .thenComparing(Comparator.comparingInt(VelocityRow::unitsSold).reversed()))
            .limit(30)
            .toList();
        ObjectNode result = baseResult(snapshot);
        result.put("periodDays", periodDays);
        result.put("targetCoverDays", targetDays);
        result.put("reorderCount", rows.stream().filter(row -> row.suggestedReorder() > 0).count());
        ArrayNode items = result.putArray("items");
        for (VelocityRow row : rows) {
            ObjectNode item = items.addObject();
            item.put("productId", row.product().id());
            item.put("name", row.product().name());
            item.put("stock", row.product().stock());
            item.put("unitsSold", row.unitsSold());
            item.put("dailyVelocity", round(row.dailyVelocity()));
            item.put("suggestedReorder", row.suggestedReorder());
        }
        return result;
    }

    private JsonNode ledgerSummary(JsonNode arguments, BusinessSnapshot snapshot) {
        int periodDays = intArg(arguments, "periodDays", 30, 1, 3650);
        List<BusinessSnapshot.TransactionRecord> rows = transactionsSince(snapshot, snapshot.capturedAtMillis() - periodDays * DAY_MILLIS);
        long moneyIn = rows.stream().mapToLong(this::cashIn).sum();
        long moneyOut = rows.stream().mapToLong(this::cashOut).sum();
        ObjectNode result = baseResult(snapshot);
        result.put("periodDays", periodDays);
        result.put("transactionCount", rows.size());
        result.put("moneyInCents", moneyIn);
        result.put("moneyIn", money(moneyIn, snapshot));
        result.put("moneyOutCents", moneyOut);
        result.put("moneyOut", money(moneyOut, snapshot));
        result.put("netCents", moneyIn - moneyOut);
        result.put("net", money(moneyIn - moneyOut, snapshot));
        result.put("creditOutstandingCents", snapshot.customers().stream().mapToLong(BusinessSnapshot.CustomerRecord::balanceCents).sum());
        return result;
    }

    private JsonNode ledgerAnomalies(JsonNode arguments, BusinessSnapshot snapshot) {
        int periodDays = intArg(arguments, "periodDays", 30, 1, 3650);
        List<BusinessSnapshot.TransactionRecord> rows = transactionsSince(snapshot, snapshot.capturedAtMillis() - periodDays * DAY_MILLIS).stream()
            .sorted(Comparator.comparingLong(BusinessSnapshot.TransactionRecord::createdAtMillis))
            .toList();
        List<BusinessSnapshot.TransactionRecord> expenses = rows.stream().filter(tx -> "EXPENSE".equals(tx.type())).toList();
        double averageExpense = expenses.stream().mapToLong(tx -> Math.abs(tx.totalCents())).average().orElse(0.0);
        long largeThreshold = Math.max(100_000L, Math.round(averageExpense * 3.0));
        ArrayNode findings = mapper.createArrayNode();
        for (BusinessSnapshot.TransactionRecord expense : expenses) {
            if (Math.abs(expense.totalCents()) >= largeThreshold && expenses.size() > 1) {
                ObjectNode finding = findings.addObject();
                finding.put("type", "large_expense");
                finding.put("transactionId", expense.id());
                finding.put("description", expense.description());
                finding.put("amountCents", Math.abs(expense.totalCents()));
                finding.put("amount", money(Math.abs(expense.totalCents()), snapshot));
            }
        }
        for (int index = 1; index < rows.size(); index++) {
            BusinessSnapshot.TransactionRecord previous = rows.get(index - 1);
            BusinessSnapshot.TransactionRecord current = rows.get(index);
            boolean sameShape = current.type().equals(previous.type())
                && Math.abs(current.totalCents()) == Math.abs(previous.totalCents())
                && normalize(current.description()).equals(normalize(previous.description()))
                && normalize(current.paymentMethod()).equals(normalize(previous.paymentMethod()));
            if (sameShape && current.createdAtMillis() - previous.createdAtMillis() <= 5 * 60_000L) {
                ObjectNode finding = findings.addObject();
                finding.put("type", "possible_duplicate");
                finding.put("transactionId", current.id());
                finding.put("relatedTransactionId", previous.id());
                finding.put("description", current.description());
                finding.put("amountCents", Math.abs(current.totalCents()));
            }
        }
        long net = rows.stream().mapToLong(this::cashIn).sum() - rows.stream().mapToLong(this::cashOut).sum();
        if (net < 0) {
            ObjectNode finding = findings.addObject();
            finding.put("type", "negative_cash_flow");
            finding.put("netCents", net);
            finding.put("net", money(net, snapshot));
        }
        ObjectNode result = baseResult(snapshot);
        result.put("periodDays", periodDays);
        result.put("anomalyCount", findings.size());
        result.put("reviewRequired", !findings.isEmpty());
        result.set("findings", findings);
        return result;
    }

    private JsonNode serviceQueue(JsonNode arguments, BusinessSnapshot snapshot) {
        int limit = intArg(arguments, "limit", 20, 1, 50);
        List<BusinessSnapshot.ServiceTicketRecord> open = snapshot.serviceTickets().stream()
            .filter(ticket -> !"COMPLETED".equals(ticket.status()))
            .sorted(Comparator.comparingLong(BusinessSnapshot.ServiceTicketRecord::createdAtMillis))
            .limit(limit)
            .toList();
        ObjectNode result = baseResult(snapshot);
        result.put("openCount", open.size());
        result.put("bookedCount", open.stream().filter(ticket -> "BOOKED".equals(ticket.status())).count());
        result.put("inProgressCount", open.stream().filter(ticket -> "IN_PROGRESS".equals(ticket.status())).count());
        result.put("unassignedCount", open.stream().filter(ticket -> ticket.assignedTechnician().isBlank() && ticket.activeTechnician().isBlank()).count());
        ArrayNode tickets = result.putArray("tickets");
        for (BusinessSnapshot.ServiceTicketRecord ticket : open) {
            ObjectNode item = tickets.addObject();
            item.put("ticketId", ticket.id());
            item.put("service", ticket.serviceName());
            item.put("customer", ticket.customerName());
            item.put("status", ticket.status());
            item.put("technician", !ticket.activeTechnician().isBlank() ? ticket.activeTechnician() : ticket.assignedTechnician());
            item.put("ageHours", Math.max(0, (snapshot.capturedAtMillis() - ticket.createdAtMillis()) / 3_600_000L));
            item.put("balanceCents", Math.max(0, ticket.totalCents() - ticket.paidCents()));
        }
        return result;
    }

    private JsonNode customerCredit(JsonNode arguments, BusinessSnapshot snapshot) {
        int limit = intArg(arguments, "limit", 20, 1, 50);
        List<BusinessSnapshot.CustomerRecord> debtors = snapshot.customers().stream()
            .filter(customer -> customer.balanceCents() > 0)
            .sorted(Comparator.comparingLong(BusinessSnapshot.CustomerRecord::balanceCents).reversed())
            .limit(limit)
            .toList();
        ObjectNode result = baseResult(snapshot);
        long total = debtors.stream().mapToLong(BusinessSnapshot.CustomerRecord::balanceCents).sum();
        result.put("debtorCount", debtors.size());
        result.put("totalOutstandingCents", total);
        result.put("totalOutstanding", money(total, snapshot));
        ArrayNode customers = result.putArray("customers");
        for (BusinessSnapshot.CustomerRecord customer : debtors) {
            ObjectNode item = customers.addObject();
            item.put("customerId", customer.id());
            item.put("name", customer.name());
            item.put("maskedPhone", customer.maskedPhone());
            item.put("balanceCents", customer.balanceCents());
            item.put("balance", money(customer.balanceCents(), snapshot));
            item.put("visits", customer.visits());
        }
        return result;
    }

    private JsonNode syncHealth(JsonNode arguments, BusinessSnapshot snapshot) {
        BusinessSnapshot.SyncRecord sync = snapshot.sync();
        long latest = Math.max(sync.latestProductSyncMillis(), Math.max(sync.latestStockSyncMillis(), sync.latestScanMillis()));
        long ageMinutes = latest <= 0 ? -1 : Math.max(0, (snapshot.capturedAtMillis() - latest) / 60_000L);
        String status = !sync.phonePaired() ? "NOT_PAIRED"
            : latest <= 0 ? "PAIRED_NO_ACTIVITY"
            : ageMinutes > 24 * 60 ? "STALE"
            : "HEALTHY";
        ObjectNode result = baseResult(snapshot);
        result.put("status", status);
        result.put("phonePaired", sync.phonePaired());
        result.put("pairedDevice", sync.pairedDevice());
        result.put("productSyncRecords", sync.productSyncCount());
        result.put("stockIntakeRecords", sync.stockSyncCount());
        result.put("scanEvents", sync.scanCount());
        result.put("lastActivityAgeMinutes", ageMinutes);
        return result;
    }

    private ObjectNode baseResult(BusinessSnapshot snapshot) {
        ObjectNode result = mapper.createObjectNode();
        result.put("businessName", snapshot.businessName());
        result.put("currency", snapshot.currency());
        result.put("capturedAtMillis", snapshot.capturedAtMillis());
        return result;
    }

    private List<BusinessSnapshot.TransactionRecord> transactionsSince(BusinessSnapshot snapshot, long cutoff) {
        return snapshot.transactions().stream().filter(transaction -> transaction.createdAtMillis() >= cutoff).toList();
    }

    private long saleRevenue(List<BusinessSnapshot.TransactionRecord> transactions) {
        return transactions.stream().filter(this::isSale).mapToLong(BusinessSnapshot.TransactionRecord::totalCents).sum();
    }

    private boolean isSale(BusinessSnapshot.TransactionRecord transaction) {
        return "SALE".equals(transaction.type()) || "SERVICE_SALE".equals(transaction.type());
    }

    private long cashIn(BusinessSnapshot.TransactionRecord transaction) {
        return switch (transaction.type()) {
            case "SALE", "SERVICE_SALE", "PAYMENT" -> Math.max(0, transaction.paidCents());
            case "ADJUSTMENT" -> Math.max(0, transaction.totalCents());
            default -> 0L;
        };
    }

    private long cashOut(BusinessSnapshot.TransactionRecord transaction) {
        return switch (transaction.type()) {
            case "EXPENSE" -> Math.abs(transaction.totalCents());
            case "ADJUSTMENT" -> Math.max(0, -transaction.totalCents());
            default -> 0L;
        };
    }

    private int riskScore(BusinessSnapshot.ProductRecord product) {
        int score = 0;
        if (product.stock() <= 0) score += 5;
        else if (product.stock() <= 5) score += 3;
        if (product.costCents() <= 0) score += 2;
        else if (marginPercent(product) < 15.0) score += 2;
        if (!product.hasImage()) score += 1;
        return score;
    }

    private double marginPercent(BusinessSnapshot.ProductRecord product) {
        if (product.priceCents() <= 0 || product.costCents() <= 0) return 0.0;
        return round((product.priceCents() - product.costCents()) * 100.0 / product.priceCents());
    }

    private String money(long cents, BusinessSnapshot snapshot) {
        return Money.format(cents, snapshot.currency());
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private int intArg(JsonNode arguments, String name, int fallback, int minimum, int maximum) {
        int value = arguments != null && arguments.has(name) ? arguments.path(name).asInt(fallback) : fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }

    private ObjectNode objectSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", mapper.createObjectNode());
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode limitSchema() {
        ObjectNode schema = objectSchema();
        ObjectNode limit = schema.withObject("properties").putObject("limit");
        limit.put("type", "integer");
        limit.put("minimum", 1);
        limit.put("maximum", 50);
        limit.put("description", "Maximum rows to return.");
        return schema;
    }

    private ObjectNode periodSchema() {
        ObjectNode schema = objectSchema();
        ObjectNode period = schema.withObject("properties").putObject("periodDays");
        period.put("type", "integer");
        period.put("minimum", 1);
        period.put("maximum", 3650);
        period.put("description", "Number of recent calendar days to inspect.");
        return schema;
    }

    private ObjectNode velocitySchema() {
        ObjectNode schema = periodSchema();
        schema.withObject("properties").withObject("periodDays").put("maximum", 365);
        ObjectNode target = schema.withObject("properties").putObject("targetDays");
        target.put("type", "integer");
        target.put("minimum", 1);
        target.put("maximum", 90);
        target.put("description", "Desired stock-cover days.");
        return schema;
    }

    private record RegisteredTool(
        AgentToolDescriptor descriptor,
        BiFunction<JsonNode, BusinessSnapshot, JsonNode> executor
    ) implements AgentTool {
        @Override
        public JsonNode execute(JsonNode arguments, BusinessSnapshot snapshot) {
            return executor.apply(arguments, snapshot);
        }
    }

    private record ProductRisk(BusinessSnapshot.ProductRecord product, int score, double marginPercent) {}

    private record VelocityRow(BusinessSnapshot.ProductRecord product, int unitsSold, double dailyVelocity, int suggestedReorder) {}
}
