package com.biasharaai.desktop.v2;
import com.biasharaai.sync.SyncProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class BiasharaDesktopWebApp {
    private static final int DISCOVERY_PORT = 8864;
    private static final String DISCOVERY_PREFIX = "BIASHARA_DESKTOP_V1 ";
    private static final Set<String> AFRICAN_CURRENCY_CODES = Set.of(
        "XAF", "XOF", "DZD", "AOA", "BWP", "BIF", "CVE", "KMF", "CDF", "DJF",
        "EGP", "ERN", "SZL", "ETB", "GMD", "GHS", "GNF", "KES", "LSL", "LRD",
        "LYD", "MGA", "MWK", "MUR", "MRU", "MAD", "MZN", "NAD", "NGN", "RWF",
        "STN", "SCR", "SLE", "SOS", "ZAR", "SSP", "SDG", "TZS", "TND", "UGX",
        "ZMW", "ZWG"
    );

    private final DesktopStore store;
    private final AppState state;
    private final int uiPort;
    private final int phonePort;
    private final AssistantAdvisor advisor = new AssistantAdvisor();
    private final LmStudioClient lmStudioClient = new LmStudioClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SyncInboxService syncInboxService = new SyncInboxService(objectMapper);
    private final PhoneRequestAuthenticator phoneRequestAuthenticator = new PhoneRequestAuthenticator();
    private final PairingAttemptLimiter pairingAttemptLimiter = new PairingAttemptLimiter();
    private final AgentApplicationService agentService;
    private volatile String pairToken = newPairToken();
    private volatile String sessionKey;
    private volatile String pairedDevice;
    private volatile boolean signedPhoneRequestsRequired;
    private ScheduledExecutorService discoveryExecutor;

    private BiasharaDesktopWebApp(DesktopStore store, AppState state, int uiPort, int phonePort) {
        this.store = store;
        this.state = state;
        this.uiPort = uiPort;
        this.phonePort = phonePort;
        String[] bridgeSession = store.loadBridgeSession();
        this.sessionKey = bridgeSession[0];
        this.pairedDevice = bridgeSession[1];
        this.signedPhoneRequestsRequired = bridgeSession.length >= 3
            && SyncProtocol.CURRENT_VERSION.equals(bridgeSession[2]);
        this.agentService = new AgentApplicationService(
            new BusinessAgentToolCatalog(objectMapper),
            new LmStudioAgentModel(lmStudioClient, objectMapper),
            new FileAgentRunRepository(store.dataDir().resolve("agent-runs.jsonl"), objectMapper),
            objectMapper
        );
    }

    public static void main(String[] args) throws Exception {
        Path dataDir = Path.of(System.getProperty("user.home"), ".biasharaai-desktop-pro");
        DesktopStore store = new DesktopStore(dataDir);
        AppState state = store.load();
        List<String> arguments = List.of(args);
        int uiPort = availablePort("127.0.0.1", 8765, 8775);
        int phonePort = arguments.contains("--no-phone") ? -1 : availablePort("0.0.0.0", 8865, 8875);
        BiasharaDesktopWebApp app = new BiasharaDesktopWebApp(store, state, uiPort, phonePort);
        HttpServer uiServer = HttpServer.create(new InetSocketAddress("127.0.0.1", uiPort), 0);
        uiServer.createContext("/", app::route);
        uiServer.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "biashara-desktop-web");
            thread.setDaemon(true);
            return thread;
        }));
        uiServer.start();

        if (phonePort > 0) {
            HttpServer phoneServer = HttpServer.create(new InetSocketAddress("0.0.0.0", phonePort), 0);
            phoneServer.createContext("/", app::phoneRoute);
            phoneServer.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "biashara-phone-sync");
                thread.setDaemon(true);
                return thread;
            }));
            phoneServer.start();
            app.startDiscoveryBeacon();
        }

        URI uri = URI.create("http://127.0.0.1:" + uiPort + "/");
        boolean openBrowser = !arguments.contains("--no-open");
        if (openBrowser && Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(uri);
        }
        System.out.println("Biashara AI Pro Desktop running at " + uri);
        if (phonePort > 0) {
            System.out.println("Phone sync bridge running at http://" + app.localHost() + ":" + phonePort);
        } else {
            System.out.println("Phone sync bridge disabled for this run.");
        }
        Thread.currentThread().join();
    }

    private static int availablePort(String host, int start, int end) throws IOException {
        IOException last = null;
        for (int port = start; port <= end; port++) {
            try (java.net.ServerSocket socket = new java.net.ServerSocket()) {
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(host, port));
                return port;
            } catch (IOException ex) {
                last = ex;
            }
        }
        throw last == null ? new IOException("No port available.") : last;
    }

    private void phoneRoute(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/api/phone/pair")) {
                requireMethod(exchange, "POST");
                pairPhone(exchange);
                return;
            }
            if (path.equals("/api/phone/discovery")) {
                requireMethod(exchange, "GET");
                sendJson(exchange, 200, pairingJsonRaw());
                return;
            }
            if (path.equals("/api/phone/capabilities")) {
                requireMethod(exchange, "GET");
                sendJson(exchange, 200, phoneCapabilitiesJson());
                return;
            }
            if (path.equals("/api/phone/scan")) {
                requireMethod(exchange, "POST");
                phoneScan(exchange);
                return;
            }
            if (path.equals("/api/phone/product-sync")) {
                requireMethod(exchange, "POST");
                productSync(exchange);
                return;
            }
            if (path.equals("/api/phone/transaction-sync")) {
                requireMethod(exchange, "POST");
                transactionSync(exchange);
                return;
            }
            if (path.equals("/api/phone/reconcile")) {
                requireMethod(exchange, "POST");
                phoneReconcile(exchange);
                return;
            }
            if (path.equals("/api/phone/stock-intake")) {
                requireMethod(exchange, "POST");
                stockIntake(exchange);
                return;
            }
            sendJson(exchange, 404, "{\"error\":\"Phone bridge route only\"}");
        } catch (PhoneAuthenticationException ex) {
            sendJson(exchange, ex.status(), "{\"error\":\"" + json(ex.getMessage()) + "\"}");
        } catch (Exception ex) {
            sendJson(exchange, 500, "{\"error\":\"" + json(ex.getMessage()) + "\"}");
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                routeApi(exchange, path);
                return;
            }
            if (path.startsWith("/media")) {
                serveMedia(exchange);
                return;
            }
            serveAsset(exchange, path);
        } catch (PhoneAuthenticationException ex) {
            sendJson(exchange, ex.status(), "{\"error\":\"" + json(ex.getMessage()) + "\"}");
        } catch (Exception ex) {
            sendJson(exchange, 500, "{\"error\":\"" + json(ex.getMessage()) + "\"}");
        }
    }

    private void routeApi(HttpExchange exchange, String path) throws IOException {
        if (path.equals("/api/state")) {
            requireMethod(exchange, "GET");
            sendJson(exchange, 200, stateJson());
            return;
        }
        if (path.equals("/api/settings")) {
            requireMethod(exchange, "POST");
            String body = readBody(exchange);
            synchronized (this) {
                updateSettings(body);
                persist();
            }
            sendJson(exchange, 200, stateJson());
            return;
        }
        if (path.equals("/api/assistant")) {
            requireMethod(exchange, "POST");
            answerAssistant(exchange);
            return;
        }
        if (path.equals("/api/assistant/stream")) {
            requireMethod(exchange, "POST");
            streamAssistant(exchange);
            return;
        }
        if (path.equals("/api/ai/test")) {
            requireMethod(exchange, "POST");
            testAi(exchange);
            return;
        }
        if (path.equals("/api/agents")) {
            requireMethod(exchange, "GET");
            sendJson(exchange, 200, objectMapper.writeValueAsString(agentService.centerState()));
            return;
        }
        if (path.equals("/api/agents/run")) {
            requireMethod(exchange, "POST");
            runBusinessAgent(exchange);
            return;
        }
        if (path.equals("/api/product")) {
            requireMethod(exchange, "POST");
            String body = readBody(exchange);
            synchronized (this) {
                addProduct(body);
                persist();
            }
            sendJson(exchange, 200, stateJson());
            return;
        }
        if (path.equals("/api/import/product-sync")) {
            requireMethod(exchange, "POST");
            String body = readBody(exchange);
            synchronized (this) {
                importProductSync(body);
                persist();
            }
            sendJson(exchange, 200, stateJson());
            return;
        }
        if (path.equals("/api/service")) {
            requireMethod(exchange, "POST");
            String body = readBody(exchange);
            synchronized (this) {
                addService(body);
                persist();
            }
            sendJson(exchange, 200, stateJson());
            return;
        }
        if (path.equals("/api/service-ticket/book")) {
            requireMethod(exchange, "POST");
            String body = readBody(exchange);
            synchronized (this) {
                bookServiceTicket(body);
                persist();
            }
            sendJson(exchange, 200, stateJson());
            return;
        }
        if (path.equals("/api/service-ticket/lookup")) {
            requireMethod(exchange, "POST");
            ServiceTicket ticket = findServiceTicket(str(readBody(exchange), "token"));
            sendJson(exchange, 200, "{\"ticket\":" + serviceTicketJson(ticket) + "}");
            return;
        }
        if (path.equals("/api/service-ticket/start")) {
            requireMethod(exchange, "POST");
            String body = readBody(exchange);
            synchronized (this) {
                startServiceTicket(body);
                persist();
            }
            sendJson(exchange, 200, stateJson());
            return;
        }
        if (path.equals("/api/service-ticket/complete")) {
            requireMethod(exchange, "POST");
            String body = readBody(exchange);
            synchronized (this) {
                completeServiceTicket(body);
                persist();
            }
            sendJson(exchange, 200, stateJson());
            return;
        }
        if (path.equals("/api/whatsapp/catalog-export")) {
            requireMethod(exchange, "POST");
            exportWhatsAppCatalog(exchange, readBody(exchange));
            return;
        }
        if (path.equals("/api/whatsapp/catalog.csv")) {
            requireMethod(exchange, "GET");
            downloadWhatsAppCatalogCsv(exchange);
            return;
        }
        if (path.equals("/api/whatsapp/send-receipt")) {
            requireMethod(exchange, "POST");
            sendWhatsAppReceipt(exchange, readBody(exchange));
            return;
        }
        if (path.equals("/api/sale")) {
            requireMethod(exchange, "POST");
            String body = readBody(exchange);
            Transaction receipt;
            synchronized (this) {
                receipt = completeSale(body);
                persist();
            }
            sendJson(exchange, 200, stateJsonWithReceipt(receipt));
            return;
        }
        if (path.equals("/api/customer")) {
            requireMethod(exchange, "POST");
            String body = readBody(exchange);
            synchronized (this) {
                addCustomer(body);
                persist();
            }
            sendJson(exchange, 200, stateJson());
            return;
        }
        if (path.equals("/api/ledger/manual")) {
            requireMethod(exchange, "POST");
            String body = readBody(exchange);
            synchronized (this) {
                addManualLedgerEntry(body);
                persist();
            }
            sendJson(exchange, 200, stateJson());
            return;
        }
        if (path.equals("/api/pairing")) {
            requireMethod(exchange, "GET");
            sendJson(exchange, 200, pairingJson());
            return;
        }
        if (path.equals("/api/phone/pair")) {
            requireMethod(exchange, "POST");
            pairPhone(exchange);
            return;
        }
        if (path.equals("/api/phone/discovery")) {
            requireMethod(exchange, "GET");
            sendJson(exchange, 200, pairingJsonRaw());
            return;
        }
        if (path.equals("/api/phone/capabilities")) {
            requireMethod(exchange, "GET");
            sendJson(exchange, 200, phoneCapabilitiesJson());
            return;
        }
        if (path.equals("/api/phone/scan")) {
            requireMethod(exchange, "POST");
            phoneScan(exchange);
            return;
        }
        if (path.equals("/api/phone/product-sync")) {
            requireMethod(exchange, "POST");
            productSync(exchange);
            return;
        }
        if (path.equals("/api/phone/transaction-sync")) {
            requireMethod(exchange, "POST");
            transactionSync(exchange);
            return;
        }
        if (path.equals("/api/phone/reconcile")) {
            requireMethod(exchange, "POST");
            phoneReconcile(exchange);
            return;
        }
        if (path.equals("/api/phone/stock-intake")) {
            requireMethod(exchange, "POST");
            stockIntake(exchange);
            return;
        }
        sendJson(exchange, 404, "{\"error\":\"Unknown API route\"}");
    }

    private void serveAsset(HttpExchange exchange, String path) throws IOException {
        String asset = path.equals("/") ? "/web/index.html" : "/web" + path;
        String lower = asset.toLowerCase(Locale.ROOT);
        String type = lower.endsWith(".css") ? "text/css"
            : lower.endsWith(".js") ? "application/javascript"
            : lower.endsWith(".png") ? "image/png"
            : lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? "image/jpeg"
            : lower.endsWith(".webp") ? "image/webp"
            : lower.endsWith(".svg") ? "image/svg+xml"
            : "text/html";
        try (InputStream input = BiasharaDesktopWebApp.class.getResourceAsStream(asset)) {
            if (input == null) {
                sendText(exchange, 404, "Not found", "text/plain");
                return;
            }
            byte[] bytes = input.readAllBytes();
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", type + "; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }

    private void serveMedia(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String value = "";
        if (query != null && query.startsWith("path=")) {
            value = URLDecoder.decode(query.substring(5), StandardCharsets.UTF_8);
        }
        if (value.isBlank()) {
            sendText(exchange, 404, "Missing media", "text/plain");
            return;
        }
        Path image = Path.of(value).normalize();
        Path allowed = store.incomingImagesDir().toAbsolutePath().normalize();
        if (!image.toAbsolutePath().normalize().startsWith(allowed) || !Files.exists(image)) {
            sendText(exchange, 404, "Media not found", "text/plain");
            return;
        }
        String lower = image.getFileName().toString().toLowerCase(Locale.ROOT);
        String type = lower.endsWith(".png") ? "image/png" : lower.endsWith(".webp") ? "image/webp" : "image/jpeg";
        byte[] bytes = Files.readAllBytes(image);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private synchronized String stateJson() {
        long todayRevenue = state.revenueOn(LocalDate.now());
        long lowStock = state.products.stream().filter(product -> product.stock <= 5).count();
        long images = state.products.stream().filter(product -> !product.imagePath.isBlank()).count();
        long openTickets = state.serviceTickets.stream().filter(ticket -> ticket.status != ServiceTicket.Status.COMPLETED).count();
        StringBuilder out = new StringBuilder();
        out.append("{");
        out.append("\"settings\":").append(settingsJson(state.settings)).append(",");
        out.append("\"pairing\":").append(pairingJsonRaw()).append(",");
        out.append("\"metrics\":{")
            .append("\"todayRevenue\":").append(todayRevenue).append(",")
            .append("\"creditOutstanding\":").append(state.creditOutstanding()).append(",")
            .append("\"lowStock\":").append(lowStock).append(",")
            .append("\"productCount\":").append(state.products.size()).append(",")
            .append("\"serviceCount\":").append(state.services.size()).append(",")
            .append("\"openServiceTickets\":").append(openTickets).append(",")
            .append("\"imageCount\":").append(images).append(",")
            .append("\"productSyncCount\":").append(state.productSyncItems.size()).append(",")
            .append("\"stockSyncCount\":").append(state.stockSyncItems.size())
            .append("},");
        out.append("\"products\":[");
        appendList(out, state.products.stream().sorted(Comparator.comparing(product -> product.name)).map(this::productJson).toList());
        out.append("],\"services\":[");
        appendList(out, state.services.stream().sorted(Comparator.comparing(service -> service.name)).map(this::serviceJson).toList());
        out.append("],\"customers\":[");
        appendList(out, state.customers.stream().sorted(Comparator.comparing(customer -> customer.name)).map(this::customerJson).toList());
        out.append("],\"transactions\":[");
        appendList(out, state.transactions.stream().sorted(Comparator.comparing((Transaction transaction) -> transaction.createdAt).reversed()).limit(100).map(this::transactionJson).toList());
        out.append("],\"serviceTickets\":[");
        appendList(out, state.serviceTickets.stream().sorted(Comparator.comparing((ServiceTicket ticket) -> ticket.createdAt).reversed()).limit(100).map(this::serviceTicketJson).toList());
        out.append("],\"productSync\":[");
        appendList(out, state.productSyncItems.stream().limit(50).map(this::productSyncJson).toList());
        out.append("],\"stockSync\":[");
        appendList(out, state.stockSyncItems.stream().limit(50).map(this::stockSyncJson).toList());
        out.append("],\"scanEvents\":[");
        appendList(out, state.scanEvents.stream().limit(50).map(this::scanJson).toList());
        out.append("]}");
        return out.toString();
    }

    private synchronized String stateJsonWithReceipt(Transaction receipt) {
        String json = stateJson();
        if (receipt == null || json.length() < 2) {
            return json;
        }
        return json.substring(0, json.length() - 1)
            + ",\"receipt\":" + transactionJson(receipt)
            + "}";
    }

    private void addProduct(String body) {
        String name = str(body, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Product name is required.");
        }
        String barcode = str(body, "barcode");
        String imagePath = saveImage(str(body, "imageFileName"), str(body, "imageBase64"));
        state.products.add(new Product(
            state.nextId("PRD"),
            name,
            str(body, "description"),
            str(body, "sku"),
            barcode,
            str(body, "category"),
            imagePath,
            fallback(str(body, "whatsappRetailerId"), barcode),
            str(body, "whatsappImageUrl"),
            str(body, "whatsappProductUrl"),
            cents(body, "price"),
            cents(body, "cost"),
            (int) num(body, "stock", 0)
        ));
    }

    private void addService(String body) {
        String name = str(body, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Service name is required.");
        }
        state.services.add(new ServiceItem(
            state.nextId("SVC"),
            name,
            str(body, "category"),
            cents(body, "price"),
            (int) num(body, "durationMinutes", 0),
            (int) num(body, "warrantyDays", 0)
        ));
    }

    private void bookServiceTicket(String body) {
        String serviceId = str(body, "serviceId");
        ServiceItem service = state.services.stream()
            .filter(item -> item.id.equals(serviceId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Choose a service to book."));
        int quantity = Math.max(1, (int) num(body, "quantity", 1));
        long unitCents = centsOrNumber(body, "unitPrice", service.priceCents);
        long total = Math.max(0, unitCents * quantity);
        long paid = Math.min(total, Math.max(0, centsOrNumber(body, "paidAmount", total)));
        long balance = Math.max(0, total - paid);
        String customerName = fallback(str(body, "customerName"), "Walk-in customer").trim();
        String customerPhone = str(body, "customerPhone").trim();
        Customer customer = null;
        if (!customerName.isBlank() && !"Walk-in customer".equalsIgnoreCase(customerName)) {
            String createdCustomerName = customerName;
            customer = findCustomer(createdCustomerName, customerPhone).orElseGet(() -> {
                Customer created = new Customer(state.nextId("CUS"), createdCustomerName, customerPhone, 0, 0);
                state.customers.add(created);
                return created;
            });
            customer.visits++;
            customer.balanceCents += balance;
        } else if (balance > 0) {
            throw new IllegalArgumentException("Add a customer name for balance-due service bookings.");
        }

        String transactionId = state.nextId("TXN");
        Instant now = Instant.now();
        String description = quantity + " x " + service.name + " service ticket";
        state.transactions.add(0, new Transaction(
            transactionId,
            now,
            TransactionType.SERVICE_SALE,
            customer == null ? "" : customer.id,
            customer == null ? "" : customer.name,
            description,
            fallback(str(body, "paymentMethod"), "Cash"),
            total,
            0,
            total,
            paid,
            balance
        ));
        state.saleLines.add(new DesktopSaleLine(
            state.nextId("SLN"),
            transactionId,
            CartLine.Kind.SERVICE,
            service.id,
            service.name,
            "",
            service.category,
            quantity,
            unitCents,
            total,
            str(body, "assignedTechnician")
        ));

        String ticketId = state.nextId("JOB");
        String token = ticketId + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        state.serviceTickets.add(0, new ServiceTicket(
            ticketId,
            token,
            now,
            null,
            null,
            ServiceTicket.Status.BOOKED,
            transactionId,
            customer == null ? "" : customer.id,
            customer == null ? customerName : customer.name,
            customer == null ? customerPhone : customer.phone,
            service.id,
            service.name,
            service.category,
            quantity,
            unitCents,
            total,
            paid,
            fallback(str(body, "paymentMethod"), "Cash"),
            str(body, "assignedTechnician"),
            "",
            str(body, "requirements"),
            ""
        ));
    }

    private void startServiceTicket(String body) {
        ServiceTicket ticket = findServiceTicket(str(body, "token"));
        if (ticket.status == ServiceTicket.Status.COMPLETED) {
            throw new IllegalArgumentException("This service ticket is already completed.");
        }
        if (ticket.status == ServiceTicket.Status.BOOKED) {
            ticket.status = ServiceTicket.Status.IN_PROGRESS;
            ticket.startedAt = Instant.now();
        }
        ticket.activeTechnician = fallback(str(body, "technicianName"), fallback(ticket.activeTechnician, ticket.assignedTechnician));
    }

    private void completeServiceTicket(String body) {
        ServiceTicket ticket = findServiceTicket(str(body, "token"));
        if (ticket.status == ServiceTicket.Status.COMPLETED) {
            throw new IllegalArgumentException("This service ticket is already completed.");
        }
        if (ticket.startedAt == null) {
            ticket.startedAt = Instant.now();
        }
        ticket.status = ServiceTicket.Status.COMPLETED;
        ticket.completedAt = Instant.now();
        ticket.activeTechnician = fallback(str(body, "technicianName"), fallback(ticket.activeTechnician, ticket.assignedTechnician));
        ticket.completionNotes = str(body, "completionNotes");
    }

    private ServiceTicket findServiceTicket(String raw) {
        String value = fallback(raw, "").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Scan or enter a service ticket token.");
        }
        return state.serviceTickets.stream()
            .filter(ticket -> ticket.token.equalsIgnoreCase(value) || ticket.id.equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Service ticket not found."));
    }

    private void addCustomer(String body) {
        String name = str(body, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Customer name is required.");
        }
        state.customers.add(new Customer(state.nextId("CUS"), name, str(body, "phone"), 0, 0));
    }

    private void addManualLedgerEntry(String body) {
        String entryType = fallback(str(body, "entryType"), "EXPENSE")
            .trim()
            .toUpperCase(Locale.ROOT)
            .replace(" ", "_")
            .replace("-", "_");
        long amount = Math.abs(cents(body, "amount"));
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        String paymentMethod = fallback(str(body, "paymentMethod"), "Cash");
        String customerId = str(body, "customerId");
        Customer customer = state.customerById(customerId);
        TransactionType transactionType;
        long signedTotal = amount;
        long paid = amount;
        String defaultDescription;

        switch (entryType) {
            case "INCOME" -> {
                transactionType = TransactionType.PAYMENT;
                defaultDescription = "Manual money in";
            }
            case "CUSTOMER_PAYMENT" -> {
                if (customer == null) {
                    throw new IllegalArgumentException("Choose the customer who made the payment.");
                }
                transactionType = TransactionType.PAYMENT;
                long reduction = Math.min(customer.balanceCents, amount);
                customer.balanceCents = Math.max(0, customer.balanceCents - reduction);
                defaultDescription = "Customer payment from " + customer.name;
            }
            case "ADJUSTMENT_IN" -> {
                transactionType = TransactionType.ADJUSTMENT;
                defaultDescription = "Cash adjustment in";
            }
            case "ADJUSTMENT_OUT" -> {
                transactionType = TransactionType.ADJUSTMENT;
                signedTotal = -amount;
                paid = -amount;
                defaultDescription = "Cash adjustment out";
            }
            default -> {
                transactionType = TransactionType.EXPENSE;
                defaultDescription = "Manual expense";
            }
        }

        state.transactions.add(0, new Transaction(
            state.nextId("LED"),
            Instant.now(),
            transactionType,
            customer == null ? "" : customer.id,
            customer == null ? "" : customer.name,
            fallback(str(body, "description"), defaultDescription),
            paymentMethod,
            signedTotal,
            0,
            signedTotal,
            paid,
            customer == null ? 0 : customer.balanceCents
        ));
    }

    private Transaction completeSale(String body) {
        List<CartLine> lines = parseCart(body);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty.");
        }
        String transactionId = state.nextId("TXN");
        Instant createdAt = Instant.now();
        long subtotal = lines.stream().mapToLong(CartLine::totalCents).sum();
        long tax = subtotal * state.settings.taxBasisPoints / 10000L;
        long total = subtotal + tax;
        long paid = num(body, "paidCents", total);
        String paymentMethod = str(body, "paymentMethod");
        String customerId = str(body, "customerId");
        Customer customer = state.customerById(customerId);
        long balance = Math.max(0, total - paid);
        if (balance > 0 && customer == null) {
            throw new IllegalArgumentException("Select a customer for balance-due sales.");
        }
        for (CartLine line : lines) {
            if (line.kind == CartLine.Kind.PRODUCT) {
                Product product = state.products.stream().filter(item -> item.id.equals(line.itemId)).findFirst().orElseThrow();
                if (product.stock < line.quantity) {
                    throw new IllegalArgumentException(product.name + " does not have enough stock.");
                }
                product.stock -= line.quantity;
                recordStockMovement("desktop-sale:" + transactionId, "Desktop POS", product, -line.quantity, "DESKTOP_SALE", transactionId);
            }
        }
        if (customer != null) {
            customer.visits++;
            customer.balanceCents += balance;
        }
        List<DesktopSaleLine> committedLines = lines.stream()
            .map(line -> saleLineFromCart(transactionId, line))
            .toList();
        String description = lines.stream().map(line -> line.quantity + " x " + line.name).reduce((a, b) -> a + ", " + b).orElse("Sale");
        boolean servicesOnly = lines.stream().allMatch(line -> line.kind == CartLine.Kind.SERVICE);
        Transaction transaction = new Transaction(
            transactionId,
            createdAt,
            servicesOnly ? TransactionType.SERVICE_SALE : TransactionType.SALE,
            customer == null ? "" : customer.id,
            customer == null ? "" : customer.name,
            description,
            paymentMethod.isBlank() ? "Cash" : paymentMethod,
            subtotal,
            tax,
            total,
            Math.min(paid, total),
            balance
        );
        state.transactions.add(0, transaction);
        state.saleLines.addAll(committedLines);
        return transaction;
    }

    private DesktopSaleLine saleLineFromCart(String transactionId, CartLine line) {
        String barcode = "";
        String category = "";
        if (line.kind == CartLine.Kind.PRODUCT) {
            Product product = state.products.stream()
                .filter(item -> item.id.equals(line.itemId))
                .findFirst()
                .orElse(null);
            if (product != null) {
                barcode = product.barcode;
                category = product.category;
            }
        } else {
            ServiceItem service = state.services.stream()
                .filter(item -> item.id.equals(line.itemId))
                .findFirst()
                .orElse(null);
            if (service != null) {
                category = service.category;
            }
        }
        return new DesktopSaleLine(
            state.nextId("SLN"),
            transactionId,
            line.kind,
            line.itemId,
            line.name,
            barcode,
            category,
            line.quantity,
            line.unitCents,
            line.totalCents(),
            line.staffName
        );
    }

    private List<CartLine> parseCart(String body) {
        List<CartLine> lines = new ArrayList<>();
        int index = 0;
        while (true) {
            int itemIndex = body.indexOf("\"itemId\"", index);
            if (itemIndex < 0) {
                break;
            }
            int objectStart = body.lastIndexOf('{', itemIndex);
            int objectEnd = body.indexOf('}', itemIndex);
            if (objectStart < 0 || objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String kind = str(object, "kind");
            String itemId = str(object, "itemId");
            int quantity = (int) num(object, "quantity", 1);
            if ("SERVICE".equals(kind)) {
                state.services.stream().filter(service -> service.id.equals(itemId)).findFirst()
                    .ifPresent(service -> lines.add(new CartLine(CartLine.Kind.SERVICE, service.id, service.name, Math.max(1, quantity), service.priceCents)));
            } else {
                state.products.stream().filter(product -> product.id.equals(itemId)).findFirst()
                    .ifPresent(product -> lines.add(new CartLine(CartLine.Kind.PRODUCT, product.id, product.name, Math.max(1, quantity), product.priceCents)));
            }
            index = objectEnd + 1;
        }
        return lines;
    }

    private void updateSettings(String body) {
        state.settings.businessName = str(body, "businessName").trim();
        state.settings.ownerName = str(body, "ownerName");
        state.settings.currency = normalizeCurrency(str(body, "currency"));
        state.settings.taxBasisPoints = Math.round(decimal(str(body, "taxPercent")) * 100);
        state.settings.receiptFooter = str(body, "receiptFooter");
        state.settings.modelPath = str(body, "modelPath");
        String provider = fallback(str(body, "aiProvider"), state.settings.aiProvider)
            .trim()
            .toUpperCase(Locale.ROOT)
            .replace("-", "_");
        state.settings.aiProvider = "LM_STUDIO".equals(provider) ? "LM_STUDIO" : "RULES";
        state.settings.lmStudioBaseUrl = str(body, "lmStudioBaseUrl").trim();
        state.settings.lmStudioModel = str(body, "lmStudioModel").trim();
        state.settings.whatsappPhoneNumberId = str(body, "whatsappPhoneNumberId");
        state.settings.whatsappCatalogId = str(body, "whatsappCatalogId");
        state.settings.whatsappDefaultCountryCode = str(body, "whatsappDefaultCountryCode").trim();
        String accessToken = str(body, "whatsappAccessToken").trim();
        if (!accessToken.isBlank() && !"********".equals(accessToken)) {
            state.settings.whatsappAccessToken = accessToken;
        }
        state.settings.whatsappGraphVersion = normalizeGraphVersionForSettings(str(body, "whatsappGraphVersion"));
    }

    private void answerAssistant(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        String question = fallback(str(body, "question"), "Give me a concise business report.");
        String provider = normalizedAiProvider();
        if ("LM_STUDIO".equals(provider)) {
            try {
                String answer = lmStudioClient.answer(question, state);
                sendJson(exchange, 200, "{"
                    + "\"ok\":true,"
                    + "\"provider\":\"LM_STUDIO\","
                    + "\"answer\":\"" + json(answer) + "\""
                    + "}");
            } catch (Exception ex) {
                String fallbackAnswer = advisor.answer(question, state);
                sendJson(exchange, 200, "{"
                    + "\"ok\":false,"
                    + "\"provider\":\"LM_STUDIO\","
                    + "\"message\":\"" + json(ex.getMessage()) + "\","
                    + "\"fallbackAnswer\":\"" + json(fallbackAnswer) + "\""
                    + "}");
            }
            return;
        }
        sendJson(exchange, 200, "{"
            + "\"ok\":true,"
            + "\"provider\":\"RULES\","
            + "\"answer\":\"" + json(advisor.answer(question, state)) + "\""
            + "}");
    }

    private void streamAssistant(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        String question = fallback(str(body, "question"), "Give me a concise business report.");
        List<LmStudioClient.AssistantImage> images = assistantImages(body);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/event-stream; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream output = exchange.getResponseBody()) {
            String provider = normalizedAiProvider();
            writeSse(output, "status", "{\"provider\":\"" + json(provider) + "\"}");
            if (!"LM_STUDIO".equals(provider)) {
                writeSse(output, "token", "{\"token\":\"" + json(advisor.answer(question, state)) + "\"}");
                writeSse(output, "done", "{\"ok\":true,\"provider\":\"RULES\"}");
                return;
            }
            try {
                lmStudioClient.streamAnswer(question, images, state, token ->
                    writeSse(output, "token", "{\"token\":\"" + json(token) + "\"}")
                );
                writeSse(output, "done", "{\"ok\":true,\"provider\":\"LM_STUDIO\"}");
            } catch (Exception ex) {
                String fallbackAnswer = advisor.answer(question, state);
                writeSse(output, "error", "{"
                    + "\"message\":\"" + json(ex.getMessage()) + "\","
                    + "\"fallbackAnswer\":\"" + json(fallbackAnswer) + "\""
                    + "}");
                writeSse(output, "done", "{\"ok\":false,\"provider\":\"LM_STUDIO\"}");
            }
        }
    }

    private List<LmStudioClient.AssistantImage> assistantImages(String body) {
        List<LmStudioClient.AssistantImage> images = new ArrayList<>();
        long totalChars = 0;
        for (String object : objectsInArray(body, "images")) {
            if (images.size() >= 3) {
                break;
            }
            String dataUrl = fallback(str(object, "dataUrl"), str(object, "base64"));
            if (dataUrl.isBlank()) {
                continue;
            }
            totalChars += dataUrl.length();
            if (totalChars > 10_000_000L) {
                throw new IllegalArgumentException("Attached images are too large. Use up to 3 images below 10 MB total.");
            }
            String mimeType = fallback(str(object, "mimeType"), "image/jpeg").toLowerCase(Locale.ROOT);
            if (!mimeType.startsWith("image/")) {
                throw new IllegalArgumentException("Assistant attachments must be image files.");
            }
            images.add(new LmStudioClient.AssistantImage(
                str(object, "fileName"),
                mimeType,
                dataUrl
            ));
        }
        return images;
    }

    private void testAi(HttpExchange exchange) throws IOException {
        readBody(exchange);
        if (!"LM_STUDIO".equals(normalizedAiProvider())) {
            sendJson(exchange, 200, "{"
                + "\"ok\":true,"
                + "\"provider\":\"RULES\","
                + "\"message\":\"Rule-based local assistant is active. Select LM Studio in Settings to use the local server.\""
                + "}");
            return;
        }
        try {
            LmStudioClient.Probe probe = lmStudioClient.test(state.settings);
            sendJson(exchange, 200, "{"
                + "\"ok\":" + probe.ok + ","
                + "\"provider\":\"LM_STUDIO\","
                + "\"modelId\":\"" + json(probe.modelId) + "\","
                + "\"message\":\"" + json(probe.message) + "\""
                + "}");
        } catch (Exception ex) {
            sendJson(exchange, 200, "{"
                + "\"ok\":false,"
                + "\"provider\":\"LM_STUDIO\","
                + "\"message\":\"" + json(ex.getMessage()) + "\""
                + "}");
        }
    }

    private void runBusinessAgent(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        String agentId = str(body, "agentId").trim();
        if (agentId.isBlank()) {
            throw new IllegalArgumentException("Choose an agent to run.");
        }
        BusinessSnapshot snapshot = captureAgentSnapshot();
        Settings settings = copyAgentSettings();
        AgentRun run = agentService.run(agentId, snapshot, settings);
        sendJson(exchange, 200, objectMapper.writeValueAsString(run));
    }

    private synchronized BusinessSnapshot captureAgentSnapshot() {
        return BusinessSnapshot.capture(state, !sessionKey.isBlank(), pairedDevice);
    }

    private synchronized Settings copyAgentSettings() {
        Settings copy = new Settings();
        copy.businessName = state.settings.businessName;
        copy.currency = state.settings.currency;
        copy.aiProvider = state.settings.aiProvider;
        copy.lmStudioBaseUrl = state.settings.lmStudioBaseUrl;
        copy.lmStudioModel = state.settings.lmStudioModel;
        return copy;
    }

    private void exportWhatsAppCatalog(HttpExchange exchange, String body) throws IOException {
        boolean upload = bool(body, "upload");
        List<String> warnings = new ArrayList<>();
        List<String> requests = new ArrayList<>();
        int skipped = 0;
        for (Product product : state.products.stream().sorted(Comparator.comparing(item -> item.name)).toList()) {
            String request = whatsappCatalogRequest(product, warnings, upload);
            if (request.isBlank()) {
                skipped++;
            } else {
                requests.add(request);
            }
        }
        String payload = "{"
            + "\"item_type\":\"PRODUCT_ITEM\","
            + "\"allow_upsert\":true,"
            + "\"requests\":[" + String.join(",", requests) + "]"
            + "}";
        String version = normalizeGraphVersion(state.settings.whatsappGraphVersion);
        if (version.isBlank()) {
            warnings.add("Add your WhatsApp Graph API version in Settings before catalog export.");
        }
        String endpoint = state.settings.whatsappCatalogId.isBlank() || version.isBlank()
            ? ""
            : "https://graph.facebook.com/" + version + "/" + url(state.settings.whatsappCatalogId) + "/items_batch";
        if (!upload) {
            sendJson(exchange, 200, "{"
                + "\"ok\":true,"
                + "\"upload\":false,"
                + "\"endpoint\":\"" + json(endpoint) + "\","
                + "\"readyCount\":" + requests.size() + ","
                + "\"skippedCount\":" + skipped + ","
                + "\"warnings\":" + jsonArray(warnings) + ","
                + "\"payload\":" + payload
                + "}");
            return;
        }
        if (state.settings.whatsappCatalogId.isBlank()) {
            sendJson(exchange, 400, "{\"error\":\"Add your Meta catalog ID in Settings before live upload.\"}");
            return;
        }
        if (version.isBlank()) {
            sendJson(exchange, 400, "{\"error\":\"Add your WhatsApp Graph API version in Settings before live upload.\"}");
            return;
        }
        if (state.settings.whatsappAccessToken.isBlank()) {
            sendJson(exchange, 400, "{\"error\":\"Add a WhatsApp/Meta access token in Settings before live upload.\"}");
            return;
        }
        if (requests.isEmpty()) {
            sendJson(exchange, 400, "{\"error\":\"No catalog-ready products. Add price, retailer ID, public product URL, and public image URL.\"}");
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Authorization", "Bearer " + state.settings.whatsappAccessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            sendJson(exchange, 200, "{"
                + "\"ok\":" + ok + ","
                + "\"upload\":true,"
                + "\"statusCode\":" + response.statusCode() + ","
                + "\"endpoint\":\"" + json(endpoint) + "\","
                + "\"readyCount\":" + requests.size() + ","
                + "\"skippedCount\":" + skipped + ","
                + "\"warnings\":" + jsonArray(warnings) + ","
                + "\"response\":\"" + json(response.body()) + "\""
                + "}");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            sendJson(exchange, 500, "{\"error\":\"WhatsApp catalog upload was interrupted.\"}");
        } catch (Exception ex) {
            sendJson(exchange, 502, "{\"error\":\"" + json(ex.getMessage()) + "\"}");
        }
    }

    private void downloadWhatsAppCatalogCsv(HttpExchange exchange) throws IOException {
        StringBuilder csv = new StringBuilder("id,title,description,availability,condition,price,link,image_link,brand\r\n");
        String brand = state.settings.businessName == null ? "" : state.settings.businessName.trim();
        for (Product product : state.products.stream().sorted(Comparator.comparing(item -> item.name)).toList()) {
            if (product.name == null || product.name.isBlank() || product.priceCents <= 0) {
                continue;
            }
            String retailerId = fallback(product.whatsappRetailerId, fallback(product.sku, fallback(product.barcode, product.id)));
            String description = fallback(product.description, fallback(product.category, product.name));
            csv.append(csvCell(retailerId)).append(',')
                .append(csvCell(product.name)).append(',')
                .append(csvCell(description)).append(',')
                .append(csvCell(product.stock > 0 ? "in stock" : "out of stock")).append(',')
                .append(csvCell("new")).append(',')
                .append(csvCell(catalogPrice(product.priceCents))).append(',')
                .append(csvCell(product.whatsappProductUrl)).append(',')
                .append(csvCell(product.whatsappImageUrl)).append(',')
                .append(csvCell(brand)).append("\r\n");
        }
        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=biashara-meta-catalog.csv");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private String csvCell(String value) {
        String clean = value == null ? "" : value.replace("\r", " ").replace("\n", " ");
        return "\"" + clean.replace("\"", "\"\"") + "\"";
    }

    private void sendWhatsAppReceipt(HttpExchange exchange, String body) throws IOException {
        String message = fallback(str(body, "message"), str(body, "text")).trim();
        if (message.isBlank()) {
            sendJson(exchange, 400, "{\"error\":\"Receipt message is required.\"}");
            return;
        }
        String phone = whatsappRecipient(fallback(str(body, "phone"), str(body, "customerPhone")));
        String fallbackUrl = whatsappComposerUrl(phone, message);
        if (phone.isBlank()) {
            sendJson(exchange, 200, "{"
                + "\"ok\":false,"
                + "\"sent\":false,"
                + "\"fallback\":true,"
                + "\"reason\":\"Enter a customer WhatsApp number to send directly.\","
                + "\"fallbackUrl\":\"" + json(fallbackUrl) + "\""
                + "}");
            return;
        }
        String version = normalizeGraphVersion(state.settings.whatsappGraphVersion);
        if (state.settings.whatsappPhoneNumberId.isBlank() || state.settings.whatsappAccessToken.isBlank() || version.isBlank()) {
            sendJson(exchange, 200, "{"
                + "\"ok\":false,"
                + "\"sent\":false,"
                + "\"fallback\":true,"
                + "\"reason\":\"WhatsApp Business API is not configured in Settings.\","
                + "\"fallbackUrl\":\"" + json(fallbackUrl) + "\""
                + "}");
            return;
        }
        String endpoint = "https://graph.facebook.com/" + version + "/" + url(state.settings.whatsappPhoneNumberId.trim()) + "/messages";
        String payload = "{"
            + "\"messaging_product\":\"whatsapp\","
            + "\"to\":\"" + json(phone) + "\","
            + "\"type\":\"text\","
            + "\"text\":{\"preview_url\":false,\"body\":\"" + json(message) + "\"}"
            + "}";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Authorization", "Bearer " + state.settings.whatsappAccessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            sendJson(exchange, 200, "{"
                + "\"ok\":" + ok + ","
                + "\"sent\":" + ok + ","
                + "\"fallback\":" + (!ok) + ","
                + "\"statusCode\":" + response.statusCode() + ","
                + "\"endpoint\":\"" + json(endpoint) + "\","
                + "\"fallbackUrl\":\"" + json(fallbackUrl) + "\","
                + "\"response\":\"" + json(response.body()) + "\""
                + "}");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            sendJson(exchange, 500, "{\"error\":\"WhatsApp receipt send was interrupted.\"}");
        } catch (Exception ex) {
            sendJson(exchange, 200, "{"
                + "\"ok\":false,"
                + "\"sent\":false,"
                + "\"fallback\":true,"
                + "\"reason\":\"" + json(ex.getMessage()) + "\","
                + "\"fallbackUrl\":\"" + json(fallbackUrl) + "\""
                + "}");
        }
    }

    private String whatsappRecipient(String raw) {
        String text = raw == null ? "" : raw.trim();
        String digits = text.replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return "";
        }
        if (text.startsWith("+")) {
            return digits;
        }
        String country = state.settings.whatsappDefaultCountryCode == null
            ? ""
            : state.settings.whatsappDefaultCountryCode.replaceAll("\\D+", "");
        if (!country.isBlank() && digits.startsWith("0")) {
            return country + digits.substring(1);
        }
        return digits;
    }

    private String whatsappComposerUrl(String phone, String message) {
        String text = url(message);
        return phone.isBlank()
            ? "https://wa.me/?text=" + text
            : "https://wa.me/" + url(phone) + "?text=" + text;
    }

    private String whatsappCatalogRequest(Product product, List<String> warnings, boolean strict) {
        if (product.name == null || product.name.isBlank()) {
            warnings.add("Skipped one product because it has no name.");
            return "";
        }
        if (product.priceCents <= 0) {
            warnings.add(product.name + " skipped: price is missing.");
            return "";
        }
        if (normalizeCurrency(state.settings.currency).isBlank()) {
            warnings.add(product.name + " skipped: set business currency before WhatsApp catalog export.");
            return "";
        }
        String businessName = state.settings.businessName == null ? "" : state.settings.businessName.trim();
        if (businessName.isBlank()) {
            warnings.add(product.name + " needs a business name before Meta catalog export can include a brand.");
            if (strict) {
                return "";
            }
        }
        String retailerId = fallback(product.whatsappRetailerId, fallback(product.sku, fallback(product.barcode, product.id)));
        if (retailerId.isBlank()) {
            warnings.add(product.name + " skipped: retailer ID, SKU, or barcode is required.");
            return "";
        }
        String imageUrl = product.whatsappImageUrl == null ? "" : product.whatsappImageUrl.trim();
        String productUrl = product.whatsappProductUrl == null ? "" : product.whatsappProductUrl.trim();
        boolean hasPublicImage = isHttpsUrl(imageUrl);
        boolean hasPublicProductUrl = isHttpsUrl(productUrl);
        if (!hasPublicImage) {
            warnings.add(product.name + " needs a public HTTPS image URL before Meta can import it.");
        }
        if (!hasPublicProductUrl) {
            warnings.add(product.name + " needs a public HTTPS product URL before Meta can import it.");
        }
        if (strict && (!hasPublicImage || !hasPublicProductUrl)) {
            return "";
        }
        String description = fallback(product.description, fallback(product.category, product.name));
        StringBuilder data = new StringBuilder();
        data.append("{")
            .append("\"name\":\"").append(json(product.name)).append("\",")
            .append("\"description\":\"").append(json(description)).append("\",")
            .append("\"availability\":\"").append(product.stock > 0 ? "in stock" : "out of stock").append("\",")
            .append("\"condition\":\"new\",")
            .append("\"price\":\"").append(json(catalogPrice(product.priceCents))).append("\"");
        if (!businessName.isBlank()) {
            data.append(",\"brand\":\"").append(json(businessName)).append("\"");
        }
        if (hasPublicProductUrl) {
            data.append(",\"link\":\"").append(json(productUrl)).append("\"");
        }
        if (hasPublicImage) {
            data.append(",\"image_url\":\"").append(json(imageUrl)).append("\"");
        }
        data.append("}");
        return "{"
            + "\"method\":\"UPDATE\","
            + "\"retailer_id\":\"" + json(retailerId) + "\","
            + "\"data\":" + data
            + "}";
    }

    private boolean isHttpsUrl(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).startsWith("https://") && value.length() > "https://x.y".length();
    }

    private String catalogPrice(long cents) {
        String code = normalizeCurrency(state.settings.currency);
        return new BigDecimal(cents).movePointLeft(2).stripTrailingZeros().toPlainString() + " " + code;
    }

    private String normalizeGraphVersionForSettings(String raw) {
        return normalizeGraphVersion(raw);
    }

    private String normalizeGraphVersion(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (value.isBlank()) {
            return "";
        }
        if (value.matches("v\\d+\\.\\d+")) {
            return value;
        }
        if (value.matches("\\d+\\.\\d+")) {
            return "v" + value;
        }
        if (value.matches("\\d+")) {
            return "v" + value + ".0";
        }
        return "";
    }

    private String normalizedAiProvider() {
        return "LM_STUDIO".equalsIgnoreCase(state.settings.aiProvider) ? "LM_STUDIO" : "RULES";
    }

    private String normalizeCurrency(String raw) {
        String value = fallback(raw, "").trim().toUpperCase(Locale.ROOT)
            .replace("-", " ")
            .replace("_", " ")
            .replaceAll("\\s+", " ");
        if (value.isBlank()) {
            return "";
        }
        if (value.equals("FCFA") || value.equals("CFA") || value.equals("CFA FRANC")
            || value.equals("FRANC CFA") || value.equals("FRANCE CFA") || value.contains("CENTRAL AFRICAN CFA")
            || value.contains("BEAC")) {
            return "XAF";
        }
        if (value.contains("WEST AFRICAN CFA") || value.contains("BCEAO") || value.equals("F CFA")) {
            return "XOF";
        }
        String code = value.length() >= 3 ? value.substring(0, 3) : value;
        if (AFRICAN_CURRENCY_CODES.contains(code)) {
            return code;
        }
        if (AFRICAN_CURRENCY_CODES.contains(value)) {
            return value;
        }
        return value;
    }

    private synchronized void pairPhone(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        long retryAfterSeconds = pairingAttemptLimiter.retryAfterSeconds();
        if (retryAfterSeconds > 0) {
            exchange.getResponseHeaders().set("Retry-After", Long.toString(retryAfterSeconds));
            sendJson(exchange, 429, "{\"error\":\"Too many failed pairing attempts. Try again shortly.\"}");
            return;
        }
        if (!pairToken.equalsIgnoreCase(str(body, "token"))) {
            boolean locked = pairingAttemptLimiter.recordFailure();
            if (locked) {
                exchange.getResponseHeaders().set("Retry-After", Long.toString(pairingAttemptLimiter.retryAfterSeconds()));
            }
            sendJson(
                exchange,
                locked ? 429 : 401,
                "{\"error\":\"" + (locked
                    ? "Too many failed pairing attempts. Try again shortly."
                    : "Invalid pairing code") + "\"}"
            );
            return;
        }
        pairingAttemptLimiter.reset();
        sessionKey = UUID.randomUUID().toString().replace("-", "");
        pairedDevice = fallback(str(body, "deviceName"), "Mobile device");
        signedPhoneRequestsRequired = supportsSignedSync(body);
        store.saveBridgeSession(
            sessionKey,
            pairedDevice,
            signedPhoneRequestsRequired ? SyncProtocol.CURRENT_VERSION : ""
        );
        sendJson(exchange, 200, "{\"sessionKey\":\"" + json(sessionKey) + "\","
            + "\"protocolVersion\":\"" + SyncProtocol.CURRENT_VERSION + "\","
            + "\"authentication\":\"" + SyncProtocol.AUTHENTICATION + "\"}");
        pairToken = newPairToken();
    }

    private static String newPairToken() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private boolean supportsSignedSync(String body) {
        try {
            for (var version : objectMapper.readTree(body).path("supportedProtocolVersions")) {
                if (SyncProtocol.CURRENT_VERSION.equals(version.asText())) {
                    return true;
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private synchronized void phoneScan(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        requireSession(exchange, body);
        SyncInboxService.Operation operation = syncOperation(body, "SCAN");
        if (operation.replay() != null) {
            sendJson(exchange, operation.replay().httpStatus, operation.replay().responseJson);
            return;
        }
        String raw = fallback(str(body, "rawValue"), str(body, "raw"));
        ScanEvent event = new ScanEvent(state.nextId("SCN"), Instant.now(), fallback(str(body, "deviceName"), pairedDevice), scanKind(raw), raw, "Received");
        state.scanEvents.add(0, event);
        String response = "{\"accepted\":true}";
        recordSyncOutcome(operation, 200, response);
        persist();
        sendJson(exchange, 200, response);
    }

    private synchronized void productSync(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        requireSession(exchange, body);
        SyncInboxService.Operation operation = syncOperation(body, "PRODUCT_SYNC");
        if (operation.replay() != null) {
            sendJson(exchange, operation.replay().httpStatus, operation.replay().responseJson);
            return;
        }
        ProductSyncItem item = new ProductSyncItem(
            state.nextId("PSY"),
            Instant.now(),
            fallback(str(body, "deviceName"), pairedDevice),
            str(body, "mobileProductId"),
            fallback(str(body, "name"), str(body, "productName")),
            str(body, "description"),
            str(body, "sku"),
            str(body, "barcode"),
            str(body, "category"),
            (int) num(body, "stock", 0),
            num(body, "priceCents", 0),
            num(body, "costCents", 0),
            "",
            str(body, "imageFileName"),
            str(body, "imageBase64"),
            str(body, "whatsappRetailerId"),
            str(body, "whatsappImageUrl"),
            str(body, "whatsappProductUrl"),
            "Received"
        );
        applyProductSync(item);
        state.productSyncItems.add(0, item);
        String response = "{\"accepted\":true}";
        recordSyncOutcome(operation, 200, response);
        persist();
        sendJson(exchange, 200, response);
    }

    private synchronized void transactionSync(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        requireSession(exchange, body);
        SyncInboxService.Operation operation = syncOperation(body, "TRANSACTION_SYNC");
        if (operation.replay() != null) {
            sendJson(exchange, operation.replay().httpStatus, operation.replay().responseJson);
            return;
        }
        String mobileTransactionId = str(body, "mobileTransactionId");
        String transactionId = "MOB-" + safeExternalId(fallback(mobileTransactionId, str(body, "receiptNumber")));
        if (state.transactions.stream().anyMatch(transaction -> transaction.id.equals(transactionId))) {
            String response = "{\"accepted\":true,\"duplicate\":true,\"stockApplied\":false}";
            recordSyncOutcome(operation, 200, response);
            persist();
            sendJson(exchange, 200, response);
            return;
        }
        long total = num(body, "totalCents", 0);
        long paid = num(body, "paidCents", total);
        long balance = num(body, "balanceCents", 0);
        String customerName = str(body, "customerName");
        String customerPhone = str(body, "customerPhone");
        Customer customer = null;
        if (!customerName.isBlank()) {
            customer = findCustomer(customerName, customerPhone).orElseGet(() -> {
                Customer created = new Customer(state.nextId("CUS"), customerName, customerPhone, 0, 0);
                state.customers.add(created);
                return created;
            });
            customer.visits++;
            customer.balanceCents += Math.max(0, balance);
        }
        Instant createdAt = Instant.ofEpochMilli(num(body, "createdAtMillis", System.currentTimeMillis()));
        String type = str(body, "type");
        long productSubtotal = num(body, "productSubtotalCents", 0);
        long serviceSubtotal = num(body, "serviceSubtotalCents", 0);
        TransactionType transactionType = "EXPENSE".equalsIgnoreCase(type)
            ? TransactionType.EXPENSE
            : serviceSubtotal > 0 && productSubtotal <= 0 ? TransactionType.SERVICE_SALE : TransactionType.SALE;
        state.transactions.add(0, new Transaction(
            transactionId,
            createdAt,
            transactionType,
            customer == null ? "" : customer.id,
            customer == null ? "" : customer.name,
            fallback(str(body, "description"), fallback(str(body, "receiptNumber"), "Mobile sale")),
            fallback(str(body, "paymentMethod"), "Cash"),
            num(body, "subtotalCents", Math.max(0, productSubtotal + serviceSubtotal)),
            num(body, "taxCents", 0),
            total,
            Math.min(Math.max(0, paid), Math.max(0, total)),
            Math.max(0, balance)
        ));
        List<DesktopSaleLine> mobileLines = parseMobileSaleLines(body, transactionId);
        state.saleLines.addAll(mobileLines);
        boolean stockApplied = transactionType == TransactionType.SALE;
        if (stockApplied) {
            applyMobileSaleStockImpact(mobileLines, syncOperationId(operation, "mobile-transaction:" + transactionId), fallback(str(body, "deviceName"), pairedDevice));
        }
        String response = "{\"accepted\":true,\"duplicate\":false,\"stockApplied\":" + stockApplied + "}";
        recordSyncOutcome(operation, 200, response);
        persist();
        sendJson(exchange, 200, response);
    }

    private synchronized void phoneReconcile(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        requireSession(exchange, body);
        SyncInboxService.Operation operation = syncOperation(body, "RECONCILE");
        if (operation.replay() != null) {
            sendJson(exchange, operation.replay().httpStatus, operation.replay().responseJson);
            return;
        }
        mergeSettingsFromPhone(body);
        int serviceChangesApplied = applyPhoneServices(body);
        int stockChangesApplied = applyPhoneStockChanges(body);
        state.settings.settingsSyncFingerprint = settingsSyncFingerprint(state.settings);
        String response = reconcileJson(bool(body, "includeImages"), stockChangesApplied, serviceChangesApplied);
        recordSyncOutcome(operation, 200, response);
        persist();
        sendJson(exchange, 200, response);
    }

    private int applyPhoneServices(String body) {
        String sourceDevice = fallback(str(body, "deviceName"), pairedDevice);
        int applied = 0;
        for (String object : objectsInArray(body, "services")) {
            String mobileServiceId = str(object, "mobileServiceId").trim();
            String desktopServiceId = str(object, "desktopServiceId").trim();
            String name = str(object, "name").trim();
            if (mobileServiceId.isBlank() || name.isBlank()) {
                continue;
            }
            ServiceItem service = state.services.stream()
                .filter(item -> !desktopServiceId.isBlank() && item.id.equals(desktopServiceId))
                .findFirst()
                .orElseGet(() -> state.services.stream()
                    .filter(item -> sourceDevice.equals(item.sourceDevice))
                    .filter(item -> mobileServiceId.equals(item.mobileServiceId))
                    .findFirst()
                    .orElseGet(() -> state.services.stream()
                        .filter(item -> item.name.equalsIgnoreCase(name))
                        .findFirst()
                        .orElse(null)));
            long incomingUpdatedAt = Math.max(0, num(object, "updatedAt", System.currentTimeMillis()));
            String description = str(object, "description").trim();
            String category = str(object, "category").trim();
            long priceCents = Math.max(0, num(object, "priceCents", 0));
            String priceMode = normalizeServicePriceMode(str(object, "priceMode"));
            int durationMinutes = (int) Math.max(0, Math.min(Integer.MAX_VALUE, num(object, "durationMinutes", 0)));
            int warrantyDays = (int) Math.max(0, Math.min(Integer.MAX_VALUE, num(object, "warrantyDays", 0)));
            boolean visibleInKiosk = bool(object, "visibleInKiosk");
            if (service == null) {
                state.services.add(new ServiceItem(
                    state.nextId("SVC"),
                    name,
                    description,
                    category,
                    priceCents,
                    priceMode,
                    durationMinutes,
                    warrantyDays,
                    visibleInKiosk,
                    incomingUpdatedAt,
                    sourceDevice,
                    mobileServiceId
                ));
                applied++;
                continue;
            }
            boolean changed = false;
            if (service.sourceDevice.isBlank() || service.mobileServiceId.isBlank()) {
                service.sourceDevice = sourceDevice;
                service.mobileServiceId = mobileServiceId;
                changed = true;
            }
            if (incomingUpdatedAt >= service.updatedAt) {
                changed |= !service.name.equals(name)
                    || !service.description.equals(description)
                    || !service.category.equals(category)
                    || service.priceCents != priceCents
                    || !service.priceMode.equals(priceMode)
                    || service.durationMinutes != durationMinutes
                    || service.warrantyDays != warrantyDays
                    || service.visibleInKiosk != visibleInKiosk
                    || service.updatedAt != incomingUpdatedAt;
                service.name = name;
                service.description = description;
                service.category = category;
                service.priceCents = priceCents;
                service.priceMode = priceMode;
                service.durationMinutes = durationMinutes;
                service.warrantyDays = warrantyDays;
                service.visibleInKiosk = visibleInKiosk;
                service.updatedAt = incomingUpdatedAt;
            }
            if (changed) {
                applied++;
            }
        }
        return applied;
    }

    private String normalizeServicePriceMode(String value) {
        String normalized = fallback(value, "FIXED").trim().toUpperCase(Locale.ROOT);
        return Set.of("FIXED", "NEGOTIABLE", "FROM").contains(normalized) ? normalized : "FIXED";
    }

    private int applyPhoneStockChanges(String body) {
        String sourceDevice = fallback(str(body, "deviceName"), pairedDevice);
        int applied = 0;
        for (String object : objectsInArray(body, "stockChanges")) {
            String mobileProductId = str(object, "mobileProductId");
            String mutationId = str(object, "mutationId");
            boolean stockBaseKnown = bool(object, "stockBaseKnown");
            int stockBase = (int) num(object, "stockBase", 0);
            int sourceStock = (int) num(object, "stock", 0);
            if (mobileProductId.isBlank() || mutationId.isBlank() || !stockBaseKnown || stockBase < 0 || sourceStock < 0) {
                continue;
            }
            if (state.stockSyncItems.stream().anyMatch(item -> mutationId.equals(item.mutationId))) {
                continue;
            }
            Product product = findProduct(str(object, "barcode"), str(object, "name")).orElse(null);
            if (product == null) {
                continue;
            }
            StockSyncItem prior = state.stockSyncItems.stream()
                .filter(item -> sourceDevice.equals(item.sourceDevice))
                .filter(item -> mobileProductId.equals(item.mobileProductId))
                .filter(item -> item.stockBaseKnown && item.stockBase == stockBase)
                .max(Comparator.comparing(item -> item.createdAt))
                .orElse(null);
            int requestedDelta = prior == null ? sourceStock - stockBase : sourceStock - prior.sourceStock;
            int previousStock = product.stock;
            product.stock = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) product.stock + requestedDelta));
            int actualDelta = product.stock - previousStock;
            StockSyncItem event = new StockSyncItem(
                "MSY-" + safeExternalId(mutationId),
                Instant.now(),
                sourceDevice,
                product.id,
                product.name,
                product.barcode,
                product.category,
                actualDelta,
                product.priceCents,
                product.costCents,
                "",
                "",
                "",
                actualDelta == requestedDelta ? "Merged" : "Merged with zero-stock guard"
            );
            event.mobileProductId = mobileProductId;
            event.stockBaseKnown = true;
            event.stockBase = stockBase;
            event.sourceStock = sourceStock;
            event.mutationId = mutationId;
            state.stockSyncItems.add(0, event);
            recordStockMovement(mutationId, sourceDevice, product, actualDelta, "PHONE_RECONCILIATION", event.id);
            applied++;
        }
        return applied;
    }

    private void mergeSettingsFromPhone(String body) {
        String remoteSettings = objectValue(body, "settings");
        if (remoteSettings.isBlank()) {
            return;
        }
        Settings incoming = syncSettingsFromJson(remoteSettings);
        String incomingFingerprint = fallback(str(body, "settingsFingerprint"), settingsSyncFingerprint(incoming));
        String incomingLastFingerprint = str(body, "lastSettingsFingerprint");
        String localFingerprint = settingsSyncFingerprint(state.settings);
        boolean localChanged = !localFingerprint.equals(state.settings.settingsSyncFingerprint);
        boolean remoteChanged = incomingLastFingerprint.isBlank() || !incomingFingerprint.equals(incomingLastFingerprint);
        if (!remoteChanged && !state.settings.settingsSyncFingerprint.isBlank()) {
            return;
        }
        if (incomingLastFingerprint.isBlank() && settingsSyncBlank(incoming) && !settingsSyncBlank(state.settings)) {
            return;
        }
        if (!localChanged || !syncProfileHasMeaning(state.settings)) {
            applyIncomingSettings(incoming);
            return;
        }
        mergeIncomingSettings(incoming);
    }

    private Settings syncSettingsFromJson(String json) {
        Settings settings = new Settings();
        settings.businessName = cleanBusinessName(str(json, "businessName"));
        settings.ownerName = str(json, "ownerName").trim();
        settings.currency = normalizeCurrency(str(json, "currency"));
        settings.taxBasisPoints = Math.round(decimal(str(json, "taxPercent")) * 100);
        settings.receiptFooter = cleanReceiptFooter(str(json, "receiptFooter"));
        settings.whatsappPhoneNumberId = str(json, "whatsappPhoneNumberId").trim();
        settings.whatsappCatalogId = str(json, "whatsappCatalogId").trim();
        settings.whatsappDefaultCountryCode = str(json, "whatsappDefaultCountryCode").trim();
        settings.whatsappGraphVersion = normalizeGraphVersion(str(json, "whatsappGraphVersion"));
        return settings;
    }

    private void applyIncomingSettings(Settings incoming) {
        state.settings.businessName = incoming.businessName;
        state.settings.ownerName = incoming.ownerName;
        state.settings.currency = incoming.currency;
        state.settings.taxBasisPoints = incoming.taxBasisPoints;
        state.settings.receiptFooter = incoming.receiptFooter;
        state.settings.whatsappPhoneNumberId = incoming.whatsappPhoneNumberId;
        state.settings.whatsappCatalogId = incoming.whatsappCatalogId;
        state.settings.whatsappDefaultCountryCode = incoming.whatsappDefaultCountryCode;
        state.settings.whatsappGraphVersion = incoming.whatsappGraphVersion;
    }

    private void mergeIncomingSettings(Settings incoming) {
        if (isBlankOrPlaceholderBusinessName(state.settings.businessName) && !incoming.businessName.isBlank()) {
            state.settings.businessName = incoming.businessName;
        }
        if (state.settings.ownerName.isBlank() && !incoming.ownerName.isBlank()) {
            state.settings.ownerName = incoming.ownerName;
        }
        if (state.settings.currency.isBlank() && !incoming.currency.isBlank()) {
            state.settings.currency = incoming.currency;
        }
        if (state.settings.taxBasisPoints == 0 && incoming.taxBasisPoints != 0) {
            state.settings.taxBasisPoints = incoming.taxBasisPoints;
        }
        if (isBlankOrPlaceholderReceiptFooter(state.settings.receiptFooter) && !incoming.receiptFooter.isBlank()) {
            state.settings.receiptFooter = incoming.receiptFooter;
        }
        if (state.settings.whatsappPhoneNumberId.isBlank() && !incoming.whatsappPhoneNumberId.isBlank()) {
            state.settings.whatsappPhoneNumberId = incoming.whatsappPhoneNumberId;
        }
        if (state.settings.whatsappCatalogId.isBlank() && !incoming.whatsappCatalogId.isBlank()) {
            state.settings.whatsappCatalogId = incoming.whatsappCatalogId;
        }
        if (state.settings.whatsappDefaultCountryCode.isBlank() && !incoming.whatsappDefaultCountryCode.isBlank()) {
            state.settings.whatsappDefaultCountryCode = incoming.whatsappDefaultCountryCode;
        }
        if (state.settings.whatsappGraphVersion.isBlank() && !incoming.whatsappGraphVersion.isBlank()) {
            state.settings.whatsappGraphVersion = incoming.whatsappGraphVersion;
        }
    }

    private boolean syncProfileHasMeaning(Settings settings) {
        return !isBlankOrPlaceholderBusinessName(settings.businessName)
            || !settings.ownerName.isBlank()
            || !isBlankOrPlaceholderReceiptFooter(settings.receiptFooter)
            || !settings.whatsappPhoneNumberId.isBlank()
            || !settings.whatsappCatalogId.isBlank()
            || !settings.whatsappDefaultCountryCode.isBlank()
            || !settings.whatsappGraphVersion.isBlank();
    }

    private boolean settingsSyncBlank(Settings settings) {
        return !syncProfileHasMeaning(settings)
            && settings.currency.isBlank()
            && settings.taxBasisPoints == 0;
    }

    private void importProductSync(String body) {
        ProductSyncItem item = new ProductSyncItem(
            state.nextId("PSY"),
            Instant.now(),
            fallback(str(body, "deviceName"), "Desktop import"),
            str(body, "mobileProductId"),
            fallback(str(body, "name"), str(body, "productName")),
            str(body, "description"),
            str(body, "sku"),
            str(body, "barcode"),
            str(body, "category"),
            (int) num(body, "stock", 0),
            num(body, "priceCents", 0),
            num(body, "costCents", 0),
            "",
            str(body, "imageFileName"),
            str(body, "imageBase64"),
            str(body, "whatsappRetailerId"),
            str(body, "whatsappImageUrl"),
            str(body, "whatsappProductUrl"),
            "Imported"
        );
        applyProductSync(item);
        state.productSyncItems.add(0, item);
    }

    private synchronized void stockIntake(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        requireSession(exchange, body);
        SyncInboxService.Operation operation = syncOperation(body, "STOCK_INTAKE");
        if (operation.replay() != null) {
            sendJson(exchange, operation.replay().httpStatus, operation.replay().responseJson);
            return;
        }
        StockSyncItem item = new StockSyncItem(
            state.nextId("STK"),
            Instant.now(),
            fallback(str(body, "deviceName"), pairedDevice),
            "",
            str(body, "productName"),
            str(body, "barcode"),
            str(body, "category"),
            (int) num(body, "quantity", 0),
            num(body, "priceCents", 0),
            num(body, "costCents", 0),
            "",
            str(body, "imageFileName"),
            str(body, "imageBase64"),
            "Received"
        );
        applyStockIntake(item);
        state.stockSyncItems.add(0, item);
        recordStockMovement(syncOperationId(operation, item.id), item.sourceDevice, productById(item.productId), item.quantity, "STOCK_INTAKE", item.id);
        String response = "{\"accepted\":true}";
        recordSyncOutcome(operation, 200, response);
        persist();
        sendJson(exchange, 200, response);
    }

    private void applyProductSync(ProductSyncItem item) {
        if (item.name == null || item.name.isBlank()) {
            throw new IllegalArgumentException("Product name is required for catalog sync.");
        }
        String imagePath = saveImage(item.imageFileName, item.imageBase64);
        item.imagePath = imagePath;
        boolean knownMobileProduct = state.productSyncItems.stream()
            .anyMatch(previous -> item.sourceDevice.equals(previous.sourceDevice)
                && item.mobileProductId.equals(previous.mobileProductId));
        Product product = findProduct(item.barcode, item.name).orElseGet(() -> {
            Product created = new Product(state.nextId("PRD"), item.name, item.description, item.sku, item.barcode, item.category, imagePath, item.whatsappRetailerId, item.whatsappImageUrl, item.whatsappProductUrl, item.priceCents, item.costCents, item.stock);
            state.products.add(created);
            return created;
        });
        boolean mobileSalePlaceholder = !knownMobileProduct
            && product.stock == 0
            && state.saleLines.stream().anyMatch(line -> line.itemId.equals(product.id)
                && line.transactionId.startsWith("MOB-"));
        if (mobileSalePlaceholder) {
            product.stock = Math.max(0, item.stock);
        }
        product.name = fallback(item.name, product.name);
        product.description = fallback(item.description, product.description);
        product.sku = item.sku;
        product.barcode = item.barcode;
        product.category = item.category;
        product.priceCents = item.priceCents;
        product.costCents = item.costCents;
        if (!imagePath.isBlank()) {
            product.imagePath = imagePath;
        }
        product.whatsappRetailerId = fallback(item.whatsappRetailerId, fallback(product.whatsappRetailerId, product.barcode));
        product.whatsappImageUrl = fallback(item.whatsappImageUrl, product.whatsappImageUrl);
        product.whatsappProductUrl = fallback(item.whatsappProductUrl, product.whatsappProductUrl);
        item.status = knownMobileProduct ? "Catalog synced; shared stock preserved" : "Catalog synced";
    }

    private void applyStockIntake(StockSyncItem item) {
        String imagePath = saveImage(item.imageFileName, item.imageBase64);
        item.imagePath = imagePath;
        Product product = findProduct(item.barcode, item.productName).orElseGet(() -> {
            Product created = new Product(state.nextId("PRD"), fallback(item.productName, "Mobile stock item"), "", item.barcode, item.category, imagePath, item.barcode, item.priceCents, item.costCents, 0);
            state.products.add(created);
            return created;
        });
        product.stock += item.quantity;
        if (!imagePath.isBlank()) {
            product.imagePath = imagePath;
        }
        if (item.priceCents > 0) product.priceCents = item.priceCents;
        if (item.costCents > 0) product.costCents = item.costCents;
        item.productId = product.id;
        item.status = "Synced";
    }

    private Optional<Product> findProduct(String barcode, String name) {
        Optional<Product> byBarcode = state.productByBarcode(barcode);
        if (byBarcode.isPresent()) {
            return byBarcode;
        }
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return state.products.stream().filter(product -> product.name.equalsIgnoreCase(name)).findFirst();
    }

    private Product productById(String productId) {
        return state.products.stream()
            .filter(product -> product.id.equals(productId))
            .findFirst()
            .orElse(null);
    }

    private void recordStockMovement(
        String operationId,
        String sourceDevice,
        Product product,
        int quantityDelta,
        String reason,
        String referenceId
    ) {
        if (product == null || quantityDelta == 0) {
            return;
        }
        state.stockMovements.add(new StockMovement(
            state.nextId("MOV"),
            fallback(operationId, state.nextId("OP")),
            Instant.now(),
            fallback(sourceDevice, "Unknown device"),
            product.id,
            quantityDelta,
            product.stock,
            reason,
            fallback(referenceId, "")
        ));
    }

    private Optional<Customer> findCustomer(String name, String phone) {
        if (phone != null && !phone.isBlank()) {
            Optional<Customer> byPhone = state.customers.stream()
                .filter(customer -> phone.equalsIgnoreCase(customer.phone))
                .findFirst();
            if (byPhone.isPresent()) {
                return byPhone;
            }
        }
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return state.customers.stream()
            .filter(customer -> customer.name.equalsIgnoreCase(name))
            .findFirst();
    }

    private List<DesktopSaleLine> parseMobileSaleLines(String body, String transactionId) {
        List<DesktopSaleLine> lines = new ArrayList<>();
        for (String object : objectsInArray(body, "lines")) {
            String kindRaw = fallback(str(object, "kind"), "PRODUCT");
            CartLine.Kind kind = "SERVICE".equalsIgnoreCase(kindRaw) ? CartLine.Kind.SERVICE : CartLine.Kind.PRODUCT;
            String barcode = str(object, "barcode");
            String name = fallback(str(object, "name"), str(object, "productName"));
            int quantity = Math.max(1, (int) num(object, "quantity", 1));
            long unitCents = num(object, "unitCents", 0);
            long lineTotalCents = num(object, "lineTotalCents", Math.max(0, unitCents * quantity));
            String itemId = fallback(str(object, "desktopItemId"), str(object, "itemId"));
            String category = str(object, "category");
            if (kind == CartLine.Kind.PRODUCT) {
                Optional<Product> matchedProduct = findProduct(barcode, name);
                Product product;
                if (matchedProduct.isPresent()) {
                    product = matchedProduct.get();
                } else {
                    product = new Product(
                        state.nextId("PRD"),
                        fallback(name, "Mobile product"),
                        "",
                        barcode,
                        category,
                        "",
                        fallback(barcode, str(object, "mobileProductId")),
                        Math.max(0, unitCents),
                        0,
                        0
                    );
                    state.products.add(product);
                }
                itemId = product.id;
                barcode = fallback(barcode, product.barcode);
                category = fallback(category, product.category);
                name = fallback(name, product.name);
            }
            lines.add(new DesktopSaleLine(
                state.nextId("SLN"),
                transactionId,
                kind,
                itemId,
                name,
                barcode,
                category,
                quantity,
                Math.max(0, unitCents),
                Math.max(0, lineTotalCents),
                str(object, "staffName")
            ));
        }
        return lines;
    }

    private void applyMobileSaleStockImpact(List<DesktopSaleLine> lines, String operationId, String sourceDevice) {
        for (DesktopSaleLine line : lines) {
            if (line.kind != CartLine.Kind.PRODUCT || line.quantity <= 0) {
                continue;
            }
            state.products.stream()
                .filter(product -> product.id.equals(line.itemId))
                .findFirst()
                .ifPresent(product -> {
                    int previous = product.stock;
                    product.stock = Math.max(0, product.stock - line.quantity);
                    recordStockMovement(operationId, sourceDevice, product, product.stock - previous, "MOBILE_SALE", line.transactionId);
                });
        }
    }

    private String saveImage(String fileName, String base64) {
        if (base64 == null || base64.isBlank()) {
            return "";
        }
        return store.saveIncomingImage(fileName, base64);
    }

    private String imageBase64ForPhone(Product product) {
        if (product.imagePath == null || product.imagePath.isBlank()) {
            return "";
        }
        try {
            Path image = Path.of(product.imagePath).toAbsolutePath().normalize();
            Path allowed = store.incomingImagesDir().toAbsolutePath().normalize();
            if (!image.startsWith(allowed) || !Files.isRegularFile(image) || Files.size(image) > 1_200_000L) {
                return "";
            }
            return Base64.getEncoder().encodeToString(Files.readAllBytes(image));
        } catch (Exception ignored) {
            return "";
        }
    }

    private String pairingJson() {
        return pairingJsonRaw();
    }

    private String pairingJsonRaw() {
        String host = localHost();
        String phoneUrl = phonePort > 0 ? "http://" + host + ":" + phonePort : "";
        String payload = phonePort > 0 ? "biashara-desktop://pair?host=" + host + "&port=" + phonePort + "&token=" + json(pairToken) : "";
        return "{"
            + "\"token\":\"" + json(pairToken) + "\","
            + "\"sessionPaired\":" + (!sessionKey.isBlank()) + ","
            + "\"pairedDevice\":\"" + json(pairedDevice) + "\","
            + "\"businessName\":\"" + json(state.settings.businessName) + "\","
            + "\"discoveryPort\":" + DISCOVERY_PORT + ","
            + "\"localUrl\":\"" + json(phoneUrl) + "\","
            + "\"uiUrl\":\"http://127.0.0.1:" + uiPort + "\","
            + "\"protocolVersion\":\"" + SyncProtocol.CURRENT_VERSION + "\","
            + "\"authentication\":\"" + SyncProtocol.AUTHENTICATION + "\","
            + "\"payload\":\"" + json(payload) + "\""
            + "}";
    }

    private String phoneCapabilitiesJson() {
        return "{"
            + "\"protocolVersion\":\"" + SyncProtocol.CURRENT_VERSION + "\","
            + "\"supportedVersions\":[\"" + SyncProtocol.CURRENT_VERSION + "\"],"
            + "\"authentication\":\"" + SyncProtocol.AUTHENTICATION + "\","
            + "\"legacySessionAuthentication\":true,"
            + "\"operationIdempotency\":true,"
            + "\"maximumClockSkewSeconds\":" + (SyncProtocol.MAX_CLOCK_SKEW_MILLIS / 1000L) + ","
            + "\"maximumBodyBytes\":" + SyncProtocol.MAX_BODY_BYTES
            + "}";
    }

    private synchronized String reconcileJson(boolean includeImages, int stockChangesApplied, int serviceChangesApplied) {
        StringBuilder out = new StringBuilder();
        out.append("{");
        out.append("\"generatedAtMillis\":").append(System.currentTimeMillis()).append(",");
        out.append("\"stockChangesApplied\":").append(stockChangesApplied).append(",");
        out.append("\"serviceChangesApplied\":").append(serviceChangesApplied).append(",");
        out.append("\"settings\":").append(settingsSyncJson(state.settings)).append(",");
        out.append("\"settingsFingerprint\":\"").append(json(settingsSyncFingerprint(state.settings))).append("\",");
        out.append("\"products\":[");
        appendList(out, state.products.stream()
            .sorted(Comparator.comparing(product -> product.name))
            .map(product -> productJsonForPhone(product, includeImages))
            .toList());
        out.append("],\"services\":[");
        appendList(out, state.services.stream()
            .sorted(Comparator.comparing(service -> service.name))
            .map(this::serviceJson)
            .toList());
        out.append("],\"transactions\":[");
        appendList(out, state.transactions.stream()
            .filter(transaction -> !transaction.id.startsWith("MOB-"))
            .filter(transaction -> transaction.type == TransactionType.SALE || transaction.type == TransactionType.SERVICE_SALE)
            .sorted(Comparator.comparing((Transaction transaction) -> transaction.createdAt).reversed())
            .limit(200)
            .map(this::transactionJson)
            .toList());
        out.append("],\"serviceTickets\":[");
        appendList(out, state.serviceTickets.stream()
            .sorted(Comparator.comparing((ServiceTicket ticket) -> ticket.createdAt).reversed())
            .limit(200)
            .map(this::serviceTicketJson)
            .toList());
        out.append("]}");
        return out.toString();
    }

    private String settingsJson(Settings settings) {
        return "{"
            + "\"businessName\":\"" + json(settings.businessName) + "\","
            + "\"ownerName\":\"" + json(settings.ownerName) + "\","
            + "\"currency\":\"" + json(settings.currency) + "\","
            + "\"taxPercent\":\"" + (settings.taxBasisPoints / 100.0) + "\","
            + "\"receiptFooter\":\"" + json(settings.receiptFooter) + "\","
            + "\"modelPath\":\"" + json(settings.modelPath) + "\","
            + "\"aiProvider\":\"" + json(normalizedAiProvider()) + "\","
            + "\"lmStudioBaseUrl\":\"" + json(settings.lmStudioBaseUrl) + "\","
            + "\"lmStudioModel\":\"" + json(settings.lmStudioModel) + "\","
            + "\"whatsappPhoneNumberId\":\"" + json(settings.whatsappPhoneNumberId) + "\","
            + "\"whatsappCatalogId\":\"" + json(settings.whatsappCatalogId) + "\","
            + "\"whatsappDefaultCountryCode\":\"" + json(settings.whatsappDefaultCountryCode) + "\","
            + "\"whatsappGraphVersion\":\"" + json(settings.whatsappGraphVersion) + "\","
            + "\"whatsappAccessTokenConfigured\":" + (!settings.whatsappAccessToken.isBlank())
            + "}";
    }

    private String settingsSyncJson(Settings settings) {
        return "{"
            + "\"businessName\":\"" + json(cleanBusinessName(settings.businessName)) + "\","
            + "\"ownerName\":\"" + json(settings.ownerName.trim()) + "\","
            + "\"currency\":\"" + json(normalizeCurrency(settings.currency)) + "\","
            + "\"taxPercent\":\"" + (settings.taxBasisPoints / 100.0) + "\","
            + "\"receiptFooter\":\"" + json(cleanReceiptFooter(settings.receiptFooter)) + "\","
            + "\"whatsappPhoneNumberId\":\"" + json(settings.whatsappPhoneNumberId.trim()) + "\","
            + "\"whatsappCatalogId\":\"" + json(settings.whatsappCatalogId.trim()) + "\","
            + "\"whatsappDefaultCountryCode\":\"" + json(settings.whatsappDefaultCountryCode.trim()) + "\","
            + "\"whatsappGraphVersion\":\"" + json(normalizeGraphVersion(settings.whatsappGraphVersion)) + "\""
            + "}";
    }

    private String settingsSyncFingerprint(Settings settings) {
        String canonical = String.join("\n",
            cleanBusinessName(settings.businessName),
            settings.ownerName.trim(),
            normalizeCurrency(settings.currency),
            Long.toString(settings.taxBasisPoints),
            cleanReceiptFooter(settings.receiptFooter),
            settings.whatsappPhoneNumberId.trim(),
            settings.whatsappCatalogId.trim(),
            settings.whatsappDefaultCountryCode.trim(),
            normalizeGraphVersion(settings.whatsappGraphVersion)
        );
        return sha256(canonical);
    }

    private String productJson(Product product) {
        return "{"
            + "\"id\":\"" + json(product.id) + "\","
            + "\"name\":\"" + json(product.name) + "\","
            + "\"description\":\"" + json(product.description) + "\","
            + "\"sku\":\"" + json(product.sku) + "\","
            + "\"barcode\":\"" + json(product.barcode) + "\","
            + "\"category\":\"" + json(product.category) + "\","
            + "\"imageUrl\":\"" + json(product.imagePath.isBlank() ? "" : "/media?path=" + url(product.imagePath)) + "\","
            + "\"imagePath\":\"" + json(product.imagePath) + "\","
            + "\"whatsappRetailerId\":\"" + json(product.whatsappRetailerId) + "\","
            + "\"whatsappImageUrl\":\"" + json(product.whatsappImageUrl) + "\","
            + "\"whatsappProductUrl\":\"" + json(product.whatsappProductUrl) + "\","
            + "\"priceCents\":" + product.priceCents + ","
            + "\"costCents\":" + product.costCents + ","
            + "\"stock\":" + product.stock
            + "}";
    }

    private String productJsonForPhone(Product product, boolean includeImages) {
        String imageBase64 = includeImages ? imageBase64ForPhone(product) : "";
        String imageFileName = "";
        if (!imageBase64.isBlank() && product.imagePath != null && !product.imagePath.isBlank()) {
            imageFileName = Path.of(product.imagePath).getFileName().toString();
        }
        return "{"
            + "\"id\":\"" + json(product.id) + "\","
            + "\"name\":\"" + json(product.name) + "\","
            + "\"description\":\"" + json(product.description) + "\","
            + "\"sku\":\"" + json(product.sku) + "\","
            + "\"barcode\":\"" + json(product.barcode) + "\","
            + "\"category\":\"" + json(product.category) + "\","
            + "\"imageFileName\":\"" + json(imageFileName) + "\","
            + "\"imageBase64\":\"" + json(imageBase64) + "\","
            + "\"whatsappRetailerId\":\"" + json(product.whatsappRetailerId) + "\","
            + "\"whatsappImageUrl\":\"" + json(product.whatsappImageUrl) + "\","
            + "\"whatsappProductUrl\":\"" + json(product.whatsappProductUrl) + "\","
            + "\"priceCents\":" + product.priceCents + ","
            + "\"costCents\":" + product.costCents + ","
            + "\"stock\":" + product.stock
            + "}";
    }

    private String serviceJson(ServiceItem service) {
        return "{"
            + "\"id\":\"" + json(service.id) + "\","
            + "\"name\":\"" + json(service.name) + "\","
            + "\"description\":\"" + json(service.description) + "\","
            + "\"category\":\"" + json(service.category) + "\","
            + "\"priceCents\":" + service.priceCents + ","
            + "\"priceMode\":\"" + json(service.priceMode) + "\","
            + "\"durationMinutes\":" + service.durationMinutes + ","
            + "\"warrantyDays\":" + service.warrantyDays + ","
            + "\"visibleInKiosk\":" + service.visibleInKiosk + ","
            + "\"updatedAt\":" + service.updatedAt
            + "}";
    }

    private String serviceTicketJson(ServiceTicket ticket) {
        if (ticket == null) {
            return "null";
        }
        return "{"
            + "\"id\":\"" + json(ticket.id) + "\","
            + "\"token\":\"" + json(ticket.token) + "\","
            + "\"createdAt\":\"" + json(ticket.createdAt == null ? "" : ticket.createdAt.atZone(ZoneId.systemDefault()).toString()) + "\","
            + "\"createdAtMillis\":" + (ticket.createdAt == null ? 0 : ticket.createdAt.toEpochMilli()) + ","
            + "\"startedAt\":\"" + json(ticket.startedAt == null ? "" : ticket.startedAt.atZone(ZoneId.systemDefault()).toString()) + "\","
            + "\"startedAtMillis\":" + (ticket.startedAt == null ? 0 : ticket.startedAt.toEpochMilli()) + ","
            + "\"completedAt\":\"" + json(ticket.completedAt == null ? "" : ticket.completedAt.atZone(ZoneId.systemDefault()).toString()) + "\","
            + "\"completedAtMillis\":" + (ticket.completedAt == null ? 0 : ticket.completedAt.toEpochMilli()) + ","
            + "\"status\":\"" + ticket.status + "\","
            + "\"transactionId\":\"" + json(ticket.transactionId) + "\","
            + "\"customerId\":\"" + json(ticket.customerId) + "\","
            + "\"customerName\":\"" + json(ticket.customerName) + "\","
            + "\"customerPhone\":\"" + json(ticket.customerPhone) + "\","
            + "\"serviceId\":\"" + json(ticket.serviceId) + "\","
            + "\"serviceName\":\"" + json(ticket.serviceName) + "\","
            + "\"category\":\"" + json(ticket.category) + "\","
            + "\"quantity\":" + ticket.quantity + ","
            + "\"unitCents\":" + ticket.unitCents + ","
            + "\"totalCents\":" + ticket.totalCents + ","
            + "\"paidCents\":" + ticket.paidCents + ","
            + "\"balanceCents\":" + Math.max(0, ticket.totalCents - ticket.paidCents) + ","
            + "\"paymentMethod\":\"" + json(ticket.paymentMethod) + "\","
            + "\"assignedTechnician\":\"" + json(ticket.assignedTechnician) + "\","
            + "\"activeTechnician\":\"" + json(ticket.activeTechnician) + "\","
            + "\"requirements\":\"" + json(ticket.requirements) + "\","
            + "\"completionNotes\":\"" + json(ticket.completionNotes) + "\""
            + "}";
    }

    private String customerJson(Customer customer) {
        return "{"
            + "\"id\":\"" + json(customer.id) + "\","
            + "\"name\":\"" + json(customer.name) + "\","
            + "\"phone\":\"" + json(customer.phone) + "\","
            + "\"balanceCents\":" + customer.balanceCents + ","
            + "\"visits\":" + customer.visits
            + "}";
    }

    private String transactionJson(Transaction transaction) {
        Customer customer = state.customerById(transaction.customerId);
        return "{"
            + "\"id\":\"" + json(transaction.id) + "\","
            + "\"createdAt\":\"" + json(transaction.createdAt.atZone(ZoneId.systemDefault()).toString()) + "\","
            + "\"createdAtMillis\":" + transaction.createdAt.toEpochMilli() + ","
            + "\"type\":\"" + transaction.type + "\","
            + "\"customerId\":\"" + json(transaction.customerId) + "\","
            + "\"customerName\":\"" + json(transaction.customerName) + "\","
            + "\"customerPhone\":\"" + json(customer == null ? "" : customer.phone) + "\","
            + "\"description\":\"" + json(transaction.description) + "\","
            + "\"paymentMethod\":\"" + json(transaction.paymentMethod) + "\","
            + "\"subtotalCents\":" + transaction.subtotalCents + ","
            + "\"taxCents\":" + transaction.taxCents + ","
            + "\"totalCents\":" + transaction.totalCents + ","
            + "\"paidCents\":" + transaction.paidCents + ","
            + "\"balanceCents\":" + transaction.balanceCents + ","
            + "\"lines\":["
            + String.join(",", state.saleLines.stream()
                .filter(line -> line.transactionId.equals(transaction.id))
                .map(this::saleLineJson)
                .toList())
            + "]"
            + "}";
    }

    private String saleLineJson(DesktopSaleLine line) {
        return "{"
            + "\"id\":\"" + json(line.id) + "\","
            + "\"kind\":\"" + line.kind + "\","
            + "\"itemId\":\"" + json(line.itemId) + "\","
            + "\"name\":\"" + json(line.name) + "\","
            + "\"barcode\":\"" + json(line.barcode) + "\","
            + "\"category\":\"" + json(line.category) + "\","
            + "\"quantity\":" + line.quantity + ","
            + "\"unitCents\":" + line.unitCents + ","
            + "\"lineTotalCents\":" + line.lineTotalCents + ","
            + "\"staffName\":\"" + json(line.staffName) + "\""
            + "}";
    }

    private String productSyncJson(ProductSyncItem item) {
        return "{\"createdAt\":\"" + json(item.createdAt.toString()) + "\",\"sourceDevice\":\"" + json(item.sourceDevice) + "\",\"name\":\"" + json(item.name) + "\",\"barcode\":\"" + json(item.barcode) + "\",\"stock\":" + item.stock + ",\"status\":\"" + json(item.status) + "\"}";
    }

    private String stockSyncJson(StockSyncItem item) {
        return "{\"createdAt\":\"" + json(item.createdAt.toString()) + "\",\"sourceDevice\":\"" + json(item.sourceDevice) + "\",\"productName\":\"" + json(item.productName) + "\",\"barcode\":\"" + json(item.barcode) + "\",\"quantity\":" + item.quantity + ",\"status\":\"" + json(item.status) + "\"}";
    }

    private String scanJson(ScanEvent event) {
        return "{\"createdAt\":\"" + json(event.createdAt.toString()) + "\",\"sourceDevice\":\"" + json(event.sourceDevice) + "\",\"kind\":\"" + json(event.kind) + "\",\"rawValue\":\"" + json(event.rawValue) + "\",\"status\":\"" + json(event.status) + "\"}";
    }

    private void appendList(StringBuilder out, List<String> items) {
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) out.append(",");
            out.append(items.get(i));
        }
    }

    private String jsonArray(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(",");
            out.append("\"").append(json(values.get(i))).append("\"");
        }
        return out.append("]").toString();
    }

    private void requireMethod(HttpExchange exchange, String method) throws IOException {
        if (!method.equalsIgnoreCase(exchange.getRequestMethod())) {
            throw new IllegalArgumentException(method + " required");
        }
    }

    private synchronized void requireSession(HttpExchange exchange, String body) {
        String supplied = exchange.getRequestHeaders().getFirst(SyncProtocol.HEADER_SESSION);
        if (supplied == null || supplied.isBlank()) {
            supplied = str(body, "sessionKey");
        }
        PhoneRequestAuthenticator.Result result = phoneRequestAuthenticator.authenticate(
            exchange.getRequestMethod(),
            exchange.getRequestURI().getPath(),
            body.getBytes(StandardCharsets.UTF_8),
            supplied,
            exchange.getRequestHeaders().getFirst(SyncProtocol.HEADER_PROTOCOL),
            exchange.getRequestHeaders().getFirst(SyncProtocol.HEADER_REQUEST_ID),
            exchange.getRequestHeaders().getFirst(SyncProtocol.HEADER_TIMESTAMP),
            exchange.getRequestHeaders().getFirst(SyncProtocol.HEADER_NONCE),
            exchange.getRequestHeaders().getFirst(SyncProtocol.HEADER_SIGNATURE),
            sessionKey,
            !signedPhoneRequestsRequired
        );
        if (!result.accepted()) {
            throw new PhoneAuthenticationException(result.status(), result.message());
        }
        if (!result.legacy() && !signedPhoneRequestsRequired) {
            signedPhoneRequestsRequired = true;
            store.saveBridgeSession(sessionKey, pairedDevice, SyncProtocol.CURRENT_VERSION);
        }
    }

    private SyncInboxService.Operation syncOperation(String body, String operationType) {
        return syncInboxService.inspect(state.syncInboxEntries, body, operationType, pairedDevice);
    }

    private void recordSyncOutcome(SyncInboxService.Operation operation, int httpStatus, String responseJson) {
        SyncInboxEntry entry = syncInboxService.outcome(operation, httpStatus, responseJson);
        if (entry != null) {
            state.syncInboxEntries.add(entry);
        }
    }

    private String syncOperationId(SyncInboxService.Operation operation, String fallbackId) {
        return fallback(operation.id(), fallbackId);
    }

    private void persist() {
        store.save(state);
    }

    private String scanKind(String raw) {
        if (raw == null) return "Unknown";
        if (raw.startsWith("JOB-")) return "Service ticket";
        if (raw.startsWith("BSVC:")) return "Service";
        if (raw.startsWith("BSVOU:")) return "Voucher";
        if (raw.startsWith("BSRC:")) return "Receipt";
        return "Product";
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readNBytes(SyncProtocol.MAX_BODY_BYTES), StandardCharsets.UTF_8);
    }

    private void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        sendText(exchange, code, json, "application/json");
    }

    private void sendText(HttpExchange exchange, int code, String text, String type) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void writeSse(OutputStream output, String event, String dataJson) throws IOException {
        output.write(("event: " + event + "\n").getBytes(StandardCharsets.UTF_8));
        output.write(("data: " + dataJson + "\n\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private String str(String json, String key) {
        String quoted = "\"" + key + "\"";
        int keyIndex = json.indexOf(quoted);
        if (keyIndex < 0) return "";
        int colon = json.indexOf(':', keyIndex + quoted.length());
        if (colon < 0) return "";
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return "";
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                out.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private long num(String json, String key, long fallback) {
        String quoted = "\"" + key + "\"";
        int keyIndex = json.indexOf(quoted);
        if (keyIndex < 0) return fallback;
        int colon = json.indexOf(':', keyIndex + quoted.length());
        if (colon < 0) return fallback;
        StringBuilder out = new StringBuilder();
        for (int i = colon + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if ((c >= '0' && c <= '9') || c == '-') {
                out.append(c);
            } else if (!Character.isWhitespace(c)) {
                break;
            }
        }
        try {
            return out.isEmpty() ? fallback : Long.parseLong(out.toString());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean bool(String json, String key) {
        String quoted = "\"" + key + "\"";
        int keyIndex = json.indexOf(quoted);
        if (keyIndex < 0) return false;
        int colon = json.indexOf(':', keyIndex + quoted.length());
        if (colon < 0) return false;
        String tail = json.substring(colon + 1).stripLeading().toLowerCase(Locale.ROOT);
        return tail.startsWith("true") || tail.startsWith("1");
    }

    private String objectValue(String json, String key) {
        String quoted = "\"" + key + "\"";
        int keyIndex = json.indexOf(quoted);
        if (keyIndex < 0) return "";
        int objectStart = json.indexOf('{', keyIndex + quoted.length());
        if (objectStart < 0) return "";
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = objectStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(objectStart, i + 1);
                }
            }
        }
        return "";
    }

    private List<String> objectsInArray(String json, String key) {
        String quoted = "\"" + key + "\"";
        int keyIndex = json.indexOf(quoted);
        if (keyIndex < 0) return List.of();
        int arrayStart = json.indexOf('[', keyIndex + quoted.length());
        if (arrayStart < 0) return List.of();
        List<String> objects = new ArrayList<>();
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        int objectStart = -1;
        for (int i = arrayStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                if (depth == 0) {
                    objectStart = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objectStart >= 0) {
                    objects.add(json.substring(objectStart, i + 1));
                    objectStart = -1;
                }
            } else if (c == ']' && depth == 0) {
                break;
            }
        }
        return objects;
    }

    private long cents(String body, String key) {
        String raw = str(body, key);
        if (raw.isBlank()) {
            return num(body, key + "Cents", 0);
        }
        return new BigDecimal(raw.trim().replace(",", "")).movePointRight(2).longValue();
    }

    private long centsOrNumber(String body, String key, long fallback) {
        String raw = str(body, key);
        if (!raw.isBlank()) {
            try {
                return new BigDecimal(raw.trim().replace(",", "")).movePointRight(2).longValue();
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return num(body, key + "Cents", fallback);
    }

    private double decimal(String raw) {
        try {
            return raw == null || raw.isBlank() ? 0 : Double.parseDouble(raw);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String cleanBusinessName(String value) {
        String clean = value == null ? "" : value.trim();
        return isBlankOrPlaceholderBusinessName(clean) ? "" : clean;
    }

    private String cleanReceiptFooter(String value) {
        String clean = value == null ? "" : value.trim();
        return isBlankOrPlaceholderReceiptFooter(clean) ? "" : clean;
    }

    private boolean isBlankOrPlaceholderBusinessName(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.isBlank()
            || "My Business".equalsIgnoreCase(clean)
            || "Biashara AI Pro".equalsIgnoreCase(clean);
    }

    private boolean isBlankOrPlaceholderReceiptFooter(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.isBlank()
            || "Thank you!".equalsIgnoreCase(clean)
            || "Thank you for your business.".equalsIgnoreCase(clean);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                out.append(String.format("%02x", b & 0xff));
            }
            return out.toString();
        } catch (Exception ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String safeExternalId(String value) {
        String clean = fallback(value, UUID.randomUUID().toString())
            .replaceAll("[^A-Za-z0-9_-]", "-")
            .replaceAll("-+", "-");
        return clean.isBlank() ? UUID.randomUUID().toString() : clean;
    }

    private String json(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private String url(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void startDiscoveryBeacon() {
        if (phonePort <= 0 || discoveryExecutor != null) {
            return;
        }
        discoveryExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "biashara-desktop-discovery");
            thread.setDaemon(true);
            return thread;
        });
        discoveryExecutor.scheduleWithFixedDelay(() -> {
            try {
                broadcastDiscovery();
            } catch (Exception ignored) {
                // The HTTP bridge remains the source of truth if UDP broadcast is blocked.
            }
        }, 0, 2, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (discoveryExecutor != null) {
                discoveryExecutor.shutdownNow();
            }
        }, "biashara-desktop-discovery-shutdown"));
    }

    private void broadcastDiscovery() throws IOException {
        String host = localHost();
        String phoneUrl = "http://" + host + ":" + phonePort;
        String payload = DISCOVERY_PREFIX + "{"
            + "\"host\":\"" + json(host) + "\","
            + "\"port\":" + phonePort + ","
            + "\"uiPort\":" + uiPort + ","
            + "\"token\":\"" + json(pairToken) + "\","
            + "\"businessName\":\"" + json(state.settings.businessName) + "\","
            + "\"phoneUrl\":\"" + json(phoneUrl) + "\""
            + "}";
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            for (InetAddress address : discoveryBroadcastAddresses()) {
                socket.send(new DatagramPacket(bytes, bytes.length, address, DISCOVERY_PORT));
            }
        }
    }

    private List<InetAddress> discoveryBroadcastAddresses() {
        List<InetAddress> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addBroadcastAddress(out, seen, "255.255.255.255");
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface item = interfaces.nextElement();
                if (!usableLanInterface(item)) continue;
                for (InterfaceAddress address : item.getInterfaceAddresses()) {
                    InetAddress broadcast = address.getBroadcast();
                    if (broadcast == null) continue;
                    String value = broadcast.getHostAddress();
                    if (seen.add(value)) {
                        out.add(broadcast);
                    }
                }
            }
        } catch (Exception ignored) {
            // Global broadcast above is the fallback.
        }
        return out;
    }

    private void addBroadcastAddress(List<InetAddress> out, Set<String> seen, String address) {
        try {
            if (seen.add(address)) {
                out.add(InetAddress.getByName(address));
            }
        } catch (Exception ignored) {
            // Ignore invalid fallback addresses.
        }
    }

    private String localHost() {
        List<AddressCandidate> candidates = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface item = interfaces.nextElement();
                if (!usableLanInterface(item)) continue;
                Enumeration<InetAddress> addresses = item.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    String value = address.getHostAddress();
                    if (isPrivateIpv4(value)) {
                        candidates.add(new AddressCandidate(value, addressRank(item, value)));
                    }
                }
            }
        } catch (Exception ignored) {
            // Fallback below.
        }
        return candidates.stream()
            .min(Comparator.comparingInt(AddressCandidate::rank).thenComparing(AddressCandidate::host))
            .map(AddressCandidate::host)
            .orElse("127.0.0.1");
    }

    private boolean usableLanInterface(NetworkInterface item) throws IOException {
        if (!item.isUp() || item.isLoopback() || item.isVirtual()) {
            return false;
        }
        String label = (item.getName() + " " + item.getDisplayName()).toLowerCase(Locale.ROOT);
        return !(label.contains("virtual")
            || label.contains("vethernet")
            || label.contains("hyper-v")
            || label.contains("docker")
            || label.contains("wsl")
            || label.contains("vmware")
            || label.contains("virtualbox")
            || label.contains("loopback"));
    }

    private int addressRank(NetworkInterface item, String host) {
        String label = (item.getName() + " " + item.getDisplayName()).toLowerCase(Locale.ROOT);
        int rank;
        if (host.startsWith("192.168.")) {
            rank = 10;
        } else if (host.startsWith("10.")) {
            rank = 20;
        } else if (isPrivate172(host)) {
            rank = 30;
        } else {
            rank = 80;
        }
        if (label.contains("wi-fi") || label.contains("wifi") || label.contains("wireless") || label.contains("wlan")) {
            rank -= 5;
        } else if (label.contains("ethernet") || label.contains("eth")) {
            rank -= 3;
        }
        return rank;
    }

    private boolean isPrivateIpv4(String host) {
        if (host == null || host.contains(":")) return false;
        if (host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("169.254.")) {
            return true;
        }
        return isPrivate172(host);
    }

    private boolean isPrivate172(String host) {
        if (host == null || !host.startsWith("172.")) return false;
        String[] parts = host.split("\\.");
        if (parts.length != 4) return false;
        try {
            int second = Integer.parseInt(parts[1]);
            return second >= 16 && second <= 31;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private record AddressCandidate(String host, int rank) {
    }
}
