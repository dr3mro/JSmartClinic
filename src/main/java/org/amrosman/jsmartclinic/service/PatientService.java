package org.amrosman.jsmartclinic.service;

import org.amrosman.jsmartclinic.app.Session;
import org.amrosman.jsmartclinic.domain.Allergy;
import org.amrosman.jsmartclinic.domain.AuditAction;
import org.amrosman.jsmartclinic.domain.Medication;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.Problem;
import org.amrosman.jsmartclinic.domain.Vital;
import org.amrosman.jsmartclinic.repository.ClinicalRepository;
import org.amrosman.jsmartclinic.repository.PatientRepository;
import org.amrosman.jsmartclinic.util.Images;
import org.amrosman.jsmartclinic.util.Validators;

import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

public final class PatientService {

    private final PatientRepository patients;
    private final ClinicalRepository clinical;
    private final Session session;
    private final AuditService audit;

    public PatientService(PatientRepository patients, ClinicalRepository clinical, Session session, AuditService audit) {
        this.patients = patients;
        this.clinical = clinical;
        this.session = session;
        this.audit = audit;
    }

    public List<Patient> search(String query, boolean includeArchived, int page, int pageSize) {
        return patients.search(query, includeArchived, Math.max(0, page) * pageSize, pageSize);
    }

    public int count(String query, boolean includeArchived) {
        return patients.count(query, includeArchived);
    }

    public List<Patient> recent(int limit) {
        return patients.findRecent(limit);
    }

    public int total() {
        return patients.countAll();
    }

    public int newThisWeek() {
        return patients.countNewSince(Instant.now().minus(7, ChronoUnit.DAYS));
    }

    /** Basic demographics (no clinical data) — available to every signed-in role. */
    public Patient basic(long id) {
        return patients.findById(id);
    }

    public Patient findByMrn(String mrn) {
        return patients.findByMrn(mrn);
    }

    /** Opening a clinical chart is audited. */
    public Patient chart(long id) {
        session.require(Permission.VIEW_CLINICAL);
        Patient patient = patients.findById(id);
        if (patient != null) {
            audit.log(AuditAction.VIEW, "patient", id, id, "Chart opened");
        }
        return patient;
    }

    public List<Patient> duplicates(Patient patient) {
        if (patient.firstName() == null || patient.lastName() == null || patient.dateOfBirth() == null) {
            return List.of();
        }
        return patients.findDuplicates(patient.firstName(), patient.lastName(), patient.dateOfBirth());
    }

    public Patient create(Patient patient, boolean allowDuplicate) {
        session.require(Permission.PATIENT_EDIT);
        Validators.requirePatient(patient);
        if (!allowDuplicate && !duplicates(patient).isEmpty()) {
            throw new DuplicatePatientException(duplicates(patient));
        }
        long id = patients.insert(patient);
        audit.log(AuditAction.CREATE, "patient", id, id, "Patient registered");
        return patients.findById(id);
    }

    public Patient update(Patient patient) {
        session.require(Permission.PATIENT_EDIT);
        Validators.requirePatient(patient);
        patients.update(patient);
        audit.log(AuditAction.UPDATE, "patient", patient.id(), patient.id(), "Demographics updated");
        return patients.findById(patient.id());
    }

    public void archive(long id) {
        session.require(Permission.PATIENT_EDIT);
        patients.archive(id);
        audit.log(AuditAction.DELETE, "patient", id, id, "Patient archived");
    }

    public void restore(long id) {
        session.require(Permission.PATIENT_EDIT);
        patients.restore(id);
        audit.log(AuditAction.UPDATE, "patient", id, id, "Patient restored");
    }

    public byte[] photo(File file) {
        return Images.resizeToPng(file, 400);
    }

    public List<Allergy> allergies(long patientId) {
        session.require(Permission.VIEW_CLINICAL);
        return clinical.allergies(patientId);
    }

    public List<Problem> problems(long patientId) {
        session.require(Permission.VIEW_CLINICAL);
        return clinical.problems(patientId, false);
    }

    public List<Medication> medications(long patientId) {
        session.require(Permission.VIEW_CLINICAL);
        return clinical.medications(patientId);
    }

