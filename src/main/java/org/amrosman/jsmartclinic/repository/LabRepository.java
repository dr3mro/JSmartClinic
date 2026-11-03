package org.amrosman.jsmartclinic.repository;

import org.amrosman.jsmartclinic.db.Database;
import org.amrosman.jsmartclinic.domain.LabFlag;
import org.amrosman.jsmartclinic.domain.LabOrderStatus;
import org.amrosman.jsmartclinic.domain.LabTest;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

public final class LabRepository {

    /** A flattened lab order line (order + test + optional result). */
    public record LabRow(
            long orderId,
            long patientId,
            Long encounterId,
            long orderedBy,
            Instant orderedAt,
            LabOrderStatus orderStatus,
            long testId,
            String testCode,
            String testName,
            String unit,
            Double refLow,
            Double refHigh,
            Long resultId,
            Double valueNum,
            String valueText,
            LabFlag flag,
            Instant resultedAt) {
    }

    private final Database database;

    public LabRepository(Database database) {
        this.database = database;
    }

    // ---------- Catalogue ----------

    private LabTest test(ResultSet rs) throws SQLException {
        return new LabTest(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("unit"),
                Jdbc.nullableDouble(rs, "ref_low"), Jdbc.nullableDouble(rs, "ref_high"), rs.getString("category"));
    }

    public List<LabTest> tests() {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT * FROM lab_tests ORDER BY category, name", this::test);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public LabTest testById(long id) {
        try (Connection c = database.getConnection()) {
            return Jdbc.one(c, "SELECT * FROM lab_tests WHERE id=?", this::test, id);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public long insertTest(String code, String name, String unit, Double low, Double high, String category) {
        return Jdbc.transaction(database, c -> Jdbc.insert(c,
                "INSERT INTO lab_tests(code, name, unit, ref_low, ref_high, category) VALUES (?,?,?,?,?,?)",
                code, name, unit, low, high, category));
    }

    public void updateTest(long id, String code, String name, String unit, Double low, Double high, String category) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE lab_tests SET code=?, name=?, unit=?, ref_low=?, ref_high=?, category=? WHERE id=?",
                code, name, unit, low, high, category, id));
    }

    public void deleteTest(long id) {
        Jdbc.transaction(database, c -> Jdbc.update(c, "DELETE FROM lab_tests WHERE id=?", id));
    }

    // ---------- Orders / results ----------

    private LabRow row(ResultSet rs) throws SQLException {
        return new LabRow(rs.getLong("order_id"), rs.getLong("patient_id"), Jdbc.nullableLong(rs, "encounter_id"),
                rs.getLong("ordered_by"), Jdbc.instant(rs, "ordered_at"),
                LabOrderStatus.from(rs.getString("order_status")), rs.getLong("test_id"), rs.getString("test_code"),
                rs.getString("test_name"), rs.getString("unit"), Jdbc.nullableDouble(rs, "ref_low"),
                Jdbc.nullableDouble(rs, "ref_high"), Jdbc.nullableLong(rs, "result_id"),
                Jdbc.nullableDouble(rs, "value_num"), rs.getString("value_text"),
                LabFlag.from(rs.getString("flag")), Jdbc.instant(rs, "resulted_at"));
    }

    private static final String ROW_SQL = "SELECT o.id AS order_id, o.patient_id, o.encounter_id, o.ordered_by, "
            + "o.ordered_at, o.status AS order_status, t.id AS test_id, t.code AS test_code, t.name AS test_name, "
            + "t.unit, t.ref_low, t.ref_high, r.id AS result_id, r.value_num, r.value_text, r.flag, r.resulted_at "
            + "FROM lab_orders o JOIN lab_results r ON r.lab_order_id = o.id "
            + "JOIN lab_tests t ON t.id = r.lab_test_id ";

    public List<LabRow> rowsForPatient(long patientId) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, ROW_SQL + "WHERE o.patient_id=? ORDER BY o.ordered_at DESC, t.name",
                    this::row, patientId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<LabRow> pendingRows() {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, ROW_SQL + "WHERE o.status IN ('ORDERED','COLLECTED') ORDER BY o.ordered_at",
                    this::row);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<LabRow> recentRows(int limit) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, ROW_SQL + "ORDER BY o.ordered_at DESC LIMIT ?", this::row, limit);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public long createOrder(long patientId, Long encounterId, long orderedBy, List<Long> testIds) {
        return Jdbc.transaction(database, c -> {
            Instant now = Instant.now();
            long orderId = Jdbc.insert(c, "INSERT INTO lab_orders(patient_id, encounter_id, ordered_by, ordered_at, "
                            + "status, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                    patientId, encounterId, orderedBy, now, LabOrderStatus.ORDERED.name(), now, now);
            for (Long testId : testIds) {
                Jdbc.insert(c, "INSERT INTO lab_results(lab_order_id, lab_test_id, created_at, updated_at) "
                        + "VALUES (?,?,?,?)", orderId, testId, now, now);
            }
            return orderId;
        });
    }

    public void setOrderStatus(long orderId, LabOrderStatus status) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE lab_orders SET status=?, updated_at=? WHERE id=?", status.name(), Instant.now(), orderId));
    }

    public void saveResult(long resultId, Double valueNum, String valueText, LabFlag flag) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE lab_results SET value_num=?, value_text=?, flag=?, resulted_at=?, updated_at=? WHERE id=?",
                valueNum, valueText, flag == null ? null : flag.name(), Instant.now(), Instant.now(), resultId));
    }

    public boolean allResultsEntered(long orderId) {
        try (Connection c = database.getConnection()) {
            return Jdbc.count(c, "SELECT COUNT(*) FROM lab_results WHERE lab_order_id=? AND value_num IS NULL "
                    + "AND value_text IS NULL", orderId) == 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<LabRow> trend(long patientId, long testId) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, ROW_SQL + "WHERE o.patient_id=? AND t.id=? AND (r.value_num IS NOT NULL) "
                    + "ORDER BY o.ordered_at", this::row, patientId, testId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public int countPending() {
        try (Connection c = database.getConnection()) {
            return (int) Jdbc.count(c, "SELECT COUNT(*) FROM lab_orders WHERE status IN ('ORDERED','COLLECTED')");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
