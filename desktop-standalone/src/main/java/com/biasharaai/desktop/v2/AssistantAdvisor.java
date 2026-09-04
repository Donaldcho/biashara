package com.biasharaai.desktop.v2;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Collectors;

final class AssistantAdvisor {
    String welcome(AppState state) {
        return "I am ready. I can answer questions about sales, stock, services, credit, phone scans, and the desktop AI model.";
    }

    String answer(String question, AppState state) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (q.contains("low") || q.contains("stock")) {
            return lowStock(state);
        }
        if (q.contains("credit") || q.contains("owe") || q.contains("debt")) {
            return credit(state);
        }
        if (q.contains("service")) {
            return serviceSummary(state);
        }
        if (q.contains("ledger") || q.contains("cash flow") || q.contains("expense")) {
            return ledgerSummary(state);
        }
        if (q.contains("agent") || q.contains("warning") || q.contains("alert")) {
            return agentSummary(state);
        }
        if (q.contains("today") || q.contains("sale") || q.contains("revenue")) {
            return salesToday(state);
        }
        if (q.contains("scan") || q.contains("phone")) {
            return scanSummary(state);
        }
        if (q.contains("model") || q.contains("ai")) {
            return modelStatus(state);
        }
        return businessReview(state);
    }

    String businessReview(AppState state) {
        long today = state.revenueOn(LocalDate.now());
        long credit = state.creditOutstanding();
        long lowStockCount = state.products.stream().filter(product -> product.stock <= 5).count();
        String topProduct = state.products.stream()
            .max(Comparator.comparingLong(product -> product.priceCents * Math.max(0, product.stock)))
            .map(product -> product.name)
            .orElse("No products yet");
        return "Today revenue is " + Money.format(today, state.settings.currency) + ". "
            + "Outstanding customer credit is " + Money.format(credit, state.settings.currency) + ". "
            + lowStockCount + " product(s) need stock attention. "
            + "The strongest catalog value is currently " + topProduct + ". "
            + "Keep product cost prices and service duration updated so pricing advice stays useful.";
    }

    private String salesToday(AppState state) {
        long today = state.revenueOn(LocalDate.now());
        long count = state.transactions.stream()
            .filter(transaction -> transaction.date().equals(LocalDate.now()))
            .filter(transaction -> transaction.type == TransactionType.SALE || transaction.type == TransactionType.SERVICE_SALE)
            .count();
        return "You recorded " + count + " sale(s) today totaling " + Money.format(today, state.settings.currency) + ".";
    }

    private String lowStock(AppState state) {
        String items = state.products.stream()
            .filter(product -> product.stock <= 5)
            .sorted(Comparator.comparingInt(product -> product.stock))
            .map(product -> product.name + " (" + product.stock + " left)")
            .collect(Collectors.joining(", "));
        if (items.isBlank()) {
            return "No urgent low-stock products. Keep scanning stock intake after supplier visits.";
        }
        return "Low stock: " + items + ". Restock these before running promotions or accepting bulk orders.";
    }

    private String credit(AppState state) {
        long total = state.creditOutstanding();
        String customers = state.customers.stream()
            .filter(customer -> customer.balanceCents > 0)
            .sorted(Comparator.comparingLong(customer -> -customer.balanceCents))
            .limit(5)
            .map(customer -> customer.name + " " + Money.format(customer.balanceCents, state.settings.currency))
            .collect(Collectors.joining(", "));
        if (customers.isBlank()) {
            return "No customer credit is outstanding.";
        }
        return "Outstanding credit is " + Money.format(total, state.settings.currency) + ". Follow up with: " + customers + ".";
    }

    private String serviceSummary(AppState state) {
        long serviceSales = state.transactions.stream()
            .filter(transaction -> transaction.type == TransactionType.SERVICE_SALE)
            .mapToLong(transaction -> transaction.totalCents)
            .sum();
        String services = state.services.stream()
            .map(service -> service.name + " at " + Money.format(service.priceCents, state.settings.currency))
            .collect(Collectors.joining(", "));
        return "Service revenue recorded on desktop is " + Money.format(serviceSales, state.settings.currency)
            + ". Active services: " + (services.isBlank() ? "none yet" : services) + ".";
    }

    private String ledgerSummary(AppState state) {
        long moneyIn = state.transactions.stream()
            .filter(transaction -> transaction.type == TransactionType.SALE || transaction.type == TransactionType.SERVICE_SALE || transaction.type == TransactionType.PAYMENT)
            .mapToLong(transaction -> Math.max(0, transaction.paidCents))
            .sum();
        long expenses = state.transactions.stream()
            .filter(transaction -> transaction.type == TransactionType.EXPENSE)
            .mapToLong(transaction -> Math.abs(transaction.totalCents))
            .sum();
        long adjustmentOut = state.transactions.stream()
            .filter(transaction -> transaction.type == TransactionType.ADJUSTMENT && transaction.totalCents < 0)
            .mapToLong(transaction -> Math.abs(transaction.totalCents))
            .sum();
        return "The desktop ledger shows money in of " + Money.format(moneyIn, state.settings.currency)
            + ", money out of " + Money.format(expenses + adjustmentOut, state.settings.currency)
            + ", and outstanding customer credit of " + Money.format(state.creditOutstanding(), state.settings.currency)
            + ". Use Ledger to add expenses, cash adjustments, and customer payments.";
    }

    private String agentSummary(AppState state) {
        long lowStock = state.products.stream().filter(product -> product.stock <= 5).count();
        long missingImages = state.products.stream().filter(product -> product.imagePath == null || product.imagePath.isBlank()).count();
        long marginRisk = state.products.stream()
            .filter(product -> product.costCents > 0 && product.priceCents > 0 && product.priceCents <= Math.round(product.costCents * 1.2))
            .count();
        return "Desktop agents are watching " + lowStock + " low-stock item(s), "
            + missingImages + " product(s) without images, "
            + marginRisk + " pricing risk(s), and "
            + Money.format(state.creditOutstanding(), state.settings.currency) + " in customer credit.";
    }

    private String scanSummary(AppState state) {
        long pending = state.scanEvents.stream().filter(event -> "Received".equals(event.status)).count();
        long applied = state.scanEvents.stream().filter(event -> "Applied".equals(event.status)).count();
        return "Phone bridge has " + applied + " applied scan(s) and " + pending + " received scan(s). "
            + "Open Phone Link to pair a device or review recent scans.";
    }

    private String modelStatus(AppState state) {
        if (state.settings.modelPath == null || state.settings.modelPath.isBlank()) {
            return "No built-in desktop AI model path is configured. Open Settings to use LM Studio or set a local model path later.";
        }
        return "Desktop AI model path is configured at " + state.settings.modelPath
            + ". LM Studio can also be selected in Settings when the local server is running.";
    }
}
