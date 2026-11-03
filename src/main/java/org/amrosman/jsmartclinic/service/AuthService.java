package org.amrosman.jsmartclinic.service;

import org.amrosman.jsmartclinic.app.Session;
import org.amrosman.jsmartclinic.domain.AuditAction;
import org.amrosman.jsmartclinic.domain.Role;
import org.amrosman.jsmartclinic.domain.User;
import org.amrosman.jsmartclinic.repository.SettingsRepository;
import org.amrosman.jsmartclinic.repository.UserRepository;
import org.amrosman.jsmartclinic.util.PasswordHasher;
import org.amrosman.jsmartclinic.util.Validators;

import java.time.Duration;
import java.time.Instant;

/** Authentication, first-run setup, lockout and password policy. */
public final class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository users;
    private final Session session;
    private final AuditService audit;
    private final SettingsRepository settings;

    public AuthService(UserRepository users, Session session, AuditService audit, SettingsRepository settings) {
        this.users = users;
        this.session = session;
        this.audit = audit;
        this.settings = settings;
    }

    public boolean firstRun() {
        return users.count() == 0;
    }

    public User createFirstAdministrator(String clinicName, String fullName, String username, String password) {
        return createFirstAdministrator(clinicName, fullName, username, password, false, null);
    }

    public User createFirstAdministrator(String clinicName, String fullName, String username, String password,
                                         boolean clinician, String specialty) {
        if (!firstRun()) {
            throw new IllegalStateException("An administrator already exists");
        }
        String cleanUser = Validators.required(username, "Username");
        String cleanName = Validators.required(fullName, "Full name");
        String cleanSpecialty = specialty == null || specialty.isBlank() ? null : specialty.trim();
        PasswordHasher.validatePolicy(password);
        long id = users.insert(cleanUser, cleanName, PasswordHasher.hash(password), Role.ADMIN,
                cleanSpecialty, clinician);
        if (clinicName != null && !clinicName.isBlank()) {
            settings.put("clinic_name", clinicName.trim());
        }
        User admin = users.findById(id);
        session.login(admin);
        audit.log(AuditAction.CREATE, "user", id, null, "First administrator created");
        return admin;
    }

    public User login(String username, String password) {
        User user = users.findByUsername(Validators.required(username, "Username"));
        if (user == null) {
            throw new AccessDeniedException("Invalid username or password");
        }
        if (!user.active()) {
            throw new AccessDeniedException("This account is inactive. Contact an administrator.");
        }
        if (user.locked()) {
            throw new AccessDeniedException("Account locked until " + user.lockedUntil()
                    .atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
        }
        if (!PasswordHasher.verify(password, user.passwordHash())) {
            int failures = user.failedLogins() + 1;
            Instant lockUntil = failures >= MAX_FAILED_ATTEMPTS ? Instant.now().plus(LOCK_DURATION) : null;
            users.recordLoginFailure(user.id(), failures, lockUntil);
            if (lockUntil != null) {
                throw new AccessDeniedException("Too many failed attempts. Account locked for 15 minutes.");
            }
            throw new AccessDeniedException("Invalid username or password");
        }
        users.recordLoginSuccess(user.id());
        User refreshed = users.findById(user.id());
        session.login(refreshed);
        audit.log(AuditAction.LOGIN, "session", refreshed.id(), null, null);
        return refreshed;
    }

    public void logout() {
        if (session.loggedIn()) {
            audit.log(AuditAction.LOGOUT, "session", session.userId(), null, null);
        }
        session.logout();
    }

    /** Re-authentication for the inactivity lock screen. */
    public boolean verifyCurrentPassword(String password) {
        if (!session.loggedIn()) {
            return false;
        }
        User user = users.findById(session.userId());
        return user != null && PasswordHasher.verify(password, user.passwordHash());
    }

    public void changePassword(long userId, String oldPassword, String newPassword) {
        boolean self = session.userId() == userId;
        if (!self) {
            session.require(Permission.MANAGE_USERS_SETTINGS);
        }
        User user = users.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        if (self && !PasswordHasher.verify(oldPassword, user.passwordHash())) {
            throw new AccessDeniedException("Current password is incorrect");
        }
        PasswordHasher.validatePolicy(newPassword);
        users.setPassword(userId, PasswordHasher.hash(newPassword));
        audit.log(AuditAction.UPDATE, "user", userId, null, "Password changed");
    }

    public void resetPassword(long userId, String newPassword) {
        session.require(Permission.MANAGE_USERS_SETTINGS);
        PasswordHasher.validatePolicy(newPassword);
        users.setPassword(userId, PasswordHasher.hash(newPassword));
        audit.log(AuditAction.UPDATE, "user", userId, null, "Password reset");
    }
}
