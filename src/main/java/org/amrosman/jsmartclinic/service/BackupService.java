package org.amrosman.jsmartclinic.service;

import org.amrosman.jsmartclinic.app.Session;
import org.amrosman.jsmartclinic.db.Database;
import org.amrosman.jsmartclinic.domain.AuditAction;
import org.amrosman.jsmartclinic.repository.SettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** SQLite online backups, retention and restore. */
public final class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final int KEEP = 14;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Database database;
    private final SettingsRepository settings;
    private final Session session;
    private final AuditService audit;

    public BackupService(Database database, SettingsRepository settings, Session session, AuditService audit) {
        this.database = database;
        this.settings = settings;
        this.session = session;
        this.audit = audit;
    }

    public List<Path> backups() {
        session.require(Permission.BACKUP);
        return listFiles();
    }

    public Path backupNow() {
        session.require(Permission.BACKUP);
        Path target = createBackup();
        audit.log(AuditAction.EXPORT, "backup", null, null, target.getFileName().toString());
        return target;
    }

    /** Called on startup; no permission check (the app is not authenticated yet). */
    public void automaticDailyBackupIfDue() {
        if (!Boolean.parseBoolean(settings.get("auto_backup", "true"))) {
            return;
        }
        LocalDate today = LocalDate.now();
        String last = settings.get("last_backup_at", "");
        if (!last.isBlank()) {
            Instant lastAt = org.amrosman.jsmartclinic.util.Dates.fromText(last);
            if (lastAt != null && lastAt.atZone(ZoneId.systemDefault()).toLocalDate().equals(today)) {
                return;
            }
        }
        try {
            createBackup();
            log.info("Daily backup created");
        } catch (RuntimeException e) {
            log.warn("Daily backup failed", e);
        }
    }

    private Path createBackup() {
        database.checkpoint();
        Path dir = database.backupDir();
        Path target = dir.resolve("emr-" + STAMP.format(java.time.LocalDateTime.now()) + ".db");
        try (Connection connection = database.getConnection()) {
            if (connection instanceof org.sqlite.SQLiteConnection sqlite) {
                sqlite.getDatabase().backup("main", target.toAbsolutePath().toString(), null);
            } else {
                Files.copy(database.file(), target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Backup failed: " + e.getMessage(), e);
        }
        Database.restrictPermissions(target);
        settings.put("last_backup_at", Instant.now().toString());
        prune(dir);
        return target;
    }

    public void restore(Path backup) {
        session.require(Permission.BACKUP);
        if (backup == null || !Files.exists(backup)) {
            throw new IllegalArgumentException("Backup file not found");
        }
        database.checkpoint();
        try {
            Files.copy(backup, database.file(), StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(Path.of(database.file() + "-wal"));
            Files.deleteIfExists(Path.of(database.file() + "-shm"));
        } catch (IOException e) {
            throw new IllegalStateException("Restore failed: " + e.getMessage(), e);
        }
        audit.log(AuditAction.UPDATE, "backup", null, null, "Restored from " + backup.getFileName());
    }

    private void prune(Path dir) {
        List<Path> files = listFiles();
        for (int i = KEEP; i < files.size(); i++) {
            try {
                Files.deleteIfExists(files.get(i));
            } catch (IOException e) {
                log.warn("Unable to prune backup {}", files.get(i), e);
            }
        }
    }

    private List<Path> listFiles() {
        Path dir = database.backupDir();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".db")).forEach(files::add);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to list backups", e);
        }
        files.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
        return files;
    }
}
