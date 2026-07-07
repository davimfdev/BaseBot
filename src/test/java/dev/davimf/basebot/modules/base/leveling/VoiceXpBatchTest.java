package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VoiceXpBatchTest {
    private SqliteManager sqlite;
    private VoiceSessionRepository sessions;
    private UserLevelRepository users;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        sessions = new VoiceSessionRepository(sqlite);
        users = new UserLevelRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void appliesXpAndAdvancesCreditedUntilInOneTransaction() {
        sessions.open("g1", "u1", "c1", 1000L);
        sessions.open("g1", "u2", "c1", 1000L);
        long id1 = sessions.openSessions("g1").get(0).id();
        long id2 = sessions.openSessions("g1").get(1).id();

        List<VoiceXpBatch.Result> results = VoiceXpBatch.apply(sqlite, List.of(
                new VoiceXpBatch.Credit(id1, "g1", "u1", 10, 61000L),
                new VoiceXpBatch.Credit(id2, "g1", "u2", 0, 61000L)));

        assertEquals(10, users.xp("g1", "u1"));
        assertEquals(0, users.xp("g1", "u2"));
        assertEquals(61000L, sessions.openSessions("g1").stream()
                .filter(o -> o.userId().equals("u1")).findFirst().orElseThrow().xpCreditedUntil());
        assertEquals(1, results.size());
        assertEquals(0, results.get(0).oldXp());
        assertEquals(10, results.get(0).newXp());
    }
}
