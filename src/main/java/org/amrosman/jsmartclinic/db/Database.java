package org.amrosman.jsmartclinic.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Set;

/**
 * Connection factory for the local SQLite database. A new connection is opened per
 * operation and always configured with the required PRAGMAs.
 */
public final class Database {

    private static final Logger log = LoggerFactory.getLogger(Database.class);

    private final Path file;
    private final Path dataDir;

    public Database(Path file) {
        this.file = file.toAbsolutePath();
        this.dataDir = this.file.getParent();
        try {
            if (dataDir != null) {
                Files.createDirectories(dataDir);
            }
            if (!this.file.getFileName().toString().equals(":memory:")) {
                Files.createDirectories(this.file.getParent());
                restrictPermissions(this.file.getParent());
                if (Files.exists(this.file)) {
                    restrictPermissions(this.file);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create application data folder: " + dataDir, e);
        }
    }

    /** Opens the database in the per-user application data folder (or $JSMARTCLINIC_DATA_DIR). */
    public static Database openDefault() {
        return new Database(defaultDataDir().resolve("emr.db"));
    }

    public static Path defaultDataDir() {
        String override = System.getenv("JSMARTCLINIC_DATA_DIR");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", "JSmartClinic");
        }
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return Path.of(appData == null ? home : appData, "JSmartClinic");
        }
        String xdg = System.getenv("XDG_DATA_HOME");
        return xdg == null || xdg.isBlank()
                ? Path.of(home, ".local", "share", "JSmartClinic")
                : Path.of(xdg, "JSmartClinic");
    }

    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        if (Files.exists(file)) {
            // SQLite creates the file with the process umask; tighten it after creation.
            restrictPermissions(file);
        }
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA journal_mode = WAL");
            st.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    public Path file() {
        return file;
    }

    public Path dataDir() {
        return dataDir;
    }

    public Path documentsDir() {
        return ensureDir(dataDir.resolve("documents"));
    }

    public Path backupDir() {
        return ensureDir(dataDir.resolve("backup"));
    }

    public Path templatesDir() {
        return ensureDir(dataDir.resolve("templates"));
    }

    private Path ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
            restrictPermissions(dir);
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create folder " + dir, e);
        }
    }

    public static void restrictPermissions(Path path) {
        try {
            // Directories need the execute bit to be traversable; files only need read/write.
            Set<PosixFilePermission> ownerOnly = PosixFilePermissions.fromString(
                    Files.isDirectory(path) ? "rwx------" : "rw-------");
            Files.setPosixFilePermissions(path, ownerOnly);
        } catch (UnsupportedOperationException | IOException e) {
            log.debug("POSIX permissions not supported for {}", path);
        }
    }

    public void checkpoint() {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (SQLException e) {
            log.warn("Unable to checkpoint WAL", e);
        }
    }
}
