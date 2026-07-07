package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Pontos sociais (rep/biscoito) com gate de cooldown atômico (migração 031). */
public final class SocialRepository {

    public record GiveResult(boolean ok, long newPoints, long readyAt) {}
    public record Entry(String userId, long points) {}

    private final SqliteManager sqlite;

    public SocialRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    public GiveResult give(String g, String giver, String target, String type, long now, long cooldownMs) {
        if (!gate(g, giver, type, now, cooldownMs)) {
            return new GiveResult(false, points(g, target, type), lastTs(g, giver, type) + cooldownMs);
        }
        addPoint(g, target, type);
        return new GiveResult(true, points(g, target, type), 0);
    }

    /** Escrita condicional: primeira vez (INSERT OR IGNORE) ou cooldown vencido (UPDATE). */
    private boolean gate(String g, String giver, String type, long now, long cooldownMs) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ins = c.prepareStatement(
                     "INSERT OR IGNORE INTO social_gifts (guild_id, giver_id, type, last_ts) VALUES (?,?,?,?)")) {
            ins.setString(1, g);
            ins.setString(2, giver);
            ins.setString(3, type);
            ins.setLong(4, now);
            if (ins.executeUpdate() > 0) {
                return true;
            }
        } catch (SQLException e) {
            throw new RepositoryException("social gate insert " + g + "/" + giver, e);
        }
        try (Connection c = sqlite.getConnection();
             PreparedStatement up = c.prepareStatement(
                     "UPDATE social_gifts SET last_ts=? WHERE guild_id=? AND giver_id=? AND type=? AND (? - last_ts) >= ?")) {
            up.setLong(1, now);
            up.setString(2, g);
            up.setString(3, giver);
            up.setString(4, type);
            up.setLong(5, now);
            up.setLong(6, cooldownMs);
            return up.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("social gate update " + g + "/" + giver, e);
        }
    }

    private void addPoint(String g, String user, String type) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO social_points (guild_id, user_id, type, points) VALUES (?,?,?,1) "
                     + "ON CONFLICT (guild_id, user_id, type) DO UPDATE SET points = points + 1")) {
            ps.setString(1, g);
            ps.setString(2, user);
            ps.setString(3, type);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("social addPoint " + g + "/" + user, e);
        }
    }

    public long points(String g, String user, String type) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT points FROM social_points WHERE guild_id=? AND user_id=? AND type=?")) {
            ps.setString(1, g);
            ps.setString(2, user);
            ps.setString(3, type);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RepositoryException("social points " + g + "/" + user, e);
        }
    }

    private long lastTs(String g, String giver, String type) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT last_ts FROM social_gifts WHERE guild_id=? AND giver_id=? AND type=?")) {
            ps.setString(1, g);
            ps.setString(2, giver);
            ps.setString(3, type);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RepositoryException("social lastTs " + g + "/" + giver, e);
        }
    }

    public List<Entry> top(String g, String type, int limit) {
        List<Entry> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT user_id, points FROM social_points WHERE guild_id=? AND type=? "
                     + "ORDER BY points DESC, user_id LIMIT ?")) {
            ps.setString(1, g);
            ps.setString(2, type);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Entry(rs.getString("user_id"), rs.getLong("points")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("social top " + g, e);
        }
    }
}
