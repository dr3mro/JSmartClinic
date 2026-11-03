package org.amrosman.jsmartclinic.domain;

public enum EncounterStatus {
    DRAFT,
    SIGNED,
    AMENDED;

    public static EncounterStatus from(String value) {
        return value == null ? DRAFT : EncounterStatus.valueOf(value);
    }
}
