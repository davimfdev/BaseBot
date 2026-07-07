package dev.davimf.basebot.modules.base.giveaway;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GiveawayRepositoryTest {
    private SqliteManager sqlite;
    private GiveawayRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new GiveawayRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    private Giveaway draft(long endsAt) {
        return new Giveaway(null, "g1", "c1", null, "Nitro", 100, 2, endsAt, false,
                "role1", 3, 5, 20, 23);
    }

    @Test
    void createFindRoundtrips() {
        String id = repo.create(draft(9999L));
        Giveaway g = repo.find(id).orElseThrow();
        assertEquals("Nitro", g.prize());
        assertEquals(2, g.winners());
        assertEquals(100, g.coinReward());
        assertTrue(g.hasWindow());
        assertEquals(8, id.length());
    }

    @Test
    void entriesAreIdempotent() {
        String id = repo.create(draft(9999L));
        assertTrue(repo.addEntry(id, "u1"));
        assertFalse(repo.addEntry(id, "u1"));
        assertTrue(repo.addEntry(id, "u2"));
        assertEquals(2, repo.entryCount(id));
        assertEquals(2, repo.entries(id).size());
    }

    @Test
    void dueActiveAndSetEnded() {
        String id = repo.create(draft(1000L));
        repo.setMessageId(id, "m1");
        assertEquals(1, repo.dueActive(2000L).size());
        repo.setEnded(id);
        assertTrue(repo.dueActive(2000L).isEmpty());
        assertEquals("m1", repo.find(id).orElseThrow().messageId());
    }
}
