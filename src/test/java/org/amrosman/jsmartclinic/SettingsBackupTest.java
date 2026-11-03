package org.amrosman.jsmartclinic;

import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.AuditAction;
import org.amrosman.jsmartclinic.domain.Role;
import org.amrosman.jsmartclinic.service.AccessDeniedException;
import org.amrosman.jsmartclinic.service.SettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsBackupTest {

    @TempDir
    Path temp;

    @Test
    void profileDefaultsAndUpdates() {
        AppContext context = TestSupport.adminContext(temp);
        SettingsService.ClinicProfile defaults = context.settings().profile();
        assertThat(defaults.name()).isEqualTo("Test Clinic");
        assertThat(defaults.currency()).isEqualTo("$");
        assertThat(defaults.taxRate()).isZero();
        assertThat(defaults.autoBackup()).isTrue();

        context.settings().saveProfile(new SettingsService.ClinicProfile("New Clinic", "2 Road", "123",
                "new@example.test", "/tmp/logo.png", "€", 150, "dark", "ar", false));
        SettingsService.ClinicProfile saved = context.settings().profile();
        assertThat(saved.name()).isEqualTo("New Clinic");
        assertThat(saved.address()).isEqualTo("2 Road");
        assertThat(saved.currency()).isEqualTo("€");
        assertThat(saved.taxRate()).isEqualTo(100);
        assertThat(saved.autoBackup()).isFalse();
        assertThat(context.settings().taxRate()).isEqualTo(100);
        assertThat(context.settings().currency()).isEqualTo("€");

        context.settings().saveProfile(new SettingsService.ClinicProfile(null, null, null, null, null,
                null, -5, null, null, true));
        assertThat(context.settings().profile().taxRate()).isZero();
        assertThat(context.settings().profile().name()).isEqualTo("JSmartClinic");

        context.settings().setTheme("dark");
        context.settings().setLanguage("ar");
        assertThat(context.settings().profile().theme()).isEqualTo("dark");
        assertThat(context.settings().profile().language()).isEqualTo("ar");

        context.settings().put("custom", "value");
        assertThat(context.settings().get("custom", "fallback")).isEqualTo("value");
        assertThat(context.settings().get("missing", "fallback")).isEqualTo("fallback");
        assertThat(context.settings().all()).containsEntry("custom", "value");
        context.settingsRepository().put("tax_rate", "not-a-number");
        assertThat(context.settings().taxRate()).isZero();
    }

    @Test
    void settingsAndBackupsRequireAdmin() {
        AppContext context = TestSupport.adminContext(temp);
        TestSupport.addUser(context, "doctor", Role.DOCTOR, "General");
        TestSupport.login(context, "doctor");

        assertThatThrownBy(() -> context.settings().saveProfile(new SettingsService.ClinicProfile(
                "Hack", "", "", "", "", "$", 0, "light", "en", true)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> context.settings().put("clinic_name", "Hack"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(context.backups()::backups).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(context.backups()::backupNow).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> context.backups().restore(temp.resolve("missing.db")))
                .isInstanceOf(AccessDeniedException.class);

        context.settings().setTheme("dark");
        assertThat(context.settings().profile().theme()).isEqualTo("dark");
    }

    @Test
    void backupListCreateAndRestore() {
        AppContext context = TestSupport.adminContext(temp);
        Path backup = context.backups().backupNow();

        assertThat(backup).exists();
        assertThat(context.backups().backups()).contains(backup);
        assertThat(context.settings().get("last_backup_at", "")).isNotBlank();
        assertThat(context.audit().recent(10)).anyMatch(e -> e.action() == AuditAction.EXPORT);

        context.settingsRepository().put("clinic_name", "Changed After Backup");
        assertThat(context.settings().clinicName()).isEqualTo("Changed After Backup");
        context.backups().restore(backup);
        assertThat(context.settings().clinicName()).isEqualTo("Test Clinic");

        assertThatThrownBy(() -> context.backups().restore(temp.resolve("missing.db")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not found");
    }

    @Test
    void automaticBackupRunsAtMostOncePerDay() {
        AppContext context = TestSupport.adminContext(temp);
        context.settingsRepository().put("auto_backup", "true");
        context.settingsRepository().put("last_backup_at", "");

        context.backups().automaticDailyBackupIfDue();
        assertThat(context.backups().backups()).hasSize(1);
        String last = context.settings().get("last_backup_at", "");
        assertThat(last).isNotBlank();
        assertThat(Instant.parse(last)).isNotNull();

        context.backups().automaticDailyBackupIfDue();
        assertThat(context.backups().backups()).hasSize(1);

        context.settingsRepository().put("auto_backup", "false");
        context.settingsRepository().put("last_backup_at", "");
        context.backups().automaticDailyBackupIfDue();
        assertThat(context.backups().backups()).hasSize(1);
    }

    @Test
    void auditLogQueriesByPatientAndRecent() {
        AppContext context = TestSupport.adminContext(temp);
        var patient = TestSupport.createPatient(context, "Uma", "Underwood");
        context.patients().chart(patient.id());

        assertThat(context.audit().recent(50)).isNotEmpty();
        assertThat(context.audit().forPatient(patient.id(), 50)).isNotEmpty()
                .allSatisfy(event -> assertThat(event.patientId()).isEqualTo(patient.id()));
    }
}
