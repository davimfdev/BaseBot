package dev.davimf.basebot.modules.facs.punish;

import dev.davimf.basebot.database.model.Punishment;
import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQLite store for punishments (BOTSPECS Module 4 — /punir, /punições). */
public final class PunishmentRepository {

    private final SqliteManager sqlite;

    public PunishmentRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public String create(String guildId, String userId, String type, int level, String reason,
                         String appliedBy, String expiresAtIso) {
        String id = newId();
        String sql = """
                INSERT INTO punishments
                    (id, guild_id, user_id, type, level, reason, applied_by, expires_at, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
                """;
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, guildId);
            ps.setString(3, userId);
            ps.setString(4, type);
            ps.setInt(5, level);
            ps.setString(6, reason);
            ps.setString(7, appliedBy);
            ps.setString(8, expiresAtIso);
            ps.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new RepositoryException("create punishment for " + userId, e);
        }
    }

    public Optional<Punishment> find(String id) {
        return queryOne("SELECT * FROM punishments WHERE id = ?", id);
    }

    public List<Punishment> listByUser(String guildId, String userId) {
        return query("SELECT * FROM punishments WHERE guild_id = ? AND user_id = ? "
                + "ORDER BY created_at DESC", guildId, userId);
    }

    public List<Punishment> listActiveByUser(String guildId, String userId) {
        return query("SELECT * FROM punishments WHERE guild_id = ? AND user_id = ? AND active = 1 "
                + "ORDER BY created_at DESC", guildId, userId);
    }

    /** Highest active ADV level for a user, or 0 when none is active. */
    public int highestActiveAdvLevel(String guildId, String userId) {
        String sql = "SELECT COALESCE(MAX(level), 0) FROM punishments "
                + "WHERE guild_id = ? AND user_id = ? AND type = 'ADV' AND active = 1";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("max adv level for " + userId, e);
        }
    }

    /** Deactivates all active ADVs for a user (used when a new ADV replaces the stack). */
    public void deactivateAdvs(String guildId, String userId) {
        update("UPDATE punishments SET active = 0 WHERE guild_id = ? AND user_id = ? "
                + "AND type = 'ADV' AND active = 1", guildId, userId);
    }

    public void setActive(String id, boolean active) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE punishments SET active = ? WHERE id = ?")) {
            ps.setInt(1, active ? 1 : 0);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("set active for punishment " + id, e);
        }
    }

    /** Active ADVs whose {@code expires_at} is at or before {@code nowIso}. */
    public List<Punishment> listExpiredAdvs(String nowIso) {
        return query("SELECT * FROM punishments WHERE type = 'ADV' AND active = 1 "
                + "AND expires_at IS NOT NULL AND expires_at <= ?", nowIso);
    }

    // --- helpers ---------------------------------------------------------------

    private void update(String sql, String a, String b) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, a);
            ps.setString(2, b);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("update punishments", e);
        }
    }

    private List<Punishment> query(String sql, String... args) {
        List<Punishment> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setString(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("query punishments", e);
        }
    }

    private Optional<Punishment> queryOne(String sql, String arg) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("query punishment", e);
        }
    }

    private static Punishment map(ResultSet rs) throws SQLException {
        return new Punishment(
                rs.getString("id"),
                rs.getString("guild_id"),
                rs.getString("user_id"),
                rs.getString("type"),
                rs.getInt("level"),
                rs.getString("reason"),
                rs.getString("applied_by"),
                rs.getString("created_at"),
                rs.getString("expires_at"),
                rs.getInt("active") != 0);
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
