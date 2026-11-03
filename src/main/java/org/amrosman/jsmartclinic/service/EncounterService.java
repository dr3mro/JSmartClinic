package org.amrosman.jsmartclinic.service;

import org.amrosman.jsmartclinic.app.Session;
import org.amrosman.jsmartclinic.domain.AuditAction;
import org.amrosman.jsmartclinic.domain.Encounter;
import org.amrosman.jsmartclinic.domain.EncounterDiagnosis;
import org.amrosman.jsmartclinic.domain.EncounterStatus;
import org.amrosman.jsmartclinic.domain.Vital;
import org.amrosman.jsmartclinic.repository.ClinicalRepository;
import org.amrosman.jsmartclinic.repository.EncounterRepository;

import java.time.Instant;
import java.util.List;

public final class EncounterService {

    private final EncounterRepository encounters;
    private final ClinicalRepository clinical;
    private final Session session;
    private final AuditService audit;
    private final TemplateService templates;

    public EncounterService(EncounterRepository encounters, ClinicalRepository clinical, Session session,
                            AuditService audit, TemplateService templates) {
        this.encounters = encounters;
        this.clinical = clinical;
        this.session = session;
        this.audit = audit;
        this.templates = templates;
    }

    public List<Encounter> forPatient(long patientId) {
        session.require(Permission.VIEW_CLINICAL);
        return encounters.findByPatient(patientId);
    }

    public Encounter byId(long id) {
        session.require(Permission.VIEW_CLINICAL);
        return encounters.findById(id);
    }

    public List<Encounter> recent(int limit) {
        session.require(Permission.VIEW_CLINICAL);
        return encounters.findRecent(limit);
    }

    /** Starts a new draft encounter, reusing an existing unsigned draft for the same patient. */
    public Encounter start(long patientId, Long appointmentId, String specialty, String templateId) {
        session.require(Permission.ENCOUNTERS);
        Encounter draft = encounters.findDraft(patientId, session.userId());
        if (draft != null) {
            return draft;
        }
        String specialtyValue = specialty == null || specialty.isBlank() ? templates.specialtyForUser(session.current()) : specialty;
        String templateValue = templateId == null || templateId.isBlank()
                ? templates.defaultFor(specialtyValue).map(TemplateService.Template::id).orElse(null)
                : templateId;
        long id = encounters.insert(new Encounter(0, patientId, session.userId(), appointmentId, null, specialtyValue,
                templateValue, Instant.now(), "", "", "", "", "", "{}", EncounterStatus.DRAFT, null, null, null, null));
        audit.log(AuditAction.CREATE, "encounter", id, patientId, "Encounter started");
        return encounters.findById(id);
    }

    public Encounter save(Encounter encounter) {
        session.require(Permission.ENCOUNTERS);
        Encounter existing = encounters.findById(encounter.id());
        if (existing == null) {
            throw new IllegalArgumentException("Encounter not found");
        }
        if (existing.signedAt() != null) {
            throw new IllegalStateException("Signed encounters are read-only. Create an addendum instead.");
        }
        if (encounter.chiefComplaint() == null || encounter.subjective() == null || encounter.objective() == null
                || encounter.assessment() == null || encounter.plan() == null) {
            throw new IllegalArgumentException("Encounter content fields cannot be null");
        }
        encounters.updateContent(encounter);
        return encounters.findById(encounter.id());
    }

    public Encounter sign(long encounterId) {
        session.require(Permission.ENCOUNTERS);
        Encounter encounter = encounters.findById(encounterId);
        if (encounter == null) {
            throw new IllegalArgumentException("Encounter not found");
        }
        if (encounter.signedAt() != null) {
            throw new IllegalStateException("Encounter is already signed");
        }
        if (encounter.chiefComplaint() == null && encounter.assessment() == null && encounter.plan() == null) {
            throw new IllegalStateException("Add clinical content before signing");
        }
        encounters.sign(encounterId, session.userId(), EncounterStatus.SIGNED);
        if (encounter.amendsEncounterId() != null) {
            encounters.markAmended(encounter.amendsEncounterId());
        }
        audit.log(AuditAction.UPDATE, "encounter", encounterId, encounter.patientId(), "Encounter signed");
        return encounters.findById(encounterId);
    }

    public Encounter amend(long originalId) {
        session.require(Permission.ENCOUNTERS);
        Encounter original = encounters.findById(originalId);
        if (original == null) {
            throw new IllegalArgumentException("Encounter not found");
        }
        if (!original.signed()) {
            throw new IllegalStateException("Only signed encounters can be amended");
        }
        long id = encounters.insertAmendment(original, session.userId());
        audit.log(AuditAction.CREATE, "encounter", id, original.patientId(), "Addendum to encounter " + originalId);
        return encounters.findById(id);
    }

    public void deleteDraft(long encounterId) {
        session.require(Permission.ENCOUNTERS);
        encounters.deleteDraft(encounterId);
        audit.log(AuditAction.DELETE, "encounter", encounterId, null, "Draft discarded");
    }

    public int drafts() {
        session.require(Permission.VIEW_CLINICAL);
        return encounters.countDrafts();
    }

    // ---------- Diagnoses ----------

    public List<EncounterDiagnosis> diagnoses(long encounterId) {
        session.require(Permission.VIEW_CLINICAL);
        return clinical.diagnoses(encounterId);
    }

    public void addDiagnosis(long encounterId, long patientId, String code, String description, boolean primary) {
        session.require(Permission.ENCOUNTERS);
        Encounter encounter = encounters.findById(encounterId);
        if (encounter == null || encounter.status() == EncounterStatus.SIGNED) {
            throw new IllegalStateException("Encounter is not editable");
        }
        long id = clinical.addDiagnosis(encounterId, code, description, primary);
        audit.log(AuditAction.CREATE, "diagnosis", id, patientId, "Diagnosis added");
    }

    public void removeDiagnosis(long diagnosisId, long patientId) {
        session.require(Permission.ENCOUNTERS);
        clinical.removeDiagnosis(diagnosisId);
        audit.log(AuditAction.DELETE, "diagnosis", diagnosisId, patientId, "Diagnosis removed");
    }

    // ---------- Vitals ----------

    public List<Vital> vitals(long patientId, int limit) {
        session.require(Permission.VIEW_CLINICAL);
        return clinical.vitals(patientId, limit);
    }

    public List<Vital> vitalsForEncounter(long encounterId) {
        session.require(Permission.VIEW_CLINICAL);
        return clinical.vitalsForEncounter(encounterId);
    }

    public long addVital(Vital vital) {
        session.require(Permission.VITALS);
        if (vital.systolic() != null && (vital.systolic() < 40 || vital.systolic() > 300)) {
            throw new IllegalArgumentException("Systolic blood pressure is out of range");
        }
        if (vital.diastolic() != null && (vital.diastolic() < 20 || vital.diastolic() > 200)) {
            throw new IllegalArgumentException("Diastolic blood pressure is out of range");
        }
        if (vital.spo2() != null && (vital.spo2() < 40 || vital.spo2() > 100)) {
            throw new IllegalArgumentException("SpO2 must be between 40 and 100");
        }
        long id = clinical.addVital(vital);
        audit.log(AuditAction.CREATE, "vital", id, vital.patientId(), "Vitals recorded");
        return id;
    }

    public void removeVital(long id, long patientId) {
        session.require(Permission.VITALS);
        clinical.removeVital(id);
        audit.log(AuditAction.DELETE, "vital", id, patientId, "Vital removed");
    }
}
