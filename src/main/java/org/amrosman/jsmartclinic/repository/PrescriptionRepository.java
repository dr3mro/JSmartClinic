package org.amrosman.jsmartclinic.repository;

import org.amrosman.jsmartclinic.db.Database;
import org.amrosman.jsmartclinic.domain.Prescription;
import org.amrosman.jsmartclinic.domain.PrescriptionItem;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

public final class PrescriptionRepository {

    private final Database database;

    public PrescriptionRepository(Database database) {
        this.database = database;
    }

    private Prescription map(ResultSet rs) throws SQLException {
        return new Prescription(rs.getLong("id"), rs.getLong("patient_id"), Jdbc.nullableLong(rs, "encounter_id"),
                rs.getLong("prescriber_id"), Jdbc.instant(rs, "issued_at"), rs.getString("notes"),
                Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "updated_at"));
    }

    private PrescriptionItem item(ResultSet rs) throws SQLException {
        return new PrescriptionItem(rs.getLong("id"), rs.getLong("prescription_id"), rs.getString("drug_name"),
                rs.getString("strength"), rs.getString("form"), rs.getString("dose"), rs.getString("frequency"),
                rs.getString("duration"), rs.getString("quantity"), rs.getString("instructions"));
    }

    public Prescription findById(long id) {
        try (Connection c = database.getConnection()) {
            return Jdbc.one(c, "SELECT * FROM prescriptions WHERE id=?", this::map, id);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Prescription> findByPatient(long patientId) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM prescriptions WHERE patient_id=? ORDER BY issued_at DESC",
                    this::map, patientId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Prescription> findRecent(int limit) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM prescriptions ORDER BY issued_at DESC LIMIT ?", this::map, limit);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<PrescriptionItem> items(long prescriptionId) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM prescription_items WHERE prescription_id=? ORDER BY id",
                    this::item, prescriptionId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public long insert(long patientId, Long encounterId, long prescriberId, String notes, List<PrescriptionItem> items) {
        return Jdbc.transaction(database, c -> {
            Instant now = Instant.now();
            long id = Jdbc.insert(c, "INSERT INTO prescriptions(patient_id, encounter_id, prescriber_id, issued_at, "
                            + "notes, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                    patientId, encounterId, prescriberId, now, notes, now, now);
            for (PrescriptionItem item : items) {
                Jdbc.insert(c, "INSERT INTO prescription_items(prescription_id, drug_name, strength, form, dose, "
                                + "frequency, duration, quantity, instructions) VALUES (?,?,?,?,?,?,?,?,?)",
                        id, item.drugName(), item.strength(), item.form(), item.dose(), item.frequency(),
                        item.duration(), item.quantity(), item.instructions());
            }
            return id;
        });
    }
}
