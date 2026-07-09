package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Baldes semanais de tempo em call (migração 039). Fonte de verdade do ranking; o Postgres é
 * só uma cópia para o site, alimentada pelo {@link VoiceTimeFlusher}.
 */
public final class VoiceTimeRepository {

    public record Entry(String userId, long ms) {}

    public record DirtyRow(String guildId, String userId, long weekStart, long ms) {}

    private final SqliteManager sqlite;

    public VoiceTimeRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    /** Soma {@code deltaMs} ao balde da semana e marca a linha como pendente de sync. */
    public void addMs(String guildId, String userId, long weekStart, long deltaMs) {
        String sql = "INSERT INTO voice_weekly_time (guild_id, user_id, week_start, ms, dirty) "
                + "VALUES (?,?,?,?,1) "
                + "ON CONFLICT (guild_id, user_id, week_start) "
                + "DO UPDATE SET ms = ms + excluded.ms, dirty = 1";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setLong(3, weekStart);
            ps.setLong(4, deltaMs);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("addMs " + guildId + "/" + userId, e);
        }
    }

    public long msOf(String guildId, String userId, long weekStart) {
        String sql = "SELECT ms FROM voice_weekly_time WHERE guild_id=? AND user_id=? AND week_start=?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setLong(3, weekStart);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RepositoryException("msOf " + guildId + "/" + userId, e);
        }
    }

    /** Ranking da semana. Quem tem {@code ms = 0} não aparece. */
    public List<Entry> topPage(String guildId, long weekStart, int limit, int offset) {
        String sql = "SELECT user_id, ms FROM voice_weekly_time "
                + "WHERE guild_id=? AND week_start=? AND ms > 0 "
                + "ORDER BY ms DESC, user_id LIMIT ? OFFSET ?";
        List<Entry> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setLong(2, weekStart);
            ps.setInt(3, limit);
            ps.setInt(4, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Entry(rs.getString("user_id"), rs.getLong("ms")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("topPage " + guildId, e);
        }
    }

    public int count(String guildId, long weekStart) {
        String sql = "SELECT COUNT(*) FROM voice_weekly_time WHERE guild_id=? AND week_start=? AND ms > 0";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setLong(2, weekStart);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("count " + guildId, e);
        }
    }

    public List<DirtyRow> dirtyRows(int limit) {
        String sql = "SELECT guild_id, user_id, week_start, ms FROM voice_weekly_time "
                + "WHERE dirty = 1 LIMIT ?";
        List<DirtyRow> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new DirtyRow(rs.getString("guild_id"), rs.getString("user_id"),
                            rs.getLong("week_start"), rs.getLong("ms")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("dirtyRows", e);
        }
    }

    /**
     * Limpa {@code dirty} SÓ se {@code ms} ainda for o valor que subiu ao Postgres. Se o ticker
     * incrementou durante o flush, a linha continua suja e sobe na próxima rodada.
     * {@code ms} é monotonicamente crescente, então igualdade significa "não mudou".
     */
    public boolean clearDirty(String guildId, String userId, long weekStart, long flushedMs) {
        String sql = "UPDATE voice_weekly_time SET dirty = 0 "
                + "WHERE guild_id=? AND user_id=? AND week_start=? AND ms=?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setLong(3, weekStart);
            ps.setLong(4, flushedMs);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("clearDirty " + guildId + "/" + userId, e);
        }
    }

    /** Apaga baldes antigos JÁ sincronizados. Linha suja nunca é podada antes de subir. */
    public int purgeWeeksBefore(long cutoffWeekStart) {
        String sql = "DELETE FROM voice_weekly_time WHERE week_start < ? AND dirty = 0";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, cutoffWeekStart);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("purgeWeeksBefore", e);
        }
    }
}
