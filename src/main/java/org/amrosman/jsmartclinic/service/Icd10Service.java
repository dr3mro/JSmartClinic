package org.amrosman.jsmartclinic.service;

import org.amrosman.jsmartclinic.db.Database;
import org.amrosman.jsmartclinic.repository.Jdbc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** ICD-10-CM subset used by diagnosis search. Loaded from a bundled CSV into SQLite. */
public final class Icd10Service {

    public record IcdCode(String code, String description) {
        @Override
        public String toString() {
            return code + " — " + description;
        }
    }

    private final Database database;

    public Icd10Service(Database database) {
        this.database = database;
        ensureLoaded();
    }

    private void ensureLoaded() {
        try (Connection c = database.getConnection()) {
            long count = Jdbc.count(c, "SELECT COUNT(*) FROM icd10_codes");
            if (count > 0) {
                return;
            }
            try (InputStream in = getClass().getResourceAsStream("/data/icd10.csv")) {
                if (in == null) {
                    return;
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    boolean first = true;
                    while ((line = reader.readLine()) != null) {
                        if (first) {
                            first = false;
                            continue;
                        }
                        String[] parts = line.split(",", 2);
                        if (parts.length == 2 && !parts[0].isBlank()) {
                            Jdbc.update(c, "INSERT OR IGNORE INTO icd10_codes(code, description) VALUES (?,?)",
                                    parts[0].trim(), parts[1].replace("\"", "").trim());
                        }
                    }
                }
            }
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Unable to load ICD-10 catalogue", e);
        }
    }

    public List<IcdCode> search(String query, int limit) {
        String q = query == null ? "" : query.trim();
        try (Connection c = database.getConnection()) {
            if (q.isBlank()) {
                return Jdbc.list(c, "SELECT code, description FROM icd10_codes ORDER BY code LIMIT ?",
                        rs -> new IcdCode(rs.getString("code"), rs.getString("description")), limit);
            }
            String like = "%" + q.toLowerCase(java.util.Locale.ROOT) + "%";
            return Jdbc.list(c, "SELECT code, description FROM icd10_codes "
                            + "WHERE LOWER(code) LIKE ? OR LOWER(description) LIKE ? ORDER BY code LIMIT ?",
                    rs -> new IcdCode(rs.getString("code"), rs.getString("description")), like, like, limit);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
