package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.postgres.PostgresPool;
import dev.davimf.basebot.database.postgres.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Postgres store para cargos por nível (config editável pelo dashboard). */
public final class LevelRewardRepository {

    private final PostgresPool pool;

    public LevelRewardRepository(PostgresPool pool) { this.pool = pool; }

    public void put(String guildId, int level, String roleId) {
        String sql = "INSERT INTO level_rewards (guild_id, level, role_id) VALUES (?,?,?) "
                + "ON CONFLICT (guild_id, level) DO UPDATE SET role_id = EXCLUDED.role_id";
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setInt(2, level);
            ps.setString(3, roleId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("put reward " + guildId + "/" + level, e);
        }
    }

    public void remove(String guildId, int level) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM level_rewards WHERE guild_id=? AND level=?")) {
            ps.setString(1, guildId);
            ps.setInt(2, level);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("remove reward " + guildId + "/" + level, e);
        }
    }

    /** Mapa nível→roleId ordenado por nível. */
    public Map<Integer, String> all(String guildId) {
        Map<Integer, String> out = new LinkedHashMap<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT level, role_id FROM level_rewards WHERE guild_id=? ORDER BY level")) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getInt("level"), rs.getString("role_id"));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("all rewards " + guildId, e);
        }
    }
}
