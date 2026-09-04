package com.biasharaai.desktop.v2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class LmStudioClient {
    private static final int MAX_CONTEXT_CHARS = 8000;

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(4))
        .build();

    Probe test(Settings settings) throws IOException {
        URI endpoint = endpoint(settings, "/models");
        HttpResponse<String> response = send(HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(8))
            .GET()
            .build());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return new Probe(false, "LM Studio returned HTTP " + response.statusCode() + " from " + endpoint, "");
        }
        int models = countJsonStrings(response.body(), "id");
        String first = firstJsonString(response.body(), "id", 0);
        if (models == 0 || first.isBlank()) {
            return new Probe(false, "LM Studio is reachable, but no loaded model was returned. Load a model in LM Studio and start the local server.", "");
        }
        String configured = clean(settings.lmStudioModel);
        String active = configured.isBlank() ? first : configured;
        return new Probe(true, "LM Studio is connected. Using model: " + active, active);
    }

    String answer(String question, AppState state) throws IOException {
        return answer(question, List.of(), state);
    }

    String answer(String question, List<AssistantImage> images, AppState state) throws IOException {
        String prompt = clean(question);
        if (prompt.isBlank()) {
            throw new IllegalArgumentException("Ask a question first.");
        }
        String model = clean(state.settings.lmStudioModel);
        if (model.isBlank()) {
            String modelsJson = modelsJson(state.settings);
            model = firstJsonString(modelsJson, "id", 0);
        }
        if (model.isBlank()) {
            throw new IllegalStateException("LM Studio is reachable, but no model is loaded.");
        }

        URI endpoint = endpoint(state.settings, "/chat/completions");
        String requestJson = chatRequestJson(model, prompt, state, images, false);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(180))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = firstJsonString(response.body(), "message", 0);
            if (message.isBlank()) {
                message = "LM Studio returned HTTP " + response.statusCode() + ".";
            }
            throw new IOException(message);
        }
        String content = firstJsonString(response.body(), "content", 0).trim();
        if (content.isBlank()) {
            throw new IOException("LM Studio returned an empty answer.");
        }
        return content;
    }

    void streamAnswer(String question, List<AssistantImage> images, AppState state, TokenConsumer consumer) throws IOException {
        String prompt = clean(question);
        if (prompt.isBlank()) {
            throw new IllegalArgumentException("Ask a question first.");
        }
        String model = clean(state.settings.lmStudioModel);
        if (model.isBlank()) {
            String modelsJson = modelsJson(state.settings);
            model = firstJsonString(modelsJson, "id", 0);
        }
        if (model.isBlank()) {
            throw new IllegalStateException("LM Studio is reachable, but no model is loaded.");
        }

        URI endpoint = endpoint(state.settings, "/chat/completions");
        String requestJson = chatRequestJson(model, prompt, state, images, true);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(240))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
            .build();
        HttpResponse<InputStream> response = sendStream(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            String message = firstJsonString(body, "message", 0);
            if (message.isBlank()) {
                message = "LM Studio returned HTTP " + response.statusCode() + ".";
            }
            throw new IOException(message);
        }

        boolean streamed = false;
        StringBuilder nonSse = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isBlank()) {
                    continue;
                }
                if (!trimmed.startsWith("data:")) {
                    nonSse.append(trimmed);
                    continue;
                }
                String data = trimmed.substring("data:".length()).trim();
                if ("[DONE]".equals(data)) {
                    break;
                }
                String token = firstJsonString(data, "content", 0);
                if (!token.isEmpty()) {
                    consumer.accept(token);
                    streamed = true;
                }
            }
        }
        if (!streamed && !nonSse.isEmpty()) {
            String content = firstJsonString(nonSse.toString(), "content", 0);
            if (!content.isBlank()) {
                consumer.accept(content);
                return;
            }
        }
        if (!streamed) {
            streamText(answer(prompt, images, state), consumer);
        }
    }

    private void streamText(String text, TokenConsumer consumer) throws IOException {
        String value = clean(text);
        if (value.isBlank()) {
            throw new IOException("LM Studio returned an empty answer.");
        }
        int start = 0;
        while (start < value.length()) {
            int end = Math.min(value.length(), start + 48);
            if (end < value.length()) {
                int space = value.lastIndexOf(' ', end);
                if (space > start + 16) {
                    end = space + 1;
                }
            }
            consumer.accept(value.substring(start, end));
            start = end;
        }
    }

    private String chatRequestJson(String model, String prompt, AppState state, List<AssistantImage> images, boolean stream) {
        return "{"
            + "\"model\":\"" + json(model) + "\","
            + "\"messages\":["
            + "{\"role\":\"system\",\"content\":\"" + json(systemPrompt(state.settings)) + "\"},"
            + "{\"role\":\"user\",\"content\":" + userContentJson(prompt, state, images) + "}"
            + "],"
            + "\"temperature\":0.25,"
            + "\"max_tokens\":420,"
            + "\"stream\":" + stream
            + "}";
    }

    private String userContentJson(String prompt, AppState state, List<AssistantImage> images) {
        String text = "Question: " + prompt + "\n\n" + businessContext(state);
        List<AssistantImage> safeImages = images == null ? List.of() : images.stream().limit(3).toList();
        if (safeImages.isEmpty()) {
            return "\"" + json(text) + "\"";
        }
        StringBuilder out = new StringBuilder();
        out.append("[{\"type\":\"text\",\"text\":\"").append(json(text)).append("\\n\\nAttached images: ");
        for (int i = 0; i < safeImages.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(json(clean(safeImages.get(i).fileName).isBlank() ? "image-" + (i + 1) : safeImages.get(i).fileName));
        }
        out.append("\"}");
        for (AssistantImage image : safeImages) {
            out.append(",{\"type\":\"image_url\",\"image_url\":{\"url\":\"")
                .append(json(imageDataUrl(image)))
                .append("\"}}");
        }
        out.append("]");
        return out.toString();
    }

    private String imageDataUrl(AssistantImage image) {
        String value = clean(image.dataUrl);
        if (value.startsWith("data:image/")) {
            return value;
        }
        String mimeType = clean(image.mimeType).toLowerCase(Locale.ROOT);
        if (!mimeType.startsWith("image/")) {
            mimeType = "image/jpeg";
        }
        return "data:" + mimeType + ";base64," + value;
    }

    private String systemPrompt(Settings settings) {
        return "You are the local AI assistant inside Biashara AI Pro Desktop. "
            + "Use only the business context provided by the desktop app. "
            + "Do not invent sales, stock, customers, suppliers, or Azure records that are not in the context. "
            + "Answer directly and do not include chain-of-thought or thinking tags. "
            + "Give practical advice for a small retail or service business, use " + clean(settings.currency).toUpperCase(Locale.ROOT)
            + " for money, and keep answers concise enough for a busy owner or manager. "
            + "When the question asks for a report, include revenue, stock risks, credit, sync status, and next actions.";
    }

    private String businessContext(AppState state) {
        LocalDate today = LocalDate.now();
        long todayRevenue = state.revenueOn(today);
        long credit = state.creditOutstanding();
        long lowStock = state.products.stream().filter(product -> product.stock <= 5).count();
        long serviceRevenue = state.transactions.stream()
            .filter(transaction -> transaction.type == TransactionType.SERVICE_SALE)
            .mapToLong(transaction -> transaction.totalCents)
            .sum();
        long moneyIn = state.transactions.stream()
            .filter(transaction -> transaction.type == TransactionType.SALE
                || transaction.type == TransactionType.SERVICE_SALE
                || transaction.type == TransactionType.PAYMENT)
            .mapToLong(transaction -> Math.max(0, transaction.paidCents))
            .sum();
        long moneyOut = state.transactions.stream()
            .filter(transaction -> transaction.type == TransactionType.EXPENSE)
            .mapToLong(transaction -> Math.max(0, transaction.totalCents))
            .sum();

        StringBuilder out = new StringBuilder();
        out.append("Local desktop business context generated on ").append(today).append(".\n");
        out.append("Business: ").append(clean(state.settings.businessName)).append("\n");
        out.append("Owner: ").append(clean(state.settings.ownerName)).append("\n");
        out.append("Currency: ").append(clean(state.settings.currency)).append("\n");
        out.append("Summary: today revenue ").append(Money.format(todayRevenue, state.settings.currency))
            .append(", outstanding credit ").append(Money.format(credit, state.settings.currency))
            .append(", products ").append(state.products.size())
            .append(", services ").append(state.services.size())
            .append(", low-stock products ").append(lowStock)
            .append(", stored product images ").append(state.products.stream().filter(product -> !clean(product.imagePath).isBlank()).count())
            .append(".\n");
        out.append("Cash flow: money in ").append(Money.format(moneyIn, state.settings.currency))
            .append(", money out ").append(Money.format(moneyOut, state.settings.currency))
            .append(", net ").append(Money.format(moneyIn - moneyOut, state.settings.currency))
            .append(".\n");
        out.append("Service revenue recorded on desktop: ").append(Money.format(serviceRevenue, state.settings.currency)).append(".\n");
        out.append("Mobile sync: product sync records ").append(state.productSyncItems.size())
            .append(", stock intake records ").append(state.stockSyncItems.size())
            .append(", scan events ").append(state.scanEvents.size())
            .append(".\n\n");

        out.append("Low-stock products:\n");
        StringBuilder lowStockRows = new StringBuilder();
        state.products.stream()
            .filter(product -> product.stock <= 5)
            .sorted(Comparator.comparingInt(product -> product.stock))
            .limit(15)
            .forEach(product -> lowStockRows.append("- ")
                .append(clean(product.name)).append(": ")
                .append(product.stock).append(" left, price ")
                .append(Money.format(product.priceCents, state.settings.currency))
                .append(", cost ").append(Money.format(product.costCents, state.settings.currency))
                .append(", barcode ").append(clean(product.barcode))
                .append("\n"));
        out.append(lowStockRows.isEmpty() ? "- None.\n" : lowStockRows);

        out.append("\nProduct catalog snapshot:\n");
        state.products.stream()
            .sorted(Comparator.comparing(product -> clean(product.name).toLowerCase(Locale.ROOT)))
            .limit(25)
            .forEach(product -> out.append("- ")
                .append(clean(product.name))
                .append(" | category ").append(clean(product.category))
                .append(" | stock ").append(product.stock)
                .append(" | price ").append(Money.format(product.priceCents, state.settings.currency))
                .append(" | cost ").append(Money.format(product.costCents, state.settings.currency))
                .append(" | barcode ").append(clean(product.barcode))
                .append(" | image ").append(clean(product.imagePath).isBlank() ? "no" : "yes")
                .append("\n"));

        out.append("\nServices:\n");
        StringBuilder serviceRows = new StringBuilder();
        state.services.stream()
            .sorted(Comparator.comparing(service -> clean(service.name).toLowerCase(Locale.ROOT)))
            .limit(20)
            .forEach(service -> serviceRows.append("- ")
                .append(clean(service.name))
                .append(" | category ").append(clean(service.category))
                .append(" | price ").append(Money.format(service.priceCents, state.settings.currency))
                .append(" | duration ").append(service.durationMinutes).append(" minutes")
                .append(" | warranty ").append(service.warrantyDays).append(" days")
                .append("\n"));
        out.append(serviceRows.isEmpty() ? "- None.\n" : serviceRows);

        out.append("\nCustomer credit:\n");
        StringBuilder customerRows = new StringBuilder();
        state.customers.stream()
            .filter(customer -> customer.balanceCents > 0)
            .sorted(Comparator.comparingLong((Customer customer) -> customer.balanceCents).reversed())
            .limit(10)
            .forEach(customer -> customerRows.append("- ")
                .append(clean(customer.name))
                .append(" | phone ").append(clean(customer.phone))
                .append(" | balance ").append(Money.format(customer.balanceCents, state.settings.currency))
                .append(" | visits ").append(customer.visits)
                .append("\n"));
        out.append(customerRows.isEmpty() ? "- None.\n" : customerRows);

        out.append("\nRecent transactions:\n");
        StringBuilder transactionRows = new StringBuilder();
        state.transactions.stream()
            .sorted(Comparator.comparing((Transaction transaction) -> transaction.createdAt).reversed())
            .limit(15)
            .forEach(transaction -> transactionRows.append("- ")
                .append(transaction.createdAt.atZone(ZoneId.systemDefault()).toLocalDate())
                .append(" | ").append(transaction.type)
                .append(" | ").append(clean(transaction.description))
                .append(" | customer ").append(clean(transaction.customerName))
                .append(" | method ").append(clean(transaction.paymentMethod))
                .append(" | total ").append(Money.format(transaction.totalCents, state.settings.currency))
                .append(" | paid ").append(Money.format(transaction.paidCents, state.settings.currency))
                .append(" | balance ").append(Money.format(transaction.balanceCents, state.settings.currency))
                .append("\n"));
        out.append(transactionRows.isEmpty() ? "- None.\n" : transactionRows);

        return limit(out.toString(), MAX_CONTEXT_CHARS);
    }

    private String modelsJson(Settings settings) throws IOException {
        URI endpoint = endpoint(settings, "/models");
        HttpResponse<String> response = send(HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(8))
            .GET()
            .build());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("LM Studio returned HTTP " + response.statusCode() + " from " + endpoint + ".");
        }
        return response.body();
    }

    String resolveModelId(Settings settings) throws IOException {
        String configured = clean(settings.lmStudioModel);
        if (!configured.isBlank()) {
            return configured;
        }
        return firstJsonString(modelsJson(settings), "id", 0);
    }

    String completeAgentTurn(Settings settings, String requestJson) throws IOException {
        URI endpoint = endpoint(settings, "/chat/completions");
        HttpRequest request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(180))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = firstJsonString(response.body(), "message", 0);
            throw new IOException(message.isBlank() ? "LM Studio returned HTTP " + response.statusCode() + "." : message);
        }
        return response.body();
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("LM Studio request was interrupted.", ex);
        } catch (IOException ex) {
            throw new IOException("Could not reach LM Studio. Start the LM Studio local server and load a model. " + ex.getMessage(), ex);
        }
    }

    private HttpResponse<InputStream> sendStream(HttpRequest request) throws IOException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("LM Studio request was interrupted.", ex);
        } catch (IOException ex) {
            throw new IOException("Could not reach LM Studio. Start the LM Studio local server and load a model. " + ex.getMessage(), ex);
        }
    }

    private URI endpoint(Settings settings, String path) {
        String base = clean(settings.lmStudioBaseUrl);
        if (base.isBlank()) {
            throw new IllegalArgumentException("LM Studio base URL is not configured. Add it in Settings before using LM Studio.");
        }
        if (!base.contains("://")) {
            base = "http://" + base;
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        URI baseUri = URI.create(base);
        String basePath = baseUri.getPath();
        if (basePath == null || basePath.isBlank() || "/".equals(basePath)) {
            base = base + "/v1";
        } else if (!basePath.endsWith("/v1") && !basePath.contains("/v1/")) {
            base = base + "/v1";
        }
        String suffix = path.startsWith("/") ? path : "/" + path;
        URI endpoint = URI.create(base + suffix);
        validateLocalEndpoint(endpoint);
        return endpoint;
    }

    private void validateLocalEndpoint(URI uri) {
        String scheme = clean(uri.getScheme()).toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("LM Studio URL must use http or https.");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("LM Studio URL must not include username or password details.");
        }
        String host = clean(uri.getHost());
        if (host.isBlank()) {
            throw new IllegalArgumentException("LM Studio URL must include a host.");
        }
        if ("localhost".equalsIgnoreCase(host) || "0.0.0.0".equals(host)) {
            return;
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                return;
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("LM Studio host could not be resolved: " + host);
        }
        throw new IllegalArgumentException("LM Studio URL must point to localhost or a private LAN address.");
    }

    private String firstJsonString(String body, String key, int fromIndex) {
        String quoted = "\"" + key + "\"";
        int keyIndex = body.indexOf(quoted, Math.max(0, fromIndex));
        if (keyIndex < 0) {
            return "";
        }
        int colon = body.indexOf(':', keyIndex + quoted.length());
        if (colon < 0) {
            return "";
        }
        int start = body.indexOf('"', colon + 1);
        if (start < 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = start + 1; i < body.length(); i++) {
            char c = body.charAt(i);
            if (escaped) {
                if (c == 'u' && i + 4 < body.length()) {
                    String hex = body.substring(i + 1, i + 5);
                    try {
                        out.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    } catch (NumberFormatException ex) {
                        out.append('u').append(hex);
                        i += 4;
                    }
                } else {
                    out.append(switch (c) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        default -> c;
                    });
                }
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

    private int countJsonStrings(String body, String key) {
        int count = 0;
        int index = 0;
        String quoted = "\"" + key + "\"";
        while (index >= 0 && index < body.length()) {
            int keyIndex = body.indexOf(quoted, index);
            if (keyIndex < 0) {
                break;
            }
            if (!firstJsonString(body, key, keyIndex).isBlank()) {
                count++;
            }
            index = keyIndex + quoted.length();
        }
        return count;
    }

    private String json(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", " ")
            .replace("\t", "\\t");
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String limit(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "\nAdditional rows were omitted to keep the local AI request compact.";
    }

    static final class Probe {
        final boolean ok;
        final String message;
        final String modelId;

        Probe(boolean ok, String message, String modelId) {
            this.ok = ok;
            this.message = message;
            this.modelId = modelId;
        }
    }

    static final class AssistantImage {
        final String fileName;
        final String mimeType;
        final String dataUrl;

        AssistantImage(String fileName, String mimeType, String dataUrl) {
            this.fileName = fileName;
            this.mimeType = mimeType;
            this.dataUrl = dataUrl;
        }
    }

    @FunctionalInterface
    interface TokenConsumer {
        void accept(String token) throws IOException;
    }
}
