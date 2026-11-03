package org.amrosman.jsmartclinic.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Applies {@code db/migration/V###__*.sql} scripts in order inside a transaction and
 * records each version in {@code schema_version}. Safe to run on every start.
 */
public final class MigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);
    private static final String MIGRATION_ROOT = "org/amrosman/jsmartclinic/db/migration";
    private static final String INDEX = "index.txt";

    private final Database database;

    public MigrationRunner(Database database) {
        this.database = database;
    }

    public void migrate() {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            ensureVersionTable(connection);
            List<String> migrations = discover();
            for (String migration : migrations) {
                int version = versionOf(migration);
                if (isApplied(connection, version)) {
                    continue;
                }
                log.info("Applying migration {}", migration);
                String sql = read(migration);
                for (String statement : splitStatements(sql)) {
                    try (Statement st = connection.createStatement()) {
                        st.execute(statement);
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO schema_version(version, applied_at) VALUES (?, ?)")) {
                    ps.setInt(1, version);
                    ps.setString(2, java.time.Instant.now().toString());
                    ps.executeUpdate();
                }
                connection.commit();
            }
            connection.commit();
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Database migration failed", e);
        }
    }

    private void ensureVersionTable(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL)");
        }
    }

    private boolean isApplied(Connection connection, int version) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM schema_version WHERE version = ?")) {
            ps.setInt(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private List<String> discover() throws IOException {
        URL url = getClass().getResource("/" + MIGRATION_ROOT);
        List<String> names = new ArrayList<>();
        if (url == null) {
            return names;
        }
        if ("file".equals(url.getProtocol())) {
            try (Stream<Path> stream = Files.list(Path.of(url.toURI()))) {
                stream.filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql"))
                        .map(p -> p.getFileName().toString())
                        .forEach(names::add);
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
        } else {
            String index = readText(MIGRATION_ROOT + "/" + INDEX);
            for (String line : index.split("\\R")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    names.add(trimmed);
                }
            }
        }
        names.sort(Comparator.comparingInt(this::versionOf));
        return names;
    }

    private int versionOf(String name) {
        String digits = name.replaceFirst("^V", "").replaceFirst("__.*$", "");
        return Integer.parseInt(digits.replaceAll("\\D", ""));
    }

    private String read(String name) throws IOException {
        return readText(MIGRATION_ROOT + "/" + name);
    }

    private String readText(String resource) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/" + resource)) {
            if (in == null) {
                throw new IOException("Missing resource: " + resource);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        }
    }

    /**
     * Splits a SQL script on statement terminators while keeping trigger bodies
     * ({@code BEGIN … END;}) intact and ignoring {@code --} comments.
     */
    static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inTrigger = false;
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean lineComment = false;

        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            char next = i + 1 < script.length() ? script.charAt(i + 1) : '\0';

            if (lineComment) {
                if (c == '\n') {
                    lineComment = false;
                    current.append(c);
                }
                continue;
            }
            if (!singleQuote && !doubleQuote && c == '-' && next == '-') {
                lineComment = true;
                i++;
                continue;
            }
            if (c == '\'' && !doubleQuote) {
                singleQuote = !singleQuote;
            } else if (c == '"' && !singleQuote) {
                doubleQuote = !doubleQuote;
            }

            if (c == ';' && !singleQuote && !doubleQuote) {
                String upper = current.toString().trim().toUpperCase(java.util.Locale.ROOT);
                if (upper.startsWith("CREATE TRIGGER") && !upper.endsWith("END")) {
                    current.append(c).append('\n');
                    continue;
                }
                current.append(c);
                String statement = current.toString().trim();
                if (!statement.equals(";")) {
                    statements.add(statement);
                }
                current.setLength(0);
                inTrigger = false;
                continue;
            }
            current.append(c);
            if (c == '\n' && !inTrigger) {
                String upper = current.toString().trim().toUpperCase(java.util.Locale.ROOT);
                if (upper.startsWith("CREATE TRIGGER")) {
                    inTrigger = true;
                }
            }
        }
        if (!current.toString().isBlank()) {
            statements.add(current.toString().trim());
        }
        return statements;
    }
}
