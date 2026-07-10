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

class VoiceTimeRepositoryTest {

    private static final long W1 = VoiceWeek.weekStart(1_700_000_000_000L);
    private static final long W2 = VoiceWeek.nextWeekStart(W1);

    private SqliteManager sqlite;
    private VoiceTimeRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new VoiceTimeRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void addMsAccumulatesPerWeekAndMarksDirty() {
        repo.addMs("g1", "u1", W1, 1000);
        repo.addMs("g1", "u1", W1, 500);
        repo.addMs("g1", "u1", W2, 300);

        assertEquals(1500, repo.msOf("g1", "u1", W1));
        assertEquals(300, repo.msOf("g1", "u1", W2));
        assertEquals(2, repo.dirtyRows(10).size());
    }

    @Test
    void missingRowReadsAsZero() {
        assertEquals(0, repo.msOf("g1", "nobody", W1));
    }

    @Test
    void clearDirtySucceedsOnlyWhenMsIsUnchanged() {
        repo.addMs("g1", "u1", W1, 1000);

        // O ticker incrementou depois que o flusher leu 1000: a linha continua suja.
        repo.addMs("g1", "u1", W1, 1000);
        assertFalse(repo.clearDirty("g1", "u1", W1, 1000));
        assertEquals(1, repo.dirtyRows(10).size());

        // Com o valor corrente, limpa.
        assertTrue(repo.clearDirty("g1", "u1", W1, 2000));
        assertTrue(repo.dirtyRows(10).isEmpty());
    }

    @Test
    void purgeWeeksBeforeSkipsDirtyRows() {
        repo.addMs("g1", "old-dirty", W1, 10);
        repo.addMs("g1", "old-clean", W1, 20);
        repo.clearDirty("g1", "old-clean", W1, 20);

        assertEquals(1, repo.purgeWeeksBefore(W2));

        assertEquals(10, repo.msOf("g1", "old-dirty", W1));
        assertEquals(0, repo.msOf("g1", "old-clean", W1));
    }

    @Test
    void allOfWeekReturnsEveryRowIncludingZeroes() {
        repo.addMs("g1", "u1", W1, 100);
        repo.addMs("g1", "zero", W1, 0);
        repo.addMs("g1", "outra-semana", W2, 50);
        repo.addMs("g2", "outra-guild", W1, 70);

        List<VoiceTimeRepository.Entry> rows = repo.allOfWeek("g1", W1);

        assertEquals(2, rows.size(), "linhas com ms=0 tambem voltam: quem tem pendente pode somar");
        assertTrue(rows.stream().anyMatch(e -> e.userId().equals("u1") && e.ms() == 100));
        assertTrue(rows.stream().anyMatch(e -> e.userId().equals("zero") && e.ms() == 0));
    }
}
