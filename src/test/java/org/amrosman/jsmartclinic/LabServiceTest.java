package org.amrosman.jsmartclinic;

import org.amrosman.jsmartclinic.app.AppContext;
import org.amrosman.jsmartclinic.domain.LabFlag;
import org.amrosman.jsmartclinic.domain.LabOrderStatus;
import org.amrosman.jsmartclinic.domain.LabTest;
import org.amrosman.jsmartclinic.domain.Patient;
import org.amrosman.jsmartclinic.domain.Role;
import org.amrosman.jsmartclinic.repository.LabRepository;
import org.amrosman.jsmartclinic.service.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabServiceTest {

    @TempDir
    Path temp;

    private AppContext context;
    private Patient patient;
    private LabTest hgb;
    private LabTest wbc;

    @BeforeEach
    void setUp() {
        context = TestSupport.adminContext(temp);
        TestSupport.addUser(context, "doctor", Role.DOCTOR, "General");
        patient = TestSupport.createPatient(context, "Rosa", "Reed");
        TestSupport.login(context, "doctor");
        hgb = test("HGB");
        wbc = test("WBC");
    }

    @Test
    void catalogueManagementIsRestrictedToSettingsManagers() {
        assertThat(context.labs().tests()).isNotEmpty();
        assertThat(context.labs().testsForOrdering()).isNotEmpty();

        context.session().login(context.users().byId(1));
        context.labs().saveTest(0, "TEST1", "Test marker", "mg/L", 1.0, 2.0, "Custom");
        LabTest created = context.labs().tests().stream()
                .filter(t -> "TEST1".equals(t.code())).findFirst().orElseThrow();
        assertThat(created.name()).isEqualTo("Test marker");
        context.labs().saveTest(created.id(), "TEST1", "Test marker renamed", "mg/L", 1.0, 3.0, "Custom");
        assertThat(context.labs().tests().stream().filter(t -> t.id() == created.id()).findFirst().orElseThrow()
                .name()).isEqualTo("Test marker renamed");
        context.labs().deleteTest(created.id());
        assertThat(context.labs().tests()).noneMatch(t -> t.id() == created.id());

        assertThatThrownBy(() -> context.labs().saveTest(0, "BAD", " ", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        TestSupport.login(context, "doctor");
        assertThatThrownBy(() -> context.labs().saveTest(0, "BAD", "Nope", null, null, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> context.labs().deleteTest(1)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void orderingAndResultEntryFlow() {
        long orderId = context.labs().order(patient.id(), null, List.of(hgb.id(), wbc.id()));
        assertThat(orderId).isPositive();
        assertThat(context.labs().forPatient(patient.id())).hasSize(2);
        assertThat(context.labs().pending()).hasSize(2);
        assertThat(context.labs().pendingCount()).isEqualTo(1);
        assertThat(context.labs().recent(10)).hasSize(2);

        LabRepository.LabRow hgbRow = row(orderId, "HGB");
        assertThat(hgbRow.orderStatus()).isEqualTo(LabOrderStatus.ORDERED);
        assertThat(hgbRow.valueNum()).isNull();

        LabFlag low = context.labs().saveResult(orderId, hgbRow.resultId(), 10.0, null);
        assertThat(low).isEqualTo(LabFlag.LOW);
        assertThat(context.labs().forPatient(patient.id()).stream()
                .filter(r -> r.testCode().equals("HGB")).findFirst().orElseThrow().orderStatus())
                .isEqualTo(LabOrderStatus.COLLECTED);

        LabRepository.LabRow wbcRow = row(orderId, "WBC");
        LabFlag normal = context.labs().saveResult(orderId, wbcRow.resultId(), 5.0, null);
        assertThat(normal).isEqualTo(LabFlag.NORMAL);
        assertThat(context.labs().forPatient(patient.id()).get(0).orderStatus()).isEqualTo(LabOrderStatus.RESULTED);
        assertThat(context.labs().pending()).isEmpty();
        assertThat(context.labs().pendingCount()).isZero();
        assertThat(context.labs().trend(patient.id(), hgb.id())).hasSize(1);
        assertThat(context.labs().trend(patient.id(), hgb.id()).get(0).valueNum()).isEqualTo(10.0);

        assertThatThrownBy(() -> context.labs().order(patient.id(), null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultsCannotBeEnteredOnCancelledOrders() {
        long orderId = context.labs().order(patient.id(), null, List.of(hgb.id()));
        LabRepository.LabRow row = row(orderId, "HGB");
        context.labs().cancel(orderId, patient.id());

        assertThatThrownBy(() -> context.labs().saveResult(orderId, row.resultId(), 10.0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cancelled");

        assertThat(context.labs().forPatient(patient.id()).get(0).orderStatus())
                .isEqualTo(LabOrderStatus.CANCELLED);
        assertThat(context.labs().forPatient(patient.id()).get(0).valueNum()).isNull();
    }

    @Test
    void resultsMustBelongToTheOrderBeingSaved() {
        long first = context.labs().order(patient.id(), null, List.of(hgb.id()));
        long second = context.labs().order(patient.id(), null, List.of(wbc.id()));
        LabRepository.LabRow hgbRow = row(first, "HGB");

        assertThatThrownBy(() -> context.labs().saveResult(second, hgbRow.resultId(), 10.0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> context.labs().saveResult(second, 999_999L, 10.0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultFlagsAndAuditPatientComeFromTheStoredOrder() {
        LabTest potassium = test("K");
        long orderId = context.labs().order(patient.id(), null, List.of(potassium.id()));
        LabRepository.LabRow row = row(orderId, "K");

        LabFlag flag = context.labs().saveResult(orderId, row.resultId(), 6.8, null);

        assertThat(flag).isEqualTo(LabFlag.CRITICAL);
        assertThat(context.labs().forPatient(patient.id()).get(0).flag()).isEqualTo(LabFlag.CRITICAL);
        context.session().login(context.users().byId(1));
        assertThat(context.auditEvents().forPatient(patient.id(), 50))
                .anyMatch(event -> "lab_result".equals(event.entity()));
    }

    @Test
    void collectedAndCancelledOrders() {
        long orderId = context.labs().order(patient.id(), null, List.of(hgb.id()));
        context.labs().markCollected(orderId, patient.id());
        assertThat(context.labs().forPatient(patient.id()).get(0).orderStatus()).isEqualTo(LabOrderStatus.COLLECTED);

        long cancelled = context.labs().order(patient.id(), null, List.of(wbc.id()));
        context.labs().cancel(cancelled, patient.id());
        assertThat(context.labs().forPatient(patient.id()).stream()
                .filter(r -> r.orderId() == cancelled).findFirst().orElseThrow().orderStatus())
                .isEqualTo(LabOrderStatus.CANCELLED);
    }

    @Test
    void flagsDeriveFromReferenceRange() {
        assertThat(context.labs().flagFor(hgb, null)).isNull();
        assertThat(context.labs().flagFor(null, 10.0)).isNull();
        assertThat(context.labs().flagFor(hgb, 14.0)).isEqualTo(LabFlag.NORMAL);
        assertThat(context.labs().flagFor(hgb, 10.0)).isEqualTo(LabFlag.LOW);
        assertThat(context.labs().flagFor(hgb, 5.0)).isEqualTo(LabFlag.LOW);
        assertThat(context.labs().flagFor(hgb, 18.5)).isEqualTo(LabFlag.HIGH);
        assertThat(context.labs().flagFor(hgb, 25.0)).isEqualTo(LabFlag.HIGH);

        LabTest lowOnly = new LabTest(0, "LOWONLY", "Low only", null, 10.0, null, null, null, "Custom");
        assertThat(context.labs().flagFor(lowOnly, 9.0)).isEqualTo(LabFlag.LOW);
        assertThat(context.labs().flagFor(lowOnly, 11.0)).isEqualTo(LabFlag.NORMAL);
        LabTest highOnly = new LabTest(0, "HIGHONLY", "High only", null, null, 10.0, null, null, "Custom");
        assertThat(context.labs().flagFor(highOnly, 11.0)).isEqualTo(LabFlag.HIGH);
        assertThat(context.labs().flagFor(highOnly, 9.0)).isEqualTo(LabFlag.NORMAL);
    }

    @Test
    void criticalFlagsRequireExplicitCriticalLimits() {
        LabTest glucose = test("GLU");
        assertThat(glucose.critLow()).isEqualTo(40.0);
        assertThat(glucose.critHigh()).isEqualTo(450.0);
        assertThat(context.labs().flagFor(glucose, 130.0)).isEqualTo(LabFlag.HIGH);
        assertThat(context.labs().flagFor(glucose, 30.0)).isEqualTo(LabFlag.CRITICAL);
        assertThat(context.labs().flagFor(glucose, 500.0)).isEqualTo(LabFlag.CRITICAL);

        LabTest potassium = test("K");
        assertThat(context.labs().flagFor(potassium, 5.5)).isEqualTo(LabFlag.HIGH);
        assertThat(context.labs().flagFor(potassium, 6.8)).isEqualTo(LabFlag.CRITICAL);
        assertThat(context.labs().flagFor(potassium, 2.0)).isEqualTo(LabFlag.CRITICAL);

        LabTest crp = test("CRP");
        assertThat(crp.critLow()).isNull();
        assertThat(crp.critHigh()).isNull();
        assertThat(context.labs().flagFor(crp, 8.0)).isEqualTo(LabFlag.HIGH);
    }

    @Test
    void catalogueStoresCriticalLimits() {
        context.session().login(context.users().byId(1));
        context.labs().saveTest(0, "TESTCRIT", "Critical marker", "mg/L", 1.0, 2.0, 0.5, 5.0, "Custom");
        LabTest created = context.labs().tests().stream()
                .filter(t -> "TESTCRIT".equals(t.code())).findFirst().orElseThrow();
        assertThat(created.critLow()).isEqualTo(0.5);
        assertThat(created.critHigh()).isEqualTo(5.0);
        assertThat(context.labs().flagFor(created, 4.0)).isEqualTo(LabFlag.HIGH);
        assertThat(context.labs().flagFor(created, 6.0)).isEqualTo(LabFlag.CRITICAL);
    }

    @Test
    void labsRequireLabPermission() {
        TestSupport.login(context, "admin");
        TestSupport.addUser(context, "billing", Role.BILLING, null);
        context.auth().logout();
        TestSupport.login(context, "billing");

        assertThatThrownBy(() -> context.labs().tests()).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> context.labs().order(patient.id(), null, List.of(hgb.id())))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> context.labs().pending()).isInstanceOf(AccessDeniedException.class);
    }

    private LabTest test(String code) {
        return context.labs().tests().stream().filter(t -> code.equals(t.code())).findFirst().orElseThrow();
    }

    private LabRepository.LabRow row(long orderId, String code) {
        return context.labs().forPatient(patient.id()).stream()
                .filter(r -> r.orderId() == orderId && r.testCode().equals(code))
                .findFirst().orElseThrow();
    }
}
