package dev.davimf.basebot.database.postgres;

import dev.davimf.basebot.modules.base.vip.VipGrant;
import dev.davimf.basebot.modules.base.vip.VipProvisionStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcVipGrantRepository implements VipGrantRepository {
    private final PostgresPool pool;

    public JdbcVipGrantRepository(PostgresPool pool) { this.pool = pool; }

    @Override public void upsertActive(VipGrant g) {
        String sql = """
                INSERT INTO vip_grants
                  (id, guild_id, plan_id, user_id, call_channel_id, control_role_id, reveal_on_occupancy,
                   granted_at, expires_at, active, provision_status, provision_error, granted_by, updated_at)
                VALUES (?,?,?,?,?,?,?, now(), ?, true, ?, NULL, ?, now())
                """;
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, g.id()); ps.setString(2, g.guildId()); ps.setString(3, g.planId());
            ps.setString(4, g.userId()); ps.setString(5, g.callChannelId()); ps.setString(6, g.controlRoleId());
            ps.setBoolean(7, g.revealOnOccupancy());
            if (g.expiresAt() == null) ps.setNull(8, Types.TIMESTAMP_WITH_TIMEZONE);
            else ps.setObject(8, OffsetDateTime.ofInstant(g.expiresAt(), ZoneOffset.UTC));
            ps.setString(9, g.provisionStatus().db());
            ps.setString(10, g.grantedBy());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RepositoryException("upsert vip_grant " + g.id(), e); }
    }

    @Override public Optional<VipGrant> findActiveByUser(String guildId, String userId) {
        String sql = "SELECT * FROM vip_grants WHERE guild_id = ? AND user_id = ? AND active = true";
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId); ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) { throw new RepositoryException("findActiveByUser " + guildId + "/" + userId, e); }
    }

    @Override public Optional<VipGrant> findById(String id) {
        String sql = "SELECT * FROM vip_grants WHERE id = ?";
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) { throw new RepositoryException("find vip_grant " + id, e); }
    }

    @Override public List<VipGrant> activeGrants() {
        String sql = "SELECT * FROM vip_grants WHERE active = true ORDER BY granted_at DESC";
        List<VipGrant> out = new ArrayList<>();
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) { throw new RepositoryException("activeGrants", e); }
        return out;
    }

    @Override public List<VipGrant> listActiveByGuild(String guildId) {
        String sql = "SELECT * FROM vip_grants WHERE guild_id = ? AND active = true ORDER BY granted_at DESC";
        List<VipGrant> out = new ArrayList<>();
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(map(rs)); }
        } catch (SQLException e) { throw new RepositoryException("listActiveByGuild " + guildId, e); }
        return out;
    }

    @Override public List<VipGrant> dueForExpiry(Instant now) {
        String sql = "SELECT * FROM vip_grants WHERE active = true AND expires_at IS NOT NULL AND expires_at <= ? ORDER BY expires_at";
        List<VipGrant> out = new ArrayList<>();
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(map(rs)); }
        } catch (SQLException e) { throw new RepositoryException("dueForExpiry", e); }
        return out;
    }

    @Override public void updateProvision(String id, VipProvisionStatus status, String error,
                                          String callChannelId, String controlRoleId) {
        String sql = """
                UPDATE vip_grants SET provision_status = ?, provision_error = ?,
                       call_channel_id = COALESCE(?, call_channel_id),
                       control_role_id = COALESCE(?, control_role_id), updated_at = now()
                 WHERE id = ?
                """;
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status.db()); ps.setString(2, error);
            ps.setString(3, callChannelId); ps.setString(4, controlRoleId); ps.setString(5, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RepositoryException("updateProvision " + id, e); }
    }

    @Override public void deactivate(String id, VipProvisionStatus status, String revokedBy, Instant revokedAt) {
        String sql = """
                UPDATE vip_grants SET active = false, provision_status = ?, revoked_by = ?,
                       revoked_at = ?, updated_at = now() WHERE id = ?
                """;
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status.db()); ps.setString(2, revokedBy);
            ps.setObject(3, OffsetDateTime.ofInstant(revokedAt, ZoneOffset.UTC)); ps.setString(4, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RepositoryException("deactivate " + id, e); }
    }

    @Override public void updateReveal(String id, boolean reveal) {
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE vip_grants SET reveal_on_occupancy = ?, updated_at = now() WHERE id = ?")) {
            ps.setBoolean(1, reveal); ps.setString(2, id); ps.executeUpdate();
        } catch (SQLException e) { throw new RepositoryException("updateReveal " + id, e); }
    }

    private VipGrant map(ResultSet rs) throws SQLException {
        return new VipGrant(
                rs.getString("id"), rs.getString("guild_id"), rs.getString("plan_id"), rs.getString("user_id"),
                rs.getString("call_channel_id"), rs.getString("control_role_id"),
                rs.getBoolean("reveal_on_occupancy"),
                inst(rs, "granted_at"), inst(rs, "expires_at"), rs.getBoolean("active"),
                VipProvisionStatus.fromDb(rs.getString("provision_status")), rs.getString("provision_error"),
                rs.getString("granted_by"), inst(rs, "revoked_at"), rs.getString("revoked_by"),
                inst(rs, "updated_at"));
    }

    private static Instant inst(ResultSet rs, String col) throws SQLException {
        OffsetDateTime odt = rs.getObject(col, OffsetDateTime.class);
        return odt == null ? null : odt.toInstant();
    }
}
