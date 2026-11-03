package org.amrosman.jsmartclinic.domain;

public enum PaymentMethod {
    CASH,
    CARD,
    INSURANCE,
    TRANSFER;

    public static PaymentMethod from(String value) {
        return value == null ? CASH : PaymentMethod.valueOf(value);
    }
}
