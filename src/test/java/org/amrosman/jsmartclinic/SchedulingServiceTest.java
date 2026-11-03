package org.amrosman.jsmartclinic;

import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.Appointment;
import org.amrosman.jsmartclinic.domain.AppointmentStatus;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.Role;
import org.amrosman.jsmartclinic.domain.User;
import org.amrosman.jsmartclinic.service.AccessDeniedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchedulingServiceTest {

    @TempDir
    Path temp;

    @Test
    void createReadAndEnrichAppointments() {
        AppContext context = TestSupport.adminContext(temp);
        User doctor = TestSupport.addUser(context, "doctor", Role.DOCTOR, "General");
        Patient patient = TestSupport.createPatient(context, "Ivy", "Irwin");
        Instant start = todayAt(9, 0);
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        long id = context.scheduling().create(patient.id(), doctor.id(), start, end, "Check-up", "Bring records");

        Appointment appointment = context.scheduling().byId(id);
        assertThat(appointment.status()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(appointment.durationMinutes()).isEqualTo(30);
        assertThat(context.scheduling().day(doctor.id(), LocalDate.now())).hasSize(1);
        assertThat(context.scheduling().week(doctor.id(), LocalDate.now())).hasSize(1);
        assertThat(context.scheduling().forPatient(patient.id())).hasSize(1);
        assertThat(context.scheduling().day(doctor.id(), LocalDate.now().minusDays(1))).isEmpty();
        assertThat(context.scheduling().todayCount()).isEqualTo(1);
        assertThat(context.scheduling().weekCount()).isEqualTo(1);

        var entry = context.scheduling().day(doctor.id(), LocalDate.now()).get(0);
        assertThat(entry.patientName()).isEqualTo("Ivy Irwin");
        assertThat(entry.timeLabel()).isEqualTo("09:00");
        assertThat(entry.provider().id()).isEqualTo(doctor.id());
        assertThat(context.scheduling().minutesWaiting(entry)).isGreaterThanOrEqualTo(0);
    }

    @Test
    void conflictsAndValidationAreRejected() {
        AppContext context = TestSupport.adminContext(temp);
        User doctor = TestSupport.addUser(context, "doctor", Role.DOCTOR, "General");
        User otherDoctor = TestSupport.addUser(context, "doctor2", Role.DOCTOR, "General");
        Patient patient = TestSupport.createPatient(context, "Jack", "Jones");
        Instant start = todayAt(10, 0);
        Instant end = start.plus(30, ChronoUnit.MINUTES);
        long id = context.scheduling().create(patient.id(), doctor.id(), start, end, "First", null);

        assertThatThrownBy(() -> context.scheduling().create(patient.id(), doctor.id(), start, end, "Clash", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already has an appointment");
        long adjacent = context.scheduling().create(patient.id(), doctor.id(), end,
                end.plus(30, ChronoUnit.MINUTES), "Adjacent", null);
        assertThat(adjacent).isPositive();
        long other = context.scheduling().create(patient.id(), otherDoctor.id(), start, end, "Other", null);
        assertThat(other).isPositive();

        assertThatThrownBy(() -> context.scheduling().create(patient.id(), doctor.id(), end, start, "Bad", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("after");
        assertThatThrownBy(() -> context.scheduling().create(patient.id(), doctor.id(), null, end, "Bad", null))
                .isInstanceOf(IllegalArgumentException.class);

        context.scheduling().update(id, patient.id(), doctor.id(), todayAt(12, 0),
                todayAt(12, 30), "Moved", "Notes");
        assertThat(context.scheduling().byId(id).reason()).isEqualTo("Moved");
        assertThatThrownBy(() -> context.scheduling().update(id, patient.id(), doctor.id(), end,
                end.plus(30, ChronoUnit.MINUTES), "Clash", null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> context.scheduling().update(999, patient.id(), doctor.id(), end,
                end.plus(30, ChronoUnit.MINUTES), "Missing", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> context.scheduling().update(id, patient.id(), doctor.id(), end, start,
                "Backwards", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void statusWorkflowIsEnforced() {
        AppContext context = TestSupport.adminContext(temp);
        User doctor = TestSupport.addUser(context, "doctor", Role.DOCTOR, "General");
        Patient patient = TestSupport.createPatient(context, "Kara", "King");
        Instant start = todayAt(8, 0);
        long id = context.scheduling().create(patient.id(), doctor.id(), start,
                start.plus(30, ChronoUnit.MINUTES), "Visit", null);

        context.scheduling().changeStatus(id, AppointmentStatus.SCHEDULED);
        context.scheduling().changeStatus(id, AppointmentStatus.CHECKED_IN);
        assertThat(context.scheduling().waitingRoom()).hasSize(1);
        assertThat(context.scheduling().waitingCount()).isEqualTo(1);

        context.scheduling().changeStatus(id, AppointmentStatus.IN_PROGRESS);
        assertThat(context.scheduling().waitingCount()).isZero();
        context.scheduling().changeStatus(id, AppointmentStatus.COMPLETED);
        assertThat(context.scheduling().byId(id).status()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThatThrownBy(() -> context.scheduling().changeStatus(id, AppointmentStatus.SCHEDULED))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Cannot move");
        assertThatThrownBy(() -> context.scheduling().changeStatus(999, AppointmentStatus.COMPLETED))
                .isInstanceOf(IllegalArgumentException.class);

        long second = context.scheduling().create(patient.id(), doctor.id(), todayAt(11, 0),
                todayAt(11, 30), "Second", null);
        context.scheduling().cancel(second);
        assertThat(context.scheduling().byId(second).status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThatThrownBy(() -> context.scheduling().cancel(second))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void schedulingRequiresSchedulePermission() {
        AppContext context = TestSupport.adminContext(temp);
        User doctor = TestSupport.addUser(context, "doctor", Role.DOCTOR, "General");
        TestSupport.addUser(context, "billing", Role.BILLING, null);
        Patient patient = TestSupport.createPatient(context, "Liam", "Lane");

        context.session().login(context.auth().login("billing", TestSupport.PASSWORD));
        assertThatThrownBy(() -> context.scheduling().create(patient.id(), doctor.id(), todayAt(9, 0),
                todayAt(9, 30), "No", null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> context.scheduling().changeStatus(1, AppointmentStatus.CHECKED_IN))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void scheduleEntriesHandleMissingPatient() {
        AppContext context = TestSupport.adminContext(temp);
        User doctor = TestSupport.addUser(context, "doctor", Role.DOCTOR, "General");
        Patient patient = TestSupport.createPatient(context, "Mona", "Moore");
        long id = context.scheduling().create(patient.id(), doctor.id(), todayAt(13, 0),
                todayAt(13, 30), "Visit", null);
        context.patientRepository().archive(patient.id());

        var entry = context.scheduling().day(doctor.id(), LocalDate.now()).stream()
                .filter(e -> e.appointment().id() == id).findFirst().orElseThrow();
        assertThat(entry.patientName()).isEqualTo("Mona Moore");
        assertThat(context.scheduling().byId(id)).isNotNull();
    }

    private static Instant todayAt(int hour, int minute) {
        return LocalDate.now().atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant();
    }
}
