package dev.davimf.basebot.database.postgres;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies the Postgres schema for the guild-config "source of truth" from the bundled
 * {@code /db/postgres/*.sql} files, tracking applied files in a {@code schema_migrations}
 * ledger so each runs at most once.
 *
 * <p>Unlike the SQLite migrator, this is <b>not</b> run automatically on bot startup: the
 * Postgres schema is shared with the web dashboard, so it is applied deliberately via the
 * {@code ApplyPostgresSchema} tool. Migrations are idempotent ({@code IF NOT EXISTS} /
 * {@code ADD COLUMN IF NOT EXISTS}) so re-running is safe.
 */
public final class PostgresMigrator {

    /** Ordered migration resources. Append new files here; never reorder. */
    public static final List<String> MIGRATIONS = List.of(
            "/db/postgres/001_guild_config.sql",
            "/db/postgres/002_guild_settings.sql",
            "/db/postgres/003_ticket_categories.sql"
    );

    private PostgresMigrator() {}

    /** Applies any not-yet-applied migrations; returns the list that ran this call. */
    public static List<String> migrate(Connection c) throws SQLException {
        ensureLedger(c);
        List<String> applied = new ArrayList<>();
        for (String resource : MIGRATIONS) {
            if (isApplied(c, resource)) {
                continue;
            }
            String sql = readResource(resource);
            boolean prevAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute(sql);
                record(c, resource);
                c.commit();
                applied.add(resource);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAutoCommit);
            }
        }
        return applied;
    }

    private static void ensureLedger(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                        name       TEXT PRIMARY KEY,
                        applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
                    )
                    """);
        }
    }

    private static boolean isApplied(Connection c, String resource) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM schema_migrations WHERE name = ?")) {
            ps.setString(1, resource);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void record(Connection c, String resource) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO schema_migrations(name) VALUES (?)")) {
            ps.setString(1, resource);
            ps.executeUpdate();
        }
    }

    private static String readResource(String resource) {
        try (InputStream in = PostgresMigrator.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Migration resource not found: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read migration: " + resource, e);
        }
    }
}
