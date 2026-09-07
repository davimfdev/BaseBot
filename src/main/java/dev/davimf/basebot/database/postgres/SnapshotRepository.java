package dev.davimf.basebot.database.postgres;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Publishes per-guild Discord snapshots so the dashboard can validate channels/roles without a
 * bot token. Every write is scoped to this bot's {@code botInstanceId}: a compromised instance
 * cannot touch another client's rows (v1 = guards in WHERE; RLS is the future hardening).
 *
 * <p>Writes are <b>diff-based</b>: a full sync upserts every row with {@code ON CONFLICT DO UPDATE
 * ... WHERE <any column> IS DISTINCT FROM EXCLUDED}, so a row whose data did not change is not
 * rewritten (its {@code updated_at} stays put), then deletes only the rows that no longer exist on
 * Discord. There is no {@code DELETE}-all-then-reinsert, so a partial failure never leaves the guild
 * momentarily empty, and an unchanged guild costs zero writes. Single-entity methods let the JDA
 * listener touch just the one channel/role that actually changed.
 */
public final class SnapshotRepository {

    public record ChannelRow(String channelId, String name, String type, String parentId,
                             Integer position, boolean canView, boolean canSend) {}

    public record RoleRow(String roleId, String name, Integer position, boolean managed, boolean canAssign) {}

    /** Outcome of a diff-based reconcile, for structured logging/metrics. */
    public record SyncCounts(int total, int written, int deleted) {
        public int unchanged() { return Math.max(0, total - written); }
    }

    private static final String CHANNEL_UPSERT = """
            INSERT INTO guild_channels_snapshot
                (guild_id, channel_id, name, type, parent_id, position, bot_can_view, bot_can_send, updated_at)
            VALUES (?,?,?,?,?,?,?,?, now())
            ON CONFLICT (guild_id, channel_id) DO UPDATE SET
                name = EXCLUDED.name, type = EXCLUDED.type, parent_id = EXCLUDED.parent_id,
                position = EXCLUDED.position, bot_can_view = EXCLUDED.bot_can_view,
                bot_can_send = EXCLUDED.bot_can_send, updated_at = now()
            WHERE guild_channels_snapshot.name         IS DISTINCT FROM EXCLUDED.name
               OR guild_channels_snapshot.type         IS DISTINCT FROM EXCLUDED.type
               OR guild_channels_snapshot.parent_id    IS DISTINCT FROM EXCLUDED.parent_id
               OR guild_channels_snapshot.position     IS DISTINCT FROM EXCLUDED.position
               OR guild_channels_snapshot.bot_can_view IS DISTINCT FROM EXCLUDED.bot_can_view
               OR guild_channels_snapshot.bot_can_send IS DISTINCT FROM EXCLUDED.bot_can_send
            """;

    private static final String ROLE_UPSERT = """
            INSERT INTO guild_roles_snapshot
                (guild_id, role_id, name, position, managed, bot_can_assign, updated_at)
            VALUES (?,?,?,?,?,?, now())
            ON CONFLICT (guild_id, role_id) DO UPDATE SET
                name = EXCLUDED.name, position = EXCLUDED.position, managed = EXCLUDED.managed,
                bot_can_assign = EXCLUDED.bot_can_assign, updated_at = now()
            WHERE guild_roles_snapshot.name           IS DISTINCT FROM EXCLUDED.name
               OR guild_roles_snapshot.position       IS DISTINCT FROM EXCLUDED.position
               OR guild_roles_snapshot.managed        IS DISTINCT FROM EXCLUDED.managed
               OR guild_roles_snapshot.bot_can_assign IS DISTINCT FROM EXCLUDED.bot_can_assign
            """;

    @FunctionalInterface
    interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    private final ConnectionProvider connections;
    private final String botInstanceId;

    public SnapshotRepository(PostgresPool pool, String botInstanceId) {
        this(pool::getConnection, botInstanceId);
    }

