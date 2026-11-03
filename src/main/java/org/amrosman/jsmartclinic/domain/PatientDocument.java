package org.amrosman.jsmartclinic.domain;

import java.time.Instant;

public record PatientDocument(
        long id,
        long patientId,
        Long encounterId,
        String title,
        String mimeType,
        String fileName,
        String sha256,
        Long uploadedBy,
        Instant createdAt) {
}
