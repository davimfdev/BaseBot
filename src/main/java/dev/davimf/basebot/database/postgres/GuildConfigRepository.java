package dev.davimf.basebot.database.postgres;

import dev.davimf.basebot.database.model.GuildConfig;

import java.util.Optional;

/**
 * Read/write access to per-guild configuration in Postgres.
 *
 * <p>Kept as an interface on purpose: the concrete table shape is still in flux
 * (dashboard guild config is on Supabase today and will move to a dedicated Neon
 * schema). Callers depend only on this contract, so swapping the backing store is a
 * single-class change ({@link JdbcGuildConfigRepository} -&gt; a future implementation).
 */
public interface GuildConfigRepository {

    /** Loads a guild's config, or empty if the guild has never been set up. */
    Optional<GuildConfig> find(String guildId);

    /** Loads config, falling back to {@link GuildConfig#empty(String)} when absent. */
    default GuildConfig findOrEmpty(String guildId) {
        return find(guildId).orElseGet(() -> GuildConfig.empty(guildId));
    }

    /** Upserts the full config row for a guild. */
    void save(GuildConfig config);

    /** Updates a single feature toggle without rewriting the whole row. */
    void setToggle(String guildId, String key, boolean value);
}
