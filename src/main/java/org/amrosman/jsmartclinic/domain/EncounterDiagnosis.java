package org.amrosman.jsmartclinic.domain;

public record EncounterDiagnosis(
        long id,
        long encounterId,
        String icd10Code,
        String description,
        boolean primary) {
}
