package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CooldownRepositoryTest {
    private SqliteManager sqlite;
    private CooldownRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new CooldownRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void stampThenReadRoundtrips() {
        assertEquals(0, repo.lastTs("g1", "u1", "daily"));
        repo.stamp("g1", "u1", "daily", 5000L);
        assertEquals(5000L, repo.lastTs("g1", "u1", "daily"));
        repo.stamp("g1", "u1", "daily", 9000L);
        assertEquals(9000L, repo.lastTs("g1", "u1", "daily"));
        assertEquals(0, repo.lastTs("g1", "u1", "work"));
    }
}
