package dev.davimf.basebot.database.postgres;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class CachingGuildConfigRepositoryTest {

    /** Repo fake que conta leituras e devolve um valor mutável por guild. */
    private static final class FakeRepo implements GuildConfigRepository {
        final AtomicInteger finds = new AtomicInteger();
        GuildConfig current = base("g1", "old");

        static GuildConfig base(String g, String name) {
            return new GuildConfig(g, null, null, Map.of("log-mensagens", name),
                    Map.of(), Map.of(), List.of(), Map.of());
        }

        @Override public Optional<GuildConfig> find(String guildId) {
            finds.incrementAndGet();
            return Optional.of(current);
        }
        @Override public void save(GuildConfig config) { this.current = config; }
        @Override public void setToggle(String guildId, String key, boolean value) { }
    }

    @Test
    void secondReadWithinTtlHitsCache() {
        FakeRepo fake = new FakeRepo();
        AtomicLong clock = new AtomicLong(1_000_000L);
        var cache = new CachingGuildConfigRepository(fake, 45_000L, clock::get);

        cache.find("g1");
        cache.find("g1");
        assertEquals(1, fake.finds.get(), "segunda leitura dentro do TTL não bate no delegate");
    }

    @Test
    void readAfterTtlRefetches() {
        FakeRepo fake = new FakeRepo();
        AtomicLong clock = new AtomicLong(1_000_000L);
        var cache = new CachingGuildConfigRepository(fake, 45_000L, clock::get);

        cache.find("g1");
        clock.addAndGet(46_000L);
        cache.find("g1");
        assertEquals(2, fake.finds.get(), "após o TTL, re-busca no delegate");
    }

    @Test
    void saveEvictsSoNextReadIsFresh() {
        FakeRepo fake = new FakeRepo();
        AtomicLong clock = new AtomicLong(1_000_000L);
        var cache = new CachingGuildConfigRepository(fake, 45_000L, clock::get);

        cache.find("g1");                          // popula cache (finds=1)
        cache.save(FakeRepo.base("g1", "new"));    // evict
        Optional<GuildConfig> after = cache.find("g1"); // re-busca (finds=2)
        assertEquals(2, fake.finds.get());
        assertEquals("new", after.orElseThrow().channel("log-mensagens"));
    }
}
