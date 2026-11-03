package org.amrosman.jsmartclinic;

import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.AppointmentStatus;
import org.amrosman.jsmartclinic.domain.Encounter;
import org.amrosman.jsmartclinic.domain.EncounterDiagnosis;
import org.amrosman.jsmartclinic.domain.EncounterStatus;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.Role;
import org.amrosman.jsmartclinic.domain.Vital;
import org.amrosman.jsmartclinic.service.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncounterServiceTest {

    @TempDir
    Path temp;

    private AppContext context;
    private Patient patient;

    @BeforeEach
    void setUp() {
        context = TestSupport.adminContext(temp);
        TestSupport.addUser(context, "doctor", Role.DOCTOR, "Cardiology");
        patient = TestSupport.createPatient(context, "Nina", "North");
        TestSupport.login(context, "doctor");
    }

    @Test
    void startReusesDraftAndPicksSpecialtyTemplate() {
        Encounter first = context.encounters().start(patient.id(), null, null, null);
        assertThat(first.status()).isEqualTo(EncounterStatus.DRAFT);
        assertThat(first.specialty()).isEqualTo("Cardiology");
        assertThat(first.templateId()).isEqualTo("cardiology");
        assertThat(first.providerId()).isEqualTo(context.session().userId());
        assertThat(first.templateData()).isEqualTo("{}");

        Encounter second = context.encounters().start(patient.id(), null, "General", "general");
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(context.encounters().drafts()).isEqualTo(1);
        assertThat(context.encounters().forPatient(patient.id())).hasSize(1);
        assertThat(context.encounters().recent(10)).hasSize(1);
        assertThat(context.encounters().byId(first.id()).id()).isEqualTo(first.id());
        assertThat(context.encounters().byId(999)).isNull();
    }

    @Test
    void saveSignAndAmendPreserveHistory() {
        Encounter draft = context.encounters().start(patient.id(), null, null, null);
        Encounter saved = context.encounters().save(TestSupport.withContent(draft, "Headache",
                "Two days of pain", "BP normal", "Migraine", "Rest and analgesia"));
        assertThat(saved.chiefComplaint()).isEqualTo("Headache");

        Encounter signed = context.encounters().sign(saved.id());
        assertThat(signed.status()).isEqualTo(EncounterStatus.SIGNED);
        assertThat(signed.signedAt()).isNotNull();
        assertThat(signed.signedBy()).isEqualTo(context.session().userId());
        assertThatThrownBy(() -> context.encounters().sign(signed.id()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already signed");
        assertThatThrownBy(() -> context.encounters().save(TestSupport.withContent(signed, "Changed",
                null, null, null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("read-only");

        Encounter amendment = context.encounters().amend(signed.id());
        assertThat(amendment.id()).isNotEqualTo(signed.id());
        assertThat(amendment.status()).isEqualTo(EncounterStatus.DRAFT);
        assertThat(amendment.chiefComplaint()).isEqualTo("Headache");
        assertThat(amendment.signed()).isFalse();
        assertThat(amendment.signedAt()).isNull();
        assertThat(amendment.amendsEncounterId()).isEqualTo(signed.id());
        assertThat(context.encounters().byId(signed.id()).status()).isEqualTo(EncounterStatus.SIGNED);

        Encounter edited = context.encounters().save(TestSupport.withContent(amendment, "Headache",
                "Addendum details", "BP normal", "Migraine", "Follow up"));
        assertThat(edited.subjective()).isEqualTo("Addendum details");
        Encounter reSigned = context.encounters().sign(amendment.id());
        assertThat(reSigned.status()).isEqualTo(EncounterStatus.SIGNED);
        assertThat(reSigned.amendsEncounterId()).isEqualTo(signed.id());

        Encounter originalAfterSign = context.encounters().byId(signed.id());
        assertThat(originalAfterSign.status()).isEqualTo(EncounterStatus.AMENDED);
        assertThat(originalAfterSign.chiefComplaint()).isEqualTo("Headache");
        assertThat(originalAfterSign.subjective()).isEqualTo("Two days of pain");
        assertThatThrownBy(() -> context.encounters().save(TestSupport.withContent(reSigned, "Changed",
                null, null, null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("read-only");

        Encounter draft2 = context.encounters().start(TestSupport.createPatient(context, "Owen", "Oak").id(),
                null, null, null);
        assertThatThrownBy(() -> context.encounters().amend(draft2.id()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Only signed");
        assertThatThrownBy(() -> context.encounters().amend(999))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> context.encounters().save(TestSupport.withContent(draft2, "x", null, null,
                null, null))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyOrWhitespaceEncountersCannotBeSigned() {
        Encounter draft = context.encounters().start(patient.id(), null, null, null);
        assertThatThrownBy(() -> context.encounters().sign(draft.id()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("clinical content");

        Encounter whitespace = context.encounters().save(TestSupport.withContent(draft,
                "   ", " ", "\t", "", ""));
        assertThatThrownBy(() -> context.encounters().sign(whitespace.id()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("clinical content");

        Encounter assessmentOnly = context.encounters().save(TestSupport.withContent(whitespace,
                "", "", "", "Migraine", ""));
        Encounter signed = context.encounters().sign(assessmentOnly.id());
        assertThat(signed.status()).isEqualTo(EncounterStatus.SIGNED);
    }

    @Test
    void deleteDraftOnlyRemovesUnsignedNotes() {
        Encounter draft = context.encounters().start(patient.id(), null, null, null);
        Encounter saved = context.encounters().save(TestSupport.withContent(draft, "Cough", "", "", "Cold", ""));
        context.encounters().sign(saved.id());
        context.encounters().deleteDraft(saved.id());
        assertThat(context.encounters().byId(saved.id())).isNotNull();

        Patient other = TestSupport.createPatient(context, "Pam", "Pine");
        Encounter otherDraft = context.encounters().start(other.id(), null, null, null);
        context.encounters().deleteDraft(otherDraft.id());
        assertThat(context.encounters().byId(otherDraft.id())).isNull();
        assertThat(context.encounters().drafts()).isZero();
    }

    @Test
    void diagnosesCanOnlyBeChangedOnEditableEncounters() {
        Encounter draft = context.encounters().start(patient.id(), null, null, null);
        context.encounters().addDiagnosis(draft.id(), patient.id(), "G43.9", "Migraine", false);
        context.encounters().addDiagnosis(draft.id(), patient.id(), "R51", "Headache", true);
        var diagnoses = context.encounters().diagnoses(draft.id());
        assertThat(diagnoses).hasSize(2);
        assertThat(diagnoses.get(0).primary()).isTrue();
        assertThat(diagnoses.get(0).description()).isEqualTo("Headache");

        EncounterDiagnosis secondary = diagnoses.stream().filter(d -> !d.primary()).findFirst().orElseThrow();
        Patient other = TestSupport.createPatient(context, "Otto", "Other");
        context.encounters().removeDiagnosis(secondary.id(), other.id());
        assertThat(context.encounters().diagnoses(draft.id())).hasSize(1);
        context.session().login(context.users().byId(1));
        assertThat(context.audit().recent(10))
                .anyMatch(e -> e.action() == org.amrosman.jsmartclinic.domain.AuditAction.DELETE
                        && e.entityId() != null && e.entityId() == secondary.id()
                        && e.patientId() != null && e.patientId() == patient.id());
        TestSupport.login(context, "doctor");

        context.encounters().save(TestSupport.withContent(draft, "Headache", "", "", "Migraine", ""));
        context.encounters().sign(draft.id());
        assertThatThrownBy(() -> context.encounters().addDiagnosis(draft.id(), patient.id(), "X", "Nope", false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not editable");
        assertThatThrownBy(() -> context.encounters().addDiagnosis(999, patient.id(), "X", "Nope", false))
                .isInstanceOf(IllegalStateException.class);
        EncounterDiagnosis remaining = context.encounters().diagnoses(draft.id()).get(0);
        assertThatThrownBy(() -> context.encounters().removeDiagnosis(remaining.id(), patient.id()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("read-only");
    }

    @Test
    void vitalsAreValidatedAndLinkedToEncounter() {
        Encounter draft = context.encounters().start(patient.id(), null, null, null);
        Vital vital = new Vital(0, patient.id(), draft.id(), Instant.now(), 118, 76, 68, 14, 36.7, 99,
                64.0, 168.0, 88.0, null, null);
        long id = context.encounters().addVital(vital);

        assertThat(context.encounters().vitalsForEncounter(draft.id())).hasSize(1);
        assertThat(context.encounters().vitals(patient.id(), 5)).hasSize(1);
        assertThat(context.encounters().vitalsForEncounter(draft.id()).get(0).id()).isEqualTo(id);

        assertThatThrownBy(() -> context.encounters().addVital(new Vital(0, patient.id(), null, Instant.now(),
                20, 80, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Systolic");
        assertThatThrownBy(() -> context.encounters().addVital(new Vital(0, patient.id(), null, Instant.now(),
                120, 500, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Diastolic");
        assertThatThrownBy(() -> context.encounters().addVital(new Vital(0, patient.id(), null, Instant.now(),
                120, 80, null, null, null, 10, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SpO2");

        Patient other = TestSupport.createPatient(context, "Vera", "Vital");
        context.encounters().removeVital(id, other.id());
        assertThat(context.encounters().vitalsForEncounter(draft.id())).isEmpty();
        context.session().login(context.users().byId(1));
        assertThat(context.audit().recent(10))
                .anyMatch(e -> e.action() == org.amrosman.jsmartclinic.domain.AuditAction.DELETE
                        && e.entityId() != null && e.entityId() == id
                        && e.patientId() != null && e.patientId() == patient.id());
        TestSupport.login(context, "doctor");

        long secondId = context.encounters().addVital(new Vital(0, patient.id(), draft.id(), Instant.now(),
                120, 80, 70, 16, 36.8, 98, 70.0, 170.0, null, null, null));
        context.encounters().save(TestSupport.withContent(draft, "Vitals review", "", "", "", ""));
        context.encounters().sign(draft.id());
        assertThatThrownBy(() -> context.encounters().removeVital(secondId, patient.id()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("read-only");
        assertThatThrownBy(() -> context.encounters().addVital(new Vital(0, patient.id(), draft.id(), Instant.now(),
                120, 80, 70, 16, 36.8, 98, 70.0, 170.0, null, null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("read-only");
    }

    @Test
    void encountersRequireDoctorPermissions() {
        context.session().login(context.users().byId(1));
        TestSupport.addUser(context, "nurse", Role.NURSE, null);
        TestSupport.addUser(context, "admin2", Role.ADMIN, null);
        context.auth().logout();
        TestSupport.login(context, "nurse");

        assertThat(context.encounters().forPatient(patient.id())).isEmpty();
        assertThatThrownBy(() -> context.encounters().start(patient.id(), null, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> context.encounters().sign(1))
                .isInstanceOf(AccessDeniedException.class);

        context.auth().logout();
        TestSupport.login(context, "admin2");
        assertThatThrownBy(() -> context.encounters().start(patient.id(), null, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> context.encounters().addDiagnosis(1, patient.id(), "A", "A", false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void onlyTheDraftProviderCanEditAndAnotherDoctorCanTakeItOver() {
        Encounter draft = context.encounters().start(patient.id(), null, null, null);

        context.session().login(context.users().byId(1));
        TestSupport.addUser(context, "doctor2", Role.DOCTOR, "General");
        TestSupport.login(context, "doctor2");

        Encounter changed = TestSupport.withContent(draft, "Changed", "By another doctor", "", "Migraine", "");
        assertThatThrownBy(() -> context.encounters().save(changed))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("provider");
        assertThatThrownBy(() -> context.encounters().sign(draft.id()))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("provider");
        assertThatThrownBy(() -> context.encounters().deleteDraft(draft.id()))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("provider");

        Encounter taken = context.encounters().takeOverDraft(draft.id());
        assertThat(taken.providerId()).isEqualTo(context.session().userId());
        Encounter saved = context.encounters().save(TestSupport.withContent(taken, "Fever",
                "Taken over", "", "Viral illness", ""));
        assertThat(saved.providerId()).isEqualTo(context.session().userId());
        assertThat(context.encounters().sign(saved.id()).status()).isEqualTo(EncounterStatus.SIGNED);
    }

    @Test
    void startingFromAppointmentLinksVisit() {
        var doctor = context.users().byId(context.session().userId());
        Instant start = LocalDate.now().atTime(15, 0).atZone(ZoneId.systemDefault()).toInstant();
        long appointmentId = context.scheduling().create(patient.id(), doctor.id(), start,
                start.plusSeconds(1800), "Review", null);
        context.scheduling().changeStatus(appointmentId, AppointmentStatus.CHECKED_IN);

        Encounter encounter = context.encounters().start(patient.id(), appointmentId, null, null);
        assertThat(encounter.appointmentId()).isEqualTo(appointmentId);
    }
}