    public List<Vital> vitals(long patientId, int limit) {
        session.require(Permission.VIEW_CLINICAL);
        return clinical.vitals(patientId, limit);
    }

    public Vital latestVital(long patientId) {
        session.require(Permission.VIEW_CLINICAL);
        return clinical.latestVital(patientId);
    }

    public String allergySummary(long patientId) {
        List<Allergy> list = clinical.allergies(patientId);
        if (list.isEmpty()) {
            return "No known allergies";
        }
        return list.stream().map(Allergy::substance).collect(Collectors.joining(", "));
    }

    public String problemSummary(long patientId) {
        return clinical.problems(patientId, true).stream()
                .map(Problem::description).collect(Collectors.joining(", "));
    }

    // ---------- Allergy / problem / medication CRUD ----------

    public Allergy addAllergy(long patientId, String substance, String reaction, org.amrosman.jsmartclinic.domain.Severity severity) {
        session.require(Permission.VIEW_CLINICAL);
        Validators.required(substance, "Substance");
        long id = clinical.addAllergy(patientId, substance.trim(), reaction, severity);
        audit.log(AuditAction.CREATE, "allergy", id, patientId, "Allergy recorded");
        return clinical.allergies(patientId).stream().filter(a -> a.id() == id).findFirst().orElseThrow();
    }

    public void removeAllergy(long id, long patientId) {
        session.require(Permission.VIEW_CLINICAL);
        clinical.removeAllergy(id);
        audit.log(AuditAction.DELETE, "allergy", id, patientId, "Allergy removed");
    }

    public void addProblem(long patientId, String code, String description, java.time.LocalDate onset,
                           org.amrosman.jsmartclinic.domain.ProblemStatus status) {
        session.require(Permission.VIEW_CLINICAL);
        Validators.required(description, "Description");
        long id = clinical.addProblem(patientId, code, description.trim(), onset, status);
        audit.log(AuditAction.CREATE, "problem", id, patientId, "Problem added");
    }

    public void updateProblem(long id, long patientId, String code, String description,
                              java.time.LocalDate onset, org.amrosman.jsmartclinic.domain.ProblemStatus status) {
        session.require(Permission.VIEW_CLINICAL);
        Validators.required(description, "Description");
        clinical.updateProblem(id, code, description.trim(), onset, status);
        audit.log(AuditAction.UPDATE, "problem", id, patientId, "Problem updated");
    }

    public void removeProblem(long id, long patientId) {
        session.require(Permission.VIEW_CLINICAL);
        clinical.removeProblem(id);
        audit.log(AuditAction.DELETE, "problem", id, patientId, "Problem removed");
    }

    public void addMedication(long patientId, String name, String dose, String route, String frequency,
                              java.time.LocalDate start, java.time.LocalDate end, boolean active) {
        session.require(Permission.VIEW_CLINICAL);
        Validators.required(name, "Medication name");
        long id = clinical.addMedication(patientId, name.trim(), dose, route, frequency, start, end, active);
        audit.log(AuditAction.CREATE, "medication", id, patientId, "Medication added");
    }

    public void updateMedication(long id, long patientId, String name, String dose, String route, String frequency,
                                 java.time.LocalDate start, java.time.LocalDate end, boolean active) {
        session.require(Permission.VIEW_CLINICAL);
        Validators.required(name, "Medication name");
        clinical.updateMedication(id, name.trim(), dose, route, frequency, start, end, active);
        audit.log(AuditAction.UPDATE, "medication", id, patientId, "Medication updated");
    }

    public void deactivateMedication(long id, long patientId) {
        session.require(Permission.VIEW_CLINICAL);
        clinical.deactivateMedication(id);
        audit.log(AuditAction.UPDATE, "medication", id, patientId, "Medication stopped");
    }

    /** Thrown so the UI can ask the user to confirm a possible duplicate. */
    public static final class DuplicatePatientException extends RuntimeException {
        private final List<Patient> duplicates;

        public DuplicatePatientException(List<Patient> duplicates) {
            super("Possible duplicate patient");
            this.duplicates = duplicates;
        }

        public List<Patient> duplicates() {
            return duplicates;
        }
    }
}
