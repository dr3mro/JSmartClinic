package org.amrosman.jsmartclinic.repository;

import org.amrosman.jsmartclinic.db.Database;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public final class ReportRepository {

    public record NamedCount(String label, long count) {
    }

    public record PeriodAmount(String period, long cents) {
    }

    private final Database database;

    public ReportRepository(Database database) {
        this.database = database;
    }

    public List<NamedCount> appointmentsByProvider(LocalDate from, LocalDate to) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT u.full_name AS label, COUNT(*) AS total FROM appointments a "
                            + "JOIN users u ON u.id=a.provider_id WHERE a.start_at>=? AND a.start_at<? "
                            + "AND a.status<>'CANCELLED' GROUP BY u.full_name ORDER BY total DESC",
                    rs -> new NamedCount(rs.getString("label"), rs.getLong("total")), start(from), end(to));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<NamedCount> topDiagnoses(LocalDate from, LocalDate to, int limit) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT d.description AS label, COUNT(*) AS total FROM encounter_diagnoses d "
                            + "JOIN encounters e ON e.id=d.encounter_id WHERE e.encounter_date>=? AND e.encounter_date<? "
                            + "GROUP BY d.description ORDER BY total DESC LIMIT ?",
                    rs -> new NamedCount(rs.getString("label"), rs.getLong("total")), start(from), end(to), limit);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<NamedCount> demographics() {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT sex AS label, COUNT(*) AS total FROM patients WHERE deleted_at IS NULL "
                            + "GROUP BY sex ORDER BY total DESC",
                    rs -> new NamedCount(rs.getString("label"), rs.getLong("total")));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<NamedCount> ageGroups() {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT CASE "
                            + "WHEN age < 18 THEN '0-17' WHEN age < 35 THEN '18-34' WHEN age < 50 THEN '35-49' "
                            + "WHEN age < 65 THEN '50-64' ELSE '65+' END AS label, COUNT(*) AS total FROM ("
                            + "SELECT (CAST(strftime('%Y.%m%d', 'now') AS REAL) - CAST(strftime('%Y.%m%d', date_of_birth) AS REAL)) AS age "
                            + "FROM patients WHERE deleted_at IS NULL) GROUP BY label ORDER BY label",
                    rs -> new NamedCount(rs.getString("label"), rs.getLong("total")));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<PeriodAmount> revenueByMonth(LocalDate from, LocalDate to) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT strftime('%Y-%m', paid_at) AS period, SUM(amount_cents) AS total "
                            + "FROM payments WHERE paid_at>=? AND paid_at<? GROUP BY period ORDER BY period",
                    rs -> new PeriodAmount(rs.getString("period"), rs.getLong("total")), start(from), end(to));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<NamedCount> appointmentsByStatus(LocalDate from, LocalDate to) {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT status AS label, COUNT(*) AS total FROM appointments "
                            + "WHERE start_at>=? AND start_at<? GROUP BY status ORDER BY total DESC",
                    rs -> new NamedCount(rs.getString("label"), rs.getLong("total")), start(from), end(to));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private Instant start(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private Instant end(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }
}
