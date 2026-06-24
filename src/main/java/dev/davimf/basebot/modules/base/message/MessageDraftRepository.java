package dev.davimf.basebot.modules.base.message;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** SQLite store for the per-user message builder draft (BOTSPECS Module 1 — /mensagem). */
public final class MessageDraftRepository {

    private final SqliteManager sqlite;

    public MessageDraftRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public void save(String userId, String guildId, String json) {
        String sql = """
                INSERT INTO message_drafts (user_id, guild_id, json, updated_at)
                VALUES (?, ?, ?, datetime('now'))
                ON CONFLICT (user_id) DO UPDATE SET
                    guild_id = excluded.guild_id, json = excluded.json, updated_at = datetime('now')
                """;
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, guildId);
            ps.setString(3, json);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("save message draft " + userId, e);
        }
    }

    public Optional<String> find(String userId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT json FROM message_drafts WHERE user_id = ?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString("json")) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("find message draft " + userId, e);
        }
    }

    public void delete(String userId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM message_drafts WHERE user_id = ?")) {
            ps.setString(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("delete message draft " + userId, e);
        }
    }
}
