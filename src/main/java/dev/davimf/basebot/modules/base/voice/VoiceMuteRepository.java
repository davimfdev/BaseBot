package dev.davimf.basebot.modules.base.voice;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Tracks persistent voice (call) mutes so they survive reconnects (BOTSPECS Module 1). */
public final class VoiceMuteRepository {

    private final SqliteManager sqlite;

    public VoiceMuteRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public void add(String guildId, String userId) {
        String sql = "INSERT INTO voice_mutes (guild_id, user_id) VALUES (?, ?) "
                + "ON CONFLICT (guild_id, user_id) DO NOTHING";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("add voice mute " + guildId + "/" + userId, e);
        }
    }

    public void remove(String guildId, String userId) {
        String sql = "DELETE FROM voice_mutes WHERE guild_id = ? AND user_id = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("remove voice mute " + guildId + "/" + userId, e);
        }
    }

    public boolean isMuted(String guildId, String userId) {
        String sql = "SELECT 1 FROM voice_mutes WHERE guild_id = ? AND user_id = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RepositoryException("check voice mute " + guildId + "/" + userId, e);
        }
    }
}
