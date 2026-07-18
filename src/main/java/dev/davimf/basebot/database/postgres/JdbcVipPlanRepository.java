package dev.davimf.basebot.database.postgres;

import dev.davimf.basebot.modules.base.vip.VipPlan;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcVipPlanRepository implements VipPlanRepository {
    private final PostgresPool pool;

    public JdbcVipPlanRepository(PostgresPool pool) { this.pool = pool; }

    @Override public void upsert(VipPlan p) {
        String sql = """
                INSERT INTO vip_plans
                  (id, guild_id, name, discord_category_id, has_call, vip_role_id, use_control_role,
                   xp_bonus_pct, eco_bonus_pct, default_duration_minutes, reveal_default, enabled, position, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?, now())
                ON CONFLICT (id) DO UPDATE SET
                  name = EXCLUDED.name, discord_category_id = EXCLUDED.discord_category_id,
                  has_call = EXCLUDED.has_call, vip_role_id = EXCLUDED.vip_role_id,
                  use_control_role = EXCLUDED.use_control_role, xp_bonus_pct = EXCLUDED.xp_bonus_pct,
                  eco_bonus_pct = EXCLUDED.eco_bonus_pct, default_duration_minutes = EXCLUDED.default_duration_minutes,
                  reveal_default = EXCLUDED.reveal_default, enabled = EXCLUDED.enabled,
                  position = EXCLUDED.position, updated_at = now()
                """;
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p.id());
            ps.setString(2, p.guildId());
            ps.setString(3, p.name());
            ps.setString(4, p.discordCategoryId());
            ps.setBoolean(5, p.hasCall());
            ps.setString(6, p.vipRoleId());
            ps.setBoolean(7, p.useControlRole());
            ps.setInt(8, p.xpBonusPct());
            ps.setInt(9, p.ecoBonusPct());
            if (p.defaultDurationMinutes() == null) ps.setNull(10, Types.BIGINT);
            else ps.setLong(10, p.defaultDurationMinutes());
            ps.setBoolean(11, p.revealDefault());
            ps.setBoolean(12, p.enabled());
            ps.setInt(13, p.position());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RepositoryException("upsert vip_plan " + p.id(), e); }
    }

    @Override public Optional<VipPlan> findById(String id) {
        String sql = "SELECT * FROM vip_plans WHERE id = ?";
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) { throw new RepositoryException("find vip_plan " + id, e); }
    }

    @Override public List<VipPlan> listByGuild(String guildId) { return query(guildId, false); }
    @Override public List<VipPlan> listEnabledByGuild(String guildId) { return query(guildId, true); }

    private List<VipPlan> query(String guildId, boolean onlyEnabled) {
        String sql = "SELECT * FROM vip_plans WHERE guild_id = ?"
                + (onlyEnabled ? " AND enabled = true" : "") + " ORDER BY position, created_at";
        List<VipPlan> out = new ArrayList<>();
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(map(rs)); }
        } catch (SQLException e) { throw new RepositoryException("list vip_plans " + guildId, e); }
        return out;
    }

    @Override public void delete(String id) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM vip_plans WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RepositoryException("delete vip_plan " + id, e); }
    }

    private VipPlan map(ResultSet rs) throws SQLException {
        long durMin = rs.getLong("default_duration_minutes");
        Long dur = rs.wasNull() ? null : durMin;
        return new VipPlan(
                rs.getString("id"), rs.getString("guild_id"), rs.getString("name"),
                rs.getString("discord_category_id"), rs.getBoolean("has_call"), rs.getString("vip_role_id"),
                rs.getBoolean("use_control_role"), rs.getInt("xp_bonus_pct"), rs.getInt("eco_bonus_pct"),
                dur, rs.getBoolean("reveal_default"), rs.getBoolean("enabled"), rs.getInt("position"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
