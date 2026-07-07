package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SocialRepositoryTest {
    private SqliteManager sqlite;
    private SocialRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new SocialRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void firstGiveSucceedsAndIncrements() {
        SocialRepository.GiveResult r = repo.give("g", "giver", "target", "rep", 1000L, 60_000L);
        assertTrue(r.ok());
        assertEquals(1, r.newPoints());
        assertEquals(1, repo.points("g", "target", "rep"));
    }

    @Test
    void secondGiveWithinCooldownFails() {
        repo.give("g", "giver", "target", "rep", 1000L, 60_000L);
        SocialRepository.GiveResult r = repo.give("g", "giver", "target", "rep", 2000L, 60_000L);
        assertFalse(r.ok());
        assertEquals(61_000L, r.readyAt());
        assertEquals(1, repo.points("g", "target", "rep"));
    }

    @Test
    void giveAgainAfterCooldownSucceeds() {
        repo.give("g", "giver", "t", "rep", 1000L, 60_000L);
        SocialRepository.GiveResult r = repo.give("g", "giver", "t", "rep", 1000L + 60_000L, 60_000L);
        assertTrue(r.ok());
        assertEquals(2, repo.points("g", "t", "rep"));
    }

    @Test
    void topOrdersByPoints() {
        repo.give("g", "x", "a", "rep", 1L, 0L);
        repo.give("g", "y", "b", "rep", 1L, 0L);
        repo.give("g", "z", "b", "rep", 1L, 0L);
        var top = repo.top("g", "rep", 10);
        assertEquals("b", top.get(0).userId());
        assertEquals(2, top.get(0).points());
    }
}
