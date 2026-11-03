package org.amrosman.jsmartclinic.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/** Date/time helpers. The database stores UTC ISO-8601 text. */
public final class Dates {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private Dates() {
    }

    public static String toText(Instant instant) {
        return instant == null ? null : DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.MILLIS));
    }

    public static Instant fromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    public static String toText(LocalDate date) {
        return date == null ? null : date.format(SHORT_DATE);
    }

    public static LocalDate dateFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            Instant instant = fromText(text);
            return instant == null ? null : instant.atZone(ZoneId.systemDefault()).toLocalDate();
        }
    }

    public static String display(Instant instant) {
        return instant == null ? "—" : DATE_TIME.format(instant.atZone(ZoneId.systemDefault()));
    }

    public static String displayDate(LocalDate date) {
        return date == null ? "—" : DATE.format(date);
    }

    public static String displayDate(Instant instant) {
        return instant == null ? "—" : DATE.format(instant.atZone(ZoneId.systemDefault()));
    }

    public static String displayTime(Instant instant) {
        return instant == null ? "—" : TIME.format(instant.atZone(ZoneId.systemDefault()));
    }

    public static String isoDate(LocalDate date) {
        return date == null ? "" : date.format(SHORT_DATE);
    }

    /** Start of the local day, as an {@link Instant}. */
    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    /** End of the local day (exclusive), as an {@link Instant}. */
    public static Instant endOfDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }
}
