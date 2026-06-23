package dev.davimf.basebot.database.sqlite;

import dev.davimf.basebot.database.postgres.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Append-only action log in SQLite (command executions, ticket actions, moderation).
 * Fast local writes; channel-facing log embeds are produced separately by listeners.
 */
public final class ActionLogRepository {

    private final SqliteManager sqlite;

    public ActionLogRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public void log(String guildId, String actorId, String targetId, String action, String detail) {
        String sql = """
                INSERT INTO action_logs (guild_id, actor_id, target_id, action, detail)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, actorId);
            ps.setString(3, targetId);
            ps.setString(4, action);
            ps.setString(5, detail);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("write action log", e);
        }
    }
}
