package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VoiceTimeFlusherTest {

    private static final long WEEK = VoiceWeek.weekStart(1_700_000_000_000L);

    private SqliteManager sqlite;
    private VoiceTimeRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new VoiceTimeRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    /** Simula o Postgres: guarda o último ms visto por chave (upsert de total absoluto). */
    private static final class FakeUpstream implements VoiceTimeFlusher.Upstream {
        final List<Long> received = new ArrayList<>();
        long lastMs = -1;

        @Override
        public void upsert(String guildId, String userId, long weekStart, long ms) {
            received.add(ms);
            lastMs = ms; // absoluto, não soma: reenviar não duplica
        }
    }

    @Test
    void flushSendsAbsoluteTotalAndClearsDirty() {
        repo.addMs("g1", "u1", WEEK, 1000);
        FakeUpstream up = new FakeUpstream();

        VoiceTimeFlusher.flushOnce(repo, up);

        assertEquals(List.of(1000L), up.received);
        assertTrue(repo.dirtyRows(10).isEmpty());
    }

    @Test
    void repeatedFlushIsIdempotent() {
        repo.addMs("g1", "u1", WEEK, 1000);
        FakeUpstream up = new FakeUpstream();

        VoiceTimeFlusher.flushOnce(repo, up);
        repo.addMs("g1", "u1", WEEK, 0); // volta a sujar sem mudar o valor
        VoiceTimeFlusher.flushOnce(repo, up);

        assertEquals(1000L, up.lastMs, "upsert absoluto: reenviar nao duplica");
    }

    @Test
    void rowIncrementedDuringFlushStaysDirty() {
        repo.addMs("g1", "u1", WEEK, 1000);

        // Upstream que incrementa a linha no meio do upsert, como o ticker faria.
        VoiceTimeFlusher.Upstream racy = (g, u, w, ms) -> repo.addMs(g, u, w, 500);

        VoiceTimeFlusher.flushOnce(repo, racy);

        assertEquals(1500, repo.msOf("g1", "u1", WEEK));
        assertEquals(1, repo.dirtyRows(10).size(),
                "os 500 novos ainda nao subiram: a linha precisa continuar suja");
    }

    @Test
    void upstreamFailureLeavesRowDirty() {
        repo.addMs("g1", "u1", WEEK, 1000);
        VoiceTimeFlusher.Upstream broken = (g, u, w, ms) -> {
            throw new IllegalStateException("neon offline");
        };

        VoiceTimeFlusher.flushOnce(repo, broken);

        assertEquals(1, repo.dirtyRows(10).size());
    }
}
