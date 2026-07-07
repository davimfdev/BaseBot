package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VerificationRepositoryTest {

    private SqliteManager sqlite;
    private VerificationRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new VerificationRepository(sqlite);
    }

    @AfterEach
    void tearDown() {
        sqlite.close();
    }

    @Test
    void verifiedRoundTrip() {
        assertFalse(repo.isVerified("g1", "u1"));
        repo.markVerified("g1", "u1", 123L);
        assertTrue(repo.isVerified("g1", "u1"));
        assertFalse(repo.isVerified("g2", "u1")); // por servidor
        repo.forget("g1", "u1");
        assertFalse(repo.isVerified("g1", "u1"));
    }

    @Test
    void pendingIsUniquePerUser() {
        assertTrue(repo.openRequest("g1", "u1", "a", 1L));
        assertTrue(repo.hasPending("g1", "u1"));
        assertFalse(repo.openRequest("g1", "u1", "b", 2L)); // já pendente -> ignora
        assertEquals("a", repo.findPending("g1", "u1").orElseThrow().answers());
    }

    @Test
    void attachAndCloseRequest() {
        repo.openRequest("g1", "u1", "a", 1L);
        repo.attachMessage("g1", "u1", "m99");
        assertEquals("m99", repo.findPending("g1", "u1").orElseThrow().messageId());
        repo.closeRequest("g1", "u1");
        assertFalse(repo.hasPending("g1", "u1"));
        assertTrue(repo.findPending("g1", "u1").isEmpty());
    }
}
