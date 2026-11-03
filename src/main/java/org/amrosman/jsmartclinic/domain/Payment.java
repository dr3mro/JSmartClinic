package org.amrosman.jsmartclinic.domain;

import java.time.Instant;

public record Payment(
        long id,
        long invoiceId,
        long amountCents,
        PaymentMethod method,
        Instant paidAt,
        Long receivedBy,
        String reference) {
}
