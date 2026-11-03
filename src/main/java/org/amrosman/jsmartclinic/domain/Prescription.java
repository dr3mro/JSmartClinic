package org.amrosman.jsmartclinic.domain;

import java.time.Instant;

public record Prescription(
        long id,
        long patientId,
        Long encounterId,
        long prescriberId,
        Instant issuedAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {
}
