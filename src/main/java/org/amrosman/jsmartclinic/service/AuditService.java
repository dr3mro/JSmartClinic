package org.amrosman.jsmartclinic.service;

import org.amrosman.jsmartclinic.app.Session;
import org.amrosman.jsmartclinic.domain.AuditAction;
import org.amrosman.jsmartclinic.domain.AuditEvent;
import org.amrosman.jsmartclinic.repository.AuditRepository;

import java.util.List;

/** Append-only audit trail. Never logs passwords or clinical free text. */
public final class AuditService {

    private final AuditRepository repository;
    private final Session session;

    public AuditService(AuditRepository repository, Session session) {
        this.repository = repository;
        this.session = session;
    }

    public void log(AuditAction action, String entity) {
        log(action, entity, null, null, null);
    }

    public void log(AuditAction action, String entity, Long entityId, Long patientId, String details) {
        repository.insert(session.userId() <= 0 ? null : session.userId(), action, entity, entityId, patientId, details);
    }

    public List<AuditEvent> recent(int limit) {
        session.require(Permission.AUDIT);
        return repository.recent(limit);
    }

    public List<AuditEvent> forPatient(long patientId, int limit) {
        session.require(Permission.AUDIT);
        return repository.forPatient(patientId, limit);
    }
}
