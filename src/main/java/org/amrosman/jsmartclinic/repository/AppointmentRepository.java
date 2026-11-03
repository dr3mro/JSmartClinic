package org.amrosman.jsmartclinic.repository;

import org.amrosman.jsmartclinic.db.Database;
import org.amrosman.jsmartclinic.domain.Appointment;
import org.amrosman.jsmartclinic.domain.AppointmentStatus;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

public final class AppointmentRepository {

    private final Database database;

    public AppointmentRepository(Database database) {
        this.database = database;
    }

    private Appointment map(ResultSet rs) throws SQLException {
        return new Appointment(rs.getLong("id"), rs.getLong("patient_id"), rs.getLong("provider_id"),
                Jdbc.instant(rs, "start_at"), Jdbc.instant(rs, "end_at"), rs.getString("reason"),
                AppointmentStatus.from(rs.getString("status")), rs.getString("notes"),
                Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "updated_at"));
    }

    public Appointment findById(long id) {
        try (Connection c = database.getConnection()) {
            return Jdbc.one(c, "SELECT * FROM appointments WHERE id=?", this::map, id);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Appointment> findRange(Long providerId, Instant from, Instant to) {
        String sql = "SELECT * FROM appointments WHERE start_at >= ? AND start_at < ? "
                + (providerId == null ? "" : "AND provider_id = ? ")
                + "ORDER BY start_at";
        try (Connection c = database.getConnection()) {
            return providerId == null
                    ? Jdbc.list(c, sql, this::map, from, to)
                    : Jdbc.list(c, sql, this::map, from, to, providerId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Appointment> findByPatient(long patientId) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM appointments WHERE patient_id=? ORDER BY start_at DESC",
                    this::map, patientId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Appointment> waitingRoom() {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM appointments WHERE status IN ('CHECKED_IN','IN_PROGRESS') "
                    + "ORDER BY start_at", this::map);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean hasConflict(long providerId, Instant start, Instant end, Long excludeId) {
        String sql = "SELECT COUNT(*) FROM appointments WHERE provider_id=? AND id<>? "
                + "AND status NOT IN ('CANCELLED','NO_SHOW') AND start_at < ? AND end_at > ?";
        try (Connection c = database.getConnection()) {
            return Jdbc.count(c, sql, providerId, excludeId == null ? -1 : excludeId,
                    end, start) > 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public long insert(Appointment a) {
        return Jdbc.transaction(database, c -> {
            Instant now = Instant.now();
            return Jdbc.insert(c, "INSERT INTO appointments(patient_id, provider_id, start_at, end_at, reason, status, "
                            + "notes, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    a.patientId(), a.providerId(), a.startAt(), a.endAt(), a.reason(),
                    (a.status() == null ? AppointmentStatus.SCHEDULED : a.status()).name(), a.notes(), now, now);
        });
    }

    public void update(Appointment a) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE appointments SET patient_id=?, provider_id=?, start_at=?, end_at=?, reason=?, status=?, "
                        + "notes=?, updated_at=? WHERE id=?",
                a.patientId(), a.providerId(), a.startAt(), a.endAt(), a.reason(), a.status().name(), a.notes(),
                Instant.now(), a.id()));
    }

    public void updateStatus(long id, AppointmentStatus status) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE appointments SET status=?, updated_at=? WHERE id=?", status.name(), Instant.now(), id));
    }

    public int countInRange(Instant from, Instant to) {
        try (Connection c = database.getConnection()) {
            return (int) Jdbc.count(c,
                    "SELECT COUNT(*) FROM appointments WHERE start_at>=? AND start_at<? AND status<>'CANCELLED'",
                    from, to);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public int countByStatus(AppointmentStatus status) {
        try (Connection c = database.getConnection()) {
            return (int) Jdbc.count(c, "SELECT COUNT(*) FROM appointments WHERE status=?", status.name());
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
