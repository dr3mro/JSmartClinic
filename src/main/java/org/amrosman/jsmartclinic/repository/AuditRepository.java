package org.amrosman.jsmartclinic.repository;

import org.amrosman.jsmartclinic.db.Database;
import org.amrosman.jsmartclinic.domain.AuditAction;
import org.amrosman.jsmartclinic.domain.AuditEvent;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

public final class AuditRepository {

    private final Database database;

    public AuditRepository(Database database) {
        this.database = database;
    }

    private AuditEvent map(ResultSet rs) throws SQLException {
        return new AuditEvent(rs.getLong("id"), Jdbc.instant(rs, "at"), Jdbc.nullableLong(rs, "user_id"),
                AuditAction.valueOf(rs.getString("action")), rs.getString("entity"),
                Jdbc.nullableLong(rs, "entity_id"), Jdbc.nullableLong(rs, "patient_id"), rs.getString("details"));
    }

    public void insert(Long userId, AuditAction action, String entity, Long entityId, Long patientId, String details) {
        Jdbc.transaction(database, c -> Jdbc.insert(c,
                "INSERT INTO audit_log(at, user_id, action, entity, entity_id, patient_id, details) "
                        + "VALUES (?,?,?,?,?,?,?)",
                Instant.now(), userId == null || userId <= 0 ? null : userId, action.name(), entity, entityId,
                patientId, details));
    }

    public List<AuditEvent> recent(int limit) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM audit_log ORDER BY at DESC LIMIT ?", this::map, limit);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<AuditEvent> forPatient(long patientId, int limit) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM audit_log WHERE patient_id=? ORDER BY at DESC LIMIT ?",
                    this::map, patientId, limit);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
