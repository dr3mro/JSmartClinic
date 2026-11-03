package org.amrosman.jsmartclinic;

import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.BillableService;
import org.amrosman.jsmartclinic.domain.Invoice;
import org.amrosman.jsmartclinic.domain.InvoiceItem;
import org.amrosman.jsmartclinic.domain.InvoiceStatus;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.PaymentMethod;
import org.amrosman.jsmartclinic.domain.Role;
import org.amrosman.jsmartclinic.service.AccessDeniedException;
import org.amrosman.jsmartclinic.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingServiceTest {

    @TempDir
    Path temp;

    private AppContext context;
    private Patient patient;
    private BillableService consultation;

    @BeforeEach
    void setUp() {
        context = TestSupport.adminContext(temp);
        TestSupport.addUser(context, "doctor", Role.DOCTOR, "General");
        TestSupport.addUser(context, "reception", Role.RECEPTION, null);
        patient = TestSupport.createPatient(context, "Sam", "Stone");
        context.settings().saveProfile(new SettingsService.ClinicProfile("Test Clinic", "1 Road", "555",
                "clinic@example.test", "", "$", 10, "light", "en", true));
        consultation = context.billing().services(true).stream()
                .filter(s -> "CONS".equals(s.code())).findFirst().orElseThrow();
    }

    @Test
    void serviceCatalogueCrud() {
        assertThat(context.billing().services(true)).isNotEmpty();
        assertThat(context.billing().services(false)).isNotEmpty();
        assertThat(context.billing().consultationItem().description()).isEqualTo(consultation.name());

        context.billing().saveService(0, "NEW", "New service", 12345, true);
        BillableService created = context.billing().services(false).stream()
                .filter(s -> "NEW".equals(s.code())).findFirst().orElseThrow();
        assertThat(created.priceCents()).isEqualTo(12345);
        context.billing().saveService(created.id(), "NEW", "Renamed service", 22222, false);
        BillableService updated = context.billing().services(false).stream()
                .filter(s -> s.id() == created.id()).findFirst().orElseThrow();
        assertThat(updated.name()).isEqualTo("Renamed service");
        assertThat(updated.active()).isFalse();
        assertThat(context.billing().services(true)).noneMatch(s -> s.id() == created.id());

        assertThatThrownBy(() -> context.billing().saveService(0, "X", " ", 1, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invoicesApplyDiscountAndTax() {
        long invoiceId = context.billing().createInvoice(patient.id(), null,
                List.of(new InvoiceItem(0, 0, consultation.id(), consultation.name(), 2, 5000)), 1000);

        Invoice invoice = context.billing().invoice(invoiceId);
        assertThat(invoice.invoiceNo()).startsWith("INV-" + LocalDate.now().getYear() + "-");
        assertThat(invoice.subtotalCents()).isEqualTo(10_000);
        assertThat(invoice.discountCents()).isEqualTo(1_000);
        assertThat(invoice.taxCents()).isEqualTo(900);
        assertThat(invoice.totalCents()).isEqualTo(9_900);
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(context.billing().items(invoiceId)).hasSize(1)
                .allSatisfy(item -> assertThat(item.lineTotalCents()).isEqualTo(10_000));
        assertThat(context.billing().invoicesForPatient(patient.id())).hasSize(1);
        assertThat(context.billing().recentInvoices(5)).hasSize(1);
        assertThat(context.billing().outstanding()).hasSize(1);
        assertThat(context.billing().outstandingCount()).isEqualTo(1);
        assertThat(context.billing().balance(patient.id())).isEqualTo(9_900);

        assertThatThrownBy(() -> context.billing().createInvoice(patient.id(), null, List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void paymentsUpdateStatusAndBalance() throws Exception {
        long invoiceId = context.billing().createInvoice(patient.id(), null,
                List.of(new InvoiceItem(0, 0, consultation.id(), consultation.name(), 2, 5000)), 1000);

        context.billing().recordPayment(invoiceId, 4_000, PaymentMethod.CARD, "AUTH-1");
        Invoice partial = context.billing().invoice(invoiceId);
        assertThat(partial.status()).isEqualTo(InvoiceStatus.PARTIAL);
        assertThat(context.billing().paidTotal(invoiceId)).isEqualTo(4_000);
        assertThat(context.billing().balance(patient.id())).isEqualTo(5_900);
        assertThat(context.billing().payments(invoiceId)).hasSize(1)
                .allSatisfy(payment -> assertThat(payment.method()).isEqualTo(PaymentMethod.CARD));

        context.billing().recordPayment(invoiceId, 5_900, PaymentMethod.CASH, null);
        assertThat(context.billing().invoice(invoiceId).status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(context.billing().balance(patient.id())).isZero();
        assertThat(context.billing().outstandingCount()).isZero();
        assertThat(context.billing().revenue(LocalDate.now(), LocalDate.now())).isEqualTo(9_900);
        assertThat(context.billing().todayRevenue()).isEqualTo(9_900);

        assertThatThrownBy(() -> context.billing().recordPayment(invoiceId, 1, PaymentMethod.CASH, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exceeds");
        assertThatThrownBy(() -> context.billing().recordPayment(invoiceId, 0, PaymentMethod.CASH, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("greater than zero");
        assertThatThrownBy(() -> context.billing().recordPayment(999, 100, PaymentMethod.CASH, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not payable");

        Path receipt = context.billing().receiptPdf(invoiceId);
        assertThat(receipt).exists();
        assertThat(Files.readAllBytes(receipt)).startsWith("%PDF".getBytes());
        assertThatThrownBy(() -> context.billing().receiptPdf(999))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void voidingAndAddingItems() {
        long invoiceId = context.billing().createInvoice(patient.id(), null,
                List.of(new InvoiceItem(0, 0, consultation.id(), consultation.name(), 1, 1000)), 0);
        context.billing().addItem(invoiceId, new InvoiceItem(0, 0, consultation.id(), "Extra", 2, 500));
        Invoice updated = context.billing().invoice(invoiceId);
        assertThat(context.billing().items(invoiceId)).hasSize(2);
        assertThat(updated.subtotalCents()).isEqualTo(2_000);
        assertThat(updated.totalCents()).isGreaterThanOrEqualTo(updated.subtotalCents());

        context.billing().voidInvoice(invoiceId);
        assertThat(context.billing().invoice(invoiceId).status()).isEqualTo(InvoiceStatus.VOID);
        assertThatThrownBy(() -> context.billing().recordPayment(invoiceId, 100, PaymentMethod.CASH, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void billingRequiresBillingPermission() {
        context.session().login(context.users().byId(1));
        TestSupport.login(context, "doctor");
        assertThatThrownBy(() -> context.billing().services(true)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> context.billing().createInvoice(patient.id(), null,
                List.of(new InvoiceItem(0, 0, consultation.id(), consultation.name(), 1, 5000)), 0))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> context.billing().balance(patient.id())).isInstanceOf(AccessDeniedException.class);

        context.auth().logout();
        TestSupport.login(context, "reception");
        long invoiceId = context.billing().createInvoice(patient.id(), null,
                List.of(new InvoiceItem(0, 0, consultation.id(), consultation.name(), 1, 5000)), 0);
        assertThat(context.billing().invoice(invoiceId)).isNotNull();
    }
}
