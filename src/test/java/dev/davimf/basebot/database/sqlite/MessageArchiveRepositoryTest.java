package dev.davimf.basebot.database.sqlite;

import dev.davimf.basebot.config.BotConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MessageArchiveRepositoryTest {

    private SqliteManager sqlite;
    private MessageArchiveRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new MessageArchiveRepository(sqlite);
    }

    @AfterEach
    void tearDown() {
        sqlite.close();
    }

    @Test
    void upsertThenFind() {
        long now = System.currentTimeMillis();
        repo.upsert("m1", "g1", "c1", "u1", "olá", "", now);
        MessageArchiveRepository.Archived a = repo.find("m1");
        assertNotNull(a);
        assertEquals("olá", a.content());
        assertEquals("u1", a.authorId());
    }

    @Test
    void findMissingReturnsNull() {
        assertNull(repo.find("nope"));
    }

    @Test
    void updateContentChangesContent() {
        long now = System.currentTimeMillis();
        repo.upsert("m1", "g1", "c1", "u1", "antes", "", now);
        repo.updateContent("m1", "depois", now + 1000);
        assertEquals("depois", repo.find("m1").content());
    }

    @Test
    void purgeRemovesOnlyOld() {
        long now = System.currentTimeMillis();
        repo.upsert("old", "g1", "c1", "u1", "x", "", now - 1000);
        repo.upsert("new", "g1", "c1", "u1", "y", "", now + 1000);
        int removed = repo.purgeOlderThan(now);
        assertEquals(1, removed);
        assertNull(repo.find("old"));
        assertNotNull(repo.find("new"));
    }
}
