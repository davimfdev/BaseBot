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

class ShopStockRepositoryTest {

    private SqliteManager sqlite;
    private ShopStockRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new ShopStockRepository(sqlite);
    }

    @AfterEach
    void tearDown() {
        sqlite.close();
    }

    @Test
    void soldStartsAtZero() {
        assertEquals(0, repo.soldOf(1L));
    }

    @Test
    void reserveIncrementsAndRespectsFiniteLimit() {
        assertTrue(repo.reserveStock(1L, 2));   // 1
        assertTrue(repo.reserveStock(1L, 2));   // 2
        assertFalse(repo.reserveStock(1L, 2));  // esgotado
        assertEquals(2, repo.soldOf(1L));
    }

    @Test
    void reserveUnlimitedWhenStockNull() {
        assertTrue(repo.reserveStock(9L, null));
        assertTrue(repo.reserveStock(9L, null));
        assertEquals(2, repo.soldOf(9L));
    }

    @Test
    void releaseHasFloorZero() {
        repo.releaseStock(5L);
        assertEquals(0, repo.soldOf(5L));
        repo.reserveStock(5L, null);
        repo.releaseStock(5L);
        assertEquals(0, repo.soldOf(5L));
    }
}
