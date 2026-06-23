package dev.davimf.basebot.database.postgres;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.davimf.basebot.database.model.GuildConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC implementation of {@link GuildConfigRepository} backed by a single
 * {@code guild_config} table with JSONB columns (see resources/db/postgres).
 *
 * <p>The JSONB document shape (channels/roles/toggles/staff) was chosen so the long
 * tail of dashboard settings does not require a migration per feature, and so this
 * lines up cleanly with whatever the Neon schema becomes after the Supabase move.
 * When the dashboard's authoritative {@code guilds} table is finalized on Neon, this
 * class is the single place to reconcile column mappings.
 */
public final class JdbcGuildConfigRepository implements GuildConfigRepository {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, String>> STR_MAP = new TypeReference<>() {};
    private static final TypeReference<Map<String, Boolean>> BOOL_MAP = new TypeReference<>() {};
    private static final TypeReference<List<String>> STR_LIST = new TypeReference<>() {};

    private final PostgresPool pool;

    public JdbcGuildConfigRepository(PostgresPool pool) {
        this.pool = pool;
    }

    @Override
    public Optional<GuildConfig> find(String guildId) {
        String sql = """
                SELECT guild_id, log_channel_id, ticket_log_channel_id,
                       channels, roles, toggles, staff_role_ids, settings
                  FROM guild_config
                 WHERE guild_id = ?
                """;
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RepositoryException("find guild_config " + guildId, e);
        }
    }

    @Override
    public void save(GuildConfig cfg) {
        String sql = """
                INSERT INTO guild_config
                    (guild_id, log_channel_id, ticket_log_channel_id,
                     channels, roles, toggles, staff_role_ids, settings, updated_at)
                VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, now())
                ON CONFLICT (guild_id) DO UPDATE SET
                    log_channel_id        = EXCLUDED.log_channel_id,
                    ticket_log_channel_id = EXCLUDED.ticket_log_channel_id,
                    channels              = EXCLUDED.channels,
                    roles                 = EXCLUDED.roles,
                    toggles               = EXCLUDED.toggles,
                    staff_role_ids        = EXCLUDED.staff_role_ids,
                    settings              = EXCLUDED.settings,
                    updated_at            = now()
                """;
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, cfg.guildId());
            ps.setString(2, cfg.logChannelId());
            ps.setString(3, cfg.ticketLogChannelId());
            ps.setString(4, write(cfg.channels()));
            ps.setString(5, write(cfg.roles()));
            ps.setString(6, write(cfg.toggles()));
            ps.setString(7, write(cfg.staffRoleIds()));
            ps.setString(8, write(cfg.settings()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("save guild_config " + cfg.guildId(), e);
        }
    }

    @Override
    public void setToggle(String guildId, String key, boolean value) {
        // jsonb_set creates the row's toggle without rewriting siblings; upsert the row first.
        String sql = """
                INSERT INTO guild_config (guild_id, toggles, updated_at)
                VALUES (?, jsonb_build_object(?, ?::boolean), now())
                ON CONFLICT (guild_id) DO UPDATE SET
                    toggles    = jsonb_set(coalesce(guild_config.toggles, '{}'::jsonb), ARRAY[?], to_jsonb(?::boolean)),
                    updated_at = now()
                """;
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, key);
            ps.setBoolean(3, value);
            ps.setString(4, key);
            ps.setBoolean(5, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("setToggle " + guildId + "/" + key, e);
        }
    }

    private GuildConfig map(ResultSet rs) throws SQLException {
        return new GuildConfig(
                rs.getString("guild_id"),
                rs.getString("log_channel_id"),
                rs.getString("ticket_log_channel_id"),
                read(rs.getString("channels"), STR_MAP, Map.of()),
                read(rs.getString("roles"), STR_MAP, Map.of()),
                read(rs.getString("toggles"), BOOL_MAP, Map.of()),
                read(rs.getString("staff_role_ids"), STR_LIST, List.of()),
                read(rs.getString("settings"), STR_MAP, Map.of())
        );
    }

    private static String write(Object value) {
        try {
            return JSON.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new RepositoryException("serialize jsonb", e);
        }
    }

    private static <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try {
            return JSON.readValue(json, type);
        } catch (Exception e) {
            throw new RepositoryException("deserialize jsonb", e);
        }
    }
}
