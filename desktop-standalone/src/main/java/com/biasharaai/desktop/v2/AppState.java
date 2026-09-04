package com.biasharaai.desktop.v2;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

final class AppState {
    final Path dataDir;
    final Settings settings = new Settings();
    final List<Product> products = new ArrayList<>();
    final List<ServiceItem> services = new ArrayList<>();
    final List<Customer> customers = new ArrayList<>();
    final List<Transaction> transactions = new ArrayList<>();
    final List<DesktopSaleLine> saleLines = new ArrayList<>();
    final List<ServiceTicket> serviceTickets = new ArrayList<>();
    final List<ScanEvent> scanEvents = new ArrayList<>();
    final List<StockSyncItem> stockSyncItems = new ArrayList<>();
    final List<ProductSyncItem> productSyncItems = new ArrayList<>();

    AppState(Path dataDir) {
        this.dataDir = dataDir;
    }

    String nextId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    Optional<Product> productByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return Optional.empty();
        }
        String value = barcode.trim();
        return products.stream()
            .filter(product -> value.equalsIgnoreCase(product.barcode))
            .findFirst();
    }

    Optional<ServiceItem> serviceByToken(String raw) {
        if (raw == null || !raw.startsWith("BSVC:")) {
            return Optional.empty();
        }
        String token = raw.substring("BSVC:".length()).trim();
        return services.stream()
            .filter(service -> service.id.equalsIgnoreCase(token) || service.name.equalsIgnoreCase(token))
            .findFirst();
    }

    Customer customerById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return customers.stream()
            .filter(customer -> customer.id.equals(id))
            .findFirst()
            .orElse(null);
    }

    long revenueOn(LocalDate date) {
        return transactions.stream()
            .filter(transaction -> transaction.type == TransactionType.SALE || transaction.type == TransactionType.SERVICE_SALE)
            .filter(transaction -> transaction.date().equals(date))
            .mapToLong(transaction -> transaction.totalCents)
            .sum();
    }

    long creditOutstanding() {
        return customers.stream().mapToLong(customer -> customer.balanceCents).sum();
    }
}

final class Settings {
    String businessName = "";
    String ownerName = "";
    String currency = "";
    long taxBasisPoints = 0L;
    String receiptFooter = "";
    String modelPath = "";
    String aiProvider = "RULES";
    String lmStudioBaseUrl = "";
    String lmStudioModel = "";
    boolean phoneBridgeEnabled = true;
    String whatsappPhoneNumberId = "";
    String whatsappCatalogId = "";
    String whatsappDefaultCountryCode = "";
    String whatsappAccessToken = "";
    String whatsappGraphVersion = "";
    String settingsSyncFingerprint = "";
}

final class Product {
    String id;
    String name;
    String description;
    String sku;
    String barcode;
    String category;
    String imagePath;
    String whatsappRetailerId;
    String whatsappImageUrl;
    String whatsappProductUrl;
    long priceCents;
    long costCents;
    int stock;

    Product(String id, String name, String sku, String barcode, String category, long priceCents, long costCents, int stock) {
        this(id, name, "", sku, barcode, category, "", "", "", "", priceCents, costCents, stock);
    }

    Product(String id, String name, String sku, String barcode, String category, String imagePath, String whatsappRetailerId, long priceCents, long costCents, int stock) {
        this(id, name, "", sku, barcode, category, imagePath, whatsappRetailerId, "", "", priceCents, costCents, stock);
    }

    Product(String id, String name, String description, String sku, String barcode, String category, String imagePath, String whatsappRetailerId, String whatsappImageUrl, String whatsappProductUrl, long priceCents, long costCents, int stock) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.sku = sku;
        this.barcode = barcode;
        this.category = category;
        this.imagePath = imagePath;
        this.whatsappRetailerId = whatsappRetailerId;
        this.whatsappImageUrl = whatsappImageUrl;
        this.whatsappProductUrl = whatsappProductUrl;
        this.priceCents = priceCents;
        this.costCents = costCents;
        this.stock = stock;
    }
}

final class ServiceItem {
    String id;
    String name;
    String description;
    String category;
    long priceCents;
    String priceMode;
    int durationMinutes;
    int warrantyDays;
    boolean visibleInKiosk;
    long updatedAt;
    String sourceDevice;
    String mobileServiceId;

