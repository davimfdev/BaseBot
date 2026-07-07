package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** SQLite store para XP por usuário (migração 027). */
public final class UserLevelRepository {

    public record Entry(String userId, long xp) {}

    private final SqliteManager sqlite;

    public UserLevelRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    /** Soma {@code delta} ao XP (upsert) e retorna o novo total. */
    public long addXp(String guildId, String userId, long delta) {
        String sql = "INSERT INTO user_levels (guild_id, user_id, xp) VALUES (?,?,?) "
                + "ON CONFLICT (guild_id, user_id) DO UPDATE SET xp = xp + excluded.xp";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setLong(3, delta);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("addXp " + guildId + "/" + userId, e);
        }
        return xp(guildId, userId);
    }

    public long xp(String guildId, String userId) {
        return longQuery("SELECT xp FROM user_levels WHERE guild_id=? AND user_id=?", guildId, userId);
    }

    public long lastMessageTs(String guildId, String userId) {
        return longQuery("SELECT last_message_ts FROM user_levels WHERE guild_id=? AND user_id=?", guildId, userId);
    }

    public void setLastMessageTs(String guildId, String userId, long ts) {
        String sql = "INSERT INTO user_levels (guild_id, user_id, last_message_ts) VALUES (?,?,?) "
                + "ON CONFLICT (guild_id, user_id) DO UPDATE SET last_message_ts = excluded.last_message_ts";
        exec(sql, ps -> { ps.setString(1, guildId); ps.setString(2, userId); ps.setLong(3, ts); });
    }

    public void setXp(String guildId, String userId, long xp) {
        String sql = "INSERT INTO user_levels (guild_id, user_id, xp) VALUES (?,?,?) "
                + "ON CONFLICT (guild_id, user_id) DO UPDATE SET xp = excluded.xp";
        exec(sql, ps -> { ps.setString(1, guildId); ps.setString(2, userId); ps.setLong(3, Math.max(0, xp)); });
    }

    public List<Entry> topPage(String guildId, int limit, int offset) {
        List<Entry> out = new ArrayList<>();
        String sql = "SELECT user_id, xp FROM user_levels WHERE guild_id=? ORDER BY xp DESC, user_id LIMIT ? OFFSET ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Entry(rs.getString("user_id"), rs.getLong("xp")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("topPage " + guildId, e);
        }
    }

    /** Posição 1-based do usuário no ranking por XP (0 se sem registro). */
    public int rank(String guildId, String userId) {
        long myXp = xp(guildId, userId);
        if (myXp <= 0 && count(guildId) == 0) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM user_levels WHERE guild_id=? AND xp > ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setLong(2, myXp);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) + 1 : 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("rank " + guildId + "/" + userId, e);
        }
    }

    public int count(String guildId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM user_levels WHERE guild_id=?")) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("count " + guildId, e);
        }
    }

    private long longQuery(String sql, String guildId, String userId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RepositoryException("query " + guildId + "/" + userId, e);
        }
    }

    private interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private void exec(String sql, Binder binder) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("exec", e);
        }
    }
}
