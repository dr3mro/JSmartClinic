package org.amrosman.jsmartclinic.domain;

import java.time.Instant;

public record LabOrder(
        long id,
        long patientId,
        Long encounterId,
        long orderedBy,
        Instant orderedAt,
        LabOrderStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
