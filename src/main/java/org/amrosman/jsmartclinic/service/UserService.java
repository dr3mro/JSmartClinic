package org.amrosman.jsmartclinic.service;

import org.amrosman.jsmartclinic.app.Session;
import org.amrosman.jsmartclinic.domain.AuditAction;
import org.amrosman.jsmartclinic.domain.Role;
import org.amrosman.jsmartclinic.domain.User;
import org.amrosman.jsmartclinic.repository.UserRepository;
import org.amrosman.jsmartclinic.util.PasswordHasher;
import org.amrosman.jsmartclinic.util.Validators;

import java.util.List;

public final class UserService {

    private final UserRepository users;
    private final Session session;
    private final AuditService audit;

    public UserService(UserRepository users, Session session, AuditService audit) {
        this.users = users;
        this.session = session;
        this.audit = audit;
    }

    public int count() {
        return (int) users.count();
    }

    public List<User> all() {
        session.require(Permission.MANAGE_USERS_SETTINGS);
        return users.findAll();
    }

    public List<User> providers() {
        return users.findProviders();
    }

    public User byId(long id) {
        return users.findById(id);
    }

    public User create(String username, String fullName, Role role, String specialty, String password) {
        session.require(Permission.MANAGE_USERS_SETTINGS);
        String cleanUser = Validators.required(username, "Username");
        String cleanName = Validators.required(fullName, "Full name");
        PasswordHasher.validatePolicy(password);
        if (users.findByUsername(cleanUser) != null) {
            throw new IllegalArgumentException("Username already exists");
        }
        long id = users.insert(cleanUser, cleanName, PasswordHasher.hash(password), role, specialty);
        audit.log(AuditAction.CREATE, "user", id, null, "User " + cleanUser + " created");
        return users.findById(id);
    }

    public void update(long id, String fullName, Role role, String specialty, boolean active) {
        session.require(Permission.MANAGE_USERS_SETTINGS);
        User existing = users.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("User not found");
        }
        if (existing.id() == session.userId() && !active) {
            throw new IllegalArgumentException("You cannot deactivate your own account");
        }
        User updated = new User(existing.id(), existing.username(), Validators.required(fullName, "Full name"),
                existing.passwordHash(), role, specialty, active, existing.failedLogins(), existing.lockedUntil(),
                existing.lastLoginAt(), existing.createdAt(), existing.updatedAt());
        users.update(updated);
        audit.log(AuditAction.UPDATE, "user", id, null, "User updated");
    }

    public void resetPassword(long id, String password) {
        session.require(Permission.MANAGE_USERS_SETTINGS);
        PasswordHasher.validatePolicy(password);
        users.setPassword(id, PasswordHasher.hash(password));
        audit.log(AuditAction.UPDATE, "user", id, null, "Password reset by administrator");
    }
}
