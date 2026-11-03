package org.amrosman.jsmartclinic.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;

public record Patient(
        long id,
        String mrn,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        Sex sex,
        String phone,
        String email,
        String address,
        String city,
        String nationalId,
        String bloodType,
        String maritalStatus,
        String occupation,
        String emergencyName,
        String emergencyPhone,
        String emergencyRelation,
        String insuranceProvider,
        String insuranceNumber,
        byte[] photo,
        String notes,
        String status,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt) {

    public String fullName() {
        return (firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName);
    }

    public int age() {
        if (dateOfBirth == null) {
            return 0;
        }
        return Math.max(0, Period.between(dateOfBirth, LocalDate.now()).getYears());
    }

    public int ageAt(LocalDate date) {
        return dateOfBirth == null ? 0 : Math.max(0, Period.between(dateOfBirth, date).getYears());
    }

    public String initials() {
        String f = firstName == null || firstName.isBlank() ? "" : firstName.substring(0, 1);
        String l = lastName == null || lastName.isBlank() ? "" : lastName.substring(0, 1);
        return (f + l).toUpperCase(Locale.ROOT);
    }

    public String shortName() {
        return fullName().trim();
    }
}
