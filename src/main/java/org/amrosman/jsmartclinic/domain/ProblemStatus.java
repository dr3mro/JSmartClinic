package org.amrosman.jsmartclinic.domain;

public enum ProblemStatus {
    ACTIVE,
    RESOLVED;

    public static ProblemStatus from(String value) {
        return value == null ? ACTIVE : ProblemStatus.valueOf(value);
    }
}
