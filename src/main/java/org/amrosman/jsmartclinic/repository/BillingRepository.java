package org.amrosman.jsmartclinic.repository;

import org.amrosman.jsmartclinic.db.Database;
import org.amrosman.jsmartclinic.domain.BillableService;
import org.amrosman.jsmartclinic.domain.Invoice;
import org.amrosman.jsmartclinic.domain.InvoiceItem;
import org.amrosman.jsmartclinic.domain.InvoiceStatus;
import org.amrosman.jsmartclinic.domain.Payment;
import org.amrosman.jsmartclinic.domain.PaymentMethod;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public final class BillingRepository {

    private final Database database;

    public BillingRepository(Database database) {
        this.database = database;
    }

    private BillableService service(ResultSet rs) throws SQLException {
        return new BillableService(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getLong("price_cents"), rs.getInt("active") == 1);
    }

    private Invoice invoice(ResultSet rs) throws SQLException {
        return new Invoice(rs.getLong("id"), rs.getString("invoice_no"), rs.getLong("patient_id"),
                Jdbc.nullableLong(rs, "encounter_id"), Jdbc.instant(rs, "issued_at"), rs.getLong("subtotal_cents"),
                rs.getLong("discount_cents"), rs.getLong("tax_cents"), rs.getLong("total_cents"),
                InvoiceStatus.from(rs.getString("status")), Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "updated_at"));
    }

    private InvoiceItem item(ResultSet rs) throws SQLException {
        return new InvoiceItem(rs.getLong("id"), rs.getLong("invoice_id"), Jdbc.nullableLong(rs, "service_id"),
                rs.getString("description"), rs.getInt("qty"), rs.getLong("unit_price_cents"));
    }

    private Payment payment(ResultSet rs) throws SQLException {
        return new Payment(rs.getLong("id"), rs.getLong("invoice_id"), rs.getLong("amount_cents"),
                PaymentMethod.from(rs.getString("method")), Jdbc.instant(rs, "paid_at"),
                Jdbc.nullableLong(rs, "received_by"), rs.getString("reference"));
    }

    // ---------- Service catalogue ----------

    public List<BillableService> services(boolean activeOnly) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM services " + (activeOnly ? "WHERE active=1 " : "")
                    + "ORDER BY name", this::service);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public long insertService(String code, String name, long priceCents, boolean active) {
        return Jdbc.transaction(database, c -> Jdbc.insert(c,
                "INSERT INTO services(code, name, price_cents, active) VALUES (?,?,?,?)",
                code, name, priceCents, active));
    }

    public void updateService(long id, String code, String name, long priceCents, boolean active) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE services SET code=?, name=?, price_cents=?, active=? WHERE id=?",
                code, name, priceCents, active, id));
    }

    // ---------- Invoices ----------

    public Invoice findInvoice(long id) {
        try (Connection c = database.getConnection()) {
            return Jdbc.one(c, "SELECT * FROM invoices WHERE id=?", this::invoice, id);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Invoice> invoicesForPatient(long patientId) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM invoices WHERE patient_id=? ORDER BY issued_at DESC",
                    this::invoice, patientId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Invoice> recentInvoices(int limit) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM invoices ORDER BY issued_at DESC LIMIT ?", this::invoice, limit);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Invoice> outstandingInvoices() {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM invoices WHERE status IN ('UNPAID','PARTIAL') ORDER BY issued_at",
                    this::invoice);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<InvoiceItem> items(long invoiceId) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM invoice_items WHERE invoice_id=? ORDER BY id",
                    this::item, invoiceId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Payment> payments(long invoiceId) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM payments WHERE invoice_id=? ORDER BY paid_at", this::payment, invoiceId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public long createInvoice(long patientId, Long encounterId, List<InvoiceItem> items, long discountCents,
                              int taxRate) {
        return Jdbc.transaction(database, c -> {
            long subtotal = items.stream().mapToLong(InvoiceItem::lineTotalCents).sum();
            long taxable = Math.max(0, subtotal - discountCents);
            long tax = Math.round(taxable * (taxRate / 100.0));
            long total = taxable + tax;
            Instant now = Instant.now();
            long next = Jdbc.count(c, "SELECT COALESCE(MAX(id),0)+1 FROM invoices");
            String invoiceNo = "INV-" + LocalDate.now().getYear() + "-" + String.format("%05d", next);
            long id = Jdbc.insert(c, "INSERT INTO invoices(invoice_no, patient_id, encounter_id, issued_at, "
                            + "subtotal_cents, discount_cents, tax_cents, total_cents, status, created_at, updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    invoiceNo, patientId, encounterId, now, subtotal, discountCents, tax, total,
                    InvoiceStatus.UNPAID.name(), now, now);
            for (InvoiceItem item : items) {
                Jdbc.insert(c, "INSERT INTO invoice_items(invoice_id, service_id, description, qty, unit_price_cents) "
                                + "VALUES (?,?,?,?,?)",
                        id, item.serviceId(), item.description(), item.qty(), item.unitPriceCents());
            }
            return id;
        });
    }

    public void addItem(long invoiceId, InvoiceItem item) {
        Jdbc.transaction(database, c -> {
            Jdbc.insert(c, "INSERT INTO invoice_items(invoice_id, service_id, description, qty, unit_price_cents) "
                            + "VALUES (?,?,?,?,?)",
                    invoiceId, item.serviceId(), item.description(), item.qty(), item.unitPriceCents());
            recalculate(c, invoiceId);
            return null;
        });
    }

    public void recordPayment(long invoiceId, long amountCents, PaymentMethod method, Long receivedBy,
                              String reference) {
        Jdbc.transaction(database, c -> {
            Jdbc.insert(c, "INSERT INTO payments(invoice_id, amount_cents, method, paid_at, received_by, reference) "
                            + "VALUES (?,?,?,?,?,?)",
                    invoiceId, amountCents, method.name(), Instant.now(), receivedBy, reference);
            long paid = Jdbc.count(c, "SELECT COALESCE(SUM(amount_cents),0) FROM payments WHERE invoice_id=?", invoiceId);
            long total = Jdbc.count(c, "SELECT total_cents FROM invoices WHERE id=?", invoiceId);
            InvoiceStatus status = paid <= 0 ? InvoiceStatus.UNPAID
                    : paid >= total ? InvoiceStatus.PAID : InvoiceStatus.PARTIAL;
            Jdbc.update(c, "UPDATE invoices SET status=?, updated_at=? WHERE id=?", status.name(), Instant.now(), invoiceId);
            return null;
        });
    }

    public long paidTotal(long invoiceId) {
        try (Connection c = database.getConnection()) {
            return Jdbc.count(c, "SELECT COALESCE(SUM(amount_cents),0) FROM payments WHERE invoice_id=?", invoiceId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void voidInvoice(long invoiceId) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE invoices SET status='VOID', updated_at=? WHERE id=?", Instant.now(), invoiceId));
    }

    public long balanceForPatient(long patientId) {
        try (Connection c = database.getConnection()) {
            long billed = Jdbc.count(c, "SELECT COALESCE(SUM(total_cents),0) FROM invoices "
                    + "WHERE patient_id=? AND status<>'VOID'", patientId);
            long paid = Jdbc.count(c, "SELECT COALESCE(SUM(p.amount_cents),0) FROM payments p "
                    + "JOIN invoices i ON i.id=p.invoice_id WHERE i.patient_id=? AND i.status<>'VOID'", patientId);
            return Math.max(0, billed - paid);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public long revenue(LocalDate from, LocalDate to) {
        try (Connection c = database.getConnection()) {
            Instant start = from.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant end = to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            return Jdbc.count(c, "SELECT COALESCE(SUM(amount_cents),0) FROM payments WHERE paid_at>=? AND paid_at<?",
                    start, end);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public int countOutstanding() {
        try (Connection c = database.getConnection()) {
            return (int) Jdbc.count(c, "SELECT COUNT(*) FROM invoices WHERE status IN ('UNPAID','PARTIAL')");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void recalculate(Connection c, long invoiceId) throws SQLException {
        long subtotal = Jdbc.count(c, "SELECT COALESCE(SUM(qty * unit_price_cents),0) FROM invoice_items "
                + "WHERE invoice_id=?", invoiceId);
        long discount = Jdbc.count(c, "SELECT discount_cents FROM invoices WHERE id=?", invoiceId);
        long taxRate = 0;
        long tax = Jdbc.count(c, "SELECT tax_cents FROM invoices WHERE id=?", invoiceId);
        long total = subtotal - discount + tax;
        Jdbc.update(c, "UPDATE invoices SET subtotal_cents=?, total_cents=?, updated_at=? WHERE id=?",
                subtotal, total, Instant.now(), invoiceId);
    }
}
