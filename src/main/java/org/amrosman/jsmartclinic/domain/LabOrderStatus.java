package org.amrosman.jsmartclinic.domain;

public enum LabOrderStatus {
    ORDERED,
    COLLECTED,
    RESULTED,
    CANCELLED;

    public static LabOrderStatus from(String value) {
        return value == null ? ORDERED : LabOrderStatus.valueOf(value);
    }
}
