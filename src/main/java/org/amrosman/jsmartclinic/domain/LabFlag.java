package org.amrosman.jsmartclinic.domain;

public enum LabFlag {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL;

    public static LabFlag from(String value) {
        return value == null ? null : LabFlag.valueOf(value);
    }
}
