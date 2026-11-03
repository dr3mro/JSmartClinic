package org.amrosman.jsmartclinic.service;

import org.amrosman.jsmartclinic.domain.Role;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Capability matrix from the project plan (section 5). */
public enum Permission {
    MANAGE_USERS_SETTINGS,
    PATIENT_EDIT,
    VIEW_CLINICAL,
    SCHEDULE,
    VITALS,
    ENCOUNTERS,
    PRESCRIBE,
    LABS,
    BILLING,
    REPORTS,
    BACKUP,
    AUDIT,
    DOCUMENTS;

    private static final Map<Role, Set<Permission>> BY_ROLE = new EnumMap<>(Role.class);

    static {
        BY_ROLE.put(Role.ADMIN, all(
                MANAGE_USERS_SETTINGS, PATIENT_EDIT, VIEW_CLINICAL, SCHEDULE, VITALS,
                LABS, BILLING, REPORTS, BACKUP, AUDIT, DOCUMENTS));
        BY_ROLE.put(Role.DOCTOR, all(
                PATIENT_EDIT, VIEW_CLINICAL, SCHEDULE, VITALS, ENCOUNTERS, PRESCRIBE,
                LABS, REPORTS, DOCUMENTS));
        BY_ROLE.put(Role.NURSE, all(
                PATIENT_EDIT, VIEW_CLINICAL, SCHEDULE, VITALS, LABS, DOCUMENTS));
        BY_ROLE.put(Role.RECEPTION, all(
                PATIENT_EDIT, SCHEDULE, BILLING));
        BY_ROLE.put(Role.BILLING, all(
                BILLING, REPORTS));
    }

    private static Set<Permission> all(Permission... permissions) {
        return Collections.unmodifiableSet(EnumSet.of(permissions[0], permissions));
    }

    public static boolean has(Role role, Permission permission) {
        return role != null && BY_ROLE.getOrDefault(role, Set.of()).contains(permission);
    }

    public static Set<Permission> of(Role role) {
        return BY_ROLE.getOrDefault(role, Set.of());
    }
}
