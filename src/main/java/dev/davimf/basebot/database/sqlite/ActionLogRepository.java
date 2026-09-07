package dev.davimf.basebot.database.sqlite;

import dev.davimf.basebot.database.postgres.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only action log in SQLite (command executions, ticket actions, moderation).
 * Fast local writes; channel-facing log embeds are produced separately by listeners.
 */
public final class ActionLogRepository {

    private final SqliteManager sqlite;

    public ActionLogRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    /** One persisted action-log row (for /relatorio). */
    public record Entry(String guildId, String actorId, String targetId, String action,
                        String detail, String createdAt) {}

    /** Action-log rows for a guild within the last {@code days} (0 or less = all time). */
    public List<Entry> listSince(String guildId, int days) {
        String sql = "SELECT * FROM action_logs WHERE guild_id = ?"
                + (days > 0 ? " AND created_at >= datetime('now', ?)" : "")
                + " ORDER BY created_at DESC";
        List<Entry> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            if (days > 0) {
                ps.setString(2, "-" + days + " days");
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Entry(rs.getString("guild_id"), rs.getString("actor_id"),
                            rs.getString("target_id"), rs.getString("action"),
                            rs.getString("detail"), rs.getString("created_at")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list action logs for " + guildId, e);
        }
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
