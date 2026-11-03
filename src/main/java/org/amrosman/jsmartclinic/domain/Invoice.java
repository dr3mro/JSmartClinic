package org.amrosman.jsmartclinic.domain;

import java.time.Instant;

public record Invoice(
        long id,
        String invoiceNo,
        long patientId,
        Long encounterId,
        Instant issuedAt,
        long subtotalCents,
        long discountCents,
        long taxCents,
        long totalCents,
        InvoiceStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public long balanceCents() {
        return totalCents;
    }
}
