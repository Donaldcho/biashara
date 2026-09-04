package com.biasharaai.desktop.v2;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Enumeration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PhoneBridgeServer {
    interface Listener {
        void onPhoneBridgeScan(ScanEvent event);

        void onPhoneStockIntake(StockSyncItem item);

        void onPhoneProductSync(ProductSyncItem item);

        void onPhoneBridgeChanged();
    }

    static final class BridgeStatus {
        final boolean running;
        final int port;
        final String host;
        final String token;
        final Instant tokenExpiresAt;
        final String pairedDevice;
        final int scansAccepted;
        final int stockItemsAccepted;
        final int productsAccepted;

        BridgeStatus(boolean running, int port, String host, String token, Instant tokenExpiresAt, String pairedDevice, int scansAccepted, int stockItemsAccepted, int productsAccepted) {
            this.running = running;
            this.port = port;
            this.host = host;
            this.token = token;
            this.tokenExpiresAt = tokenExpiresAt;
            this.pairedDevice = pairedDevice;
            this.scansAccepted = scansAccepted;
            this.stockItemsAccepted = stockItemsAccepted;
            this.productsAccepted = productsAccepted;
        }
    }

    private final int port;
    private final Listener listener;
    private HttpServer server;
    private ExecutorService executor;
    private String token = "";
    private String sessionKey = "";
    private Instant tokenExpiresAt = Instant.EPOCH;
    private String pairedDevice = "";
    private int scansAccepted = 0;
    private int stockItemsAccepted = 0;
    private int productsAccepted = 0;

    PhoneBridgeServer(int port, Listener listener) {
        this.port = port;
        this.listener = listener;
        rotateToken();
    }

    synchronized void start() {
        if (server != null) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
            server.createContext("/health", this::health);
            server.createContext("/pair", this::pair);
            server.createContext("/scan", this::scan);
            server.createContext("/stock-intake", this::stockIntake);
            server.createContext("/product-sync", this::productSync);
            server.createContext("/status", this::statusEndpoint);
            executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "biashara-phone-bridge");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.start();
            listener.onPhoneBridgeChanged();
        } catch (IOException ex) {
            server = null;
            throw new IllegalStateException("Could not start phone bridge on port " + port, ex);
        }
    }

    synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        pairedDevice = "";
        sessionKey = "";
        listener.onPhoneBridgeChanged();
    }

    synchronized void rotateToken() {
        token = randomToken();
        tokenExpiresAt = Instant.now().plus(Duration.ofMinutes(20));
        pairedDevice = "";
        sessionKey = "";
        listener.onPhoneBridgeChanged();
    }

    synchronized BridgeStatus status() {
        return new BridgeStatus(server != null, port, localHost(), token, tokenExpiresAt, pairedDevice, scansAccepted, stockItemsAccepted, productsAccepted);
    }

    synchronized String pairingPayload() {
        BridgeStatus status = status();
        return "biashara-desktop://pair?host=" + status.host + "&port=" + status.port + "&token=" + token;
    }

    private void health(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "{\"ok\":true,\"service\":\"biashara-desktop-phone-bridge\"}");
    }

    private void statusEndpoint(HttpExchange exchange) throws IOException {
        BridgeStatus status = status();
        respond(exchange, 200, "{"
            + "\"running\":" + status.running + ","
            + "\"paired\":" + (!status.pairedDevice.isBlank()) + ","
            + "\"device\":\"" + json(status.pairedDevice) + "\","
            + "\"scansAccepted\":" + status.scansAccepted + ","
            + "\"stockItemsAccepted\":" + status.stockItemsAccepted + ","
            + "\"productsAccepted\":" + status.productsAccepted
            + "}");
    }

    private void pair(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"POST required\"}");
            return;
        }
        String body = readBody(exchange);
        String suppliedToken = value(body, "token");
        String device = value(body, "deviceName");
        synchronized (this) {
            if (Instant.now().isAfter(tokenExpiresAt) || !token.equals(suppliedToken)) {
                respond(exchange, 401, "{\"error\":\"Invalid or expired pairing token\"}");
                return;
            }
            sessionKey = UUID.randomUUID().toString().replace("-", "");
            pairedDevice = device.isBlank() ? "Mobile device" : device;
        }
        listener.onPhoneBridgeChanged();
        respond(exchange, 200, "{\"sessionKey\":\"" + json(sessionKey) + "\"}");
    }

    private void scan(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"POST required\"}");
            return;
        }
        String body = readBody(exchange);
        String suppliedSession = suppliedSession(exchange, body);
        String raw = value(body, "rawValue");
        if (raw.isBlank()) {
            raw = value(body, "raw");
        }
        String source = value(body, "deviceName");
        synchronized (this) {
            if (sessionKey.isBlank() || !sessionKey.equals(suppliedSession)) {
                respond(exchange, 401, "{\"error\":\"Pair phone first\"}");
                return;
            }
            scansAccepted++;
        }
        ScanEvent event = new ScanEvent(
            "SCN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT),
            Instant.now(),
            source.isBlank() ? pairedDevice : source,
            scanKind(raw),
            raw,
            "Received"
        );
        listener.onPhoneBridgeScan(event);
        respond(exchange, 200, "{\"accepted\":true,\"scanId\":\"" + json(event.id) + "\"}");
    }

    private void stockIntake(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"POST required\"}");
            return;
        }
        String body = readBody(exchange);
        String suppliedSession = suppliedSession(exchange, body);
        synchronized (this) {
            if (sessionKey.isBlank() || !sessionKey.equals(suppliedSession)) {
                respond(exchange, 401, "{\"error\":\"Pair phone first\"}");
                return;
            }
            stockItemsAccepted++;
        }
        String productName = value(body, "productName");
        String barcode = value(body, "barcode");
        int quantity = (int) number(body, "quantity", 0);
        if (productName.isBlank() && barcode.isBlank()) {
            respond(exchange, 400, "{\"error\":\"productName or barcode required\"}");
            return;
        }
        if (quantity <= 0) {
            respond(exchange, 400, "{\"error\":\"quantity must be positive\"}");
            return;
        }
        StockSyncItem item = new StockSyncItem(
            "STK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT),
            Instant.now(),
            sourceDevice(body),
            "",
            productName,
            barcode,
            value(body, "category"),
            quantity,
            number(body, "priceCents", 0),
            number(body, "costCents", 0),
            "",
            value(body, "imageFileName"),
            value(body, "imageBase64"),
            "Received"
        );
        listener.onPhoneStockIntake(item);
        respond(exchange, 200, "{\"accepted\":true,\"stockSyncId\":\"" + json(item.id) + "\"}");
    }

    private void productSync(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"POST required\"}");
            return;
        }
        String body = readBody(exchange);
        String suppliedSession = suppliedSession(exchange, body);
        synchronized (this) {
            if (sessionKey.isBlank() || !sessionKey.equals(suppliedSession)) {
                respond(exchange, 401, "{\"error\":\"Pair phone first\"}");
                return;
            }
            productsAccepted++;
        }
        String productName = value(body, "name");
        if (productName.isBlank()) {
            productName = value(body, "productName");
        }
        String barcode = value(body, "barcode");
        if (productName.isBlank() && barcode.isBlank()) {
            respond(exchange, 400, "{\"error\":\"name or barcode required\"}");
            return;
        }
        ProductSyncItem item = new ProductSyncItem(
            "PSY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT),
            Instant.now(),
            sourceDevice(body),
            value(body, "mobileProductId"),
            productName,
            value(body, "sku"),
            barcode,
            value(body, "category"),
            (int) number(body, "stock", 0),
            number(body, "priceCents", 0),
            number(body, "costCents", 0),
            "",
            value(body, "imageFileName"),
            value(body, "imageBase64"),
            value(body, "whatsappRetailerId"),
            "Received"
        );
        listener.onPhoneProductSync(item);
        respond(exchange, 200, "{\"accepted\":true,\"productSyncId\":\"" + json(item.id) + "\"}");
    }

    private String scanKind(String raw) {
        if (raw == null) {
            return "Unknown";
        }
        if (raw.startsWith("BSVC:")) {
            return "Service";
        }
        if (raw.startsWith("BSVOU:")) {
            return "Voucher";
        }
        if (raw.startsWith("BSRC:")) {
            return "Receipt";
        }
        return "Product";
    }

    private void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(8 * 1024 * 1024);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String suppliedSession(HttpExchange exchange, String body) {
        String suppliedSession = exchange.getRequestHeaders().getFirst("X-Biashara-Session");
        if (suppliedSession == null || suppliedSession.isBlank()) {
            suppliedSession = value(body, "sessionKey");
        }
        return suppliedSession == null ? "" : suppliedSession;
    }

    private String sourceDevice(String body) {
        String source = value(body, "deviceName");
        return source.isBlank() ? pairedDevice : source;
    }

    private String value(String json, String key) {
        String quoted = "\"" + key + "\"";
        int keyIndex = json.indexOf(quoted);
        if (keyIndex < 0) {
            return "";
        }
        int colon = json.indexOf(':', keyIndex + quoted.length());
        if (colon < 0) {
            return "";
        }
        int start = json.indexOf('"', colon + 1);
        if (start < 0) {
            return "";
        }
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

    private long number(String json, String key, long fallback) {
        String quoted = "\"" + key + "\"";
        int keyIndex = json.indexOf(quoted);
        if (keyIndex < 0) {
            return fallback;
        }
        int colon = json.indexOf(':', keyIndex + quoted.length());
        if (colon < 0) {
            return fallback;
        }
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

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String randomToken() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String localHost() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address.isSiteLocalAddress() && !address.getHostAddress().contains(":")) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // Fallback below.
        }
        return "127.0.0.1";
    }
}
