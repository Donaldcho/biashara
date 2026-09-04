package com.biasharaai.desktop.v2;

import java.util.List;

final class AgentTestFixtures {
    private AgentTestFixtures() {}

    static BusinessSnapshot snapshot() {
        long now = System.currentTimeMillis();
        return new BusinessSnapshot(
            "Test Shop",
            "KES",
            now,
            List.of(
                new BusinessSnapshot.ProductRecord("P-1", "Fast Item", "Retail", "111", 2_000, 1_000, 1, true),
                new BusinessSnapshot.ProductRecord("P-2", "Missing Cost", "Retail", "222", 3_000, 0, 10, false)
            ),
            List.of(new BusinessSnapshot.CustomerRecord("C-1", "Customer One", "***1234", 5_000, 3)),
            List.of(
                new BusinessSnapshot.TransactionRecord("T-1", now - 60_000, "SALE", "C-1", "Customer One", "10 x Fast Item", "Cash", 20_000, 20_000, 0),
                new BusinessSnapshot.TransactionRecord("T-2", now - 120_000, "EXPENSE", "", "", "Transport", "Cash", 2_000, 2_000, 0)
            ),
            List.of(new BusinessSnapshot.SaleLineRecord("T-1", "PRODUCT", "P-1", "Fast Item", 10, 20_000)),
            List.of(new BusinessSnapshot.ServiceTicketRecord("JOB-1", now - 7_200_000, 0, "BOOKED", "Customer One", "Repair", "", "", 1, 10_000, 5_000)),
            1,
            new BusinessSnapshot.SyncRecord(true, "Test Phone", 4, 2, 3, now - 60_000, now - 120_000, now - 30_000)
        );
    }
}
