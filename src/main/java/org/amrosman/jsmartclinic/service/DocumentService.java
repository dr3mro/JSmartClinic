package org.amrosman.jsmartclinic.service;

import org.amrosman.jsmartclinic.app.Session;
import org.amrosman.jsmartclinic.db.Database;
import org.amrosman.jsmartclinic.domain.AuditAction;
import org.amrosman.jsmartclinic.domain.PatientDocument;
import org.amrosman.jsmartclinic.repository.DocumentRepository;
import org.amrosman.jsmartclinic.util.Sha256;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;

public final class DocumentService {

    private final DocumentRepository documents;
    private final Database database;
    private final Session session;
    private final AuditService audit;

    public DocumentService(DocumentRepository documents, Database database, Session session, AuditService audit) {
        this.documents = documents;
        this.database = database;
        this.session = session;
        this.audit = audit;
    }

    public List<PatientDocument> forPatient(long patientId) {
        session.require(Permission.DOCUMENTS);
        return documents.findByPatient(patientId);
    }

    public PatientDocument attach(long patientId, Long encounterId, File file, String title) {
        session.require(Permission.DOCUMENTS);
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String sha = Sha256.hex(bytes);
            String extension = extension(file.getName());
            Path target = database.documentsDir().resolve(sha + extension);
            if (!Files.exists(target)) {
                Files.copy(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                Database.restrictPermissions(target);
            }
            String mime = Files.probeContentType(file.toPath());
            long id = documents.insert(patientId, encounterId,
                    title == null || title.isBlank() ? file.getName() : title.trim(),
                    mime == null ? "application/octet-stream" : mime, file.getName(), sha, session.userId());
            audit.log(AuditAction.CREATE, "document", id, patientId, "Document attached");
            return documents.findByPatient(patientId).stream().filter(d -> d.id() == id).findFirst().orElseThrow();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to attach document: " + e.getMessage(), e);
        }
    }

    public Path fileFor(PatientDocument document) {
        session.require(Permission.DOCUMENTS);
        String extension = extension(document.fileName());
        return database.documentsDir().resolve(document.sha256() + extension);
    }

    public void delete(PatientDocument document) {
        session.require(Permission.DOCUMENTS);
        documents.delete(document.id());
        audit.log(AuditAction.DELETE, "document", document.id(), document.patientId(), "Document removed");
    }

    public Path exportsDir() {
        Path dir = database.dataDir().resolve("exports");
        try {
            Files.createDirectories(dir);
            Database.restrictPermissions(dir);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return dir;
    }

    public Path uniqueExport(String prefix, String extension) {
        return exportsDir().resolve(prefix + "-" + Instant.now().toEpochMilli() + extension);
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot);
    }
}
