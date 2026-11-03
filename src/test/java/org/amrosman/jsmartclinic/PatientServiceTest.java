package org.amrosman.jsmartclinic;

import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.ProblemStatus;
import org.amrosman.jsmartclinic.domain.Role;
import org.amrosman.jsmartclinic.domain.Severity;
import org.amrosman.jsmartclinic.domain.Vital;
import org.amrosman.jsmartclinic.service.AccessDeniedException;
import org.amrosman.jsmartclinic.service.PatientService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientServiceTest {

    @TempDir
    Path temp;

    @Test
    void registrationGeneratesMrnAndSearches() {
        AppContext context = TestSupport.adminContext(temp);
        Patient patient = context.patients().create(TestSupport.patient("Alice", "Anderson"), false);

        assertThat(patient.id()).isPositive();
        assertThat(patient.mrn()).startsWith("P-");
        assertThat(patient.fullName().trim()).isEqualTo("Alice Anderson");
        assertThat(patient.age()).isPositive();
        assertThat(patient.initials()).isEqualTo("AA");
        assertThat(patient.shortName()).isEqualTo("Alice Anderson");
        assertThat(context.patients().findByMrn(patient.mrn()).id()).isEqualTo(patient.id());
        assertThat(context.patients().search("alice", false, 0, 10)).extracting(Patient::id)
                .containsExactly(patient.id());
        assertThat(context.patients().search(patient.mrn(), false, 0, 10)).hasSize(1);
        assertThat(context.patients().search(patient.phone(), false, 0, 10)).hasSize(1);
        assertThat(context.patients().count("anderson", false)).isEqualTo(1);
        assertThat(context.patients().total()).isEqualTo(1);
        assertThat(context.patients().newThisWeek()).isEqualTo(1);
        assertThat(context.patients().recent(5)).hasSize(1);
        assertThat(context.patients().basic(patient.id()).id()).isEqualTo(patient.id());
        assertThat(context.patients().search("", false, 0, 0)).isEmpty();
    }

    @Test
    void registrationValidatesDemographicsAndDuplicates() {
        AppContext context = TestSupport.adminContext(temp);
        Patient base = TestSupport.patient("Dana", "Doe");
        context.patients().create(base, false);

        assertThatThrownBy(() -> context.patients().create(base, false))
                .isInstanceOf(PatientService.DuplicatePatientException.class)
                .satisfies(e -> assertThat(((PatientService.DuplicatePatientException) e).duplicates()).hasSize(1));
        assertThat(context.patients().create(base, true).id()).isPositive();
        assertThat(context.patients().duplicates(base)).hasSize(2);
        assertThat(context.patients().duplicates(TestSupport.patient("No", "Mrn"))).isEmpty();

        assertThatThrownBy(() -> context.patients().create(TestSupport.withFirstName(base, ""), true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("First name");
        assertThatThrownBy(() -> context.patients().create(
                TestSupport.withDateOfBirth(base, LocalDate.now().plusDays(1)), true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("future");
        assertThatThrownBy(() -> context.patients().create(TestSupport.withContact(base, "12", "bad-email"), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> context.patients().create(TestSupport.withContact(base, "not-a-phone",
                "ok@example.test"), true)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Phone");
        assertThatThrownBy(() -> context.patients().create(
                TestSupport.withDateOfBirth(base, null), true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Date of birth");
    }

    @Test
    void updateArchiveAndRestore() {
        AppContext context = TestSupport.adminContext(temp);
        Patient patient = TestSupport.createPatient(context, "Eve", "Evans");

        Patient updated = context.patients().update(TestSupport.withLastName(patient, "Everett"));
        assertThat(updated.lastName()).isEqualTo("Everett");
        assertThat(updated.updatedAt()).isAfterOrEqualTo(patient.updatedAt());

        context.patients().archive(patient.id());
        assertThat(context.patients().search("Everett", false, 0, 10)).isEmpty();
        assertThat(context.patients().search("Everett", true, 0, 10)).hasSize(1);
        assertThat(context.patients().total()).isZero();

        context.patients().restore(patient.id());
        assertThat(context.patients().total()).isEqualTo(1);
        assertThat(context.patients().basic(patient.id()).status()).isEqualTo("ACTIVE");
    }

    @Test
    void chartAndClinicalListsRespectPermissions() {
        AppContext context = TestSupport.adminContext(temp);
        TestSupport.addUser(context, "reception", Role.RECEPTION, null);
        TestSupport.addUser(context, "doctor", Role.DOCTOR, "General");
        Patient patient = TestSupport.createPatient(context, "Fay", "Fox");

        context.session().login(context.auth().login("reception", TestSupport.PASSWORD));
        assertThat(context.patients().basic(patient.id())).isNotNull();
        assertThatThrownBy(() -> context.patients().chart(patient.id()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> context.patients().allergies(patient.id()))
                .isInstanceOf(AccessDeniedException.class);

        TestSupport.login(context, "doctor");
        assertThat(context.patients().chart(patient.id()).id()).isEqualTo(patient.id());
        assertThat(context.patients().allergies(patient.id())).isEmpty();
        assertThat(context.patients().problems(patient.id())).isEmpty();
        assertThat(context.patients().medications(patient.id())).isEmpty();
        assertThat(context.patients().vitals(patient.id(), 10)).isEmpty();
        assertThat(context.patients().latestVital(patient.id())).isNull();
    }

    @Test
    void allergiesProblemsAndMedicationsCrud() {
        AppContext context = TestSupport.adminContext(temp);
        Patient patient = TestSupport.createPatient(context, "Gina", "Green");

        var allergy = context.patients().addAllergy(patient.id(), " Penicillin ", "Rash", Severity.MODERATE);
        assertThat(allergy.substance()).isEqualTo("Penicillin");
        assertThat(context.patients().allergySummary(patient.id())).isEqualTo("Penicillin");

        context.patients().addProblem(patient.id(), "J45", " Asthma ", LocalDate.now().minusYears(2),
                ProblemStatus.ACTIVE);
        context.patients().addProblem(patient.id(), "I10", "Hypertension", null, ProblemStatus.RESOLVED);
        assertThat(context.patients().problems(patient.id())).hasSize(2);
        assertThat(context.patients().problemSummary(patient.id())).isEqualTo("Asthma");

        var problem = context.patients().problems(patient.id()).stream()
                .filter(p -> p.description().equals("Asthma")).findFirst().orElseThrow();
        context.patients().updateProblem(problem.id(), patient.id(), "J45", "Asthma controlled",
                LocalDate.now(), ProblemStatus.RESOLVED);
        assertThat(context.patients().problemSummary(patient.id())).isEmpty();
        context.patients().removeProblem(problem.id(), patient.id());
        assertThat(context.patients().problems(patient.id())).hasSize(1);

        context.patients().addMedication(patient.id(), " Salbutamol ", "100 mcg", "Inhalation", "PRN",
                LocalDate.now(), null, true);
        var medication = context.patients().medications(patient.id()).get(0);
        assertThat(medication.name()).isEqualTo("Salbutamol");
        context.patients().updateMedication(medication.id(), patient.id(), "Salbutamol", "200 mcg",
                "Inhalation", "BID", LocalDate.now(), null, true);
        assertThat(context.patients().medications(patient.id()).get(0).dose()).isEqualTo("200 mcg");
        context.patients().deactivateMedication(medication.id(), patient.id());
        assertThat(context.patients().medications(patient.id()).get(0).active()).isFalse();

        context.patients().removeAllergy(allergy.id(), patient.id());
        assertThat(context.patients().allergies(patient.id())).isEmpty();
        assertThat(context.patients().allergySummary(patient.id())).isEqualTo("No known allergies");

        assertThatThrownBy(() -> context.patients().addAllergy(patient.id(), " ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> context.patients().addProblem(patient.id(), null, " ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> context.patients().addMedication(patient.id(), "", null, null, null,
                null, null, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> context.patients().updateMedication(medication.id(), patient.id(), " ",
                null, null, null, null, null, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> context.patients().updateProblem(problem.id(), patient.id(), null, " ",
                null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void vitalsAndPhotoHandling() throws Exception {
        AppContext context = TestSupport.adminContext(temp);
        Patient patient = TestSupport.createPatient(context, "Hana", "Hill");

        Vital vital = new Vital(0, patient.id(), null, Instant.now(), 120, 80, 72, 16, 36.6, 98,
                70.0, 175.0, 90.0, null, null);
        long id = context.encounters().addVital(vital);
        Vital saved = context.patients().latestVital(patient.id());
        assertThat(saved.id()).isEqualTo(id);
        assertThat(saved.bmi()).isEqualTo(22.9);
        assertThat(saved.bloodPressure()).isEqualTo("120/80");
        assertThat(context.patients().vitals(patient.id(), 10)).hasSize(1);

        Path image = temp.resolve("photo.png");
        ImageIO.write(new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB), "png", image.toFile());
        byte[] resized = context.patients().photo(image.toFile());
        assertThat(resized).isNotEmpty();
        BufferedImage read = ImageIO.read(new java.io.ByteArrayInputStream(resized));
        assertThat(Math.max(read.getWidth(), read.getHeight())).isLessThanOrEqualTo(400);
        assertThat(context.patients().update(TestSupport.withPhoto(patient, resized)).photo()).isEqualTo(resized);

        Path invalid = Files.writeString(temp.resolve("not-image.txt"), "hello");
        assertThatThrownBy(() -> context.patients().photo(invalid.toFile()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
