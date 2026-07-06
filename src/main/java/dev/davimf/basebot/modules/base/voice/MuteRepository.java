// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.voice
// 
// Class: MuteRepository
// 
// Constructors:
//   - `Constructor` : `public MuteRepository(SqliteManager sqlite)`
// 
// Methods:
//   - `Method` : `public boolean isActive(String guildId, String userId, String type, long nowMillis)`
//   - `Method` : `public List<Entry> listExpired(long nowMillis)`
// 
// Fields:
//   - `Field` : `public static final String TEXT`
//   - `Field` : `public static final String VOICE`
//   - `Field` : `private final SqliteManager sqlite`
// 
// Record: Entry
// 
// Record Components:
//   - Record Component : public final String guildId
//   - Record Component : public final String userId
//   - Record Component : public final String type
// [OUTLINE END]



package dev.davimf.basebot.modules.base.voice;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Timed mutes (TEXT role + VOICE server mute) with an expiry (BOTSPECS Module 1). */
public final class MuteRepository {

    public static final String TEXT = "TEXT";
    public static final String VOICE = "VOICE";

    /** An expired mute to lift. */
    public record Entry(String guildId, String userId, String type) {}

    private final SqliteManager sqlite;

    public MuteRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public void add(String guildId, String userId, String type, long expiresAtMillis) {
        String sql = """
                INSERT INTO timed_mutes (guild_id, user_id, type, expires_at_millis)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (guild_id, user_id, type) DO UPDATE SET expires_at_millis = excluded.expires_at_millis
                """;
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setString(3, type);
            ps.setLong(4, expiresAtMillis);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("add timed mute " + guildId + "/" + userId, e);
        }
    }

    public void remove(String guildId, String userId, String type) {
        String sql = "DELETE FROM timed_mutes WHERE guild_id = ? AND user_id = ? AND type = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setString(3, type);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("remove timed mute " + guildId + "/" + userId, e);
        }
    }

    /** True if a non-expired mute of {@code type} exists for the user. */
    public boolean isActive(String guildId, String userId, String type, long nowMillis) {
        String sql = "SELECT 1 FROM timed_mutes WHERE guild_id = ? AND user_id = ? AND type = ? "
                + "AND expires_at_millis > ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setString(3, type);
            ps.setLong(4, nowMillis);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RepositoryException("check timed mute " + guildId + "/" + userId, e);
        }
    }

    public List<Entry> listExpired(long nowMillis) {
        String sql = "SELECT guild_id, user_id, type FROM timed_mutes WHERE expires_at_millis <= ?";
        List<Entry> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, nowMillis);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Entry(rs.getString("guild_id"), rs.getString("user_id"), rs.getString("type")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list expired mutes", e);
        }
    }
}
