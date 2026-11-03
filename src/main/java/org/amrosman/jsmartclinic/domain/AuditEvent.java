package org.amrosman.jsmartclinic.domain;

import java.time.Instant;

public record AuditEvent(
        long id,
        Instant at,
        Long userId,
        AuditAction action,
        String entity,
        Long entityId,
        Long patientId,
        String details) {
}
