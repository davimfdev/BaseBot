package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Cooldowns por (guild,user,action) para /daily /trabalhar /crime /roubar (migração 029). */
public final class CooldownRepository {

    private final SqliteManager sqlite;

    public CooldownRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    public long lastTs(String g, String u, String action) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT last_ts FROM eco_cooldowns WHERE guild_id=? AND user_id=? AND action=?")) {
            ps.setString(1, g);
            ps.setString(2, u);
            ps.setString(3, action);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RepositoryException("cooldown get " + g + "/" + u + "/" + action, e);
        }
    }

    public void stamp(String g, String u, String action, long ts) {
        String sql = "INSERT INTO eco_cooldowns (guild_id, user_id, action, last_ts) VALUES (?,?,?,?) "
                + "ON CONFLICT (guild_id, user_id, action) DO UPDATE SET last_ts = excluded.last_ts";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, g);
            ps.setString(2, u);
            ps.setString(3, action);
            ps.setLong(4, ts);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("cooldown stamp " + g + "/" + u + "/" + action, e);
        }
    }
}
