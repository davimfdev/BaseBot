package dev.davimf.basebot.database.sqlite;

import dev.davimf.basebot.config.BotConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalInstanceRepositoryTest {

    private SqliteManager sqlite;
    private LocalInstanceRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new LocalInstanceRepository(sqlite);
    }

    @AfterEach
    void tearDown() {
        sqlite.close();
    }

    @Test
    void generatesOnceAndStaysStable() {
        String id = repo.getOrCreate();
        assertNotNull(id);
        assertFalse(id.isBlank());
        // Same repo instance returns the same id.
        assertEquals(id, repo.getOrCreate());
        // A fresh repo over the same DB reads the persisted id (no regeneration).
        assertEquals(id, new LocalInstanceRepository(sqlite).getOrCreate());
    }
}
