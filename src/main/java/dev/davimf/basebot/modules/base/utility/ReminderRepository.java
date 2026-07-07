package dev.davimf.basebot.modules.base.utility;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** SQLite store dos lembretes (migração 033). */
public final class ReminderRepository {

    private final SqliteManager sqlite;

    public ReminderRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    public String create(String guildId, String userId, String channelId, String message, long remindAt) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String sql = "INSERT INTO reminders (id, guild_id, user_id, channel_id, message, remind_at) VALUES (?,?,?,?,?,?)";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, guildId);
            ps.setString(3, userId);
            ps.setString(4, channelId);
            ps.setString(5, message);
            ps.setLong(6, remindAt);
            ps.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new RepositoryException("reminder create " + guildId + "/" + userId, e);
        }
    }

    public List<Reminder> due(long now) {
        return query("SELECT * FROM reminders WHERE remind_at <= ? ORDER BY remind_at", ps -> ps.setLong(1, now));
    }

    public List<Reminder> listByUser(String guildId, String userId) {
        return query("SELECT * FROM reminders WHERE guild_id=? AND user_id=? ORDER BY remind_at", ps -> {
            ps.setString(1, guildId);
            ps.setString(2, userId);
        });
    }

    public void delete(String id) {
        exec("DELETE FROM reminders WHERE id=?", ps -> ps.setString(1, id));
    }

    /** Cancela só se o lembrete for do próprio usuário. */
    public boolean cancel(String id, String userId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM reminders WHERE id=? AND user_id=?")) {
            ps.setString(1, id);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("reminder cancel " + id, e);
        }
    }

    private interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private List<Reminder> query(String sql, Binder binder) {
        List<Reminder> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Reminder(rs.getString("id"), rs.getString("guild_id"), rs.getString("user_id"),
                            rs.getString("channel_id"), rs.getString("message"), rs.getLong("remind_at")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("reminder query", e);
        }
    }

    private void exec(String sql, Binder binder) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("reminder exec", e);
        }
    }
}
