package org.amrosman.jsmartclinic.service;

import org.amrosman.jsmartclinic.app.Session;
import org.amrosman.jsmartclinic.domain.AuditAction;
import org.amrosman.jsmartclinic.domain.BillableService;
import org.amrosman.jsmartclinic.domain.Invoice;
import org.amrosman.jsmartclinic.domain.InvoiceItem;
import org.amrosman.jsmartclinic.domain.InvoiceStatus;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.Payment;
import org.amrosman.jsmartclinic.domain.PaymentMethod;
import org.amrosman.jsmartclinic.repository.BillingRepository;
import org.amrosman.jsmartclinic.repository.PatientRepository;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

public final class BillingService {

    private final BillingRepository billing;
    private final PatientRepository patients;
    private final Session session;
    private final AuditService audit;
    private final SettingsService settings;
    private final PdfService pdf;

    public BillingService(BillingRepository billing, PatientRepository patients, Session session, AuditService audit,
                          SettingsService settings, PdfService pdf) {
        this.billing = billing;
        this.patients = patients;
        this.session = session;
        this.audit = audit;
        this.settings = settings;
        this.pdf = pdf;
    }

    public List<BillableService> services(boolean activeOnly) {
        session.require(Permission.BILLING);
        return billing.services(activeOnly);
    }

    public void saveService(long id, String code, String name, long priceCents, boolean active) {
        session.require(Permission.BILLING);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Service name is required");
        }
        if (id <= 0) {
            billing.insertService(code, name, priceCents, active);
        } else {
            billing.updateService(id, code, name, priceCents, active);
        }
        audit.log(AuditAction.UPDATE, "service", id, null, "Service catalogue updated");
    }

    public Invoice invoice(long id) {
        session.require(Permission.BILLING);
        return billing.findInvoice(id);
    }

    public List<Invoice> invoicesForPatient(long patientId) {
        session.require(Permission.BILLING);
        return billing.invoicesForPatient(patientId);
    }

    public List<Invoice> recentInvoices(int limit) {
        session.require(Permission.BILLING);
        return billing.recentInvoices(limit);
    }

    public List<Invoice> outstanding() {
        session.require(Permission.BILLING);
        return billing.outstandingInvoices();
    }

    public List<InvoiceItem> items(long invoiceId) {
        session.require(Permission.BILLING);
        return billing.items(invoiceId);
    }

    public List<Payment> payments(long invoiceId) {
        session.require(Permission.BILLING);
        return billing.payments(invoiceId);
    }

    public long createInvoice(long patientId, Long encounterId, List<InvoiceItem> items, long discountCents) {
        session.require(Permission.BILLING);
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Add at least one service to the invoice");
        }
        long id = billing.createInvoice(patientId, encounterId, items, discountCents, settings.taxRate());
        audit.log(AuditAction.CREATE, "invoice", id, patientId, items.size() + " item(s)");
        return id;
    }

    public void addItem(long invoiceId, InvoiceItem item) {
        session.require(Permission.BILLING);
        billing.addItem(invoiceId, item);
        audit.log(AuditAction.UPDATE, "invoice", invoiceId, null, "Item added");
    }

    public void recordPayment(long invoiceId, long amountCents, PaymentMethod method, String reference) {
        session.require(Permission.BILLING);
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
        Invoice invoice = billing.findInvoice(invoiceId);
        if (invoice == null || invoice.status() == InvoiceStatus.VOID) {
            throw new IllegalArgumentException("Invoice is not payable");
        }
        long balance = invoice.totalCents() - billing.paidTotal(invoiceId);
        if (amountCents > balance) {
            throw new IllegalArgumentException("Payment exceeds outstanding balance");
        }
        billing.recordPayment(invoiceId, amountCents, method, session.userId(), reference);
        audit.log(AuditAction.UPDATE, "invoice", invoiceId, invoice.patientId(), "Payment recorded");
    }

    public void voidInvoice(long invoiceId) {
        session.require(Permission.BILLING);
        billing.voidInvoice(invoiceId);
        audit.log(AuditAction.UPDATE, "invoice", invoiceId, null, "Invoice voided");
    }

    public long balance(long patientId) {
        session.require(Permission.BILLING);
        return billing.balanceForPatient(patientId);
    }

    public long revenue(LocalDate from, LocalDate to) {
        session.require(Permission.BILLING);
        return billing.revenue(from, to);
    }

    public long todayRevenue() {
        session.require(Permission.BILLING);
        return billing.revenue(LocalDate.now(), LocalDate.now());
    }

    public int outstandingCount() {
        session.require(Permission.BILLING);
        return billing.countOutstanding();
    }

    public long paidTotal(long invoiceId) {
        session.require(Permission.BILLING);
        return billing.paidTotal(invoiceId);
    }

    /** Renders a receipt PDF and audits the PRINT action. */
    public Path receiptPdf(long invoiceId) {
        session.require(Permission.BILLING);
        Invoice invoice = billing.findInvoice(invoiceId);
        if (invoice == null) {
            throw new IllegalArgumentException("Invoice not found");
        }
        Patient patient = patients.findById(invoice.patientId());
        Path path = pdf.receipt(invoice, patient, billing.items(invoiceId), billing.payments(invoiceId));
        audit.log(AuditAction.PRINT, "invoice", invoiceId, invoice.patientId(), "Receipt PDF");
        return path;
    }

    public InvoiceItem consultationItem() {
        return billing.services(true).stream()
                .filter(s -> "CONS".equalsIgnoreCase(s.code()))
                .findFirst()
                .map(s -> new InvoiceItem(0, 0, s.id(), s.name(), 1, s.priceCents()))
                .orElseGet(() -> new InvoiceItem(0, 0, null, "Consultation", 1, 0));
    }
}
