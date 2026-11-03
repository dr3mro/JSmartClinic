package org.amrosman.jsmartclinic.domain;

public enum Role {
    ADMIN("Administrator"),
    DOCTOR("Doctor"),
    NURSE("Nurse"),
    RECEPTION("Reception"),
    BILLING("Billing");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Role from(String value) {
        return value == null ? RECEPTION : Role.valueOf(value);
    }
}
