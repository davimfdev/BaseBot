package dev.davimf.basebot.database.postgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Postgres store for saved action types (BOTSPECS Module 4), config editável pelo dashboard.
 * Managed in {@code /setup → Ações} and consumed by {@code /painel-acoes}: each type carries a
 * name, contingent bounds and a dirty-money payout.
 */
public final class ActionTypeRepository {

    /** A saved action type. */
    public record ActionType(String id, String guildId, String name,
                             int maxContingent, int minContingent, int dirtyMoney) {}

    private final PostgresPool pool;

    public ActionTypeRepository(PostgresPool pool) {
        this.pool = pool;
    }

    public List<ActionType> listByGuild(String guildId) {
        String sql = "SELECT * FROM fac_action_types WHERE guild_id = ? ORDER BY name";
        List<ActionType> out = new ArrayList<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list action types " + guildId, e);
        }
    }

    public Optional<ActionType> find(String id) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM fac_action_types WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("find action type " + id, e);
        }
    }

    public int count(String guildId) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM fac_action_types WHERE guild_id = ?")) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RepositoryException("count action types " + guildId, e);
        }
    }

    public void upsert(ActionType type) {
        String sql = """
                INSERT INTO fac_action_types
                    (id, guild_id, name, max_contingent, min_contingent, dirty_money)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    name           = EXCLUDED.name,
                    max_contingent = EXCLUDED.max_contingent,
                    min_contingent = EXCLUDED.min_contingent,
                    dirty_money    = EXCLUDED.dirty_money
                """;
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, type.id());
            ps.setString(2, type.guildId());
            ps.setString(3, type.name());
            ps.setInt(4, type.maxContingent());
            ps.setInt(5, type.minContingent());
            ps.setInt(6, type.dirtyMoney());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("upsert action type " + type.id(), e);
        }
    }

    public void delete(String id) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM fac_action_types WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("delete action type " + id, e);
        }
    }

    private static ActionType map(ResultSet rs) throws SQLException {
        return new ActionType(rs.getString("id"), rs.getString("guild_id"), rs.getString("name"),
                rs.getInt("max_contingent"), rs.getInt("min_contingent"), rs.getInt("dirty_money"));
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
