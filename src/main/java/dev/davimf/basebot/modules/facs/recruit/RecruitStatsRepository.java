package dev.davimf.basebot.modules.facs.recruit;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Recruiter statistics (BOTSPECS Module 4) backed by the {@code recruiter_stats} table. */
public final class RecruitStatsRepository {

    private final SqliteManager sqlite;

    public RecruitStatsRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    /** Increments a recruiter's count by one and returns the new total. */
    public int increment(String guildId, String recruiterId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO recruiter_stats (guild_id, recruiter_id, recruits) VALUES (?, ?, 1)
                     ON CONFLICT (guild_id, recruiter_id) DO UPDATE SET recruits = recruits + 1
                     """)) {
            ps.setString(1, guildId);
            ps.setString(2, recruiterId);
            ps.executeUpdate();
            return get(guildId, recruiterId);
        } catch (SQLException e) {
            throw new RepositoryException("increment recruiter " + recruiterId, e);
        }
    }

    public int get(String guildId, String recruiterId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT recruits FROM recruiter_stats WHERE guild_id = ? AND recruiter_id = ?")) {
            ps.setString(1, guildId);
            ps.setString(2, recruiterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("get recruiter " + recruiterId, e);
        }
    }
}
