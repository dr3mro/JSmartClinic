package org.amrosman.jsmartclinic.domain;

public enum Severity {
    MILD,
    MODERATE,
    SEVERE;

    public static Severity from(String value) {
        return value == null ? null : Severity.valueOf(value);
    }
}
