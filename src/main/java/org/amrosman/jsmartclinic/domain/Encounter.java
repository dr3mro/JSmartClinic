package org.amrosman.jsmartclinic.domain;

import java.time.Instant;

public record Encounter(
        long id,
        long patientId,
        long providerId,
        Long appointmentId,
        String specialty,
        String templateId,
        Instant encounterDate,
        String chiefComplaint,
        String subjective,
        String objective,
        String assessment,
        String plan,
        String templateData,
        EncounterStatus status,
        Instant signedAt,
        Long signedBy,
        Instant createdAt,
        Instant updatedAt) {

    public boolean signed() {
        return status == EncounterStatus.SIGNED || status == EncounterStatus.AMENDED;
    }
}
