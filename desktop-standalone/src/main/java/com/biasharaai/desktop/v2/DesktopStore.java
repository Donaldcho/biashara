package com.biasharaai.desktop.v2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class DesktopStore {
    private final Path dataDir;
    private final Path productsFile;
    private final Path servicesFile;
    private final Path customersFile;
    private final Path transactionsFile;
    private final Path saleLinesFile;
    private final Path serviceTicketsFile;
    private final Path scansFile;
    private final Path stockSyncFile;
    private final Path productSyncFile;
    private final Path settingsFile;

    DesktopStore(Path dataDir) {
        this.dataDir = dataDir;
        this.productsFile = dataDir.resolve("products.tsv");
        this.servicesFile = dataDir.resolve("services.tsv");
        this.customersFile = dataDir.resolve("customers.tsv");
        this.transactionsFile = dataDir.resolve("transactions.tsv");
        this.saleLinesFile = dataDir.resolve("sale-lines.tsv");
        this.serviceTicketsFile = dataDir.resolve("service-tickets.tsv");
        this.scansFile = dataDir.resolve("phone-scans.tsv");
        this.stockSyncFile = dataDir.resolve("stock-sync.tsv");
        this.productSyncFile = dataDir.resolve("product-sync.tsv");
        this.settingsFile = dataDir.resolve("settings.properties");
    }

    AppState load() {
        try {
            Files.createDirectories(dataDir);
            AppState state = new AppState(dataDir);
            loadSettings(state.settings);
            loadProducts(state);
            loadServices(state);
            loadCustomers(state);
            loadTransactions(state);
            loadSaleLines(state);
            loadServiceTickets(state);
            loadScans(state);
            loadStockSync(state);
            loadProductSync(state);
            removeDemoSeedIfPristine(state);
            save(state);
            return state;
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not load desktop data from " + dataDir, ex);
        }
    }

    void save(AppState state) {
        try {
            Files.createDirectories(dataDir);
            saveSettings(state.settings);
            writeLines(productsFile, state.products.stream().map(this::productLine).toList());
            writeLines(servicesFile, state.services.stream().map(this::serviceLine).toList());
            writeLines(customersFile, state.customers.stream().map(this::customerLine).toList());
            writeLines(transactionsFile, state.transactions.stream().map(this::transactionLine).toList());
            writeLines(saleLinesFile, state.saleLines.stream().map(this::saleLine).toList());
            writeLines(serviceTicketsFile, state.serviceTickets.stream().map(this::serviceTicketLine).toList());
            writeLines(scansFile, state.scanEvents.stream().map(this::scanLine).toList());
            writeLines(stockSyncFile, state.stockSyncItems.stream().map(this::stockSyncLine).toList());
            writeLines(productSyncFile, state.productSyncItems.stream().map(this::productSyncLine).toList());
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not save desktop data to " + dataDir, ex);
        }
    }

    Path dataDir() {
        return dataDir;
    }

    Path modelsDir() {
        return dataDir.resolve("models");
    }

    Path incomingImagesDir() {
        return dataDir.resolve("incoming-images");
    }

    String[] loadBridgeSession() {
        try {
            List<String[]> rows = readRows(dataDir.resolve("bridge-session.tsv"));
            if (rows.isEmpty() || rows.get(0).length < 2) {
                return new String[] {"", "", ""};
            }
            String protocolVersion = rows.get(0).length >= 3 ? rows.get(0)[2] : "";
            return new String[] {rows.get(0)[0], rows.get(0)[1], protocolVersion};
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not load the desktop bridge session", ex);
        }
    }

    void saveBridgeSession(String sessionKey, String pairedDevice, String protocolVersion) {
        try {
            Files.createDirectories(dataDir);
            writeLines(dataDir.resolve("bridge-session.tsv"), List.of(join(sessionKey, pairedDevice, protocolVersion)));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not save the desktop bridge session", ex);
        }
    }

    String saveIncomingImage(String fileName, String base64) {
        if (base64 == null || base64.isBlank()) {
            return "";
        }
        try {
            Path root = incomingImagesDir().toAbsolutePath().normalize();
            Files.createDirectories(root);
            String safeName = safe(fileName == null || fileName.isBlank() ? "mobile-stock-image.jpg" : fileName)
                .replace("..", "")
                .replace("\\", "-")
                .replace("/", "-");
            if (!safeName.toLowerCase().matches(".*\\.(jpg|jpeg|png|webp)$")) {
                safeName = safeName + ".jpg";
            }
            Path target = root.resolve(System.currentTimeMillis() + "-" + safeName).normalize();
            if (!target.startsWith(root)) {
                throw new IOException("Invalid image path.");
            }
            String payload = base64;
            int comma = payload.indexOf(',');
            if (payload.startsWith("data:") && comma >= 0) {
                payload = payload.substring(comma + 1);
            }
            Files.write(target, Base64.getDecoder().decode(payload));
            return target.toAbsolutePath().toString();
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("Could not save incoming stock image.", ex);
        }
    }

    Path importModel(Path source) {
        try {
            Files.createDirectories(modelsDir());
            Path target = modelsDir().resolve(source.getFileName()).normalize();
            if (!target.startsWith(modelsDir())) {
                throw new IOException("Invalid model path.");
            }
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not import model.", ex);
        }
    }

    Path exportBackup(Path targetZip) {
        try {
            if (targetZip.getParent() != null) {
                Files.createDirectories(targetZip.getParent());
            }
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(targetZip))) {
                addIfExists(out, productsFile);
                addIfExists(out, servicesFile);
                addIfExists(out, customersFile);
                addIfExists(out, transactionsFile);
                addIfExists(out, saleLinesFile);
                addIfExists(out, serviceTicketsFile);
                addIfExists(out, scansFile);
                addIfExists(out, stockSyncFile);
                addIfExists(out, productSyncFile);
                addIfExists(out, settingsFile);
            }
            return targetZip;
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not export backup.", ex);
        }
    }

    private void loadSettings(Settings settings) throws IOException {
        if (!Files.exists(settingsFile)) {
            return;
        }
        Properties properties = new Properties();
        try (var input = Files.newInputStream(settingsFile)) {
            properties.load(input);
        }
        boolean legacyPlaceholderProfile = "Biashara AI Pro".equals(properties.getProperty("businessName", ""))
            && properties.getProperty("ownerName", "").isBlank()
            && "Thank you for your business.".equals(properties.getProperty("receiptFooter", ""))
            && properties.getProperty("whatsappPhoneNumberId", "").isBlank()
            && properties.getProperty("whatsappCatalogId", "").isBlank();
        settings.businessName = properties.getProperty("businessName", settings.businessName);
        settings.ownerName = properties.getProperty("ownerName", settings.ownerName);
        settings.currency = properties.getProperty("currency", settings.currency);
        settings.taxBasisPoints = parseLong(properties.getProperty("taxBasisPoints"), settings.taxBasisPoints);
        settings.receiptFooter = properties.getProperty("receiptFooter", settings.receiptFooter);
        settings.modelPath = properties.getProperty("modelPath", settings.modelPath);
        settings.aiProvider = properties.getProperty("aiProvider", settings.aiProvider);
        settings.lmStudioBaseUrl = properties.getProperty("lmStudioBaseUrl", settings.lmStudioBaseUrl);
        settings.lmStudioModel = properties.getProperty("lmStudioModel", settings.lmStudioModel);
        settings.phoneBridgeEnabled = Boolean.parseBoolean(properties.getProperty("phoneBridgeEnabled", "true"));
        settings.whatsappPhoneNumberId = properties.getProperty("whatsappPhoneNumberId", settings.whatsappPhoneNumberId);
        settings.whatsappCatalogId = properties.getProperty("whatsappCatalogId", settings.whatsappCatalogId);
        settings.whatsappDefaultCountryCode = properties.getProperty("whatsappDefaultCountryCode", settings.whatsappDefaultCountryCode);
        settings.whatsappAccessToken = properties.getProperty("whatsappAccessToken", settings.whatsappAccessToken);
        settings.whatsappGraphVersion = properties.getProperty("whatsappGraphVersion", settings.whatsappGraphVersion);
        settings.settingsSyncFingerprint = properties.getProperty("settingsSyncFingerprint", settings.settingsSyncFingerprint);
        if ("Biashara AI Pro".equals(settings.businessName)) {
            settings.businessName = "";
        }
        if (legacyPlaceholderProfile && "KES".equalsIgnoreCase(settings.currency)) {
            settings.currency = "";
        }
        if ("Thank you for your business.".equals(settings.receiptFooter)) {
            settings.receiptFooter = "";
        }
        if ("+254".equals(settings.whatsappDefaultCountryCode)) {
            settings.whatsappDefaultCountryCode = "";
        }
        if ("v23.0".equalsIgnoreCase(settings.whatsappGraphVersion)) {
            settings.whatsappGraphVersion = "";
        }
    }

    private void saveSettings(Settings settings) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("businessName", settings.businessName);
        properties.setProperty("ownerName", settings.ownerName);
        properties.setProperty("currency", settings.currency);
        properties.setProperty("taxBasisPoints", Long.toString(settings.taxBasisPoints));
        properties.setProperty("receiptFooter", settings.receiptFooter);
        properties.setProperty("modelPath", settings.modelPath);
        properties.setProperty("aiProvider", settings.aiProvider);
        properties.setProperty("lmStudioBaseUrl", settings.lmStudioBaseUrl);
        properties.setProperty("lmStudioModel", settings.lmStudioModel);
        properties.setProperty("phoneBridgeEnabled", Boolean.toString(settings.phoneBridgeEnabled));
        properties.setProperty("whatsappPhoneNumberId", settings.whatsappPhoneNumberId);
        properties.setProperty("whatsappCatalogId", settings.whatsappCatalogId);
        properties.setProperty("whatsappDefaultCountryCode", settings.whatsappDefaultCountryCode);
        properties.setProperty("whatsappAccessToken", settings.whatsappAccessToken);
        properties.setProperty("whatsappGraphVersion", settings.whatsappGraphVersion);
        properties.setProperty("settingsSyncFingerprint", settings.settingsSyncFingerprint);
        try (var output = Files.newOutputStream(settingsFile)) {
            properties.store(output, "Biashara AI Pro Desktop settings");
        }
    }

    private void loadProducts(AppState state) throws IOException {
        for (String[] row : readRows(productsFile)) {
            if (row.length >= 8) {
                if (row.length >= 13) {
                    state.products.add(new Product(row[0], row[1], row[2], row[3], row[4], row[5], row[6], row[7], row[8], row[9], parseLong(row[10], 0), parseLong(row[11], 0), parseInt(row[12], 0)));
                } else if (row.length >= 10) {
                    state.products.add(new Product(row[0], row[1], row[2], row[3], row[4], row[5], row[6], parseLong(row[7], 0), parseLong(row[8], 0), parseInt(row[9], 0)));
                } else {
                    state.products.add(new Product(row[0], row[1], row[2], row[3], row[4], parseLong(row[5], 0), parseLong(row[6], 0), parseInt(row[7], 0)));
                }
            }
        }
    }

    private void loadServiceTickets(AppState state) throws IOException {
        for (String[] row : readRows(serviceTicketsFile)) {
            if (row.length >= 22) {
                state.serviceTickets.add(new ServiceTicket(
                    row[0],
                    row[1],
                    parseInstant(row[2]),
                    parseInstantOrNull(row[3]),
                    parseInstantOrNull(row[4]),
                    parseTicketStatus(row[5]),
                    row[6],
                    row[7],
                    row[8],
                    row[9],
                    row[10],
                    row[11],
                    row[12],
                    parseInt(row[13], 1),
                    parseLong(row[14], 0),
                    parseLong(row[15], 0),
                    parseLong(row[16], 0),
                    row[17],
                    row[18],
                    row[19],
                    row[20],
                    row[21]
                ));
            }
        }
    }

    private void loadServices(AppState state) throws IOException {
        long legacyUpdatedAt = Files.exists(servicesFile)
            ? Files.getLastModifiedTime(servicesFile).toMillis()
            : System.currentTimeMillis();
        for (String[] row : readRows(servicesFile)) {
            if (row.length >= 6) {
                state.services.add(new ServiceItem(
                    row[0],
                    row[1],
                    row.length > 6 ? row[6] : "",
                    row[2],
                    parseLong(row[3], 0),
                    row.length > 7 ? row[7] : "FIXED",
                    parseInt(row[4], 0),
                    parseInt(row[5], 0),
                    row.length <= 8 || Boolean.parseBoolean(row[8]),
                    row.length > 9 ? parseLong(row[9], legacyUpdatedAt) : legacyUpdatedAt,
                    row.length > 10 ? row[10] : "",
                    row.length > 11 ? row[11] : ""
                ));
            }
        }
    }

    private void loadCustomers(AppState state) throws IOException {
        for (String[] row : readRows(customersFile)) {
            if (row.length >= 5) {
                state.customers.add(new Customer(row[0], row[1], row[2], parseLong(row[3], 0), parseInt(row[4], 0)));
            }
        }
    }

    private void loadTransactions(AppState state) throws IOException {
        for (String[] row : readRows(transactionsFile)) {
            if (row.length >= 11) {
                state.transactions.add(new Transaction(
                    row[0],
                    parseInstant(row[1]),
                    parseType(row[2]),
                    row[3],
                    row[4],
                    row[5],
                    row[6],
                    parseLong(row[7], 0),
                    parseLong(row[8], 0),
                    parseLong(row[9], 0),
                    parseLong(row[10], 0),
                    row.length >= 12 ? parseLong(row[11], 0) : 0
                ));
            }
        }
    }

    private void loadSaleLines(AppState state) throws IOException {
        for (String[] row : readRows(saleLinesFile)) {
            if (row.length >= 11) {
                state.saleLines.add(new DesktopSaleLine(
                    row[0],
                    row[1],
                    parseKind(row[2]),
                    row[3],
                    row[4],
                    row[5],
                    row[6],
                    parseInt(row[7], 0),
                    parseLong(row[8], 0),
                    parseLong(row[9], 0),
                    row[10]
                ));
            }
        }
    }

    private void loadScans(AppState state) throws IOException {
        for (String[] row : readRows(scansFile)) {
            if (row.length >= 6) {
                state.scanEvents.add(new ScanEvent(row[0], parseInstant(row[1]), row[2], row[3], row[4], row[5]));
            }
        }
    }

    private void loadStockSync(AppState state) throws IOException {
        for (String[] row : readRows(stockSyncFile)) {
            if (row.length >= 12) {
                StockSyncItem item = new StockSyncItem(
                    row[0],
                    parseInstant(row[1]),
                    row[2],
                    row[3],
                    row[4],
                    row[5],
                    row[6],
                    parseInt(row[7], 0),
                    parseLong(row[8], 0),
                    parseLong(row[9], 0),
                    row[10],
                    "",
                    "",
                    row[11]
                );
                if (row.length >= 17) {
                    item.mobileProductId = row[12];
                    item.stockBaseKnown = Boolean.parseBoolean(row[13]);
                    item.stockBase = parseInt(row[14], 0);
                    item.sourceStock = parseInt(row[15], 0);
                    item.mutationId = row[16];
                }
                state.stockSyncItems.add(item);
            }
        }
    }

    private void loadProductSync(AppState state) throws IOException {
        for (String[] row : readRows(productSyncFile)) {
            if (row.length >= 17) {
                state.productSyncItems.add(new ProductSyncItem(
                    row[0],
                    parseInstant(row[1]),
                    row[2],
                    row[3],
                    row[4],
                    row[5],
                    row[6],
                    row[7],
                    row[8],
                    parseInt(row[9], 0),
                    parseLong(row[10], 0),
                    parseLong(row[11], 0),
                    row[12],
                    "",
                    "",
                    row[13],
                    row[14],
                    row[15],
                    row[16]
                ));
            } else if (row.length >= 14) {
                state.productSyncItems.add(new ProductSyncItem(
                    row[0],
                    parseInstant(row[1]),
                    row[2],
                    row[3],
                    row[4],
                    "",
                    row[5],
                    row[6],
                    row[7],
                    parseInt(row[8], 0),
                    parseLong(row[9], 0),
                    parseLong(row[10], 0),
                    row[11],
                    "",
                    "",
                    row[12],
                    "",
                    "",
                    row[13]
                ));
            }
        }
    }

    private void removeDemoSeedIfPristine(AppState state) {
        if (!state.transactions.isEmpty() || !state.stockSyncItems.isEmpty() || !state.productSyncItems.isEmpty()) {
            return;
        }
        state.products.removeIf(product ->
            ("Hair oil 100ml".equals(product.name) && "600100000001".equals(product.barcode))
                || ("Phone charger".equals(product.name) && "600100000002".equals(product.barcode))
                || ("Notebook A5".equals(product.name) && "600100000003".equals(product.barcode))
        );
        state.services.removeIf(service ->
            "Braiding service".equals(service.name)
                || "Phone repair diagnosis".equals(service.name)
                || "Delivery within town".equals(service.name)
        );
        state.customers.removeIf(customer ->
            "Walk-in customer".equals(customer.name)
                || "Amina Otieno".equals(customer.name)
        );
    }

    private List<String[]> readRows(Path file) throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        List<String[]> rows = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                rows.add(line.split("\t", -1));
            }
        }
        return rows;
    }

    private void writeLines(Path file, List<String> lines) throws IOException {
        Files.write(file, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private String productLine(Product product) {
        return join(product.id, product.name, product.description, product.sku, product.barcode, product.category, product.imagePath, product.whatsappRetailerId, product.whatsappImageUrl, product.whatsappProductUrl, product.priceCents, product.costCents, product.stock);
    }

    private String serviceLine(ServiceItem service) {
        return join(
            service.id,
            service.name,
            service.category,
            service.priceCents,
            service.durationMinutes,
            service.warrantyDays,
            service.description,
            service.priceMode,
            service.visibleInKiosk,
            service.updatedAt,
            service.sourceDevice,
            service.mobileServiceId
        );
    }

    private String customerLine(Customer customer) {
        return join(customer.id, customer.name, customer.phone, customer.balanceCents, customer.visits);
    }

    private String transactionLine(Transaction transaction) {
        return join(
            transaction.id,
            transaction.createdAt,
            transaction.type,
            transaction.customerId,
            transaction.customerName,
            transaction.description,
            transaction.paymentMethod,
            transaction.subtotalCents,
            transaction.taxCents,
            transaction.totalCents,
            transaction.paidCents,
            transaction.balanceCents
        );
    }

    private String saleLine(DesktopSaleLine line) {
        return join(
            line.id,
            line.transactionId,
            line.kind,
            line.itemId,
            line.name,
            line.barcode,
            line.category,
            line.quantity,
            line.unitCents,
            line.lineTotalCents,
            line.staffName
        );
    }

    private String serviceTicketLine(ServiceTicket ticket) {
        return join(
            ticket.id,
            ticket.token,
            ticket.createdAt,
            ticket.startedAt == null ? "" : ticket.startedAt,
            ticket.completedAt == null ? "" : ticket.completedAt,
            ticket.status,
            ticket.transactionId,
            ticket.customerId,
            ticket.customerName,
            ticket.customerPhone,
            ticket.serviceId,
            ticket.serviceName,
            ticket.category,
            ticket.quantity,
            ticket.unitCents,
            ticket.totalCents,
            ticket.paidCents,
            ticket.paymentMethod,
            ticket.assignedTechnician,
            ticket.activeTechnician,
            ticket.requirements,
            ticket.completionNotes
        );
    }

    private String scanLine(ScanEvent event) {
        return join(event.id, event.createdAt, event.sourceDevice, event.kind, event.rawValue, event.status);
    }

    private String stockSyncLine(StockSyncItem item) {
        return join(
            item.id,
            item.createdAt,
            item.sourceDevice,
            item.productId,
            item.productName,
            item.barcode,
            item.category,
            item.quantity,
            item.priceCents,
            item.costCents,
            item.imagePath,
            item.status,
            item.mobileProductId,
            item.stockBaseKnown,
            item.stockBase,
            item.sourceStock,
            item.mutationId
        );
    }

    private String productSyncLine(ProductSyncItem item) {
        return join(
            item.id,
            item.createdAt,
            item.sourceDevice,
            item.mobileProductId,
            item.name,
            item.description,
            item.sku,
            item.barcode,
            item.category,
            item.stock,
            item.priceCents,
            item.costCents,
            item.imagePath,
            item.whatsappRetailerId,
            item.whatsappImageUrl,
            item.whatsappProductUrl,
            item.status
        );
    }

    private String join(Object... values) {
        List<String> escaped = new ArrayList<>();
        for (Object value : values) {
            escaped.add(safe(value == null ? "" : value.toString()));
        }
        return String.join("\t", escaped);
    }

    private String safe(String value) {
        return value.replace("\t", " ").replace("\r", " ").replace("\n", " ").trim();
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    private Instant parseInstantOrNull(String value) {
        try {
            return value == null || value.isBlank() ? null : Instant.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private TransactionType parseType(String value) {
        try {
            return TransactionType.valueOf(value);
        } catch (Exception ignored) {
            return TransactionType.ADJUSTMENT;
        }
    }

    private CartLine.Kind parseKind(String value) {
        try {
            return CartLine.Kind.valueOf(value);
        } catch (Exception ignored) {
            return CartLine.Kind.PRODUCT;
        }
    }

    private ServiceTicket.Status parseTicketStatus(String value) {
        try {
            return ServiceTicket.Status.valueOf(value);
        } catch (Exception ignored) {
            return ServiceTicket.Status.BOOKED;
        }
    }

    private void addIfExists(ZipOutputStream out, Path file) throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        out.putNextEntry(new ZipEntry(file.getFileName().toString()));
        Files.copy(file, out);
        out.closeEntry();
    }
}
