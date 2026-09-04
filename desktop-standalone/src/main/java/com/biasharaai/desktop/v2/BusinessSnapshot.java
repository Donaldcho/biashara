package com.biasharaai.desktop.v2;

import java.time.Instant;
import java.util.List;

record BusinessSnapshot(
    String businessName,
    String currency,
    long capturedAtMillis,
    List<ProductRecord> products,
    List<CustomerRecord> customers,
    List<TransactionRecord> transactions,
    List<SaleLineRecord> saleLines,
    List<ServiceTicketRecord> serviceTickets,
    int serviceCount,
    SyncRecord sync
) {
    BusinessSnapshot {
        products = List.copyOf(products);
        customers = List.copyOf(customers);
        transactions = List.copyOf(transactions);
        saleLines = List.copyOf(saleLines);
        serviceTickets = List.copyOf(serviceTickets);
    }

    static BusinessSnapshot capture(AppState state, boolean phonePaired, String pairedDevice) {
        long latestProductSync = state.productSyncItems.stream()
            .map(item -> item.createdAt)
            .filter(value -> value != null)
            .mapToLong(Instant::toEpochMilli)
            .max()
            .orElse(0L);
        long latestStockSync = state.stockSyncItems.stream()
            .map(item -> item.createdAt)
            .filter(value -> value != null)
            .mapToLong(Instant::toEpochMilli)
            .max()
            .orElse(0L);
        long latestScan = state.scanEvents.stream()
            .map(item -> item.createdAt)
            .filter(value -> value != null)
            .mapToLong(Instant::toEpochMilli)
            .max()
            .orElse(0L);

        return new BusinessSnapshot(
            clean(state.settings.businessName),
            clean(state.settings.currency),
            System.currentTimeMillis(),
            state.products.stream().map(product -> new ProductRecord(
                product.id,
                clean(product.name),
                clean(product.category),
                clean(product.barcode),
                product.priceCents,
                product.costCents,
                product.stock,
                !clean(product.imagePath).isBlank()
            )).toList(),
            state.customers.stream().map(customer -> new CustomerRecord(
                customer.id,
                clean(customer.name),
                maskPhone(customer.phone),
                customer.balanceCents,
                customer.visits
            )).toList(),
            state.transactions.stream().map(transaction -> new TransactionRecord(
                transaction.id,
                transaction.createdAt == null ? 0L : transaction.createdAt.toEpochMilli(),
                transaction.type.name(),
                clean(transaction.customerId),
                clean(transaction.customerName),
                clean(transaction.description),
                clean(transaction.paymentMethod),
                transaction.totalCents,
                transaction.paidCents,
                transaction.balanceCents
            )).toList(),
            state.saleLines.stream().map(line -> new SaleLineRecord(
                line.transactionId,
                line.kind.name(),
                line.itemId,
                clean(line.name),
                line.quantity,
                line.lineTotalCents
            )).toList(),
            state.serviceTickets.stream().map(ticket -> new ServiceTicketRecord(
                ticket.id,
                ticket.createdAt == null ? 0L : ticket.createdAt.toEpochMilli(),
                ticket.startedAt == null ? 0L : ticket.startedAt.toEpochMilli(),
                ticket.status.name(),
                clean(ticket.customerName),
                clean(ticket.serviceName),
                clean(ticket.assignedTechnician),
                clean(ticket.activeTechnician),
                ticket.quantity,
                ticket.totalCents,
                ticket.paidCents
            )).toList(),
            state.services.size(),
            new SyncRecord(
                phonePaired,
                clean(pairedDevice),
                state.productSyncItems.size(),
                state.stockSyncItems.size(),
                state.scanEvents.size(),
                latestProductSync,
                latestStockSync,
                latestScan
            )
        );
    }

    private static String maskPhone(String phone) {
        String value = clean(phone);
        if (value.length() <= 4) {
            return value.isBlank() ? "" : "****";
        }
        return "***" + value.substring(value.length() - 4);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    record ProductRecord(
        String id,
        String name,
        String category,
        String barcode,
        long priceCents,
        long costCents,
        int stock,
        boolean hasImage
    ) {}

    record CustomerRecord(
        String id,
        String name,
        String maskedPhone,
        long balanceCents,
        int visits
    ) {}

    record TransactionRecord(
        String id,
        long createdAtMillis,
        String type,
        String customerId,
        String customerName,
        String description,
        String paymentMethod,
        long totalCents,
        long paidCents,
        long balanceCents
    ) {}

    record SaleLineRecord(
        String transactionId,
        String kind,
        String itemId,
        String name,
        int quantity,
        long lineTotalCents
    ) {}

    record ServiceTicketRecord(
        String id,
        long createdAtMillis,
        long startedAtMillis,
        String status,
        String customerName,
        String serviceName,
        String assignedTechnician,
        String activeTechnician,
        int quantity,
        long totalCents,
        long paidCents
    ) {}

    record SyncRecord(
        boolean phonePaired,
        String pairedDevice,
        int productSyncCount,
        int stockSyncCount,
        int scanCount,
        long latestProductSyncMillis,
        long latestStockSyncMillis,
        long latestScanMillis
    ) {}
}
