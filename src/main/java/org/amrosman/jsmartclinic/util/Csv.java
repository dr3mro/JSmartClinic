package org.amrosman.jsmartclinic.util;

import java.util.List;

/** Minimal RFC-4180 CSV writing helpers. */
public final class Csv {

    private Csv() {
    }

    public static String row(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(values[i]));
        }
        return sb.append('\n').toString();
    }

    public static String table(List<String> headers, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder(row(headers.toArray()));
        for (List<Object> row : rows) {
            sb.append(row(row.toArray()));
        }
        return sb.toString();
    }

    private static String escape(Object value) {
        String s = value == null ? "" : String.valueOf(value);
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
