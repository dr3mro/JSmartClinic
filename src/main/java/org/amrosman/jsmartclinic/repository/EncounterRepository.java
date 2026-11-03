package org.amrosman.jsmartclinic.repository;

import org.amrosman.jsmartclinic.db.Database;
import org.amrosman.jsmartclinic.domain.Encounter;
import org.amrosman.jsmartclinic.domain.EncounterStatus;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

public final class EncounterRepository {

    private final Database database;

    public EncounterRepository(Database database) {
        this.database = database;
    }

    private Encounter map(ResultSet rs) throws SQLException {
        return new Encounter(rs.getLong("id"), rs.getLong("patient_id"), rs.getLong("provider_id"),
                Jdbc.nullableLong(rs, "appointment_id"), rs.getString("specialty"), rs.getString("template_id"),
                Jdbc.instant(rs, "encounter_date"), rs.getString("chief_complaint"), rs.getString("subjective"),
                rs.getString("objective"), rs.getString("assessment"), rs.getString("plan"),
                rs.getString("template_data"), EncounterStatus.from(rs.getString("status")),
                Jdbc.instant(rs, "signed_at"), Jdbc.nullableLong(rs, "signed_by"),
                Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "updated_at"));
    }

    public Encounter findById(long id) {
        try (Connection c = database.getConnection()) {
            return Jdbc.one(c, "SELECT * FROM encounters WHERE id=?", this::map, id);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Encounter> findByPatient(long patientId) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM encounters WHERE patient_id=? ORDER BY encounter_date DESC",
                    this::map, patientId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Encounter> findRecent(int limit) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM encounters ORDER BY encounter_date DESC LIMIT ?", this::map, limit);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Encounter findDraft(long patientId, long providerId) {
        try (Connection c = database.getConnection()) {
            return Jdbc.one(c, "SELECT * FROM encounters WHERE patient_id=? AND provider_id=? AND status='DRAFT' "
                    + "ORDER BY encounter_date DESC LIMIT 1", this::map, patientId, providerId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public long insert(Encounter e) {
        return Jdbc.transaction(database, c -> {
            Instant now = Instant.now();
            return Jdbc.insert(c, "INSERT INTO encounters(patient_id, provider_id, appointment_id, specialty, "
                            + "template_id, encounter_date, chief_complaint, subjective, objective, assessment, plan, "
                            + "template_data, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    e.patientId(), e.providerId(), e.appointmentId(), e.specialty(), e.templateId(),
                    e.encounterDate() == null ? now : e.encounterDate(), e.chiefComplaint(), e.subjective(),
                    e.objective(), e.assessment(), e.plan(), e.templateData(), EncounterStatus.DRAFT.name(), now, now);
        });
    }

    public void updateContent(Encounter e) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE encounters SET specialty=?, template_id=?, chief_complaint=?, subjective=?, objective=?, "
                        + "assessment=?, plan=?, template_data=?, updated_at=? WHERE id=?",
                e.specialty(), e.templateId(), e.chiefComplaint(), e.subjective(), e.objective(), e.assessment(),
                e.plan(), e.templateData(), Instant.now(), e.id()));
    }

    public void sign(long id, long userId, EncounterStatus target) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE encounters SET status=?, signed_at=?, signed_by=?, updated_at=? WHERE id=?",
                target.name(), Instant.now(), userId, Instant.now(), id));
    }

    public long insertAmendment(Encounter original, long providerId) {
        return Jdbc.transaction(database, c -> {
            Instant now = Instant.now();
            return Jdbc.insert(c, "INSERT INTO encounters(patient_id, provider_id, appointment_id, specialty, "
                            + "template_id, encounter_date, chief_complaint, subjective, objective, assessment, plan, "
                            + "template_data, status, signed_at, signed_by, created_at, updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    original.patientId(), providerId, original.appointmentId(), original.specialty(),
                    original.templateId(), now, original.chiefComplaint(), original.subjective(),
                    original.objective(), original.assessment(), original.plan(), original.templateData(),
                    EncounterStatus.AMENDED.name(), now, providerId, now, now);
        });
    }

    public void deleteDraft(long id) {
        Jdbc.transaction(database, c -> Jdbc.update(c, "DELETE FROM encounters WHERE id=? AND status='DRAFT'", id));
    }

    public int countDrafts() {
        try (Connection c = database.getConnection()) {
            return (int) Jdbc.count(c, "SELECT COUNT(*) FROM encounters WHERE status='DRAFT'");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
