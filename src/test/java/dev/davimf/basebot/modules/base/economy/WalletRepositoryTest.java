package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WalletRepositoryTest {
    private SqliteManager sqlite;
    private WalletRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new WalletRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void addCashUpsertsAndGet() {
        repo.addCash("g1", "u1", 100);
        assertEquals(100, repo.get("g1", "u1").cash());
        assertEquals(0, repo.get("g1", "u1").bank());
    }

    @Test
    void depositMovesCashToBankAtomically() {
        repo.addCash("g1", "u1", 100);
        assertTrue(repo.deposit("g1", "u1", 60));
        assertEquals(40, repo.get("g1", "u1").cash());
        assertEquals(60, repo.get("g1", "u1").bank());
        assertFalse(repo.deposit("g1", "u1", 999));
        assertEquals(40, repo.get("g1", "u1").cash());
    }

    @Test
    void withdrawMovesBankToCashAtomically() {
        repo.addCash("g1", "u1", 100);
        repo.deposit("g1", "u1", 100);
        assertTrue(repo.withdraw("g1", "u1", 30));
        assertEquals(30, repo.get("g1", "u1").cash());
        assertEquals(70, repo.get("g1", "u1").bank());
        assertFalse(repo.withdraw("g1", "u1", 999));
    }

    @Test
    void transferDebitsPayerAndCreatesRecipientRow() {
        repo.addCash("g1", "payer", 100);
        assertTrue(repo.transfer("g1", "payer", "newbie", 40));
        assertEquals(60, repo.get("g1", "payer").cash());
        assertEquals(40, repo.get("g1", "newbie").cash());
    }

    @Test
    void transferFailsWithoutDebitWhenInsufficient() {
        repo.addCash("g1", "payer", 10);
        assertFalse(repo.transfer("g1", "payer", "other", 40));
        assertEquals(10, repo.get("g1", "payer").cash());
        assertEquals(0, repo.get("g1", "other").cash());
    }

    @Test
    void tryDebitCashFloorsAtBalance() {
        repo.addCash("g1", "u1", 30);
        assertFalse(repo.tryDebitCash("g1", "u1", 40));
        assertEquals(30, repo.get("g1", "u1").cash());
        assertTrue(repo.tryDebitCash("g1", "u1", 20));
        assertEquals(10, repo.get("g1", "u1").cash());
    }

    @Test
    void topAndRankByTotal() {
        repo.addCash("g1", "a", 100);
        repo.addCash("g1", "b", 50); repo.addBank("g1", "b", 300);
        repo.addCash("g1", "c", 200);
        List<WalletRepository.Entry> top = repo.topPage("g1", 10, 0);
        assertEquals("b", top.get(0).userId());
        assertEquals(350, top.get(0).total());
        assertEquals("c", top.get(1).userId());
        assertEquals(1, repo.rank("g1", "b"));
        assertEquals(3, repo.count("g1"));
    }
}
