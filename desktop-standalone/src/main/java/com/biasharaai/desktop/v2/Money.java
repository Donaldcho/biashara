package com.biasharaai.desktop.v2;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class Money {
    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
        "XAF", "XOF", "BIF", "DJF", "GNF", "KMF", "MGA", "RWF", "UGX"
    );
    private static final Map<String, String> CURRENCY_LABELS = Map.ofEntries(
        Map.entry("XAF", "FCFA"),
        Map.entry("XOF", "FCFA"),
        Map.entry("KES", "KSh"),
        Map.entry("TZS", "TSh"),
        Map.entry("UGX", "USh"),
        Map.entry("RWF", "RF")
    );

    private Money() {
    }

    static long parseCents(String value) {
        String normalized = value == null ? "" : value.trim().replace(",", "");
        if (normalized.isEmpty()) {
            return 0L;
        }
        return new BigDecimal(normalized)
            .setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .longValueExact();
    }

    static String format(long cents, String currency) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.getDefault());
        String code = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        int digits = ZERO_DECIMAL_CURRENCIES.contains(code) ? 0 : 2;
        format.setMinimumFractionDigits(digits);
        format.setMaximumFractionDigits(digits);
        String amount = format.format(cents / 100.0);
        return code.isBlank() ? amount : CURRENCY_LABELS.getOrDefault(code, code) + " " + amount;
    }

    static String input(long cents) {
        return BigDecimal.valueOf(cents, 2).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
