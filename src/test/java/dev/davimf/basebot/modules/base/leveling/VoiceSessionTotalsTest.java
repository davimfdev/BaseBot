package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VoiceSessionTotalsTest {
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
    void totalSumsClosedAndOpenSessions() {
        long now = System.currentTimeMillis();
        repo.open("g1", "u1", "c1", now - 3_600_000L);
        repo.closeOpen("g1", "u1", now - 1_800_000L); // fechou: 30min
        repo.open("g1", "u1", "c1", now - 600_000L);   // aberta ~10min
        long total = repo.totalVoiceMs("g1", "u1");
        assertTrue(total >= 1_800_000L + 600_000L - 5_000L, "≈ 40min, foi " + total);
        assertEquals(2, repo.sessionsOf("g1", "u1").size());
    }
}
