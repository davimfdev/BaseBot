package dev.davimf.basebot.modules.base.selfroles;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SelfRolePanelRepositoryTest {

    private SqliteManager sqlite;
    private SelfRolePanelRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new SelfRolePanelRepository(sqlite);
    }

    @AfterEach
    void tearDown() {
        sqlite.close();
    }

    @Test
    void createFindUpdateOptionsPublishDelete() {
        String id = repo.createPanel("g1", "Cores", "Escolha", SelfRolePanel.STYLE_MENU, true);
        SelfRolePanel p = repo.find(id).orElseThrow();
        assertEquals("Cores", p.title());
        assertTrue(p.unique());
        assertEquals(SelfRolePanel.STYLE_MENU, p.style());
        assertTrue(p.options().isEmpty());

        repo.setOptions(id, List.of(
                new SelfRolePanel.Option("100", "Azul", null, 0),
                new SelfRolePanel.Option("200", "Verde", "🟢", 1)));
        assertEquals(List.of("100", "200"), repo.find(id).orElseThrow().roleIds());

        repo.setPublished(id, "555", "999");
        SelfRolePanel pub = repo.find(id).orElseThrow();
        assertEquals("555", pub.channelId());
        assertEquals("999", pub.messageId());

        assertEquals(1, repo.list("g1").size());
        repo.delete(id);
        assertEquals(Optional.empty(), repo.find(id));
    }
}
