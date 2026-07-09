package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VoiceXpBatchTest {

    private SqliteManager sqlite;
    private VoiceSessionRepository sessions;
    private UserLevelRepository users;
    private VoiceTimeRepository times;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        sessions = new VoiceSessionRepository(sqlite);
        users = new UserLevelRepository(sqlite);
        times = new VoiceTimeRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    private long openSessionId(String user, long at) {
        sessions.open("g1", user, "c1", at);
        return sessions.openSession("g1", user).id();
    }

    @Test
    void appliesXpAndTimeAndAdvancesBothWatermarks() {
        long id = openSessionId("u1", 1000L);
        long week = VoiceWeek.weekStart(1000L);

        VoiceXpBatch.apply(sqlite, List.of(
                new VoiceXpBatch.Credit(id, "g1", "u1", 10, 1000L, 61_000L, 61_000L)));

        assertEquals(10, users.xp("g1", "u1"));
        assertEquals(60_000L, times.msOf("g1", "u1", week));

        VoiceSessionRepository.Open s = sessions.openSession("g1", "u1");
        assertEquals(61_000L, s.xpCreditedUntil());
        assertEquals(61_000L, s.timeCreditedUntil());
    }

    @Test
    void ineligibleWindowAdvancesWatermarksWithoutCrediting() {
        long id = openSessionId("u1", 1000L);
        long week = VoiceWeek.weekStart(1000L);

        // Mutado a janela inteira: xpDelta 0, e timeTo == timeFrom (sem crédito).
        VoiceXpBatch.apply(sqlite, List.of(
                new VoiceXpBatch.Credit(id, "g1", "u1", 0, 1000L, 1000L, 61_000L)));

        assertEquals(0, users.xp("g1", "u1"));
        assertEquals(0, times.msOf("g1", "u1", week));

        VoiceSessionRepository.Open s = sessions.openSession("g1", "u1");
        assertEquals(61_000L, s.xpCreditedUntil());
        assertEquals(61_000L, s.timeCreditedUntil(),
                "a watermark precisa avancar, senao o tempo mutado seria creditado retroativamente");
    }

    @Test
    void timeWithoutXpIsPossible() {
        long id = openSessionId("u1", 1000L);
        long week = VoiceWeek.weekStart(1000L);

        // Sozinho no canal: tempo conta, XP nao.
        VoiceXpBatch.apply(sqlite, List.of(
                new VoiceXpBatch.Credit(id, "g1", "u1", 0, 1000L, 61_000L, 61_000L)));

        assertEquals(0, users.xp("g1", "u1"));
        assertEquals(60_000L, times.msOf("g1", "u1", week));
    }

    @Test
    void windowCrossingMondayIsWrittenToTwoWeeklyRows() {
        long sunday = ZonedDateTime.of(2026, 7, 5, 23, 59, 30, 0, VoiceWeek.ZONE)
                .toInstant().toEpochMilli();
        long monday = ZonedDateTime.of(2026, 7, 6, 0, 0, 30, 0, VoiceWeek.ZONE)
                .toInstant().toEpochMilli();

        long id = openSessionId("u1", sunday);
        VoiceXpBatch.apply(sqlite, List.of(
                new VoiceXpBatch.Credit(id, "g1", "u1", 0, sunday, monday, monday)));

        assertEquals(30_000L, times.msOf("g1", "u1", VoiceWeek.weekStart(sunday)));
        assertEquals(30_000L, times.msOf("g1", "u1", VoiceWeek.weekStart(monday)));
    }

    @Test
    void returnsOldAndNewXpOnlyForCreditedUsers() {
        long id1 = openSessionId("u1", 1000L);
        long id2 = openSessionId("u2", 1000L);

        List<VoiceXpBatch.Result> results = VoiceXpBatch.apply(sqlite, List.of(
                new VoiceXpBatch.Credit(id1, "g1", "u1", 10, 1000L, 1000L, 61_000L),
                new VoiceXpBatch.Credit(id2, "g1", "u2", 0, 1000L, 1000L, 61_000L)));

        assertEquals(1, results.size());
        assertEquals("u1", results.get(0).userId());
        assertEquals(0, results.get(0).oldXp());
        assertEquals(10, results.get(0).newXp());
    }
}
