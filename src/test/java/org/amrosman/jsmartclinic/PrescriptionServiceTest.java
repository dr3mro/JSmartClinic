package org.amrosman.jsmartclinic;

import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.Prescription;
import org.amrosman.jsmartclinic.domain.PrescriptionItem;
import org.amrosman.jsmartclinic.domain.Role;
import org.amrosman.jsmartclinic.domain.Severity;
import org.amrosman.jsmartclinic.service.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrescriptionServiceTest {

    @TempDir
    Path temp;

    private AppContext context;
    private Patient patient;

    @BeforeEach
    void setUp() {
        context = TestSupport.adminContext(temp);
        TestSupport.addUser(context, "doctor", Role.DOCTOR, "General");
        patient = TestSupport.createPatient(context, "Quinn", "Quick");
        TestSupport.login(context, "doctor");
    }

    @Test
    void createRenewAndReadPrescriptions() {
        List<PrescriptionItem> items = List.of(item("Amoxicillin", "500 mg"), item("Paracetamol", "500 mg"));
        long id = context.prescriptions().create(patient.id(), null, "Take with food", items);

        Prescription prescription = context.prescriptions().byId(id);
        assertThat(prescription.prescriberId()).isEqualTo(context.session().userId());
        assertThat(prescription.notes()).isEqualTo("Take with food");
        assertThat(context.prescriptions().items(id)).hasSize(2)
                .extracting(PrescriptionItem::drugName)
                .containsExactly("Amoxicillin", "Paracetamol");
        assertThat(context.prescriptions().forPatient(patient.id())).hasSize(1);
        assertThat(context.prescriptions().recent(10)).hasSize(1);

        long renewed = context.prescriptions().renew(id);
        Prescription renewal = context.prescriptions().byId(renewed);
        assertThat(renewal.patientId()).isEqualTo(patient.id());
        assertThat(renewal.notes()).contains("Take with food").contains("Renewal of prescription #" + id);
        assertThat(context.prescriptions().items(renewed)).hasSize(2);
        assertThat(context.prescriptions().forPatient(patient.id())).hasSize(2);
    }

    @Test
    void validationAndPermissionsAreEnforced() {
        assertThatThrownBy(() -> context.prescriptions().create(patient.id(), null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least one");
        assertThatThrownBy(() -> context.prescriptions().create(patient.id(), null, null,
                List.of(item(" ", "10 mg")))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Drug name");
        assertThatThrownBy(() -> context.prescriptions().renew(999))
                .isInstanceOf(IllegalArgumentException.class);

        context.auth().logout();
        TestSupport.login(context, "admin");
        assertThatThrownBy(() -> context.prescriptions().create(patient.id(), null, null,
                List.of(item("Aspirin", "75 mg")))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> context.prescriptions().allergyWarnings(patient.id(),
                List.of(item("Aspirin", "75 mg")))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void allergyCrossCheckFlagsSubstringMatches() {
        context.patients().addAllergy(patient.id(), "Penicillin", "Rash", Severity.SEVERE);
        context.patients().addAllergy(patient.id(), "Sulfa", "Hives", Severity.MODERATE);

        List<PrescriptionItem> safe = List.of(item("Paracetamol", "500 mg"));
        assertThat(context.prescriptions().allergyWarnings(patient.id(), safe)).isEmpty();
        assertThat(context.prescriptions().allergyWarningText(patient.id(), safe)).isNull();

        List<PrescriptionItem> risky = List.of(item("Penicillin V", "250 mg"), item("Sulfasalazine", "500 mg"));
        assertThat(context.prescriptions().allergyWarnings(patient.id(), risky)).hasSize(2)
                .allSatisfy(warning -> assertThat(warning).contains("allergy"));
        assertThat(context.prescriptions().allergyWarningText(patient.id(), risky))
                .contains("Penicillin V").contains("Sulfasalazine");

        assertThat(context.prescriptions().allergyWarnings(patient.id(),
                List.of(item(null, "1 mg")))).isEmpty();
    }

    private static PrescriptionItem item(String drug, String strength) {
        return new PrescriptionItem(0, 0, drug, strength, "Tablet", "1 tablet", "TID", "5 days", "15",
                "After meals");
    }
}
