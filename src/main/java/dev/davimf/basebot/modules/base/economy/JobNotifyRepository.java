package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Prefs de aviso de trabalho + watermark de "já avisei" (migração 040). */
public final class JobNotifyRepository {

    public record UserRef(String guildId, String userId) {}

    private final SqliteManager sqlite;

    public JobNotifyRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    public boolean isEnabled(String g, String u) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT job_notify FROM eco_prefs WHERE guild_id=? AND user_id=?")) {
            ps.setString(1, g);
            ps.setString(2, u);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        } catch (SQLException e) {
            throw new RepositoryException("jobnotify isEnabled " + g + "/" + u, e);
        }
    }

    public void setEnabled(String g, String u, boolean on) {
        String sql = "INSERT INTO eco_prefs (guild_id, user_id, job_notify) VALUES (?,?,?) "
                + "ON CONFLICT (guild_id, user_id) DO UPDATE SET job_notify = excluded.job_notify";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, g);
            ps.setString(2, u);
            ps.setInt(3, on ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("jobnotify setEnabled " + g + "/" + u, e);
        }
    }

    public List<UserRef> enabledUsers() {
        List<UserRef> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT guild_id, user_id FROM eco_prefs WHERE job_notify=1")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new UserRef(rs.getString(1), rs.getString(2)));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("jobnotify enabledUsers", e);
        }
    }

    public long notifiedTs(String g, String u, String action) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT notified_ts FROM eco_job_notify WHERE guild_id=? AND user_id=? AND action=?")) {
            ps.setString(1, g);
            ps.setString(2, u);
            ps.setString(3, action);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RepositoryException("jobnotify notifiedTs " + g + "/" + u + "/" + action, e);
        }
    }

    public void stampNotified(String g, String u, String action, long ts) {
        String sql = "INSERT INTO eco_job_notify (guild_id, user_id, action, notified_ts) VALUES (?,?,?,?) "
                + "ON CONFLICT (guild_id, user_id, action) DO UPDATE SET notified_ts = excluded.notified_ts";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, g);
            ps.setString(2, u);
            ps.setString(3, action);
            ps.setLong(4, ts);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("jobnotify stamp " + g + "/" + u + "/" + action, e);
        }
    }
}
