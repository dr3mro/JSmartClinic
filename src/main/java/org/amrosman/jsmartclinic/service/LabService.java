package org.amrosman.jsmartclinic.service;

import org.amrosman.jsmartclinic.app.Session;
import org.amrosman.jsmartclinic.domain.AuditAction;
import org.amrosman.jsmartclinic.domain.LabFlag;
import org.amrosman.jsmartclinic.domain.LabOrderStatus;
import org.amrosman.jsmartclinic.domain.LabTest;
import org.amrosman.jsmartclinic.repository.LabRepository;

import java.util.List;

public final class LabService {

    private final LabRepository labs;
    private final Session session;
    private final AuditService audit;

    public LabService(LabRepository labs, Session session, AuditService audit) {
        this.labs = labs;
        this.session = session;
        this.audit = audit;
    }

    public List<LabTest> tests() {
        session.require(Permission.LABS);
        return labs.tests();
    }

    public List<LabTest> testsForOrdering() {
        session.require(Permission.LABS);
        return labs.tests();
    }

    public void saveTest(long id, String code, String name, String unit, Double low, Double high, String category) {
        saveTest(id, code, name, unit, low, high, null, null, category);
    }

    public void saveTest(long id, String code, String name, String unit, Double low, Double high,
                         Double criticalLow, Double criticalHigh, String category) {
        session.require(Permission.MANAGE_USERS_SETTINGS);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Test name is required");
        }
        if (criticalLow != null && criticalHigh != null && criticalLow > criticalHigh) {
            throw new IllegalArgumentException("Critical low must be below critical high");
        }
        if (id <= 0) {
            labs.insertTest(code, name, unit, low, high, criticalLow, criticalHigh, category);
        } else {
            labs.updateTest(id, code, name, unit, low, high, criticalLow, criticalHigh, category);
        }
        audit.log(AuditAction.UPDATE, "lab_test", id, null, "Lab catalogue updated");
    }

    public void deleteTest(long id) {
        session.require(Permission.MANAGE_USERS_SETTINGS);
        labs.deleteTest(id);
    }

    public List<LabRepository.LabRow> forPatient(long patientId) {
        session.require(Permission.LABS);
        return labs.rowsForPatient(patientId);
    }

    public List<LabRepository.LabRow> pending() {
        session.require(Permission.LABS);
        return labs.pendingRows();
    }

    public List<LabRepository.LabRow> recent(int limit) {
        session.require(Permission.LABS);
        return labs.recentRows(limit);
    }

    public List<LabRepository.LabRow> trend(long patientId, long testId) {
        session.require(Permission.LABS);
        return labs.trend(patientId, testId);
    }

    public long order(long patientId, Long encounterId, List<Long> testIds) {
        session.require(Permission.LABS);
        if (testIds == null || testIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one test");
        }
        long id = labs.createOrder(patientId, encounterId, session.userId(), testIds);
        audit.log(AuditAction.CREATE, "lab_order", id, patientId, testIds.size() + " test(s) ordered");
        return id;
    }

    public void markCollected(long orderId, long patientId) {
        session.require(Permission.LABS);
        labs.setOrderStatus(orderId, LabOrderStatus.COLLECTED);
        audit.log(AuditAction.UPDATE, "lab_order", orderId, patientId, "Specimen collected");
    }

    public void cancel(long orderId, long patientId) {
        session.require(Permission.LABS);
        labs.setOrderStatus(orderId, LabOrderStatus.CANCELLED);
        audit.log(AuditAction.UPDATE, "lab_order", orderId, patientId, "Order cancelled");
    }

    /**
     * Saves one result line. The test, patient and order status are read from the database,
     * never trusted from the caller.
     */
    public LabFlag saveResult(long orderId, long resultId, Double numeric, String text) {
        session.require(Permission.LABS);
        LabRepository.LabRow row = labs.resultRow(resultId);
        if (row == null || row.orderId() != orderId) {
            throw new IllegalArgumentException("This result does not belong to the selected lab order");
        }
        if (row.orderStatus() == LabOrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Results cannot be entered on a cancelled order");
        }
        LabTest test = labs.testById(row.testId());
        LabFlag flag = flagFor(test, numeric);
        labs.saveResult(resultId, numeric, text, flag);
        labs.setOrderStatus(orderId, labs.allResultsEntered(orderId)
                ? LabOrderStatus.RESULTED : LabOrderStatus.COLLECTED);
        audit.log(AuditAction.UPDATE, "lab_result", resultId, row.patientId(), "Result entered");
        return flag;
    }

    /** Auto-flags values against the catalogue reference and explicit critical limits. */
    public LabFlag flagFor(LabTest test, Double value) {
        if (test == null) {
            return null;
        }
        return flagFor(test.refLow(), test.refHigh(), test.critLow(), test.critHigh(), value);
    }

    /** Flags a value as critical only when it crosses an explicit critical limit. */
    public static LabFlag flagFor(Double low, Double high, Double criticalLow, Double criticalHigh,
                                  Double value) {
        if (value == null) {
            return null;
        }
        if (criticalLow != null && value < criticalLow) {
            return LabFlag.CRITICAL;
        }
        if (criticalHigh != null && value > criticalHigh) {
            return LabFlag.CRITICAL;
        }
        if (low != null && value < low) {
            return LabFlag.LOW;
        }
        if (high != null && value > high) {
            return LabFlag.HIGH;
        }
        return LabFlag.NORMAL;
    }

    public int pendingCount() {
        session.require(Permission.LABS);
        return labs.countPending();
    }
}
