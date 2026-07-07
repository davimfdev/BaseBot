package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import dev.davimf.basebot.modules.base.economy.ShopPurchaseRepository.Purchase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShopPurchaseRepositoryTest {
    private SqliteManager sqlite;
    private ShopPurchaseRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new ShopPurchaseRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    private Purchase p(long itemId, String user, Long expires) {
        return new Purchase(0, "g1", itemId, user, "role1", expires, 100, System.currentTimeMillis());
    }

    @Test
    void countActiveIncludesPermAndUnexpiredTemp() {
        long now = System.currentTimeMillis();
        repo.insert(p(1, "u1", null));            // perm ativo
        repo.insert(p(1, "u1", now + 60_000));    // temp ativo
        repo.insert(p(1, "u1", now - 60_000));    // temp expirado (não conta)
        repo.insert(p(2, "u1", null));            // outro item (não conta)
        assertEquals(2, repo.countActiveByUserItem("g1", "u1", 1, now));
    }

    @Test
    void activeTempReturnsUnexpiredElseNull() {
        long now = System.currentTimeMillis();
        assertNull(repo.activeTemp("g1", "u1", 1, now));
        long id = repo.insert(p(1, "u1", now + 60_000));
        Purchase found = repo.activeTemp("g1", "u1", 1, now);
        assertNotNull(found);
        assertEquals(id, found.id());
        // um expirado não é "active"
        repo.insert(p(2, "u1", now - 10));
        assertNull(repo.activeTemp("g1", "u1", 2, now));
    }

    @Test
    void extendUpdatesExpiry() {
        long now = System.currentTimeMillis();
        long id = repo.insert(p(1, "u1", now + 1000));
        assertTrue(repo.extend(id, now + 99_000));
        assertEquals(now + 99_000, repo.activeTemp("g1", "u1", 1, now).expiresAt());
    }

    @Test
    void dueReturnsExpiredAndClaimIsSingleUse() {
        long now = System.currentTimeMillis();
        long expired = repo.insert(p(1, "u1", now - 1));
        repo.insert(p(1, "u2", null));            // perm: nunca due
        repo.insert(p(1, "u3", now + 60_000));    // futuro: não due
        List<Purchase> due = repo.due(now);
        assertEquals(1, due.size());
        assertEquals(expired, due.get(0).id());
        assertTrue(repo.claim(expired));
        assertFalse(repo.claim(expired)); // segundo claim falha (já deletado)
        assertTrue(repo.due(now).isEmpty());
    }
}
