package org.amrosman.jsmartclinic.domain;

import java.time.Instant;
import java.time.LocalDate;

public record Problem(
        long id,
        long patientId,
        String icd10Code,
        String description,
        LocalDate onsetDate,
        ProblemStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
