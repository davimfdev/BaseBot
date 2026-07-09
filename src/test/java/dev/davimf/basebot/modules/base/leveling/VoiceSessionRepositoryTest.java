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

class VoiceSessionRepositoryTest {
    private SqliteManager sqlite;
    private VoiceSessionRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new VoiceSessionRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void openThenListReturnsOpenSession() {
        repo.open("g1", "u1", "c1", 1000L);
        List<VoiceSessionRepository.Open> open = repo.openSessions("g1");
        assertEquals(1, open.size());
        assertEquals("c1", open.get(0).channelId());
        assertEquals(1000L, open.get(0).joinTime());
        assertEquals(1000L, open.get(0).xpCreditedUntil());
    }

    @Test
    void closeOpenRemovesFromOpenList() {
        repo.open("g1", "u1", "c1", 1000L);
        repo.closeOpen("g1", "u1", 2000L);
        assertTrue(repo.openSessions("g1").isEmpty());
    }

    @Test
    void openSessionsAllGuilds() {
        repo.open("g1", "u1", "c1", 1000L);
        repo.open("g2", "u2", "c2", 1000L);
        assertEquals(2, repo.openSessions().size());
    }

    @Test
    void reanchorMovesBothWatermarksAndCreditsNothing() {
        repo.open("g1", "u1", "c1", 1_000L);
        repo.reanchor("g1", "u1", 90_000_000L);

        VoiceSessionRepository.Open s = repo.openSession("g1", "u1");
        assertEquals(90_000_000L, s.xpCreditedUntil());
        assertEquals(90_000_000L, s.timeCreditedUntil());
        assertEquals(1_000L, s.joinTime(), "join_time nao muda: so as watermarks sao reancoradas");
    }

    @Test
    void reanchorIgnoresClosedSessions() {
        repo.open("g1", "u1", "c1", 1_000L);
        repo.closeOpen("g1", "u1", 2_000L);
        repo.reanchor("g1", "u1", 90_000_000L);

        assertEquals(2_000L, repo.sessionsOf("g1", "u1").get(0)[1], "sessao fechada nao e tocada");
    }
}
