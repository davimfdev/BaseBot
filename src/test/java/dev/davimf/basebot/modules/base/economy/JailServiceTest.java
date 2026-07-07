package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import dev.davimf.basebot.modules.base.economy.CrimeStateRepository.State;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JailServiceTest {
    private SqliteManager sqlite;
    private CrimeStateRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new CrimeStateRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void jailAndReleaseUpsert() {
        long ate = System.currentTimeMillis() + 60_000;
        repo.jail("g", "u", ate);
        State s = repo.get("g", "u");
        assertEquals(ate, s.presoAte());
        assertFalse(s.fichaSuja());
        repo.release("g", "u");
        assertEquals(0, repo.get("g", "u").presoAte());
    }

    @Test
    void setFichaPersists() {
        repo.setFicha("g", "u", true);
        assertTrue(repo.get("g", "u").fichaSuja());
        repo.setFicha("g", "u", false);
        assertFalse(repo.get("g", "u").fichaSuja());
    }

    @Test
    void resolveMarksFichaOnServedSentence() {
        // pena já expirada e não pagou fiança → cumpriu a pena → marca ficha + libera
        repo.jail("g", "u", System.currentTimeMillis() - 1);
        boolean served = JailService.resolveServed(repo, "g", "u", System.currentTimeMillis());
        assertTrue(served);                       // estava preso e a pena passou
        assertTrue(repo.get("g", "u").fichaSuja());
        assertEquals(0, repo.get("g", "u").presoAte());
    }

    @Test
    void resolveKeepsJailWhenStillServing() {
        repo.jail("g", "u", System.currentTimeMillis() + 60_000);
        assertFalse(JailService.resolveServed(repo, "g", "u", System.currentTimeMillis()));
        assertFalse(repo.get("g", "u").fichaSuja());
        assertTrue(repo.get("g", "u").presoAte() > 0);
    }
}
