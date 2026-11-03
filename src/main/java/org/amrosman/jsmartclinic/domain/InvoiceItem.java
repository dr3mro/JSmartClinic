package org.amrosman.jsmartclinic.domain;

public record InvoiceItem(
        long id,
        long invoiceId,
        Long serviceId,
        String description,
        int qty,
        long unitPriceCents) {

    public long lineTotalCents() {
        return (long) qty * unitPriceCents;
    }
}
