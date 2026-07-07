package dev.davimf.basebot.modules.base.leveling;

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

class UserLevelRepositoryTest {
    private SqliteManager sqlite;
    private UserLevelRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new UserLevelRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void addXpAccumulatesAndReturnsNewTotal() {
        assertEquals(20, repo.addXp("g1", "u1", 20));
        assertEquals(50, repo.addXp("g1", "u1", 30));
        assertEquals(50, repo.xp("g1", "u1"));
    }

    @Test
    void lastMessageTsRoundtrips() {
        repo.setLastMessageTs("g1", "u1", 1234L);
        assertEquals(1234L, repo.lastMessageTs("g1", "u1"));
    }

    @Test
    void setXpOverwrites() {
        repo.addXp("g1", "u1", 500);
        repo.setXp("g1", "u1", 10);
        assertEquals(10, repo.xp("g1", "u1"));
    }

    @Test
    void topAndRankOrderByXpDesc() {
        repo.addXp("g1", "a", 100);
        repo.addXp("g1", "b", 300);
        repo.addXp("g1", "c", 200);
        List<UserLevelRepository.Entry> top = repo.topPage("g1", 10, 0);
        assertEquals("b", top.get(0).userId());
        assertEquals("c", top.get(1).userId());
        assertEquals(1, repo.rank("g1", "b"));
        assertEquals(3, repo.rank("g1", "a"));
        assertEquals(3, repo.count("g1"));
    }
}
