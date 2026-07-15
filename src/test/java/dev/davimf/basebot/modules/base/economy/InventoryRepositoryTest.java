package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.UseResult;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.UseResultType;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.DestroyResultType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class InventoryRepositoryTest {
    private SqliteManager sqlite;
    private InventoryRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new InventoryRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void buyStoresSlotAndUsesFromCatalog() {
        long id = repo.buy("g", "u", "pickaxe_wood");
        var row = repo.list("g", "u").get(0);
        assertEquals(id, row.id());
        assertEquals(Slot.MINING, row.slot());
        assertEquals(15, row.usosLeft());
        assertFalse(row.equipped());
    }

    @Test
    void equipIsOnePerSlot() {
        long a = repo.buy("g", "u", "pickaxe_wood");
        long b = repo.buy("g", "u", "pickaxe_iron");
        assertTrue(repo.equip("g", "u", a));
        assertEquals(a, repo.equipped("g", "u", Slot.MINING).id());
        assertTrue(repo.equip("g", "u", b));                       // troca
        assertEquals(b, repo.equipped("g", "u", Slot.MINING).id());
        // só um equipado no slot
        assertEquals(1, repo.list("g", "u").stream().filter(r -> r.equipped()).count());
    }

    @Test
    void equipRejectsOtherOwner() {
        long id = repo.buy("g", "owner", "weapon_knife");
        assertFalse(repo.equip("g", "intruder", id));
        assertNull(repo.equipped("g", "intruder", Slot.WEAPON));
    }

    @Test
    void useOnceDecrementsThenBreaks() {
        long id = repo.buy("g", "u", "weapon_knife"); // 15 usos
        UseResult r1 = repo.useOnce("g", "u", id);
        assertEquals(UseResultType.USED, r1.type());
        assertEquals(14, r1.usosLeft());
        for (int i = 0; i < 13; i++) {
            repo.useOnce("g", "u", id); // até 1
        }
        UseResult broke = repo.useOnce("g", "u", id); // de 1 → 0
        assertEquals(UseResultType.USED_AND_BROKE, broke.type());
        assertTrue(repo.list("g", "u").isEmpty()); // linha removida
    }

    @Test
    void useOnceNotFoundAndNotOwner() {
        assertEquals(UseResultType.NOT_FOUND, repo.useOnce("g", "u", 999).type());
        long id = repo.buy("g", "owner", "weapon_knife");
        assertEquals(UseResultType.NOT_OWNER, repo.useOnce("g", "intruder", id).type());
    }

    @Test
    void destroyReturnsTypedResult() {
        long id = repo.buy("g", "u", "weapon_knife");
        assertEquals(DestroyResultType.NOT_OWNER, repo.destroy("g", "x", id).type());
        assertEquals(DestroyResultType.DESTROYED, repo.destroy("g", "u", id).type());
        assertEquals(DestroyResultType.NOT_FOUND, repo.destroy("g", "u", id).type());
    }

    @Test
    void useManyConsumesNAndReportsRemaining() {
        long id = repo.buy("g", "u", "weapon_rifle"); // 60 usos
        UseResult r = repo.useMany("g", "u", id, 4);
        assertEquals(UseResultType.USED, r.type());
        assertEquals(56, r.usosLeft());
    }

    @Test
    void useManyBreaksWhenNMeetsOrExceedsRemaining() {
        long id = repo.buy("g", "u", "weapon_knife"); // 15 usos
        for (int i = 0; i < 12; i++) {
            repo.useOnce("g", "u", id); // sobra 3
        }
        UseResult r = repo.useMany("g", "u", id, 4); // 3 - 4 <= 0 → quebra
        assertEquals(UseResultType.USED_AND_BROKE, r.type());
        assertTrue(repo.list("g", "u").isEmpty());
    }

    @Test
    void useManyNotFoundAndNotOwner() {
        assertEquals(UseResultType.NOT_FOUND, repo.useMany("g", "u", 999, 4).type());
        long id = repo.buy("g", "owner", "weapon_knife");
        assertEquals(UseResultType.NOT_OWNER, repo.useMany("g", "intruder", id, 4).type());
    }
}
