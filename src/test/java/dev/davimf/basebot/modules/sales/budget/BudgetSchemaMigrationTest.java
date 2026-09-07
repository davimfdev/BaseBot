package dev.davimf.basebot.modules.sales.budget;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.model.Budget;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the full SQLite migration chain applies cleanly and that the (corrected)
 * {@code budgets} schema matches what {@link BudgetRepository} expects — guarding the bug
 * where 001_init's placeholder budgets table shadowed the real one.
 */
class BudgetSchemaMigrationTest {

    @Test
    void migratesAndBudgetRepositoryRoundTrips(@TempDir Path dir) {
        BotConfig.Sqlite cfg = new BotConfig.Sqlite(dir.resolve("test.db").toString());
        try (SqliteManager sqlite = new SqliteManager(cfg)) {
            new SqliteMigrator(sqlite).migrate();

            BudgetRepository repo = new BudgetRepository(sqlite);
            String id = repo.createDraft("guild", "seller", "client");
            repo.addItem(id, "Adder", 150_000L, 2);
            repo.markSent(id, "chan-1", "msg-1", Instant.now().plusSeconds(60).toString());

            Budget b = repo.find(id).orElseThrow();
            assertEquals(Budget.PENDING, b.status());
            assertEquals("chan-1", b.channelId());
            assertEquals("msg-1", b.messageId());
            assertEquals(1, repo.listItems(id).size());
            assertEquals(300_000L, BudgetView.total(repo.listItems(id)));
            assertTrue(repo.listPendingExpired(Instant.now().plusSeconds(120).toString())
                    .stream().anyMatch(x -> x.id().equals(id)));
        }
    }
}
