package org.amrosman.jsmartclinic.domain;

public record PrescriptionItem(
        long id,
        long prescriptionId,
        String drugName,
        String strength,
        String form,
        String dose,
        String frequency,
        String duration,
        String quantity,
        String instructions) {
}