    SnapshotRepository(ConnectionProvider connections, String botInstanceId) {
        this.connections = connections;
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
        try (Connection c = connections.getConnection();
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
        try (Connection c = connections.getConnection();
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
        try (Connection c = connections.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, botInstanceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("mark absent " + guildId, e);
        }
    }

    /**
     * Reconciles the full channel set for a guild in one transaction: upsert every row (skipping
     * rows whose columns are unchanged) then delete only rows whose {@code channel_id} is no longer
     * present. Never issues a blanket {@code DELETE}, so a partial failure rolls back to the
     * previous good snapshot instead of emptying the guild.
     */
    public SyncCounts reconcileChannels(String guildId, List<ChannelRow> rows) {
        try (Connection c = connections.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                if (!ownsGuild(c, guildId)) {
                    c.rollback();
                    return new SyncCounts(rows.size(), 0, 0);
                }
                int written;
                try (PreparedStatement ps = c.prepareStatement(CHANNEL_UPSERT)) {
                    for (ChannelRow r : rows) {
                        bindChannel(ps, guildId, r);
                        ps.addBatch();
                    }
                    written = sumBatch(ps.executeBatch());
                }
                String[] keep = new String[rows.size()];
                for (int i = 0; i < rows.size(); i++) keep[i] = rows.get(i).channelId();
                int deleted = deleteAbsent(c, "guild_channels_snapshot", "channel_id", guildId, keep);
                c.commit();
                return new SyncCounts(rows.size(), written, deleted);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            throw new RepositoryException("reconcile channels " + guildId, e);
        }
    }

    /** Role counterpart of {@link #reconcileChannels}. */
    public SyncCounts reconcileRoles(String guildId, List<RoleRow> rows) {
        try (Connection c = connections.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                if (!ownsGuild(c, guildId)) {
                    c.rollback();
                    return new SyncCounts(rows.size(), 0, 0);
                }
                int written;
                try (PreparedStatement ps = c.prepareStatement(ROLE_UPSERT)) {
                    for (RoleRow r : rows) {
                        bindRole(ps, guildId, r);
                        ps.addBatch();
                    }
                    written = sumBatch(ps.executeBatch());
                }
                String[] keep = new String[rows.size()];
                for (int i = 0; i < rows.size(); i++) keep[i] = rows.get(i).roleId();
                int deleted = deleteAbsent(c, "guild_roles_snapshot", "role_id", guildId, keep);
                c.commit();
                return new SyncCounts(rows.size(), written, deleted);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            throw new RepositoryException("reconcile roles " + guildId, e);
        }
    }

    /** Upserts a single channel (no-op if unchanged); used by the incremental listener. */
    public void upsertChannel(String guildId, ChannelRow row) {
        try (Connection c = connections.getConnection()) {
            if (!ownsGuild(c, guildId)) return;
            try (PreparedStatement ps = c.prepareStatement(CHANNEL_UPSERT)) {
                bindChannel(ps, guildId, row);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RepositoryException("upsert channel " + guildId + "/" + row.channelId(), e);
        }
    }

    /** Deletes a single channel; used by the incremental listener on channel delete. */
    public void deleteChannel(String guildId, String channelId) {
        deleteOne("guild_channels_snapshot", "channel_id", guildId, channelId);
    }

    /** Upserts a single role (no-op if unchanged); used by the incremental listener. */
    public void upsertRole(String guildId, RoleRow row) {
        try (Connection c = connections.getConnection()) {
            if (!ownsGuild(c, guildId)) return;
            try (PreparedStatement ps = c.prepareStatement(ROLE_UPSERT)) {
                bindRole(ps, guildId, row);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RepositoryException("upsert role " + guildId + "/" + row.roleId(), e);
        }
    }

    /** Deletes a single role; used by the incremental listener on role delete. */
    public void deleteRole(String guildId, String roleId) {
        deleteOne("guild_roles_snapshot", "role_id", guildId, roleId);
    }

    private void deleteOne(String table, String idCol, String guildId, String id) {
        try (Connection c = connections.getConnection()) {
            if (!ownsGuild(c, guildId)) return;
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM " + table + " WHERE guild_id = ? AND " + idCol + " = ?")) {
                ps.setString(1, guildId);
                ps.setString(2, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RepositoryException("delete " + table + " " + guildId + "/" + id, e);
        }
    }

    /**
     * Deletes rows for the guild whose id is not in {@code keep}. Uses a parameterised text[] array
     * ({@code <> ALL(?)}) so ids are never concatenated into SQL. An empty {@code keep} deletes every
     * row for the guild (matches "guild has no channels/roles" — {@code x <> ALL('{}')} is true).
     * {@code table}/{@code idCol} are internal constants, never user input.
     */
    private int deleteAbsent(Connection c, String table, String idCol, String guildId, String[] keep)
            throws SQLException {
        Array keepArray = c.createArrayOf("text", keep);
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM " + table + " WHERE guild_id = ? AND " + idCol + " <> ALL(?)")) {
            ps.setString(1, guildId);
            ps.setArray(2, keepArray);
            return ps.executeUpdate();
        } finally {
            keepArray.free();
        }
    }

    private static void bindChannel(PreparedStatement ps, String guildId, ChannelRow r) throws SQLException {
        ps.setString(1, guildId);
        ps.setString(2, r.channelId());
        ps.setString(3, r.name());
        ps.setString(4, r.type());
        ps.setString(5, r.parentId());
        setNullableInt(ps, 6, r.position());
        ps.setBoolean(7, r.canView());
        ps.setBoolean(8, r.canSend());
    }

    private static void bindRole(PreparedStatement ps, String guildId, RoleRow r) throws SQLException {
        ps.setString(1, guildId);
        ps.setString(2, r.roleId());
        ps.setString(3, r.name());
        setNullableInt(ps, 4, r.position());
        ps.setBoolean(5, r.managed());
        ps.setBoolean(6, r.canAssign());
    }

    /**
     * Sums a JDBC batch result into a count of rows actually written. A skipped upsert (WHERE clause
     * false) reports 0; a driver that returns {@code SUCCESS_NO_INFO} (-2) for a row is counted as
     * written (best-effort — the DB still skipped unchanged rows regardless of what we log).
     */
    private static int sumBatch(int[] result) {
        int n = 0;
        for (int r : result) {
            if (r > 0 || r == PreparedStatement.SUCCESS_NO_INFO) n++;
        }
        return n;
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