    ServiceItem(String id, String name, String category, long priceCents, int durationMinutes, int warrantyDays) {
        this(id, name, "", category, priceCents, "FIXED", durationMinutes, warrantyDays, true, System.currentTimeMillis(), "", "");
    }

    ServiceItem(
        String id,
        String name,
        String description,
        String category,
        long priceCents,
        String priceMode,
        int durationMinutes,
        int warrantyDays,
        boolean visibleInKiosk,
        long updatedAt,
        String sourceDevice,
        String mobileServiceId
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.priceCents = priceCents;
        this.priceMode = priceMode;
        this.durationMinutes = durationMinutes;
        this.warrantyDays = warrantyDays;
        this.visibleInKiosk = visibleInKiosk;
        this.updatedAt = updatedAt;
        this.sourceDevice = sourceDevice;
        this.mobileServiceId = mobileServiceId;
    }
}

final class Customer {
    String id;
    String name;
    String phone;
    long balanceCents;
    int visits;

    Customer(String id, String name, String phone, long balanceCents, int visits) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.balanceCents = balanceCents;
        this.visits = visits;
    }
}

enum TransactionType {
    SALE,
    SERVICE_SALE,
    CREDIT,
    PAYMENT,
    EXPENSE,
    ADJUSTMENT
}

final class Transaction {
    String id;
    Instant createdAt;
    TransactionType type;
    String customerId;
    String customerName;
    String description;
    String paymentMethod;
    long subtotalCents;
    long taxCents;
    long totalCents;
    long paidCents;
    long balanceCents;

    Transaction(
        String id,
        Instant createdAt,
        TransactionType type,
        String customerId,
        String customerName,
        String description,
        String paymentMethod,
        long subtotalCents,
        long taxCents,
        long totalCents,
        long paidCents,
        long balanceCents
    ) {
        this.id = id;
        this.createdAt = createdAt;
        this.type = type;
        this.customerId = customerId;
        this.customerName = customerName;
        this.description = description;
        this.paymentMethod = paymentMethod;
        this.subtotalCents = subtotalCents;
        this.taxCents = taxCents;
        this.totalCents = totalCents;
        this.paidCents = paidCents;
        this.balanceCents = balanceCents;
    }

    LocalDate date() {
        return createdAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }
}

final class DesktopSaleLine {
    String id;
    String transactionId;
    CartLine.Kind kind;
    String itemId;
    String name;
    String barcode;
    String category;
    int quantity;
    long unitCents;
    long lineTotalCents;
    String staffName;

    DesktopSaleLine(
        String id,
        String transactionId,
        CartLine.Kind kind,
        String itemId,
        String name,
        String barcode,
        String category,
        int quantity,
        long unitCents,
        long lineTotalCents,
        String staffName
    ) {
        this.id = id;
        this.transactionId = transactionId;
        this.kind = kind;
        this.itemId = itemId;
        this.name = name;
        this.barcode = barcode;
        this.category = category;
        this.quantity = quantity;
        this.unitCents = unitCents;
        this.lineTotalCents = lineTotalCents;
        this.staffName = staffName;
    }
}

final class ServiceTicket {
    enum Status {
        BOOKED,
        IN_PROGRESS,
        COMPLETED
    }

    String id;
    String token;
    Instant createdAt;
    Instant startedAt;
    Instant completedAt;
    Status status;
    String transactionId;
    String customerId;
    String customerName;
    String customerPhone;
    String serviceId;
    String serviceName;
    String category;
    int quantity;
    long unitCents;
    long totalCents;
    long paidCents;
    String paymentMethod;
    String assignedTechnician;
    String activeTechnician;
    String requirements;
    String completionNotes;

    ServiceTicket(
        String id,
        String token,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Status status,
        String transactionId,
        String customerId,
        String customerName,
        String customerPhone,
        String serviceId,
        String serviceName,
        String category,
        int quantity,
        long unitCents,
        long totalCents,
        long paidCents,
        String paymentMethod,
        String assignedTechnician,
        String activeTechnician,
        String requirements,
        String completionNotes
    ) {
        this.id = id;
        this.token = token;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.status = status;
        this.transactionId = transactionId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.category = category;
        this.quantity = quantity;
        this.unitCents = unitCents;
        this.totalCents = totalCents;
        this.paidCents = paidCents;
        this.paymentMethod = paymentMethod;
        this.assignedTechnician = assignedTechnician;
        this.activeTechnician = activeTechnician;
        this.requirements = requirements;
        this.completionNotes = completionNotes;
    }
}

