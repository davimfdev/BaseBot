package dev.davimf.basebot.modules.base.utility;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReminderRepositoryTest {
    private SqliteManager sqlite;
    private ReminderRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new ReminderRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void createDueDelete() {
        String id = repo.create("g", "u", "c", "beber água", 1000L);
        assertEquals(8, id.length());
        assertEquals(1, repo.due(2000L).size());
        assertTrue(repo.due(500L).isEmpty());
        repo.delete(id);
        assertTrue(repo.due(2000L).isEmpty());
    }

    @Test
    void listByUserAndCancelOwnership() {
        String id = repo.create("g", "u1", "c", "x", 5000L);
        repo.create("g", "u2", "c", "y", 5000L);
        assertEquals(1, repo.listByUser("g", "u1").size());
        assertFalse(repo.cancel(id, "u2")); // não é dono
        assertTrue(repo.cancel(id, "u1"));
        assertTrue(repo.listByUser("g", "u1").isEmpty());
    }
}
