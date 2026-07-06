// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.voice
// 
// Class: MuteRepositoryTest
// 
// Fields:
//   - `Field` : `private SqliteManager sqlite`
//   - `Field` : `private MuteRepository repo`
// [OUTLINE END]



package dev.davimf.basebot.modules.base.voice;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MuteRepositoryTest {

    private SqliteManager sqlite;
    private MuteRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new MuteRepository(sqlite);
    }

    @AfterEach
    void tearDown() {
        sqlite.close();
    }

    @Test
    void activeUntilExpiry() {
        long now = System.currentTimeMillis();
        repo.add("g1", "u1", MuteRepository.VOICE, now + 60_000);
        assertTrue(repo.isActive("g1", "u1", MuteRepository.VOICE, now));
        assertFalse(repo.isActive("g1", "u1", MuteRepository.VOICE, now + 61_000)); // past expiry
        assertFalse(repo.isActive("g1", "u1", MuteRepository.TEXT, now));           // wrong type
    }

    @Test
    void listExpiredReturnsOnlyPastDue() {
        long now = System.currentTimeMillis();
        repo.add("g1", "u1", MuteRepository.TEXT, now - 1000);   // expired
        repo.add("g1", "u2", MuteRepository.VOICE, now + 60_000); // active
        List<MuteRepository.Entry> expired = repo.listExpired(now);
        assertEquals(1, expired.size());
        assertEquals("u1", expired.get(0).userId());
        assertEquals(MuteRepository.TEXT, expired.get(0).type());
    }

    @Test
    void removeClearsIt() {
        long now = System.currentTimeMillis();
        repo.add("g1", "u1", MuteRepository.VOICE, now + 60_000);
        repo.remove("g1", "u1", MuteRepository.VOICE);
        assertFalse(repo.isActive("g1", "u1", MuteRepository.VOICE, now));
    }

    @Test
    void addUpdatesExpiry() {
        long now = System.currentTimeMillis();
        repo.add("g1", "u1", MuteRepository.TEXT, now + 1000);
        repo.add("g1", "u1", MuteRepository.TEXT, now + 60_000);
        assertTrue(repo.isActive("g1", "u1", MuteRepository.TEXT, now + 30_000));
    }
}
