package org.amrosman.jsmartclinic.util;

import org.amrosman.jsmartclinic.domain.Patient;

import java.time.LocalDate;
import java.util.regex.Pattern;

/** Input validation shared by services. */
public final class Validators {

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^[+()\\-\\s0-9]{6,25}$");

    private Validators() {
    }

    public static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public static void requirePatient(Patient patient) {
        if (patient == null) {
            throw new IllegalArgumentException("Patient is required");
        }
        required(patient.firstName(), "First name");
        required(patient.lastName(), "Last name");
        if (patient.dateOfBirth() == null) {
            throw new IllegalArgumentException("Date of birth is required");
        }
        if (patient.dateOfBirth().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Date of birth cannot be in the future");
        }
        optionalEmail(patient.email());
        optionalPhone(patient.phone());
        optionalPhone(patient.emergencyPhone());
    }

    public static void optionalEmail(String email) {
        if (email != null && !email.isBlank() && !EMAIL.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("Email address is not valid");
        }
    }

    public static void optionalPhone(String phone) {
        if (phone != null && !phone.isBlank() && !PHONE.matcher(phone.trim()).matches()) {
            throw new IllegalArgumentException("Phone number is not valid");
        }
    }

    public static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
