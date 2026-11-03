package org.amrosman.jsmartclinic.repository;

import org.amrosman.jsmartclinic.db.Database;
import org.amrosman.jsmartclinic.domain.Role;
import org.amrosman.jsmartclinic.domain.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

public final class UserRepository {

    private static final String COLUMNS = "id, username, full_name, password_hash, role, specialty, active, "
            + "failed_logins, locked_until, last_login_at, created_at, updated_at";

    private final Database database;

    public UserRepository(Database database) {
        this.database = database;
    }

    private User map(java.sql.ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("full_name"),
                rs.getString("password_hash"),
                Role.from(rs.getString("role")),
                rs.getString("specialty"),
                rs.getInt("active") == 1,
                rs.getInt("failed_logins"),
                Jdbc.instant(rs, "locked_until"),
                Jdbc.instant(rs, "last_login_at"),
                Jdbc.instant(rs, "created_at"),
                Jdbc.instant(rs, "updated_at"));
    }

    public long count() {
        try (Connection c = database.getConnection()) {
            return Jdbc.count(c, "SELECT COUNT(*) FROM users");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<User> findAll() {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c, "SELECT " + COLUMNS + " FROM users ORDER BY full_name", this::map);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<User> findProviders() {
        try (Connection c = database.getConnection()) {
            return Jdbc.list(c,
                    "SELECT " + COLUMNS + " FROM users WHERE active = 1 AND role IN ('DOCTOR','NURSE') ORDER BY full_name",
                    this::map);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public User findById(long id) {
        try (Connection c = database.getConnection()) {
            return Jdbc.one(c, "SELECT " + COLUMNS + " FROM users WHERE id = ?", this::map, id);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public User findByUsername(String username) {
        try (Connection c = database.getConnection()) {
            return Jdbc.one(c, "SELECT " + COLUMNS + " FROM users WHERE LOWER(username) = LOWER(?)",
                    this::map, username);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public long insert(String username, String fullName, String passwordHash, Role role, String specialty) {
        return Jdbc.transaction(database, c -> {
            Instant now = Instant.now();
            return Jdbc.insert(c,
                    "INSERT INTO users(username, full_name, password_hash, role, specialty, active, failed_logins, "
                            + "created_at, updated_at) VALUES (?,?,?,?,?,1,0,?,?)",
                    username, fullName, passwordHash, role.name(), specialty, now, now);
        });
    }

    public void update(User user) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE users SET full_name=?, role=?, specialty=?, active=?, updated_at=? WHERE id=?",
                user.fullName(), user.role().name(), user.specialty(), user.active(), Instant.now(), user.id()));
    }

    public void setPassword(long id, String hash) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE users SET password_hash=?, failed_logins=0, locked_until=NULL, updated_at=? WHERE id=?",
                hash, Instant.now(), id));
    }

    public void setActive(long id, boolean active) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE users SET active=?, updated_at=? WHERE id=?", active, Instant.now(), id));
    }

    public void recordLoginSuccess(long id) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE users SET failed_logins=0, locked_until=NULL, last_login_at=?, updated_at=? WHERE id=?",
                Instant.now(), Instant.now(), id));
    }

    public void recordLoginFailure(long id, int failedLogins, Instant lockedUntil) {
        Jdbc.transaction(database, c -> Jdbc.update(c,
                "UPDATE users SET failed_logins=?, locked_until=?, updated_at=? WHERE id=?",
                failedLogins, lockedUntil, Instant.now(), id));
    }
}
