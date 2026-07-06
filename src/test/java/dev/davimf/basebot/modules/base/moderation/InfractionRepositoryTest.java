package dev.davimf.basebot.modules.base.moderation;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integration test: applies all migrations (incl. 022) to a temp DB and exercises the repo. */
class InfractionRepositoryTest {

    @TempDir
    Path tmp;
    private SqliteManager sqlite;
    private InfractionRepository repo;

    @BeforeEach
    void setUp() {
        sqlite = new SqliteManager(new BotConfig.Sqlite(tmp.resolve("test.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new InfractionRepository(sqlite);
    }

    @AfterEach
    void tearDown() {
        sqlite.close();
    }

    @Test
    void caseNumbersAreSequentialPerGuild() {
        long now = System.currentTimeMillis();
        Infraction a = repo.create("g1", "u1", "m1", "WARN", "spam", now, null, null);
        Infraction b = repo.create("g1", "u2", "m1", "BAN", "raid", now, null, null);
        Infraction other = repo.create("g2", "u1", "m1", "WARN", null, now, null, null);
        assertEquals(1, a.caseNumber());
        assertEquals(2, b.caseNumber());
        assertEquals(1, other.caseNumber());
    }

    @Test
    void countsActiveWarnsExcludingExpiredAndRevoked() {
        long now = System.currentTimeMillis();
        repo.create("g1", "u1", "m1", "WARN", null, now, null, null);          // active, no expiry
        repo.create("g1", "u1", "m1", "WARN", null, now, now + 60_000, null);  // active, future expiry
        repo.create("g1", "u1", "m1", "WARN", null, now, now - 60_000, null);  // expired
        Infraction toRevoke = repo.create("g1", "u1", "m1", "WARN", null, now, null, null);
        assertEquals(3, repo.countActiveWarns("g1", "u1", now));
        assertTrue(repo.revoke("g1", toRevoke.caseNumber()));
        assertEquals(2, repo.countActiveWarns("g1", "u1", now));
    }

    @Test
    void listAndDeactivateLatest() {
        long now = System.currentTimeMillis();
        repo.create("g1", "u1", "m1", "BAN", null, now, null, null);
        repo.create("g1", "u1", "m1", "BAN", null, now, null, null);
        assertEquals(2, repo.listActiveByUser("g1", "u1").size());
        repo.deactivateLatest("g1", "u1", "BAN");
        assertEquals(1, repo.listActiveByUser("g1", "u1").size());
        assertEquals(2, repo.listByUser("g1", "u1").size());
    }

    @Test
    void listExpiredActiveFindsDueCases() {
        long now = System.currentTimeMillis();
        repo.create("g1", "u1", "m1", "TEMPBAN", null, now, now - 1, null);
        repo.create("g1", "u2", "m1", "TIMEOUT", null, now, now + 60_000, null);
        assertEquals(1, repo.listExpiredActive(now).size());
        assertEquals("TEMPBAN", repo.listExpiredActive(now).get(0).type());
        assertFalse(repo.listExpiredActive(now).isEmpty());
    }
}
