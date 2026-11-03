package org.amrosman.jsmartclinic.domain;

public enum InvoiceStatus {
    UNPAID,
    PARTIAL,
    PAID,
    VOID;

    public static InvoiceStatus from(String value) {
        return value == null ? UNPAID : InvoiceStatus.valueOf(value);
    }
}
