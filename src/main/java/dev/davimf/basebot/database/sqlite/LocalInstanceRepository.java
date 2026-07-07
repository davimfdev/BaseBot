package dev.davimf.basebot.database.sqlite;

import dev.davimf.basebot.database.postgres.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Bot-local stable instance id: generated once on first boot and persisted in SQLite
 * ({@code local_meta}), so no manual {@code BOT_INSTANCE_ID} is required. The id feeds the
 * dashboard snapshots ({@code bot_instances}/{@code bot_guilds}). Survives restarts with the
 * SQLite file; a wiped local DB is effectively a new instance (new id).
 */
public final class LocalInstanceRepository {

    private static final String KEY = "bot_instance_id";

    private final SqliteManager sqlite;

    public LocalInstanceRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    /** Returns the persisted instance id, generating and storing a new UUID on first call. */
    public String getOrCreate() {
        try (Connection c = sqlite.getConnection()) {
            String existing = read(c);
            if (existing != null) {
                return existing;
            }
            String id = UUID.randomUUID().toString();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR IGNORE INTO local_meta (key, value) VALUES (?, ?)")) {
                ps.setString(1, KEY);
                ps.setString(2, id);
                ps.executeUpdate();
            }
            // Re-read: a concurrent first boot may have inserted first (INSERT OR IGNORE).
            String after = read(c);
            return after != null ? after : id;
        } catch (SQLException e) {
            throw new RepositoryException("get/create local instance id", e);
        }
    }

    private String read(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT value FROM local_meta WHERE key = ?")) {
            ps.setString(1, KEY);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
