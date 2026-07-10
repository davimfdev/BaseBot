package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class VoiceRetentionSweeperTest {

    private SqliteManager sqlite;
    private VoiceSessionRepository sessions;
    private VoiceTimeRepository times;

    private static final long NOW = 1_800_000_000_000L;
    private static final long DAY = TimeUnit.DAYS.toMillis(1);

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        sessions = new VoiceSessionRepository(sqlite);
        times = new VoiceTimeRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void deletesSessionsClosedOver90DaysAgo() {
        sessions.open("g1", "old", "c1", NOW - 92 * DAY);
        sessions.closeOpen("g1", "old", NOW - 91 * DAY);
        sessions.open("g1", "recent", "c1", NOW - 90 * DAY);
        sessions.closeOpen("g1", "recent", NOW - 89 * DAY);

        VoiceRetentionSweeper.sweepLocal(sessions, times, NOW);

        assertEquals(0, sessions.sessionsOf("g1", "old").size());
        assertEquals(1, sessions.sessionsOf("g1", "recent").size());
    }

    @Test
    void sessionClosedExactlyAtTheCutoffIsKept() {
        long cutoff = VoiceRetention.cutoff(NOW);
        sessions.open("g1", "edge", "c1", cutoff - DAY);
        sessions.closeOpen("g1", "edge", cutoff);

        VoiceRetentionSweeper.sweepLocal(sessions, times, NOW);

        assertEquals(1, sessions.sessionsOf("g1", "edge").size(),
                "'mais velho que 90 dias' e estrito (<); fechada exatamente no corte tem exatamente 90 dias e deve ser mantida");
    }

    @Test
    void neverDeletesAnOpenSessionNoMatterHowOld() {
        sessions.open("g1", "marathon", "c1", NOW - 200 * DAY);

        VoiceRetentionSweeper.sweepLocal(sessions, times, NOW);

        assertNotNull(sessions.openSession("g1", "marathon"));
    }

    @Test
    void deletesOldSyncedWeeksButKeepsDirtyOnes() {
        long oldWeek = VoiceWeek.weekStart(NOW - 120 * DAY);
        long recentWeek = VoiceWeek.weekStart(NOW - 10 * DAY);

        times.addMs("g1", "synced", oldWeek, 100);
        times.clearDirty("g1", "synced", oldWeek, 100);
        times.addMs("g1", "pending", oldWeek, 200);   // continua dirty
        times.addMs("g1", "recent", recentWeek, 300);
        times.clearDirty("g1", "recent", recentWeek, 300);

        VoiceRetentionSweeper.sweepLocal(sessions, times, NOW);

        assertEquals(0, times.msOf("g1", "synced", oldWeek));
        assertEquals(200, times.msOf("g1", "pending", oldWeek),
                "linha nao sincronizada nunca e podada antes de subir");
        assertEquals(300, times.msOf("g1", "recent", recentWeek));
    }
}
