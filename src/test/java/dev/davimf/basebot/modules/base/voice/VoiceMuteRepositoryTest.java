package dev.davimf.basebot.modules.base.voice;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceMuteRepositoryTest {

    private SqliteManager sqlite;
    private VoiceMuteRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new VoiceMuteRepository(sqlite);
    }

    @AfterEach
    void tearDown() {
        sqlite.close();
    }

    @Test
    void addThenIsMutedTrue() {
        repo.add("g1", "u1");
        assertTrue(repo.isMuted("g1", "u1"));
    }

    @Test
    void removeClearsIt() {
        repo.add("g1", "u1");
        repo.remove("g1", "u1");
        assertFalse(repo.isMuted("g1", "u1"));
    }

    @Test
    void isGuildScoped() {
        repo.add("g1", "u1");
        assertFalse(repo.isMuted("g2", "u1"));
    }

    @Test
    void addIsIdempotent() {
        repo.add("g1", "u1");
        repo.add("g1", "u1");
        assertTrue(repo.isMuted("g1", "u1"));
    }
}
