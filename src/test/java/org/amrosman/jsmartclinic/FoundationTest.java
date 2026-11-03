package org.amrosman.jsmartclinic;

import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.db.Database;
import org.amrosman.jsmartclinic.db.MigrationRunner;
import org.amrosman.jsmartclinic.domain.AuditAction;
import org.amrosman.jsmartclinic.domain.Encounter;
import org.amrosman.jsmartclinic.domain.Role;
import org.amrosman.jsmartclinic.domain.Vital;
import org.amrosman.jsmartclinic.repository.Jdbc;
import org.amrosman.jsmartclinic.service.Icd10Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FoundationTest {

    @TempDir
    Path temp;

    @Test
    void migrationsCreateSchemaSeedAndAreIdempotent() throws Exception {
        Database database = new Database(temp.resolve("emr.db"));
        new MigrationRunner(database).migrate();
        new MigrationRunner(database).migrate();

        try (Connection connection = database.getConnection()) {
            assertThat(Jdbc.count(connection, "SELECT COUNT(*) FROM schema_version")).isEqualTo(7);
            assertThat(Jdbc.count(connection, "SELECT COUNT(*) FROM lab_tests")).isEqualTo(17);
            assertThat(Jdbc.count(connection, "SELECT COUNT(*) FROM services")).isEqualTo(8);
            assertThat(Jdbc.count(connection, "SELECT COUNT(*) FROM users")).isZero();
            assertThat(Jdbc.count(connection, "SELECT COUNT(*) FROM settings WHERE key='clinic_name'")).isEqualTo(1);

            try (Statement statement = connection.createStatement()) {
                assertThat(statement.executeQuery("PRAGMA foreign_keys").getInt(1)).isEqualTo(1);
                assertThat(statement.executeQuery("PRAGMA journal_mode").getString(1)).isEqualToIgnoringCase("wal");
            }
        }
    }

    @Test
    void auditLogIsAppendOnly() {
        AppContext context = TestSupport.adminContext(temp);
        context.audit().log(AuditAction.VIEW, "patient", 1L, 1L, "test");

        assertThatThrownBy(() -> {
            try (Connection connection = context.database().getConnection()) {
                Jdbc.update(connection, "UPDATE audit_log SET details='changed'");
            }
        }).isInstanceOf(SQLException.class).hasMessageContaining("append-only");

        assertThatThrownBy(() -> {
            try (Connection connection = context.database().getConnection()) {
                Jdbc.update(connection, "DELETE FROM audit_log");
            }
        }).isInstanceOf(SQLException.class).hasMessageContaining("append-only");
    }

    @Test
    void signedEncounterRowsCannotBeUpdatedDirectly() {
        AppContext context = TestSupport.adminContext(temp);
        TestSupport.addUser(context, "doctor", Role.DOCTOR, "General");
        TestSupport.login(context, "doctor");
        var patient = TestSupport.createPatient(context, "Jane", "Doe");
        Encounter encounter = context.encounters().start(patient.id(), null, null, null);
        Encounter edited = new Encounter(encounter.id(), encounter.patientId(), encounter.providerId(),
                encounter.appointmentId(), null, encounter.specialty(), encounter.templateId(),
                encounter.encounterDate(),
                "Headache", "", "", "Migraine", "", "{}", encounter.status(), null, null, null, null);
        context.encounters().save(edited);
        context.encounters().addDiagnosis(encounter.id(), patient.id(), "R51", "Headache", true);
        long vitalId = context.encounters().addVital(new Vital(0, patient.id(), encounter.id(), Instant.now(),
                120, 80, 70, 16, 36.8, 98, 70.0, 170.0, null, null, null));
        context.encounters().sign(encounter.id());

        assertThatThrownBy(() -> {
            try (Connection connection = context.database().getConnection()) {
                Jdbc.update(connection, "UPDATE encounters SET assessment='tampered' WHERE id=?", encounter.id());
            }
        }).isInstanceOf(SQLException.class).hasMessageContaining("immutable");

        assertThatThrownBy(() -> {
            try (Connection connection = context.database().getConnection()) {
                Jdbc.update(connection, "UPDATE encounters SET status='AMENDED', assessment='tampered' WHERE id=?",
                        encounter.id());
            }
        }).isInstanceOf(SQLException.class).hasMessageContaining("immutable");

        assertThatThrownBy(() -> {
            try (Connection connection = context.database().getConnection()) {
                Jdbc.update(connection, "DELETE FROM encounters WHERE id=?", encounter.id());
            }
        }).isInstanceOf(SQLException.class).hasMessageContaining("Signed encounters cannot be deleted");

        assertThatThrownBy(() -> {
            try (Connection connection = context.database().getConnection()) {
                Jdbc.update(connection, "DELETE FROM encounter_diagnoses WHERE encounter_id=?", encounter.id());
            }
        }).isInstanceOf(SQLException.class).hasMessageContaining("immutable");

        assertThatThrownBy(() -> {
            try (Connection connection = context.database().getConnection()) {
                Jdbc.update(connection, "DELETE FROM vitals WHERE id=?", vitalId);
            }
        }).isInstanceOf(SQLException.class).hasMessageContaining("immutable");
    }

    @Test
    void bundledCataloguesLoad() {
        AppContext context = TestSupport.adminContext(temp);

        List<Icd10Service.IcdCode> matches = context.icd10().search("diabetes", 10);
        assertThat(matches).isNotEmpty().allSatisfy(code -> assertThat(code.toString()).contains("—"));
        assertThat(context.icd10().search("", 5)).hasSize(5);

        assertThat(context.drugs().all()).isNotEmpty();
        assertThat(context.drugs().search("amox", 10)).isNotEmpty();
        assertThat(context.drugs().search(null, 3)).hasSize(3);
        assertThat(context.drugs().search("zzzz", 10)).isEmpty();
        assertThat(context.drugs().all().get(0).display()).contains("Amoxicillin");
    }

    @Test
    void databaseCreatesDataFolders() {
        AppContext context = TestSupport.context(temp);
        Database database = context.database();

        assertThat(database.dataDir()).isEqualTo(temp.toAbsolutePath());
        assertThat(database.documentsDir()).isDirectory();
        assertThat(database.backupDir()).isDirectory();
        assertThat(database.templatesDir()).isDirectory();
        assertThat(database.file()).exists();
    }
}
