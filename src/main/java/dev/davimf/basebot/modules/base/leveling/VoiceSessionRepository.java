package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** SQLite store para sessões de voz (migração 028). Fonte de verdade do tempo em call. */
public final class VoiceSessionRepository {

    public record Open(long id, String guildId, String userId, String channelId,
                       long joinTime, long xpCreditedUntil) {}

    private final SqliteManager sqlite;

    public VoiceSessionRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    public void open(String guildId, String userId, String channelId, long now) {
        String sql = "INSERT INTO voice_sessions (guild_id, user_id, channel_id, join_time, xp_credited_until) "
                + "VALUES (?,?,?,?,?)";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setString(3, channelId);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("open voice session " + guildId + "/" + userId, e);
        }
    }

    public void closeOpen(String guildId, String userId, long now) {
        String sql = "UPDATE voice_sessions SET leave_time=? WHERE guild_id=? AND user_id=? AND leave_time IS NULL";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, now);
            ps.setString(2, guildId);
            ps.setString(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("close voice session " + guildId + "/" + userId, e);
        }
    }

    public List<Open> openSessions() {
        return query("SELECT * FROM voice_sessions WHERE leave_time IS NULL", null);
    }

    /** Tempo total em call (soma das sessões; abertas contam até agora). */
    public long totalVoiceMs(String guildId, String userId) {
        long now = System.currentTimeMillis();
        long total = 0;
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT join_time, leave_time FROM voice_sessions WHERE guild_id=? AND user_id=?")) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long join = rs.getLong("join_time");
                    long leave = rs.getObject("leave_time") == null ? now : rs.getLong("leave_time");
                    if (leave > join) {
                        total += leave - join;
                    }
                }
            }
            return total;
        } catch (SQLException e) {
            throw new RepositoryException("total voice " + guildId + "/" + userId, e);
        }
    }

    /** Pares [join, leave] (leave = agora se aberta) de todas as sessões do usuário. */
    public List<long[]> sessionsOf(String guildId, String userId) {
        long now = System.currentTimeMillis();
        List<long[]> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT join_time, leave_time FROM voice_sessions WHERE guild_id=? AND user_id=?")) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long join = rs.getLong("join_time");
                    long leave = rs.getObject("leave_time") == null ? now : rs.getLong("leave_time");
                    out.add(new long[]{join, leave});
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("sessionsOf " + guildId + "/" + userId, e);
        }
    }

    public List<Open> openSessions(String guildId) {
        return query("SELECT * FROM voice_sessions WHERE leave_time IS NULL AND guild_id=?", guildId);
    }

    private List<Open> query(String sql, String guildId) {
        List<Open> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (guildId != null) {
                ps.setString(1, guildId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Open(rs.getLong("id"), rs.getString("guild_id"), rs.getString("user_id"),
                            rs.getString("channel_id"), rs.getLong("join_time"), rs.getLong("xp_credited_until")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("query voice sessions", e);
        }
    }
}
