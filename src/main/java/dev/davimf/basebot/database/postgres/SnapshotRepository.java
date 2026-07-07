package dev.davimf.basebot.database.postgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Publishes per-guild Discord snapshots so the dashboard can validate channels/roles without a
 * bot token. Every write is scoped to this bot's {@code botInstanceId}: a compromised instance
 * cannot touch another client's rows (v1 = guards in WHERE; RLS is the future hardening).
 */
public final class SnapshotRepository {

    public record ChannelRow(String channelId, String name, String type, String parentId,
                             Integer position, boolean canView, boolean canSend) {}

    public record RoleRow(String roleId, String name, Integer position, boolean managed, boolean canAssign) {}

    private final PostgresPool pool;
    private final String botInstanceId;

    public SnapshotRepository(PostgresPool pool, String botInstanceId) {
        this.pool = pool;
        this.botInstanceId = botInstanceId;
    }

    public void upsertInstance(String botUserId, String applicationId, String clientName) {
        String sql = """
                INSERT INTO bot_instances (id, client_name, bot_user_id, application_id, active)
                VALUES (?::uuid, ?, ?, ?, true)
                ON CONFLICT (id) DO UPDATE SET
                    client_name    = EXCLUDED.client_name,
                    bot_user_id    = EXCLUDED.bot_user_id,
                    application_id = EXCLUDED.application_id,
                    active         = true
                """;
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, botInstanceId);
            ps.setString(2, clientName);
            ps.setString(3, botUserId);
            ps.setString(4, applicationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("upsert bot_instance " + botInstanceId, e);
        }
    }

    /** Upsert guarded so this instance never overwrites a guild owned by another instance. */
    public boolean upsertGuild(String guildId, String guildName, String ownerId) {
        String sql = """
                INSERT INTO bot_guilds (guild_id, bot_instance_id, guild_name, owner_id, bot_present, last_seen_at)
                VALUES (?, ?::uuid, ?, ?, true, now())
                ON CONFLICT (guild_id) DO UPDATE SET
                    guild_name   = EXCLUDED.guild_name,
                    owner_id     = EXCLUDED.owner_id,
                    bot_present  = true,
                    last_seen_at = now()
                WHERE bot_guilds.bot_instance_id = ?::uuid
                """;
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, botInstanceId);
            ps.setString(3, guildName);
            ps.setString(4, ownerId);
            ps.setString(5, botInstanceId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("upsert bot_guild " + guildId, e);
        }
    }

    public void markAbsent(String guildId) {
        String sql = "UPDATE bot_guilds SET bot_present = false, last_seen_at = now() "
                + "WHERE guild_id = ? AND bot_instance_id = ?::uuid";
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, botInstanceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("mark absent " + guildId, e);
        }
    }

    public void replaceChannels(String guildId, List<ChannelRow> rows) {
        try (Connection c = pool.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                if (!ownsGuild(c, guildId)) {
                    c.rollback();
                    return;
                }
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM guild_channels_snapshot WHERE guild_id = ?")) {
                    del.setString(1, guildId);
                    del.executeUpdate();
                }
                String ins = "INSERT INTO guild_channels_snapshot "
                        + "(guild_id, channel_id, name, type, parent_id, position, bot_can_view, bot_can_send, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?, now())";
                try (PreparedStatement ps = c.prepareStatement(ins)) {
                    for (ChannelRow r : rows) {
                        ps.setString(1, guildId);
                        ps.setString(2, r.channelId());
                        ps.setString(3, r.name());
                        ps.setString(4, r.type());
                        ps.setString(5, r.parentId());
                        setNullableInt(ps, 6, r.position());
                        ps.setBoolean(7, r.canView());
                        ps.setBoolean(8, r.canSend());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            throw new RepositoryException("replace channels " + guildId, e);
        }
    }

    public void replaceRoles(String guildId, List<RoleRow> rows) {
        try (Connection c = pool.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                if (!ownsGuild(c, guildId)) {
                    c.rollback();
                    return;
                }
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM guild_roles_snapshot WHERE guild_id = ?")) {
                    del.setString(1, guildId);
                    del.executeUpdate();
                }
                String ins = "INSERT INTO guild_roles_snapshot "
                        + "(guild_id, role_id, name, position, managed, bot_can_assign, updated_at) "
                        + "VALUES (?,?,?,?,?,?, now())";
                try (PreparedStatement ps = c.prepareStatement(ins)) {
                    for (RoleRow r : rows) {
                        ps.setString(1, guildId);
                        ps.setString(2, r.roleId());
                        ps.setString(3, r.name());
                        setNullableInt(ps, 4, r.position());
                        ps.setBoolean(5, r.managed());
                        ps.setBoolean(6, r.canAssign());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            throw new RepositoryException("replace roles " + guildId, e);
        }
    }

    /** Guard multi-tenant: a guild pertence a esta instância? */
    private boolean ownsGuild(Connection c, String guildId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM bot_guilds WHERE guild_id = ? AND bot_instance_id = ?::uuid")) {
            ps.setString(1, guildId);
            ps.setString(2, botInstanceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void setNullableInt(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) { ps.setNull(i, java.sql.Types.INTEGER); } else { ps.setInt(i, v); }
    }
}
