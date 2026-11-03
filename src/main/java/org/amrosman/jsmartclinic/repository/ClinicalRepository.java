package org.amrosman.jsmartclinic.repository;

import org.amrosman.jsmartclinic.db.Database;
import org.amrosman.jsmartclinic.domain.Allergy;
import org.amrosman.jsmartclinic.domain.EncounterDiagnosis;
import org.amrosman.jsmartclinic.domain.Medication;
import org.amrosman.jsmartclinic.domain.Problem;
import org.amrosman.jsmartclinic.domain.ProblemStatus;
import org.amrosman.jsmartclinic.domain.Severity;
import org.amrosman.jsmartclinic.domain.Vital;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/** Clinical lists that hang off a patient or encounter. */
public final class ClinicalRepository {

    private final Database database;

    public ClinicalRepository(Database database) {
        this.database = database;
    }

    // ---------- Allergies ----------

    private Allergy allergy(ResultSet rs) throws SQLException {
        return new Allergy(rs.getLong("id"), rs.getLong("patient_id"), rs.getString("substance"),
                rs.getString("reaction"), Severity.from(rs.getString("severity")), rs.getInt("active") == 1,
                Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "updated_at"));
    }

    public List<Allergy> allergies(long patientId) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM allergies WHERE patient_id=? AND active=1 ORDER BY substance",
                    this::allergy, patientId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public long addAllergy(long patientId, String substance, String reaction, Severity severity) {
        return Jdbc.transaction(database, c -> {
            Instant now = Instant.now();
            return Jdbc.insert(c, "INSERT INTO allergies(patient_id, substance, reaction, severity, active, "
                            + "created_at, updated_at) VALUES (?,?,?,?,1,?,?)",
                    patientId, substance, reaction, severity == null ? null : severity.name(), now, now);
        });
    }

    public void updateAllergy(long id, String substance, String reaction, Severity severity) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE allergies SET substance=?, reaction=?, severity=?, updated_at=? WHERE id=?",
                substance, reaction, severity == null ? null : severity.name(), Instant.now(), id));
    }

    public void removeAllergy(long id) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE allergies SET active=0, updated_at=? WHERE id=?", Instant.now(), id));
    }

    // ---------- Problems ----------

    private Problem problem(ResultSet rs) throws SQLException {
        return new Problem(rs.getLong("id"), rs.getLong("patient_id"), rs.getString("icd10_code"),
                rs.getString("description"), Jdbc.date(rs, "onset_date"), ProblemStatus.from(rs.getString("status")),
                Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "updated_at"));
    }

    public List<Problem> problems(long patientId, boolean activeOnly) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM problems WHERE patient_id=? "
                    + (activeOnly ? "AND status='ACTIVE' " : "") + "ORDER BY status, description",
                    this::problem, patientId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public long addProblem(long patientId, String code, String description, java.time.LocalDate onset,
                           ProblemStatus status) {
        return Jdbc.transaction(database, c -> {
            Instant now = Instant.now();
            return Jdbc.insert(c, "INSERT INTO problems(patient_id, icd10_code, description, onset_date, status, "
                            + "created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                    patientId, code, description, onset, (status == null ? ProblemStatus.ACTIVE : status).name(), now, now);
        });
    }

    public void updateProblem(long id, String code, String description, java.time.LocalDate onset, ProblemStatus status) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE problems SET icd10_code=?, description=?, onset_date=?, status=?, updated_at=? WHERE id=?",
                code, description, onset, status.name(), Instant.now(), id));
    }

    public void removeProblem(long id) {
        Jdbc.transaction(database, c -> Jdbc.update(c, "DELETE FROM problems WHERE id=?", id));
    }

    // ---------- Medications ----------

    private Medication medication(ResultSet rs) throws SQLException {
        return new Medication(rs.getLong("id"), rs.getLong("patient_id"), rs.getString("name"),
                rs.getString("dose"), rs.getString("route"), rs.getString("frequency"),
                Jdbc.date(rs, "start_date"), Jdbc.date(rs, "end_date"), rs.getInt("active") == 1,
                Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "updated_at"));
    }

    public List<Medication> medications(long patientId) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM medications WHERE patient_id=? ORDER BY active DESC, name",
                    this::medication, patientId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public long addMedication(long patientId, String name, String dose, String route, String frequency,
                              java.time.LocalDate start, java.time.LocalDate end, boolean active) {
        return Jdbc.transaction(database, c -> {
            Instant now = Instant.now();
            return Jdbc.insert(c, "INSERT INTO medications(patient_id, name, dose, route, frequency, start_date, "
                            + "end_date, active, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    patientId, name, dose, route, frequency, start, end, active, now, now);
        });
    }

    public void updateMedication(long id, String name, String dose, String route, String frequency,
                                 java.time.LocalDate start, java.time.LocalDate end, boolean active) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE medications SET name=?, dose=?, route=?, frequency=?, start_date=?, end_date=?, active=?, "
                        + "updated_at=? WHERE id=?",
                name, dose, route, frequency, start, end, active, Instant.now(), id));
    }

    public void deactivateMedication(long id) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE medications SET active=0, updated_at=? WHERE id=?", Instant.now(), id));
    }

    // ---------- Vitals ----------

    private Vital vital(ResultSet rs) throws SQLException {
        return new Vital(rs.getLong("id"), rs.getLong("patient_id"), Jdbc.nullableLong(rs, "encounter_id"),
                Jdbc.instant(rs, "measured_at"), Jdbc.nullableInt(rs, "systolic"), Jdbc.nullableInt(rs, "diastolic"),
                Jdbc.nullableInt(rs, "heart_rate"), Jdbc.nullableInt(rs, "resp_rate"),
                Jdbc.nullableDouble(rs, "temperature_c"), Jdbc.nullableInt(rs, "spo2"),
                Jdbc.nullableDouble(rs, "weight_kg"), Jdbc.nullableDouble(rs, "height_cm"),
                Jdbc.nullableDouble(rs, "glucose_mgdl"), Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "updated_at"));
    }

    public List<Vital> vitals(long patientId, int limit) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM vitals WHERE patient_id=? ORDER BY measured_at DESC LIMIT ?",
                    this::vital, patientId, limit);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Vital> vitalsForEncounter(long encounterId) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM vitals WHERE encounter_id=? ORDER BY measured_at",
                    this::vital, encounterId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Vital latestVital(long patientId) {
        List<Vital> vitals = vitals(patientId, 1);
        return vitals.isEmpty() ? null : vitals.get(0);
    }

    public long addVital(Vital v) {
        return Jdbc.transaction(database, c -> {
            Instant now = Instant.now();
            return Jdbc.insert(c, "INSERT INTO vitals(patient_id, encounter_id, measured_at, systolic, diastolic, "
                            + "heart_rate, resp_rate, temperature_c, spo2, weight_kg, height_cm, glucose_mgdl, "
                            + "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    v.patientId(), v.encounterId(), v.measuredAt() == null ? now : v.measuredAt(), v.systolic(),
                    v.diastolic(), v.heartRate(), v.respRate(), v.temperatureC(), v.spo2(), v.weightKg(),
                    v.heightCm(), v.glucoseMgdl(), now, now);
        });
    }

    public Vital findVital(long id) {
        try (Connection c = database.getConnection()) {
            return Jdbc.one(c, "SELECT * FROM vitals WHERE id=?", this::vital, id);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void removeVital(long id) {
        Jdbc.transaction(database, c -> Jdbc.update(c, "DELETE FROM vitals WHERE id=?", id));
    }

    // ---------- Encounter diagnoses ----------

    private EncounterDiagnosis diagnosis(ResultSet rs) throws SQLException {
        return new EncounterDiagnosis(rs.getLong("id"), rs.getLong("encounter_id"), rs.getString("icd10_code"),
                rs.getString("description"), rs.getInt("is_primary") == 1);
    }

    public List<EncounterDiagnosis> diagnoses(long encounterId) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM encounter_diagnoses WHERE encounter_id=? ORDER BY is_primary DESC",
                    this::diagnosis, encounterId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public long addDiagnosis(long encounterId, String code, String description, boolean primary) {
        return Jdbc.transaction(database, c -> Jdbc.insert(c,
                "INSERT INTO encounter_diagnoses(encounter_id, icd10_code, description, is_primary) VALUES (?,?,?,?)",
                encounterId, code, description, primary));
    }

    public EncounterDiagnosis findDiagnosis(long id) {
        try (Connection c = database.getConnection()) {
            return Jdbc.one(c, "SELECT * FROM encounter_diagnoses WHERE id=?", this::diagnosis, id);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void removeDiagnosis(long id) {
        Jdbc.transaction(database, c -> Jdbc.update(c, "DELETE FROM encounter_diagnoses WHERE id=?", id));
    }
}
