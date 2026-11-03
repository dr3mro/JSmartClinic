package org.amrosman.jsmartclinic.domain;

import java.time.Instant;

public record Vital(
        long id,
        long patientId,
        Long encounterId,
        Instant measuredAt,
        Integer systolic,
        Integer diastolic,
        Integer heartRate,
        Integer respRate,
        Double temperatureC,
        Integer spo2,
        Double weightKg,
        Double heightCm,
        Double glucoseMgdl,
        Instant createdAt,
        Instant updatedAt) {

    public Double bmi() {
        if (weightKg == null || heightCm == null || heightCm <= 0) {
            return null;
        }
        double m = heightCm / 100.0;
        return Math.round((weightKg / (m * m)) * 10.0) / 10.0;
    }

    public String bloodPressure() {
        if (systolic == null || diastolic == null) {
            return "—";
        }
        return systolic + "/" + diastolic;
    }
}
