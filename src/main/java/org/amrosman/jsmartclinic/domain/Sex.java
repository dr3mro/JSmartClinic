package org.amrosman.jsmartclinic.domain;

public enum Sex {
    MALE("Male"),
    FEMALE("Female"),
    OTHER("Other"),
    UNKNOWN("Unknown");

    private final String label;

    Sex(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Sex from(String value) {
        return value == null || value.isBlank() ? UNKNOWN : Sex.valueOf(value);
    }
}
