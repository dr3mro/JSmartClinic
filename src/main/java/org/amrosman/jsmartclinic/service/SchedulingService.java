package org.amrosman.jsmartclinic.service;

import org.amrosman.jsmartclinic.app.Session;
import org.amrosman.jsmartclinic.domain.Appointment;
import org.amrosman.jsmartclinic.domain.AppointmentStatus;
import org.amrosman.jsmartclinic.domain.AuditAction;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.User;
import org.amrosman.jsmartclinic.repository.AppointmentRepository;
import org.amrosman.jsmartclinic.repository.PatientRepository;
import org.amrosman.jsmartclinic.repository.UserRepository;
import org.amrosman.jsmartclinic.util.Dates;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SchedulingService {

    public record ScheduleEntry(Appointment appointment, Patient patient, User provider) {
        public String timeLabel() {
            return Dates.displayTime(appointment.startAt());
        }

        public String patientName() {
            return patient == null ? "(unknown patient)" : patient.fullName().trim();
        }
    }

    private final AppointmentRepository appointments;
    private final PatientRepository patients;
    private final UserRepository users;
    private final Session session;
    private final AuditService audit;

    public SchedulingService(AppointmentRepository appointments, PatientRepository patients, UserRepository users,
                             Session session, AuditService audit) {
        this.appointments = appointments;
        this.patients = patients;
        this.users = users;
        this.session = session;
        this.audit = audit;
    }

    public List<ScheduleEntry> day(Long providerId, LocalDate date) {
        return enrich(appointments.findRange(providerId, Dates.startOfDay(date), Dates.endOfDay(date)));
    }

    public List<ScheduleEntry> week(Long providerId, LocalDate anyDayInWeek) {
        LocalDate monday = anyDayInWeek.minusDays(anyDayInWeek.getDayOfWeek().getValue() - 1L);
        return enrich(appointments.findRange(providerId, Dates.startOfDay(monday),
                Dates.startOfDay(monday.plusDays(7))));
    }

    public List<ScheduleEntry> forPatient(long patientId) {
        return enrich(appointments.findByPatient(patientId));
    }

    public List<ScheduleEntry> waitingRoom() {
        return enrich(appointments.waitingRoom());
    }

    public Appointment byId(long id) {
        return appointments.findById(id);
    }

    public long create(long patientId, long providerId, Instant start, Instant end, String reason, String notes) {
        session.require(Permission.SCHEDULE);
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        if (appointments.hasConflict(providerId, start, end, null)) {
            throw new IllegalStateException("This provider already has an appointment in that time slot");
        }
        long id = appointments.insert(new Appointment(0, patientId, providerId, start, end, reason,
                AppointmentStatus.SCHEDULED, notes, null, null));
        audit.log(AuditAction.CREATE, "appointment", id, patientId, "Appointment scheduled");
        return id;
    }

    public void update(long id, long patientId, long providerId, Instant start, Instant end, String reason,
                       String notes) {
        session.require(Permission.SCHEDULE);
        Appointment existing = appointments.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Appointment not found");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        if (appointments.hasConflict(providerId, start, end, id)) {
            throw new IllegalStateException("This provider already has an appointment in that time slot");
        }
        appointments.update(new Appointment(id, patientId, providerId, start, end, reason,
                existing.status(), notes, existing.createdAt(), existing.updatedAt()));
        audit.log(AuditAction.UPDATE, "appointment", id, patientId, "Appointment rescheduled");
    }

    public void changeStatus(long id, AppointmentStatus target) {
        session.require(Permission.SCHEDULE);
        Appointment existing = appointments.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Appointment not found");
        }
        ensureTransition(existing.status(), target);
        appointments.updateStatus(id, target);
        audit.log(AuditAction.UPDATE, "appointment", id, existing.patientId(), "Status " + target.name());
    }

    public void cancel(long id) {
        session.require(Permission.SCHEDULE);
        Appointment existing = appointments.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Appointment not found");
        }
        if (existing.status() == AppointmentStatus.CANCELLED) {
            throw new IllegalStateException("Appointment is already cancelled");
        }
        changeStatus(id, AppointmentStatus.CANCELLED);
    }

    public int todayCount() {
        LocalDate today = LocalDate.now();
        return appointments.countInRange(Dates.startOfDay(today), Dates.endOfDay(today));
    }

    public int waitingCount() {
        return (int) waitingRoom().stream()
                .filter(e -> e.appointment().status() == AppointmentStatus.CHECKED_IN).count();
    }

    public int weekCount() {
        LocalDate monday = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1L);
        return appointments.countInRange(Dates.startOfDay(monday), Dates.startOfDay(monday.plusDays(7)));
    }

    private void ensureTransition(AppointmentStatus from, AppointmentStatus to) {
        if (from == to) {
            return;
        }
        boolean allowed = switch (from) {
            case SCHEDULED -> to == AppointmentStatus.CHECKED_IN || to == AppointmentStatus.CANCELLED
                    || to == AppointmentStatus.NO_SHOW || to == AppointmentStatus.COMPLETED;
            case CHECKED_IN -> to == AppointmentStatus.IN_PROGRESS || to == AppointmentStatus.CANCELLED
                    || to == AppointmentStatus.NO_SHOW || to == AppointmentStatus.COMPLETED;
            case IN_PROGRESS -> to == AppointmentStatus.COMPLETED || to == AppointmentStatus.CANCELLED;
            case COMPLETED, CANCELLED, NO_SHOW -> false;
        };
        if (!allowed) {
            throw new IllegalStateException("Cannot move an appointment from " + from.label() + " to " + to.label());
        }
    }

    private List<ScheduleEntry> enrich(List<Appointment> list) {
        Map<Long, Patient> patientCache = new HashMap<>();
        Map<Long, User> userCache = new HashMap<>();
        List<ScheduleEntry> result = new ArrayList<>(list.size());
        for (Appointment appointment : list) {
            Patient patient = patientCache.computeIfAbsent(appointment.patientId(), patients::findById);
            User provider = userCache.computeIfAbsent(appointment.providerId(), users::findById);
            result.add(new ScheduleEntry(appointment, patient, provider));
        }
        return result;
    }

    public long minutesWaiting(ScheduleEntry entry) {
        return Math.max(0, ChronoUnit.MINUTES.between(entry.appointment().startAt(), Instant.now()));
    }
}
