package dev.davimf.basebot.modules.facs.recruit;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Aplica todas as migrações (incl. 042) num DB temporário e exercita o repositório. */
class RecruitRequestRepositoryTest {

    @TempDir
    Path tmp;
    private SqliteManager sqlite;
    private RecruitRequestRepository repo;

    @BeforeEach
    void setUp() {
        sqlite = new SqliteManager(new BotConfig.Sqlite(tmp.resolve("test.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new RecruitRequestRepository(sqlite);
    }

    @AfterEach
    void tearDown() {
        sqlite.close();
    }

    @Test
    void saveThenFindReturnsAnswers() {
        repo.save("m1", "g1", "app", "rec", "ID123", "Zé da Silva", "555-000");
        Optional<RecruitRequestRepository.Request> r = repo.find("m1");
        assertTrue(r.isPresent());
        assertEquals("app", r.get().applicantId());
        assertEquals("ID123", r.get().idJogo());
        assertEquals("Zé da Silva", r.get().nome());
        assertEquals("PENDING", r.get().status());
    }

    @Test
    void findMissingIsEmpty() {
        assertTrue(repo.find("nope").isEmpty());
    }

    @Test
    void resolvePendingWinsOnceThenFails() {
        repo.save("m1", "g1", "app", "rec", "ID", "Nome", "tel");
        assertTrue(repo.resolvePending("m1", "ACCEPTED"), "primeiro claim vence");
        assertFalse(repo.resolvePending("m1", "REJECTED"), "segundo claim não muda mais");
        assertEquals("ACCEPTED", repo.find("m1").orElseThrow().status());
    }

    @Test
    void resolvePendingOnMissingRowIsFalse() {
        assertFalse(repo.resolvePending("ghost", "ACCEPTED"));
    }

    @Test
    void setStatusRevertsUnconditionally() {
        repo.save("m1", "g1", "app", "rec", "ID", "Nome", "tel");
        assertTrue(repo.resolvePending("m1", "ACCEPTED"));
        repo.setStatus("m1", "PENDING");
        assertEquals("PENDING", repo.find("m1").orElseThrow().status());
        assertTrue(repo.resolvePending("m1", "ACCEPTED"), "após reverter, claim volta a vencer");
    }
}
