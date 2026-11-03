package org.amrosman.jsmartclinic.domain;

public record BillableService(
        long id,
        String code,
        String name,
        long priceCents,
        boolean active) {
}
