package org.amrosman.jsmartclinic.domain;

import java.time.Instant;

public record LabResult(
        long id,
        long labOrderId,
        long labTestId,
        Double valueNum,
        String valueText,
        LabFlag flag,
        Instant resultedAt,
        Instant createdAt,
        Instant updatedAt) {
}
