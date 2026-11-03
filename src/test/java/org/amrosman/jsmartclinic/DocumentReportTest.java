package org.amrosman.jsmartclinic;

import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.BillableService;
import org.amrosman.jsmartclinic.domain.Encounter;
import org.amrosman.jsmartclinic.domain.InvoiceItem;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.PatientDocument;
import org.amrosman.jsmartclinic.domain.PaymentMethod;
import org.amrosman.jsmartclinic.domain.PrescriptionItem;
import org.amrosman.jsmartclinic.domain.Role;
import org.amrosman.jsmartclinic.domain.User;
import org.amrosman.jsmartclinic.util.Sha256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentReportTest {

    @TempDir
    Path temp;

    private AppContext context;
    private Patient patient;
    private User doctor;

    @BeforeEach
    void setUp() {
        context = TestSupport.adminContext(temp);
        doctor = TestSupport.addUser(context, "doctor", Role.DOCTOR, "General");
        patient = TestSupport.createPatient(context, "Tara", "Turner");
        TestSupport.login(context, "doctor");
    }

    @Test
    void documentsAreHashedAndDeduplicated() throws Exception {
        Path file = Files.writeString(temp.resolve("note.txt"), "hello document");
        PatientDocument first = context.documents().attach(patient.id(), null, file.toFile(), null);
        assertThat(first.title()).isEqualTo("note.txt");
        assertThat(first.sha256()).isEqualTo(Sha256.hex("hello document".getBytes()));
        assertThat(first.uploadedBy()).isEqualTo(context.session().userId());
        assertThat(context.documents().forPatient(patient.id())).hasSize(1);

        Path stored = context.documents().fileFor(first);
        assertThat(stored).exists();
        assertThat(Files.readString(stored)).isEqualTo("hello document");

        PatientDocument second = context.documents().attach(patient.id(), null, file.toFile(), "  Lab result  ");
        assertThat(second.title()).isEqualTo("Lab result");
        assertThat(second.sha256()).isEqualTo(first.sha256());
        assertThat(context.documents().forPatient(patient.id())).hasSize(2);
        assertThat(context.documents().fileFor(second)).isEqualTo(stored);

        assertThat(context.documents().exportsDir()).isDirectory();
        assertThat(context.documents().uniqueExport("report", ".csv").toString()).endsWith(".csv");

        context.documents().delete(first);
        assertThat(context.documents().forPatient(patient.id())).hasSize(1);
        assertThatThrownBy(() -> context.documents().attach(patient.id(), null,
                temp.resolve("missing.txt").toFile(), "Nope"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Unable to attach");
    }

    @Test
    void reportsAggregateClinicalAndFinancialData() {
        Instant start = LocalDate.now().atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant();
        long appointmentId = context.scheduling().create(patient.id(), doctor.id(), start,
                start.plusSeconds(1800), "Review", null);
        Encounter encounter = context.encounters().start(patient.id(), appointmentId, null, null);
        context.encounters().addDiagnosis(encounter.id(), patient.id(), "G43.9", "Migraine", true);

        context.session().login(context.users().byId(1));
        BillableService service = context.billing().services(true).get(0);
        long invoiceId = context.billing().createInvoice(patient.id(), encounter.id(),
                List.of(new InvoiceItem(0, 0, service.id(), service.name(), 2, service.priceCents())), 0);
        context.billing().recordPayment(invoiceId, context.billing().invoice(invoiceId).totalCents(),
                PaymentMethod.CASH, null);

        LocalDate today = LocalDate.now();
        assertThat(context.reports().appointmentsByProvider(today, today)).hasSize(1)
                .allSatisfy(row -> assertThat(row.count()).isEqualTo(1));
        assertThat(context.reports().topDiagnoses(today, today, 5)).hasSize(1)
                .allSatisfy(row -> assertThat(row.label()).isEqualTo("Migraine"));
        assertThat(context.reports().demographics()).hasSize(1)
                .allSatisfy(row -> assertThat(row.label()).isEqualTo("FEMALE"));
        assertThat(context.reports().ageGroups()).isNotEmpty();
        assertThat(context.reports().appointmentsByStatus(today, today)).hasSize(1)
                .allSatisfy(row -> assertThat(row.label()).isEqualTo("SCHEDULED"));
        assertThat(context.reports().revenueByMonth(today, today)).isNotEmpty();
        assertThat(context.reports().revenue(today, today)).isEqualTo(
                context.billing().invoice(invoiceId).totalCents());
    }

    @Test
    void reportExportsProduceCsvAndPdf() throws Exception {
        Path csv = context.reports().exportCsv("diagnoses", List.of("Diagnosis", "Count"),
                List.of(List.of("Migraine", 2), List.of("Asthma, mild", 1)));
        assertThat(csv).exists();
        String content = Files.readString(csv);
        assertThat(content).startsWith("Diagnosis,Count\n").contains("Migraine,2")
                .contains("\"Asthma, mild\",1");

        Path pdf = context.reports().exportPdf("Report", List.of("A", "B"),
                List.of(List.of("x", "y")));
        assertThat(pdf).exists();
        assertThat(Files.readAllBytes(pdf)).startsWith("%PDF".getBytes());
    }

    @Test
    void prescriptionPdfRenders() {
        List<PrescriptionItem> items = List.of(new PrescriptionItem(0, 0, "Amoxicillin", "500 mg",
                "Capsule", "1 capsule", "TID", "5 days", "15", "After food"));
        long id = context.prescriptions().create(patient.id(), null, "Notes", items);
        var prescription = context.prescriptions().byId(id);

        Path pdf = context.pdf().prescription(patient, doctor, prescription, context.prescriptions().items(id));

        assertThat(pdf).exists();
        assertThat(pdf.getFileName().toString()).startsWith("prescription-").endsWith(".pdf");
    }

    @Test
    void documentsRequireDocumentPermission() {
        TestSupport.login(context, "admin");
        TestSupport.addUser(context, "billing", Role.BILLING, null);
        context.auth().logout();
        TestSupport.login(context, "billing");

        assertThatThrownBy(() -> context.documents().forPatient(patient.id()))
                .isInstanceOf(org.amrosman.jsmartclinic.service.AccessDeniedException.class);
        assertThat(context.reports().demographics()).isNotNull();
    }
}
