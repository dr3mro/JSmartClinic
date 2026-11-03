package org.amrosman.jsmartclinic.domain;

public record User(
        long id,
        String username,
        String fullName,
        String passwordHash,
        Role role,
        String specialty,
        boolean active,
        int failedLogins,
        java.time.Instant lockedUntil,
        java.time.Instant lastLoginAt,
        java.time.Instant createdAt,
        java.time.Instant updatedAt) {

    public boolean locked() {
        return lockedUntil != null && lockedUntil.isAfter(java.time.Instant.now());
    }
}
