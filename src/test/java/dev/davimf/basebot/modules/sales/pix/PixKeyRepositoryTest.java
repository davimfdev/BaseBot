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
import java.util.Optional;

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

    @Test
    void upsertThenFindByUserReturnsKey() {
        repo.upsert(new PixKey("g1", "u1", "EMAIL", "a@b.com", "Loja X", "SAO PAULO"));

        Optional<PixKey> found = repo.findByUser("g1", "u1");
        assertTrue(found.isPresent());
        assertEquals("a@b.com", found.get().keyValue());
        assertEquals("Loja X", found.get().merchantName());
    }

    @Test
    void upsertReplacesExistingRow() {
        repo.upsert(new PixKey("g1", "u1", "EMAIL", "old@b.com", "Loja X", "SAO PAULO"));
        repo.upsert(new PixKey("g1", "u1", "RANDOM", "new-key", "Loja Y", "RIO"));

        PixKey k = repo.findByUser("g1", "u1").orElseThrow();
        assertEquals("RANDOM", k.keyType());
        assertEquals("new-key", k.keyValue());
        assertEquals("Loja Y", k.merchantName());
    }

    @Test
    void findByUserIsGuildScoped() {
        repo.upsert(new PixKey("g1", "u1", "EMAIL", "a@b.com", "Loja X", "SAO PAULO"));
        assertTrue(repo.findByUser("g2", "u1").isEmpty());
    }

    @Test
    void keysAreSeparatePerUser() {
        repo.upsert(new PixKey("g1", "u1", "EMAIL", "u1@b.com", "Loja 1", "SP"));
        repo.upsert(new PixKey("g1", "u2", "EMAIL", "u2@b.com", "Loja 2", "RJ"));
        assertEquals("u1@b.com", repo.findByUser("g1", "u1").orElseThrow().keyValue());
        assertEquals("u2@b.com", repo.findByUser("g1", "u2").orElseThrow().keyValue());
    }
}
