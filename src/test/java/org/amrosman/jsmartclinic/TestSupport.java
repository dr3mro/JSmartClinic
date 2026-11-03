package org.amrosman.jsmartclinic;

import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.db.Database;
import org.amrosman.jsmartclinic.db.MigrationRunner;
import org.amrosman.jsmartclinic.domain.Encounter;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.Role;
import org.amrosman.jsmartclinic.domain.Sex;
import org.amrosman.jsmartclinic.domain.User;

import java.nio.file.Path;
import java.time.LocalDate;

/** Shared fixture helpers for the service/repository integration tests. */
public final class TestSupport {

    public static final String PASSWORD = "password1234";

    private TestSupport() {
    }

    /** Creates a migrated database in the supplied temp folder and returns a wired context. */
    public static AppContext context(Path directory) {
        Database database = new Database(directory.resolve("emr.db"));
        new MigrationRunner(database).migrate();
        return AppContext.forDatabase(database);
    }

    /** Runs first-run setup and leaves the session signed in as the administrator. */
    public static AppContext adminContext(Path directory) {
        AppContext context = context(directory);
        context.auth().createFirstAdministrator("Test Clinic", "Admin User", "admin", PASSWORD);
        return context;
    }

    public static User addUser(AppContext context, String username, Role role, String specialty) {
        return context.users().create(username, "Dr " + username, role, specialty, PASSWORD);
    }

    public static void login(AppContext context, String username) {
        context.session().login(context.auth().login(username, PASSWORD));
    }

    public static Patient patient(String firstName, String lastName) {
        return patient(firstName, lastName, LocalDate.of(1990, 1, 1));
    }

    public static Patient patient(String firstName, String lastName, LocalDate dateOfBirth) {
        return new Patient(0, null, firstName, lastName, dateOfBirth, Sex.FEMALE, "+1 555 0100",
                firstName.toLowerCase() + "@example.test", "1 Main Street", "Springfield", "NAT-1", "O+",
                "Single", "Teacher", "Emergency Contact", "+1 555 0199", "Sister", "Acme Insurance", "INS-42",
                null, "Note", "ACTIVE", null, null, null);
    }

    public static Patient createPatient(AppContext context, String firstName, String lastName) {
        return context.patients().create(patient(firstName, lastName), true);
    }

    public static Patient withFirstName(Patient p, String firstName) {
        return new Patient(p.id(), p.mrn(), firstName, p.lastName(), p.dateOfBirth(), p.sex(), p.phone(), p.email(),
                p.address(), p.city(), p.nationalId(), p.bloodType(), p.maritalStatus(), p.occupation(),
                p.emergencyName(), p.emergencyPhone(), p.emergencyRelation(), p.insuranceProvider(),
                p.insuranceNumber(), p.photo(), p.notes(), p.status(), p.deletedAt(), p.createdAt(), p.updatedAt());
    }

    public static Patient withLastName(Patient p, String lastName) {
        return new Patient(p.id(), p.mrn(), p.firstName(), lastName, p.dateOfBirth(), p.sex(), p.phone(), p.email(),
                p.address(), p.city(), p.nationalId(), p.bloodType(), p.maritalStatus(), p.occupation(),
                p.emergencyName(), p.emergencyPhone(), p.emergencyRelation(), p.insuranceProvider(),
                p.insuranceNumber(), p.photo(), p.notes(), p.status(), p.deletedAt(), p.createdAt(), p.updatedAt());
    }

    public static Patient withDateOfBirth(Patient p, LocalDate dateOfBirth) {
        return new Patient(p.id(), p.mrn(), p.firstName(), p.lastName(), dateOfBirth, p.sex(), p.phone(), p.email(),
                p.address(), p.city(), p.nationalId(), p.bloodType(), p.maritalStatus(), p.occupation(),
                p.emergencyName(), p.emergencyPhone(), p.emergencyRelation(), p.insuranceProvider(),
                p.insuranceNumber(), p.photo(), p.notes(), p.status(), p.deletedAt(), p.createdAt(), p.updatedAt());
    }

    public static Patient withContact(Patient p, String phone, String email) {
        return new Patient(p.id(), p.mrn(), p.firstName(), p.lastName(), p.dateOfBirth(), p.sex(), phone, email,
                p.address(), p.city(), p.nationalId(), p.bloodType(), p.maritalStatus(), p.occupation(),
                p.emergencyName(), p.emergencyPhone(), p.emergencyRelation(), p.insuranceProvider(),
                p.insuranceNumber(), p.photo(), p.notes(), p.status(), p.deletedAt(), p.createdAt(), p.updatedAt());
    }

    public static Encounter withContent(Encounter e, String chiefComplaint, String subjective, String objective,
                                        String assessment, String plan) {
        return new Encounter(e.id(), e.patientId(), e.providerId(), e.appointmentId(), e.specialty(),
                e.templateId(), e.encounterDate(), chiefComplaint, subjective, objective, assessment, plan,
                e.templateData(), e.status(), e.signedAt(), e.signedBy(), e.createdAt(), e.updatedAt());
    }

    public static Patient withPhoto(Patient p, byte[] photo) {
        return new Patient(p.id(), p.mrn(), p.firstName(), p.lastName(), p.dateOfBirth(), p.sex(), p.phone(), p.email(),
                p.address(), p.city(), p.nationalId(), p.bloodType(), p.maritalStatus(), p.occupation(),
                p.emergencyName(), p.emergencyPhone(), p.emergencyRelation(), p.insuranceProvider(),
                p.insuranceNumber(), photo, p.notes(), p.status(), p.deletedAt(), p.createdAt(), p.updatedAt());
    }
}
