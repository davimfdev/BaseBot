package dev.davimf.basebot.database.postgres;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.davimf.basebot.database.model.GuildConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(JdbcGuildConfigRepository.class);

    @FunctionalInterface
    interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, String>> STR_MAP = new TypeReference<>() {};
    private static final TypeReference<Map<String, Boolean>> BOOL_MAP = new TypeReference<>() {};
    private static final TypeReference<List<String>> STR_LIST = new TypeReference<>() {};

    private final ConnectionProvider connections;
    private final String botInstanceId;

    public JdbcGuildConfigRepository(PostgresPool pool, String botInstanceId) {
        this(pool::getConnection, botInstanceId);
    }

    JdbcGuildConfigRepository(ConnectionProvider connections, String botInstanceId) {
        this.connections = connections;
        this.botInstanceId = botInstanceId;
    }

    @Override
    public Optional<GuildConfig> find(String guildId) {
        String sql = """
                SELECT guild_id, log_channel_id, ticket_log_channel_id,
                       channels, roles, toggles, staff_role_ids, settings
                  FROM guild_config
                 WHERE guild_id = ?
                """;
        try (Connection c = connections.getConnection();
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
        log.info("Saving guild_config for guild {} ({} channel keys, {} role keys, {} toggles)",
                cfg.guildId(), cfg.channels().size(), cfg.roles().size(), cfg.toggles().size());
        // Merge por chave (||) nos mapas JSONB: um save(fullConfig) do /setup nunca apaga chaves
        // que o dashboard gravou e que não estão nesta cópia. GuildConfigEdits só faz put (nunca
        // remove chave), então merge não perde capacidade. dashboard_access não é tocada (preservada).
        // Caveat: se o dashboard REMOVE uma chave e o bot salva uma cópia (possivelmente do cache)
        // que ainda a tem, o merge a re-adiciona — aceito (last-write-wins por chave, janela <= TTL).
        String sql = """
                WITH claimed AS (
                    INSERT INTO bot_guilds (guild_id, bot_instance_id, bot_present, last_seen_at)
                    VALUES (?, ?::uuid, true, now())
                    ON CONFLICT (guild_id) DO UPDATE SET
                        bot_instance_id = EXCLUDED.bot_instance_id,
                        bot_present     = true,
                        last_seen_at    = now()
                    RETURNING guild_id
                )
                INSERT INTO guild_config
                    (guild_id, log_channel_id, ticket_log_channel_id,
                     channels, roles, toggles, staff_role_ids, settings, updated_at)
                SELECT guild_id, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, now()
                  FROM claimed
                ON CONFLICT (guild_id) DO UPDATE SET
                    log_channel_id        = EXCLUDED.log_channel_id,
                    ticket_log_channel_id = EXCLUDED.ticket_log_channel_id,
                    channels              = coalesce(guild_config.channels, '{}'::jsonb) || EXCLUDED.channels,
                    roles                 = coalesce(guild_config.roles,    '{}'::jsonb) || EXCLUDED.roles,
                    toggles               = coalesce(guild_config.toggles,  '{}'::jsonb) || EXCLUDED.toggles,
                    staff_role_ids        = EXCLUDED.staff_role_ids,
                    settings              = coalesce(guild_config.settings, '{}'::jsonb) || EXCLUDED.settings,
                    updated_at            = now()
                """;
        try (Connection c = connections.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, cfg.guildId());
            ps.setString(2, botInstanceId);
            ps.setString(3, cfg.logChannelId());
            ps.setString(4, cfg.ticketLogChannelId());
            ps.setString(5, write(cfg.channels()));
            ps.setString(6, write(cfg.roles()));
            ps.setString(7, write(cfg.toggles()));
            ps.setString(8, write(cfg.staffRoleIds()));
            ps.setString(9, write(cfg.settings()));
            int rows = ps.executeUpdate();
            if (rows != 1) {
                String message = "atomic claim/save affected " + rows + " rows for guild " + cfg.guildId();
                log.error(message);
                throw new RepositoryException(message, new IllegalStateException(message));
            }
            log.info("Claimed guild and saved guild_config atomically for guild {}", cfg.guildId());
        } catch (SQLException e) {
            log.error("Failed to save guild_config for guild {}", cfg.guildId(), e);
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
        try (Connection c = connections.getConnection()) {
            claimGuild(c, guildId);
            if (!ownsGuild(c, guildId)) {
                String message = "ownership claim failed for guild " + guildId
                        + " and bot instance " + botInstanceId;
                log.error(message);
                throw new RepositoryException(message, new IllegalStateException(message));
            }
            try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, key);
            ps.setBoolean(3, value);
            ps.setString(4, key);
            ps.setBoolean(5, value);
            int rows = ps.executeUpdate();
            log.info("Saved toggle {}/{}={} ({} row affected)", guildId, key, value, rows);
            }
        } catch (SQLException e) {
            log.error("Failed to save toggle {}/{}", guildId, key, e);
            throw new RepositoryException("setToggle " + guildId + "/" + key, e);
        }
    }

    /**
     * Establishes ownership for a new guild or reclaims a stale binding left by an older
     * deployment of this bot. Callers only reach this repository from a live guild event.
     */
    private void claimGuild(Connection connection, String guildId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO bot_guilds (guild_id, bot_instance_id, bot_present, last_seen_at)
                VALUES (?, ?::uuid, true, now())
                ON CONFLICT (guild_id) DO UPDATE SET
                    bot_instance_id = EXCLUDED.bot_instance_id,
                    bot_present     = true,
                    last_seen_at    = now()
                """)) {
            ps.setString(1, guildId);
            ps.setString(2, botInstanceId);
            int rows = ps.executeUpdate();
            log.info("Claimed guild {} for bot instance {} ({} row affected)",
                    guildId, botInstanceId, rows);
        }
    }

    private boolean ownsGuild(Connection connection, String guildId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM bot_guilds WHERE guild_id = ? AND bot_instance_id = ?::uuid")) {
            ps.setString(1, guildId);
            ps.setString(2, botInstanceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
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
