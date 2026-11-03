package org.amrosman.jsmartclinic.domain;

public record LabTest(
        long id,
        String code,
        String name,
        String unit,
        Double refLow,
        Double refHigh,
        Double critLow,
        Double critHigh,
        String category) {
}
