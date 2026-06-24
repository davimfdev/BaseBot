package dev.davimf.basebot.database.sqlite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal forward-only migration runner for SQLite. Applies numbered {@code .sql}
 * files bundled on the classpath under {@code /db/sqlite/} and records which have run
 * in a {@code schema_migrations} table so each runs at most once.
 *
 * <p>Kept dependency-free (no Flyway) because the local schema is small and we want
 * the fat jar lean. Migration files are listed explicitly in {@link #MIGRATIONS} since
 * classpath directory scanning is unreliable inside a shaded jar.
 */
public final class SqliteMigrator {

    private static final Logger log = LoggerFactory.getLogger(SqliteMigrator.class);

    /** Ordered list of migration resources. Append new files here; never reorder. */
    private static final List<String> MIGRATIONS = List.of(
            "/db/sqlite/001_init.sql",
            "/db/sqlite/002_pix_keys.sql",
            "/db/sqlite/003_voice_mutes.sql",
            "/db/sqlite/004_pix_keys_per_user.sql",
            "/db/sqlite/005_catalog.sql",
            "/db/sqlite/006_budgets.sql",
            "/db/sqlite/007_punishments.sql",
            "/db/sqlite/008_economy.sql",
            "/db/sqlite/009_farm.sql",
            "/db/sqlite/010_recipes.sql",
            "/db/sqlite/011_fix_budgets.sql",
            "/db/sqlite/012_actions.sql",
            "/db/sqlite/013_forms.sql",
            "/db/sqlite/014_ticket_reason.sql",
            "/db/sqlite/015_ticket_events.sql"
    );

    private final SqliteManager sqlite;

    public SqliteMigrator(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public void migrate() {
        try (Connection c = sqlite.getConnection()) {
            ensureMigrationsTable(c);
            for (String resource : MIGRATIONS) {
                if (alreadyApplied(c, resource)) {
                    continue;
                }
                log.info("Applying SQLite migration {}", resource);
                applyScript(c, resource);
                recordApplied(c, resource);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("SQLite migration failed", e);
        }
    }

    private void ensureMigrationsTable(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                        name       TEXT PRIMARY KEY,
                        applied_at TEXT NOT NULL DEFAULT (datetime('now'))
                    )
                    """);
        }
    }

    private boolean alreadyApplied(Connection c, String resource) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM schema_migrations WHERE name = ?")) {
            ps.setString(1, resource);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void recordApplied(Connection c, String resource) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO schema_migrations(name) VALUES (?)")) {
            ps.setString(1, resource);
            ps.executeUpdate();
        }
    }

    private void applyScript(Connection c, String resource) throws SQLException {
        String script = readResource(resource);
        // Execute statements one at a time; split on ';' at line ends (sufficient for our DDL).
        boolean prevAuto = c.getAutoCommit();
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            for (String stmt : splitStatements(script)) {
                if (!stmt.isBlank()) {
                    st.execute(stmt);
                }
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(prevAuto);
        }
    }

    private static List<String> splitStatements(String script) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : script.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue;
            }
            cur.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                out.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (!cur.isEmpty()) {
            out.add(cur.toString());
        }
        return out;
    }

    private String readResource(String resource) {
        try (InputStream in = SqliteMigrator.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Migration resource not found: " + resource);
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read migration: " + resource, e);
        }
    }
}
