package org.amrosman.jsmartclinic.service;

import org.amrosman.jsmartclinic.app.Session;
import org.amrosman.jsmartclinic.domain.Allergy;
import org.amrosman.jsmartclinic.domain.AuditAction;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.Prescription;
import org.amrosman.jsmartclinic.domain.PrescriptionItem;
import org.amrosman.jsmartclinic.repository.ClinicalRepository;
import org.amrosman.jsmartclinic.repository.PrescriptionRepository;
import org.amrosman.jsmartclinic.util.Validators;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PrescriptionService {

    private final PrescriptionRepository prescriptions;
    private final ClinicalRepository clinical;
    private final Session session;
    private final AuditService audit;

    public PrescriptionService(PrescriptionRepository prescriptions, ClinicalRepository clinical,
                               Session session, AuditService audit) {
        this.prescriptions = prescriptions;
        this.clinical = clinical;
        this.session = session;
        this.audit = audit;
    }

    public List<Prescription> forPatient(long patientId) {
        session.require(Permission.VIEW_CLINICAL);
        return prescriptions.findByPatient(patientId);
    }

    public List<Prescription> recent(int limit) {
        session.require(Permission.VIEW_CLINICAL);
        return prescriptions.findRecent(limit);
    }

    public Prescription byId(long id) {
        session.require(Permission.VIEW_CLINICAL);
        return prescriptions.findById(id);
    }

    public List<PrescriptionItem> items(long prescriptionId) {
        session.require(Permission.VIEW_CLINICAL);
        return prescriptions.items(prescriptionId);
    }

    public long create(long patientId, Long encounterId, String notes, List<PrescriptionItem> items) {
        session.require(Permission.PRESCRIBE);
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Add at least one medication");
        }
        for (PrescriptionItem item : items) {
            Validators.required(item.drugName(), "Drug name");
        }
        long id = prescriptions.insert(patientId, encounterId, session.userId(), notes, items);
        audit.log(AuditAction.CREATE, "prescription", id, patientId, items.size() + " item(s)");
        return id;
    }

    public long renew(long prescriptionId) {
        session.require(Permission.PRESCRIBE);
        Prescription original = prescriptions.findById(prescriptionId);
        if (original == null) {
            throw new IllegalArgumentException("Prescription not found");
        }
        List<PrescriptionItem> items = prescriptions.items(prescriptionId).stream()
                .map(i -> new PrescriptionItem(0, 0, i.drugName(), i.strength(), i.form(), i.dose(),
                        i.frequency(), i.duration(), i.quantity(), i.instructions()))
                .toList();
        String notes = (original.notes() == null || original.notes().isBlank() ? "" : original.notes() + " — ")
                + "Renewal of prescription #" + prescriptionId;
        long id = prescriptions.insert(original.patientId(), original.encounterId(), session.userId(), notes, items);
        audit.log(AuditAction.CREATE, "prescription", id, original.patientId(), "Renewal");
        return id;
    }

    /** Basic (deliberately simple) substring cross-check between drug names and recorded allergies. */
    public List<String> allergyWarnings(long patientId, List<PrescriptionItem> items) {
        session.require(Permission.PRESCRIBE);
        List<Allergy> allergies = clinical.allergies(patientId);
        List<String> warnings = new ArrayList<>();
        for (PrescriptionItem item : items) {
            String drug = item.drugName() == null ? "" : item.drugName().toLowerCase(Locale.ROOT);
            for (Allergy allergy : allergies) {
                String substance = allergy.substance() == null ? "" : allergy.substance().toLowerCase(Locale.ROOT);
                if (!substance.isBlank() && drug.contains(substance)) {
                    warnings.add(item.drugName() + " may interact with recorded allergy: " + allergy.substance());
                }
            }
        }
        return warnings;
    }

    public String allergyWarningText(long patientId, List<PrescriptionItem> items) {
        List<String> warnings = allergyWarnings(patientId, items);
        return warnings.isEmpty() ? null : String.join("\n", warnings);
    }
}