final class CartLine {
    enum Kind {
        PRODUCT,
        SERVICE
    }

    final Kind kind;
    final String itemId;
    final String name;
    int quantity;
    long unitCents;
    String staffName = "";

    CartLine(Kind kind, String itemId, String name, int quantity, long unitCents) {
        this.kind = kind;
        this.itemId = itemId;
        this.name = name;
        this.quantity = quantity;
        this.unitCents = unitCents;
    }

    long totalCents() {
        return unitCents * quantity;
    }
}

final class ScanEvent {
    String id;
    Instant createdAt;
    String sourceDevice;
    String kind;
    String rawValue;
    String status;

    ScanEvent(String id, Instant createdAt, String sourceDevice, String kind, String rawValue, String status) {
        this.id = id;
        this.createdAt = createdAt;
        this.sourceDevice = sourceDevice;
        this.kind = kind;
        this.rawValue = rawValue;
        this.status = status;
    }
}

final class StockSyncItem {
    String id;
    Instant createdAt;
    String sourceDevice;
    String productId;
    String productName;
    String barcode;
    String category;
    int quantity;
    long priceCents;
    long costCents;
    String imagePath;
    String imageFileName;
    String imageBase64;
    String status;
    String mobileProductId = "";
    boolean stockBaseKnown = false;
    int stockBase = 0;
    int sourceStock = 0;
    String mutationId = "";

    StockSyncItem(
        String id,
        Instant createdAt,
        String sourceDevice,
        String productId,
        String productName,
        String barcode,
        String category,
        int quantity,
        long priceCents,
        long costCents,
        String imagePath,
        String imageFileName,
        String imageBase64,
        String status
    ) {
        this.id = id;
        this.createdAt = createdAt;
        this.sourceDevice = sourceDevice;
        this.productId = productId;
        this.productName = productName;
        this.barcode = barcode;
        this.category = category;
        this.quantity = quantity;
        this.priceCents = priceCents;
        this.costCents = costCents;
        this.imagePath = imagePath;
        this.imageFileName = imageFileName;
        this.imageBase64 = imageBase64;
        this.status = status;
    }
}

final class ProductSyncItem {
    String id;
    Instant createdAt;
    String sourceDevice;
    String mobileProductId;
    String name;
    String description;
    String sku;
    String barcode;
    String category;
    int stock;
    long priceCents;
    long costCents;
    String imagePath;
    String imageFileName;
    String imageBase64;
    String whatsappRetailerId;
    String whatsappImageUrl;
    String whatsappProductUrl;
    String status;

    ProductSyncItem(
        String id,
        Instant createdAt,
        String sourceDevice,
        String mobileProductId,
        String name,
        String sku,
        String barcode,
        String category,
        int stock,
        long priceCents,
        long costCents,
        String imagePath,
        String imageFileName,
        String imageBase64,
        String whatsappRetailerId,
        String status
    ) {
        this(id, createdAt, sourceDevice, mobileProductId, name, "", sku, barcode, category, stock, priceCents, costCents, imagePath, imageFileName, imageBase64, whatsappRetailerId, "", "", status);
    }

    ProductSyncItem(
        String id,
        Instant createdAt,
        String sourceDevice,
        String mobileProductId,
        String name,
        String description,
        String sku,
        String barcode,
        String category,
        int stock,
        long priceCents,
        long costCents,
        String imagePath,
        String imageFileName,
        String imageBase64,
        String whatsappRetailerId,
        String whatsappImageUrl,
        String whatsappProductUrl,
        String status
    ) {
        this.id = id;
        this.createdAt = createdAt;
        this.sourceDevice = sourceDevice;
        this.mobileProductId = mobileProductId;
        this.name = name;
        this.description = description;
        this.sku = sku;
        this.barcode = barcode;
        this.category = category;
        this.stock = stock;
        this.priceCents = priceCents;
        this.costCents = costCents;
        this.imagePath = imagePath;
        this.imageFileName = imageFileName;
        this.imageBase64 = imageBase64;
        this.whatsappRetailerId = whatsappRetailerId;
        this.whatsappImageUrl = whatsappImageUrl;
        this.whatsappProductUrl = whatsappProductUrl;
        this.status = status;
    }
}
