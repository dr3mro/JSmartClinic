package org.amrosman.jsmartclinic;

import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.AuditAction;
import org.amrosman.jsmartclinic.domain.Role;
import org.amrosman.jsmartclinic.domain.User;
import org.amrosman.jsmartclinic.service.AccessDeniedException;
import org.amrosman.jsmartclinic.service.Permission;
import org.amrosman.jsmartclinic.util.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthAndUserServiceTest {

    @TempDir
    Path temp;

    @Test
    void firstRunSetupCreatesAdministratorAndClinic() {
        AppContext context = TestSupport.context(temp);
        assertThat(context.auth().firstRun()).isTrue();

        User admin = context.auth().createFirstAdministrator("My Clinic", "Alice Admin", "alice", TestSupport.PASSWORD);

        assertThat(admin.role()).isEqualTo(Role.ADMIN);
        assertThat(admin.passwordHash()).startsWith("pbkdf2$210000$");
        assertThat(context.auth().firstRun()).isFalse();
        assertThat(context.session().current()).isEqualTo(admin);
        assertThat(context.settings().clinicName()).isEqualTo("My Clinic");
        assertThat(context.audit().recent(10)).anyMatch(e -> e.action() == AuditAction.CREATE);
        assertThatThrownBy(() -> context.auth().createFirstAdministrator("Other", "Bob", "bob", TestSupport.PASSWORD))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void firstRunRejectsShortPassword() {
        AppContext context = TestSupport.context(temp);
        assertThatThrownBy(() -> context.auth().createFirstAdministrator("Clinic", "Alice", "alice", "short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 10");
        assertThat(context.auth().firstRun()).isTrue();
    }

    @Test
    void loginSucceedsAndWrongPasswordLocksAfterFiveAttempts() {
        AppContext context = TestSupport.adminContext(temp);

        assertThatThrownBy(() -> context.auth().login("missing", TestSupport.PASSWORD))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> context.auth().login(" ", TestSupport.PASSWORD))
                .isInstanceOf(IllegalArgumentException.class);

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThatThrownBy(() -> context.auth().login("admin", "wrong-password"))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Invalid username or password");
        }
        User afterFour = context.users().byId(1);
        assertThat(afterFour.failedLogins()).isEqualTo(4);
        assertThat(afterFour.lockedUntil()).isNull();

        assertThatThrownBy(() -> context.auth().login("admin", "wrong-password"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Too many failed attempts");
        User locked = context.users().byId(1);
        assertThat(locked.locked()).isTrue();
        assertThat(locked.failedLogins()).isEqualTo(5);

        assertThatThrownBy(() -> context.auth().login("admin", TestSupport.PASSWORD))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Account locked until");
    }

    @Test
    void successfulLoginResetsFailuresAndRecordsLastLogin() {
        AppContext context = TestSupport.adminContext(temp);
        context.auth().logout();
        assertThatThrownBy(() -> context.auth().login("admin", "nope-nope-nope"))
                .isInstanceOf(AccessDeniedException.class);

        User loggedIn = context.auth().login("admin", TestSupport.PASSWORD);

        assertThat(loggedIn.failedLogins()).isZero();
        assertThat(loggedIn.lastLoginAt()).isNotNull();
        assertThat(context.session().loggedIn()).isTrue();
        assertThat(context.session().can(Permission.MANAGE_USERS_SETTINGS)).isTrue();
    }

    @Test
    void inactiveAccountsCannotSignIn() {
        AppContext context = TestSupport.adminContext(temp);
        User doctor = TestSupport.addUser(context, "doctor", Role.DOCTOR, "General");
        context.users().update(doctor.id(), doctor.fullName(), Role.DOCTOR, "General", false);
        context.auth().logout();

        assertThatThrownBy(() -> context.auth().login("doctor", TestSupport.PASSWORD))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void logoutClearsSessionAndAudits() {
        AppContext context = TestSupport.adminContext(temp);
        context.auth().logout();

        assertThat(context.session().loggedIn()).isFalse();
        assertThat(context.session().current()).isNull();
        assertThat(context.session().userId()).isEqualTo(-1);

        context.auth().login("admin", TestSupport.PASSWORD);
        assertThat(context.audit().recent(5)).anyMatch(e -> e.action() == AuditAction.LOGOUT);
        context.auth().logout();
    }

    @Test
    void verifyCurrentPasswordAndChangePassword() {
        AppContext context = TestSupport.adminContext(temp);
        assertThat(context.auth().verifyCurrentPassword(TestSupport.PASSWORD)).isTrue();
        assertThat(context.auth().verifyCurrentPassword("wrong-password")).isFalse();

        assertThatThrownBy(() -> context.auth().changePassword(1, "wrong-password", "newpassword1"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Current password");
        assertThatThrownBy(() -> context.auth().changePassword(1, TestSupport.PASSWORD, "short"))
                .isInstanceOf(IllegalArgumentException.class);

        context.auth().changePassword(1, TestSupport.PASSWORD, "newpassword1");
        context.auth().logout();
        assertThatThrownBy(() -> context.auth().login("admin", TestSupport.PASSWORD))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(context.auth().login("admin", "newpassword1").username()).isEqualTo("admin");

        context.auth().logout();
        assertThat(context.auth().verifyCurrentPassword("newpassword1")).isFalse();
        context.auth().login("admin", "newpassword1");
        assertThatThrownBy(() -> context.auth().changePassword(99, TestSupport.PASSWORD, "newpassword2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void administratorCanResetPasswordsButOtherRolesCannot() {
        AppContext context = TestSupport.adminContext(temp);
        User doctor = TestSupport.addUser(context, "doctor", Role.DOCTOR, "General");

        context.auth().resetPassword(doctor.id(), "resetpass12");
        context.auth().logout();
        assertThat(context.auth().login("doctor", "resetpass12").id()).isEqualTo(doctor.id());

        assertThatThrownBy(() -> context.auth().resetPassword(doctor.id(), "anotherpass"))
                .isInstanceOf(AccessDeniedException.class);
        context.auth().logout();
    }

    @Test
    void userManagementEnforcesAdminPermissionsAndRules() {
        AppContext context = TestSupport.adminContext(temp);
        User doctor = TestSupport.addUser(context, "doctor", Role.DOCTOR, "Cardiology");
        User nurse = TestSupport.addUser(context, "nurse", Role.NURSE, null);

        assertThat(context.users().count()).isEqualTo(3);
        assertThat(context.users().all()).hasSize(3);
        assertThat(context.users().providers()).extracting(User::username)
                .containsExactlyInAnyOrder("doctor", "nurse");
        assertThat(context.users().byId(doctor.id()).specialty()).isEqualTo("Cardiology");

        assertThatThrownBy(() -> context.users().create("doctor", "Duplicate", Role.DOCTOR, null, TestSupport.PASSWORD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        assertThatThrownBy(() -> context.users().create("other", "Other", Role.NURSE, null, "short"))
                .isInstanceOf(IllegalArgumentException.class);

        context.users().update(doctor.id(), "Doctor Renamed", Role.DOCTOR, "Neurology", true);
        assertThat(context.users().byId(doctor.id()).fullName()).isEqualTo("Doctor Renamed");
        assertThatThrownBy(() -> context.users().update(1, "Admin", Role.ADMIN, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("own account");
        assertThatThrownBy(() -> context.users().update(99, "Nobody", Role.NURSE, null, true))
                .isInstanceOf(IllegalArgumentException.class);

        context.users().resetPassword(nurse.id(), "nursepass12");
        assertThat(PasswordHasher.verify("nursepass12", context.users().byId(nurse.id()).passwordHash())).isTrue();

        context.auth().logout();
        assertThatThrownBy(() -> context.users().all()).isInstanceOf(AccessDeniedException.class);
        context.session().login(doctor);
        assertThatThrownBy(() -> context.users().create("x", "X", Role.NURSE, null, TestSupport.PASSWORD))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void permissionMatrixMatchesPlan() {
        assertThat(Permission.of(Role.ADMIN))
                .contains(Permission.MANAGE_USERS_SETTINGS, Permission.VIEW_CLINICAL, Permission.BILLING,
                        Permission.BACKUP, Permission.AUDIT)
                .doesNotContain(Permission.ENCOUNTERS, Permission.PRESCRIBE);
        assertThat(Permission.of(Role.DOCTOR))
                .contains(Permission.ENCOUNTERS, Permission.PRESCRIBE, Permission.LABS)
                .doesNotContain(Permission.BILLING, Permission.MANAGE_USERS_SETTINGS);
        assertThat(Permission.of(Role.NURSE)).contains(Permission.VITALS, Permission.LABS)
                .doesNotContain(Permission.ENCOUNTERS, Permission.PRESCRIBE);
        assertThat(Permission.of(Role.RECEPTION)).contains(Permission.PATIENT_EDIT, Permission.SCHEDULE,
                Permission.BILLING);
        assertThat(Permission.of(Role.BILLING)).contains(Permission.BILLING, Permission.REPORTS)
                .doesNotContain(Permission.VIEW_CLINICAL);
        assertThat(Permission.has(null, Permission.BILLING)).isFalse();
    }
}
