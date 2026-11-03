package org.amrosman.jsmartclinic.domain;

import java.time.Instant;

public record Appointment(
        long id,
        long patientId,
        long providerId,
        Instant startAt,
        Instant endAt,
        String reason,
        AppointmentStatus status,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public long durationMinutes() {
        return java.time.Duration.between(startAt, endAt).toMinutes();
    }
}
