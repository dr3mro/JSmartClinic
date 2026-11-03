package org.amrosman.jsmartclinic.repository;

import org.amrosman.jsmartclinic.db.Database;
import org.amrosman.jsmartclinic.domain.PatientDocument;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public final class DocumentRepository {

    private final Database database;

    public DocumentRepository(Database database) {
        this.database = database;
    }

    private PatientDocument map(ResultSet rs) throws SQLException {
        return new PatientDocument(rs.getLong("id"), rs.getLong("patient_id"),
                Jdbc.nullableLong(rs, "encounter_id"), rs.getString("title"), rs.getString("mime_type"),
                rs.getString("file_name"), rs.getString("sha256"), Jdbc.nullableLong(rs, "uploaded_by"),
                Jdbc.instant(rs, "created_at"));
    }

    public List<PatientDocument> findByPatient(long patientId) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM documents WHERE patient_id=? ORDER BY created_at DESC",
                    this::map, patientId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public long insert(long patientId, Long encounterId, String title, String mimeType, String fileName,
                       String sha256, Long uploadedBy) {
        return Jdbc.transaction(database, c -> Jdbc.insert(c,
                "INSERT INTO documents(patient_id, encounter_id, title, mime_type, file_name, sha256, uploaded_by, "
                        + "created_at) VALUES (?,?,?,?,?,?,?,?)",
                patientId, encounterId, title, mimeType, fileName, sha256, uploadedBy,
                java.time.Instant.now()));
    }

    public void delete(long id) {
        Jdbc.transaction(database, c -> Jdbc.update(c, "DELETE FROM documents WHERE id=?", id));
    }
}
