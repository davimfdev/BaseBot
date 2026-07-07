package dev.davimf.basebot.database.postgres;

import dev.davimf.basebot.database.model.GuildConfig;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Short-TTL read cache over a {@link GuildConfigRepository}. The bot is not the only writer
 * (the dashboard writes the same Neon rows), so this uses expiry, not global invalidation:
 * a guild's config is re-read at most once per {@code ttlMillis}. Writes by THIS bot evict the
 * guild so it immediately sees its own change. Eventually consistent with the dashboard within
 * the TTL — matches the design's "config passiva ~imediata / efeitos ativos reconciliados".
 */
public final class CachingGuildConfigRepository implements GuildConfigRepository {

    private static final long DEFAULT_TTL_MILLIS = 45_000L;

    private record Entry(Optional<GuildConfig> value, long expiresAt) {}

    private final GuildConfigRepository delegate;
    private final long ttlMillis;
    private final LongSupplier nowMillis;
    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    public CachingGuildConfigRepository(GuildConfigRepository delegate) {
        this(delegate, DEFAULT_TTL_MILLIS, System::currentTimeMillis);
    }

    public CachingGuildConfigRepository(GuildConfigRepository delegate, long ttlMillis, LongSupplier nowMillis) {
        this.delegate = delegate;
        this.ttlMillis = ttlMillis;
        this.nowMillis = nowMillis;
    }

    @Override
    public Optional<GuildConfig> find(String guildId) {
        long now = nowMillis.getAsLong();
        Entry entry = cache.get(guildId);
        if (entry != null && now < entry.expiresAt()) {
            return entry.value();
        }
        Optional<GuildConfig> fresh = delegate.find(guildId);
        cache.put(guildId, new Entry(fresh, now + ttlMillis));
        return fresh;
    }

    @Override
    public void save(GuildConfig config) {
        delegate.save(config);
        cache.remove(config.guildId());
    }

    @Override
    public void setToggle(String guildId, String key, boolean value) {
        delegate.setToggle(guildId, key, value);
        cache.remove(guildId);
    }
}
