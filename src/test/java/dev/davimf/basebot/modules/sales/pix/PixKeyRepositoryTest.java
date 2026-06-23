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
    void upsertThenFindByRoleReturnsKey() {
        repo.upsert(new PixKey("g1", "r1", "EMAIL", "a@b.com", "Loja X", "SAO PAULO"));

        Optional<PixKey> found = repo.findByRole("g1", "r1");
        assertTrue(found.isPresent());
        assertEquals("a@b.com", found.get().keyValue());
        assertEquals("Loja X", found.get().merchantName());
    }

    @Test
    void upsertReplacesExistingRow() {
        repo.upsert(new PixKey("g1", "r1", "EMAIL", "old@b.com", "Loja X", "SAO PAULO"));
        repo.upsert(new PixKey("g1", "r1", "RANDOM", "new-key", "Loja Y", "RIO"));

        PixKey k = repo.findByRole("g1", "r1").orElseThrow();
        assertEquals("RANDOM", k.keyType());
        assertEquals("new-key", k.keyValue());
        assertEquals("Loja Y", k.merchantName());
    }

    @Test
    void findByRoleIsGuildScoped() {
        repo.upsert(new PixKey("g1", "r1", "EMAIL", "a@b.com", "Loja X", "SAO PAULO"));
        assertTrue(repo.findByRole("g2", "r1").isEmpty());
    }
}
