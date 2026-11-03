package org.amrosman.jsmartclinic.domain;

import java.time.Instant;

public record Allergy(
        long id,
        long patientId,
        String substance,
        String reaction,
        Severity severity,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
