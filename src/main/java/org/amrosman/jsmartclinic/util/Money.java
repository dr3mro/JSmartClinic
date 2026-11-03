package org.amrosman.jsmartclinic.util;

import java.text.NumberFormat;
import java.util.Locale;

/** Money is always stored as integer cents. */
public final class Money {

    private Money() {
    }

    public static String format(long cents) {
        return format(cents, "$");
    }

    public static String format(long cents, String symbol) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return (symbol == null || symbol.isBlank() ? "$" : symbol) + nf.format(cents / 100.0);
    }

    public static long parseToCents(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String cleaned = text.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isBlank()) {
            return 0;
        }
        return Math.round(Double.parseDouble(cleaned) * 100);
    }
}
