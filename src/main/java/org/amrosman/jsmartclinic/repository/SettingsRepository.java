package org.amrosman.jsmartclinic.repository;

import org.amrosman.jsmartclinic.db.Database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SettingsRepository {

    private final Database database;

    public SettingsRepository(Database database) {
        this.database = database;
    }

    public Map<String, String> all() {
        try (Connection c = database.getConnection()) {
            Map<String, String> values = new LinkedHashMap<>();
            Jdbc.list(c, "SELECT key, value FROM settings ORDER BY key", rs -> {
                values.put(rs.getString("key"), rs.getString("value"));
                return null;
            });
            return values;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public String get(String key, String fallback) {
        try (Connection c = database.getConnection()) {
            String value = Jdbc.one(c, "SELECT value FROM settings WHERE key=?",
                    rs -> rs.getString("value"), key);
            return value == null ? fallback : value;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void put(String key, String value) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "INSERT INTO settings(key, value) VALUES (?,?) "
                        + "ON CONFLICT(key) DO UPDATE SET value=excluded.value", key, value));
    }

    public void putAll(Map<String, String> values) {
        Jdbc.transaction(database, c -> {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                Jdbc.update(c, "INSERT INTO settings(key, value) VALUES (?,?) "
                        + "ON CONFLICT(key) DO UPDATE SET value=excluded.value", entry.getKey(), entry.getValue());
            }
            return null;
        });
    }
}
