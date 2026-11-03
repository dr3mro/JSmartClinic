package org.amrosman.jsmartclinic.app;

import java.time.Instant;

import org.amrosman.jsmartclinic.domain.Role;
import org.amrosman.jsmartclinic.domain.User;
import org.amrosman.jsmartclinic.service.AccessDeniedException;
import org.amrosman.jsmartclinic.service.Permission;

/** Holds the authenticated user for the lifetime of the process. */
public final class Session {

    private User current;
    private Instant lastActivity = Instant.now();

    public User current() {
        return current;
    }

    public boolean loggedIn() {
        return current != null;
    }

    public Role role() {
        return current == null ? null : current.role();
    }

    public long userId() {
        return current == null ? -1 : current.id();
    }

    public void login(User user) {
        this.current = user;
        touch();
    }

    public void logout() {
        this.current = null;
    }

    public void touch() {
        this.lastActivity = Instant.now();
    }

    public Instant lastActivity() {
        return lastActivity;
    }

    public boolean can(Permission permission) {
        return current != null && Permission.has(current.role(), permission);
    }

    public void require(Permission permission) {
        if (!can(permission)) {
            throw new AccessDeniedException("Your role does not allow this action (" + permission + ")");
        }
    }
}
