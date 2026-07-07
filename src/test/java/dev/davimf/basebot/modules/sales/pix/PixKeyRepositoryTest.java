package dev.davimf.basebot.modules.sales.pix;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.model.PixKey;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PixKeyRepositoryTest {

    private SqliteManager sqlite;
    private PixKeyRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("test.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new PixKeyRepository(sqlite);
    }

    @AfterEach
    void tearDown() {
        sqlite.close();
    }

    private PixKey unsaved(String guild, String user, String type, String value, String name) {
        return new PixKey(0, guild, user, type, value, name);
    }

    @Test
    void insertThenListReturnsAllUserKeys() {
        repo.insert(unsaved("g1", "u1", "EMAIL", "a@b.com", "Loja X"));
        repo.insert(unsaved("g1", "u1", "PHONE", "+5562986089609", "Loja X"));
        List<PixKey> keys = repo.list("g1", "u1");
        assertEquals(2, keys.size());
        assertEquals("a@b.com", keys.get(0).keyValue());
        assertEquals("+5562986089609", keys.get(1).keyValue());
    }

    @Test
    void findReturnsById() {
        long id = repo.insert(unsaved("g1", "u1", "EMAIL", "a@b.com", "Loja X"));
        PixKey k = repo.find(id).orElseThrow();
        assertEquals("a@b.com", k.keyValue());
        assertEquals("Loja X", k.merchantName());
    }

    @Test
    void updateChangesValueAndName() {
        long id = repo.insert(unsaved("g1", "u1", "EMAIL", "old@b.com", "Old"));
        repo.update(id, "new@b.com", "New");
        PixKey k = repo.find(id).orElseThrow();
        assertEquals("new@b.com", k.keyValue());
        assertEquals("New", k.merchantName());
        assertEquals("EMAIL", k.keyType());
    }

    @Test
    void deleteRemovesRow() {
        long id = repo.insert(unsaved("g1", "u1", "EMAIL", "a@b.com", "Loja X"));
        repo.delete(id);
        assertTrue(repo.find(id).isEmpty());
        assertTrue(repo.list("g1", "u1").isEmpty());
    }

    @Test
    void findDefaultReturnsFirstByInsertionOrder() {
        repo.insert(unsaved("g1", "u1", "EMAIL", "first@b.com", "A"));
        repo.insert(unsaved("g1", "u1", "PHONE", "+5562986089609", "B"));
        assertEquals("first@b.com", repo.findDefault("g1", "u1").orElseThrow().keyValue());
    }

    @Test
    void keysAreGuildAndUserScoped() {
        repo.insert(unsaved("g1", "u1", "EMAIL", "u1@b.com", "L1"));
        repo.insert(unsaved("g2", "u1", "EMAIL", "other@b.com", "L2"));
        assertEquals(1, repo.list("g1", "u1").size());
        assertTrue(repo.list("g1", "u2").isEmpty());
    }
}
