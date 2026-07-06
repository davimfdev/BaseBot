package dev.davimf.basebot.modules.base.moderation;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQLite store for moderation cases (Base moderation, design 2026-06-29). */
public final class InfractionRepository {

    private final SqliteManager sqlite;

    public InfractionRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    /** Inserts a case, assigning the next per-guild {@code case_number} atomically. */
    public Infraction create(String guildId, String userId, String modId, String type, String reason,
                             long createdAt, Long expiresAt, Long durationMs) {
        try (Connection c = sqlite.getConnection()) {
            c.setAutoCommit(false);
            try {
                int caseNumber;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COALESCE(MAX(case_number), 0) + 1 FROM infractions WHERE guild_id = ?")) {
                    ps.setString(1, guildId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        caseNumber = rs.getInt(1);
                    }
                }
                long id;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO infractions (guild_id, case_number, user_id, mod_id, type, reason, "
                                + "created_at, expires_at, duration_ms, active) VALUES (?,?,?,?,?,?,?,?,?,1)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, guildId);
                    ps.setInt(2, caseNumber);
                    ps.setString(3, userId);
                    ps.setString(4, modId);
                    ps.setString(5, type);
                    ps.setString(6, reason);
                    ps.setLong(7, createdAt);
                    setNullableLong(ps, 8, expiresAt);
                    setNullableLong(ps, 9, durationMs);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        id = keys.getLong(1);
                    }
                }
                c.commit();
                return new Infraction(id, guildId, caseNumber, userId, modId, type, reason,
                        createdAt, expiresAt, durationMs, true);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RepositoryException("create infraction " + guildId + "/" + userId, e);
        }
    }

    public Optional<Infraction> findByCase(String guildId, int caseNumber) {
        String sql = "SELECT * FROM infractions WHERE guild_id = ? AND case_number = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setInt(2, caseNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("find infraction case " + guildId + "#" + caseNumber, e);
        }
    }

    /** All of a user's cases, newest first. */
    public List<Infraction> listByUser(String guildId, String userId) {
        return query("SELECT * FROM infractions WHERE guild_id = ? AND user_id = ? "
                + "ORDER BY case_number DESC", guildId, userId);
    }

    /** A user's still-active cases (for the /revogar picker), newest first. */
    public List<Infraction> listActiveByUser(String guildId, String userId) {
        return query("SELECT * FROM infractions WHERE guild_id = ? AND user_id = ? AND active = 1 "
                + "ORDER BY case_number DESC", guildId, userId);
    }

    /** Count of active, non-expired warns — the number escalation rules are matched against. */
    public int countActiveWarns(String guildId, String userId, long nowMillis) {
        String sql = "SELECT COUNT(*) FROM infractions WHERE guild_id = ? AND user_id = ? "
                + "AND type = 'WARN' AND active = 1 AND (expires_at IS NULL OR expires_at > ?)";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setLong(3, nowMillis);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RepositoryException("count warns " + guildId + "/" + userId, e);
        }
    }

    /** Marks a case inactive by case number. Returns true if a row was updated. */
    public boolean revoke(String guildId, int caseNumber) {
        String sql = "UPDATE infractions SET active = 0 WHERE guild_id = ? AND case_number = ? AND active = 1";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setInt(2, caseNumber);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("revoke case " + guildId + "#" + caseNumber, e);
        }
    }

    /** Deactivates the most recent active case of a type for a user (used by /unban, /unmute…). */
    public void deactivateLatest(String guildId, String userId, String type) {
        String sql = "UPDATE infractions SET active = 0 WHERE id = (SELECT id FROM infractions "
                + "WHERE guild_id = ? AND user_id = ? AND type = ? AND active = 1 "
                + "ORDER BY case_number DESC LIMIT 1)";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setString(3, type);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("deactivate latest " + type + " " + guildId + "/" + userId, e);
        }
    }

    public void deactivate(long id) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE infractions SET active = 0 WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("deactivate infraction " + id, e);
        }
    }

    /** Active cases whose expiry has passed — warns to decay, tempbans to lift. */
    public List<Infraction> listExpiredActive(long nowMillis) {
        String sql = "SELECT * FROM infractions WHERE active = 1 AND expires_at IS NOT NULL "
                + "AND expires_at <= ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, nowMillis);
            try (ResultSet rs = ps.executeQuery()) {
                List<Infraction> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RepositoryException("list expired infractions", e);
        }
    }

    private List<Infraction> query(String sql, String guildId, String userId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Infraction> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RepositoryException("query infractions " + guildId + "/" + userId, e);
        }
    }

    private static Infraction map(ResultSet rs) throws SQLException {
        long expires = rs.getLong("expires_at");
        Long expiresAt = rs.wasNull() ? null : expires;
        long dur = rs.getLong("duration_ms");
        Long durationMs = rs.wasNull() ? null : dur;
        return new Infraction(
                rs.getLong("id"),
                rs.getString("guild_id"),
                rs.getInt("case_number"),
                rs.getString("user_id"),
                rs.getString("mod_id"),
                rs.getString("type"),
                rs.getString("reason"),
                rs.getLong("created_at"),
                expiresAt,
                durationMs,
                rs.getInt("active") != 0);
    }

    private static void setNullableLong(PreparedStatement ps, int idx, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.BIGINT);
        } else {
            ps.setLong(idx, value);
        }
    }
}
