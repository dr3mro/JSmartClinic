package org.amrosman.jsmartclinic.domain;

import java.time.Instant;
import java.time.LocalDate;

public record Medication(
        long id,
        long patientId,
        String name,
        String dose,
        String route,
        String frequency,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
