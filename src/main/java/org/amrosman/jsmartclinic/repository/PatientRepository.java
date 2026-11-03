package org.amrosman.jsmartclinic.repository;

import org.amrosman.jsmartclinic.db.Database;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.Sex;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PatientRepository {

    private static final String COLUMNS = "id, mrn, first_name, last_name, date_of_birth, sex, phone, email, address, "
            + "city, national_id, blood_type, marital_status, occupation, emergency_name, emergency_phone, "
            + "emergency_relation, insurance_provider, insurance_number, photo, notes, status, deleted_at, "
            + "created_at, updated_at";

    private final Database database;

    public PatientRepository(Database database) {
        this.database = database;
    }

    private Patient map(ResultSet rs) throws SQLException {
        return new Patient(
                rs.getLong("id"),
                rs.getString("mrn"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                Jdbc.date(rs, "date_of_birth"),
                Sex.from(rs.getString("sex")),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("address"),
                rs.getString("city"),
                rs.getString("national_id"),
                rs.getString("blood_type"),
                rs.getString("marital_status"),
                rs.getString("occupation"),
                rs.getString("emergency_name"),
                rs.getString("emergency_phone"),
                rs.getString("emergency_relation"),
                rs.getString("insurance_provider"),
                rs.getString("insurance_number"),
                rs.getBytes("photo"),
                rs.getString("notes"),
                rs.getString("status"),
                Jdbc.instant(rs, "deleted_at"),
                Jdbc.instant(rs, "created_at"),
                Jdbc.instant(rs, "updated_at"));
    }

    public Patient findById(long id) {
        try (Connection c = database.getConnection()) {
            return Jdbc.one(c, "SELECT " + COLUMNS + " FROM patients WHERE id = ?", this::map, id);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Patient findByMrn(String mrn) {
        try (Connection c = database.getConnection()) {
            return Jdbc.one(c, "SELECT " + COLUMNS + " FROM patients WHERE mrn = ?", this::map, mrn);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Patient> search(String query, boolean includeArchived, int offset, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        String like = "%" + q + "%";
        String sql = "SELECT " + COLUMNS + " FROM patients WHERE "
                + (includeArchived ? "1=1" : "deleted_at IS NULL")
                + " AND (? = '' OR LOWER(first_name || ' ' || last_name) LIKE ? OR LOWER(mrn) LIKE ? "
                + "OR COALESCE(phone,'') LIKE ?) ORDER BY last_name, first_name LIMIT ? OFFSET ?";
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, sql, this::map, q, like, like, like, limit, offset);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public int count(String query, boolean includeArchived) {
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        String like = "%" + q + "%";
        String sql = "SELECT COUNT(*) FROM patients WHERE "
                + (includeArchived ? "1=1" : "deleted_at IS NULL")
                + " AND (? = '' OR LOWER(first_name || ' ' || last_name) LIKE ? OR LOWER(mrn) LIKE ? "
                + "OR COALESCE(phone,'') LIKE ?)";
        try (Connection c = database.getConnection()) {
            return (int) Jdbc.count(c, sql, q, like, like, like);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Patient> findRecent(int limit) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT " + COLUMNS + " FROM patients WHERE deleted_at IS NULL "
                    + "ORDER BY created_at DESC LIMIT ?", this::map, limit);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Patient> findDuplicates(String firstName, String lastName, LocalDate dob) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT " + COLUMNS + " FROM patients WHERE deleted_at IS NULL "
                            + "AND LOWER(first_name)=LOWER(?) AND LOWER(last_name)=LOWER(?) AND date_of_birth=?",
                    this::map, firstName, lastName, dob);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public long insert(Patient p) {
        return Jdbc.transaction(database, c -> {
            String mrn = p.mrn() == null || p.mrn().isBlank() ? nextMrn(c) : p.mrn();
            Instant now = Instant.now();
            return Jdbc.insert(c, "INSERT INTO patients(mrn, first_name, last_name, date_of_birth, sex, phone, email, "
                            + "address, city, national_id, blood_type, marital_status, occupation, emergency_name, "
                            + "emergency_phone, emergency_relation, insurance_provider, insurance_number, photo, notes, "
                            + "status, created_at, updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    mrn, p.firstName(), p.lastName(), p.dateOfBirth(), p.sex().name(), p.phone(), p.email(),
                    p.address(), p.city(), p.nationalId(), p.bloodType(), p.maritalStatus(), p.occupation(),
                    p.emergencyName(), p.emergencyPhone(), p.emergencyRelation(), p.insuranceProvider(),
                    p.insuranceNumber(), p.photo(), p.notes(), p.status() == null ? "ACTIVE" : p.status(), now, now);
        });
    }

    public void update(Patient p) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE patients SET first_name=?, last_name=?, date_of_birth=?, sex=?, phone=?, email=?, address=?, "
                        + "city=?, national_id=?, blood_type=?, marital_status=?, occupation=?, emergency_name=?, "
                        + "emergency_phone=?, emergency_relation=?, insurance_provider=?, insurance_number=?, photo=?, "
                        + "notes=?, status=?, updated_at=? WHERE id=?",
                p.firstName(), p.lastName(), p.dateOfBirth(), p.sex().name(), p.phone(), p.email(), p.address(),
                p.city(), p.nationalId(), p.bloodType(), p.maritalStatus(), p.occupation(), p.emergencyName(),
                p.emergencyPhone(), p.emergencyRelation(), p.insuranceProvider(), p.insuranceNumber(), p.photo(),
                p.notes(), p.status(), Instant.now(), p.id()));
    }

    public void archive(long id) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE patients SET deleted_at=?, status='ARCHIVED', updated_at=? WHERE id=?",
                Instant.now(), Instant.now(), id));
    }

    public void restore(long id) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE patients SET deleted_at=NULL, status='ACTIVE', updated_at=? WHERE id=?",
                Instant.now(), id));
    }

    public int countNewSince(Instant since) {
        try (Connection c = database.getConnection()) {
            return (int) Jdbc.count(c, "SELECT COUNT(*) FROM patients WHERE deleted_at IS NULL AND created_at >= ?", since);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public int countAll() {
        try (Connection c = database.getConnection()) {
            return (int) Jdbc.count(c, "SELECT COUNT(*) FROM patients WHERE deleted_at IS NULL");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private String nextMrn(Connection c) throws SQLException {
        long base = Jdbc.count(c, "SELECT COALESCE(MAX(id),0)+1 FROM patients");
        String candidate = "P-" + String.format("%06d", base);
        while (Jdbc.count(c, "SELECT COUNT(*) FROM patients WHERE mrn = ?", candidate) > 0) {
            base++;
            candidate = "P-" + String.format("%06d", base);
        }
        return candidate;
    }
}
