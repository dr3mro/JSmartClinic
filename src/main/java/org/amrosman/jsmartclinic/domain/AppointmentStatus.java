package org.amrosman.jsmartclinic.domain;

public enum AppointmentStatus {
    SCHEDULED("Scheduled"),
    CHECKED_IN("Checked in"),
    IN_PROGRESS("In progress"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    NO_SHOW("No show");

    private final String label;

    AppointmentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static AppointmentStatus from(String value) {
        return value == null ? SCHEDULED : AppointmentStatus.valueOf(value);
    }
}
