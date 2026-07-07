# Empregos/Equipamentos/Crime — Plano 1: Fundação — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A base do sistema de gameplay da economia: catálogo fixo de equipamentos, inventário por usuário com durabilidade, estado de cadeia/ficha, e os comandos `/mercado`, `/inventario`, `/fianca`, `/limparficha` — mais o guard de cadeia aplicado ao `/trabalhar`.

**Architecture:** Tudo em `modules/base/economy/`. Catálogo e resolvers são puros/testados; inventário e estado criminal são SQLite (migração 035) com contratos de resultado explícito (`UseResult`/`DestroyResult`); a lógica de caso de uso (comprar/equipar/cadeia) fica em serviços finos que os comandos slash só chamam e formatam. Reusa `WalletRepository`/`CooldownRepository`/`EconomyConfig` existentes.

**Tech Stack:** Java 22, JDA 6.4.2 (Components V2), SQLite via HikariCP (`SqliteManager`), JUnit 5 (sem Mockito).

## Global Constraints

- **Base ≠ facs:** nada em `modules/base/economy/` importa de `modules/facs/`.
- **Migrações:** todo `NNN_*.sql` novo DEVE ser anexado a `SqliteMigrator.MIGRATIONS`. Próxima livre = **035** (034 é a última — a Loja).
- **Components V2:** toda mensagem é `Container` (via `Panels`); emojis via `Emojis.of(...)`/`Emojis.button(...)`; custom-id `namespace:action:args` via `ComponentId.of(...)` (teto 100 chars).
- **Gated por `eco:enabled`**, checado **antes** de cooldown/inventário/cadeia/qualquer mutação.
- **Ordem segura:** nunca pagar/transferir antes de confirmar `useOnce`/`destroy`; abortar em `NOT_FOUND`/`NOT_OWNER`.
- **Multas:** `min(rolled, cashAtual)`, nunca negativa.
- **Slot nunca confiado sozinho:** vem sempre de `EquipmentCatalog.byKey(itemKey).slot()`.
- **Testes:** JUnit 5 puro; repos com SQLite temp (`@TempDir` + `SqliteMigrator.migrate()`); serviços/views/handlers/comandos via build (padrão do projeto).
- **Spec:** `docs/superpowers/specs/2026-07-02-base-jobs-tools-crime-design.md`.
- **Números:** todos em `EquipmentCatalog` + `EconomyDefaults` (ver §1/§7 do spec — copiar exatos).

---

### Task 1: `Slot` + `EquipmentCatalog` (puro, fixo)

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/economy/EquipmentCatalog.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/economy/EquipmentCatalogTest.java`

**Interfaces:**
- Produces: `EquipmentCatalog.Slot { MINING, COOKING, DELIVERY, WEAPON }`; `record Equip(String key, String name, Slot slot, int tier, long price, int maxUsos, long payoutMin, long payoutMax, long fuel, int chanceBonus, double mult, long robCap)`; estáticos `Equip byKey(String)` (null se ausente), `List<Equip> ofSlot(Slot)` (ordem por tier), `int tierOf(String key)` (0 se ausente), `List<Equip> all()`.

- [ ] **Step 1: Write the failing test**

`EquipmentCatalogTest.java`:
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Equip;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentCatalogTest {

    @Test
    void byKeyResolvesAndUnknownIsNull() {
        Equip iron = EquipmentCatalog.byKey("pickaxe_iron");
        assertNotNull(iron);
        assertEquals(Slot.MINING, iron.slot());
        assertEquals(4_000, iron.price());
        assertEquals(50, iron.maxUsos());
        assertNull(EquipmentCatalog.byKey("nope"));
    }

    @Test
    void ofSlotReturnsTierOrdered() {
        var motos = EquipmentCatalog.ofSlot(Slot.DELIVERY);
        assertEquals(5, motos.size());
        assertEquals("moto_pop", motos.get(0).key());
        assertEquals("moto_bmw", motos.get(4).key());
        for (int i = 1; i < motos.size(); i++) {
            assertTrue(motos.get(i).tier() > motos.get(i - 1).tier());
        }
    }

    @Test
    void weaponsCarryBonusMultAndRobCap() {
        Equip rifle = EquipmentCatalog.byKey("weapon_rifle");
        assertEquals(Slot.WEAPON, rifle.slot());
        assertEquals(40, rifle.chanceBonus());
        assertEquals(2.5, rifle.mult());
        assertEquals(50_000, rifle.robCap());
    }

    @Test
    void goldPickaxeHas25Uses() {
        assertEquals(25, EquipmentCatalog.byKey("pickaxe_gold").maxUsos());
    }

    @Test
    void tierOf() {
        assertEquals(3, EquipmentCatalog.tierOf("pickaxe_iron"));
        assertEquals(0, EquipmentCatalog.tierOf("nope"));
    }

    @Test
    void everyKeyUniqueAndCountsMatch() {
        assertEquals(5, EquipmentCatalog.ofSlot(Slot.MINING).size());
        assertEquals(5, EquipmentCatalog.ofSlot(Slot.COOKING).size());
        assertEquals(5, EquipmentCatalog.ofSlot(Slot.DELIVERY).size());
        assertEquals(4, EquipmentCatalog.ofSlot(Slot.WEAPON).size());
        assertEquals(19, EquipmentCatalog.all().size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.economy.EquipmentCatalogTest"`
Expected: FAIL — `EquipmentCatalog` não existe.

- [ ] **Step 3: Write the implementation**

`EquipmentCatalog.java` — transcrever exatamente as tabelas do §1 do spec:
```java
package dev.davimf.basebot.modules.base.economy;

import java.util.List;

/** Catálogo fixo de equipamentos (mesmo em todo servidor). Puro, sem estado. */
public final class EquipmentCatalog {

    public enum Slot { MINING, COOKING, DELIVERY, WEAPON }

    /** Campos não usados pelo slot ficam 0. Mineração/cozinha: payout. Entrega: payout+fuel. Arma: chanceBonus+mult+robCap. */
    public record Equip(String key, String name, Slot slot, int tier, long price, int maxUsos,
                        long payoutMin, long payoutMax, long fuel, int chanceBonus, double mult, long robCap) {}

    private static Equip tool(String key, String name, Slot slot, int tier, long price, int usos, long min, long max) {
        return new Equip(key, name, slot, tier, price, usos, min, max, 0, 0, 0, 0);
    }

    private static Equip moto(String key, String name, int tier, long price, int usos, long fuel, long min, long max) {
        return new Equip(key, name, Slot.DELIVERY, tier, price, usos, min, max, fuel, 0, 0, 0);
    }

    private static Equip weapon(String key, String name, int tier, long price, int usos, int bonus, double mult, long cap) {
        return new Equip(key, name, Slot.WEAPON, tier, price, usos, 0, 0, 0, bonus, mult, cap);
    }

    private static final List<Equip> ALL = List.of(
            tool("pickaxe_wood",    "Picareta de Madeira",     Slot.MINING, 1,    500,  15,  60,  120),
            tool("pickaxe_stone",   "Picareta de Pedra",       Slot.MINING, 2,  1_500,  30, 120,  220),
            tool("pickaxe_iron",    "Picareta de Ferro",       Slot.MINING, 3,  4_000,  50, 250,  400),
            tool("pickaxe_gold",    "Picareta de Ouro",        Slot.MINING, 4, 10_000,  25, 600,  900),
            tool("pickaxe_diamond", "Picareta de Diamante",    Slot.MINING, 5, 25_000, 100, 800, 1400),
            tool("cook_spoon",      "Colher de Pau",           Slot.COOKING, 1,    300, 20,  50,  100),
            tool("cook_whisk",      "Fouet de Silicone",       Slot.COOKING, 2,  1_200, 30, 110,  200),
            tool("cook_knife",      "Faca do Chef (Aço Inox)", Slot.COOKING, 3,  3_500, 40, 220,  380),
            tool("cook_torch",      "Maçarico Culinário",      Slot.COOKING, 4,  8_000, 35, 400,  700),
            tool("cook_case",       "Maleta Masterchef",       Slot.COOKING, 5, 20_000, 80, 750, 1200),
            moto("moto_pop",   "Honda Pop 100",           1,  2_500,  40,  30,  150,  250),
            moto("moto_titan", "CG Titan 160",            2,  8_000,  60,  60,  300,  500),
            moto("moto_xre",   "Honda XRE 300",           3, 18_000,  80, 100,  550,  850),
            moto("moto_xt",    "Yamaha XT 660 (Meiota)",  4, 40_000, 100, 180, 1000, 1500),
            moto("moto_bmw",   "BMW R1250 GS (Foguete)",  5, 90_000, 120, 300, 1800, 2800),
            weapon("weapon_knife",   "Canivete Borboleta", 1,  2_000, 15,  5, 1.0,  5_000),
            weapon("weapon_machete", "Facão de Selva",     2,  6_000, 25, 12, 1.3, 10_000),
            weapon("weapon_pistol",  "Pistola 9mm",        3, 20_000, 40, 25, 1.8, 25_000),
            weapon("weapon_rifle",   "Fuzil AR-15",        4, 50_000, 60, 40, 2.5, 50_000));

    private EquipmentCatalog() {}

    public static List<Equip> all() { return ALL; }

    public static Equip byKey(String key) {
        for (Equip e : ALL) {
            if (e.key().equals(key)) {
                return e;
            }
        }
        return null;
    }

    public static List<Equip> ofSlot(Slot slot) {
        return ALL.stream().filter(e -> e.slot() == slot)
                .sorted(java.util.Comparator.comparingInt(Equip::tier)).toList();
    }

    public static int tierOf(String key) {
        Equip e = byKey(key);
        return e == null ? 0 : e.tier();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.economy.EquipmentCatalogTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/economy/EquipmentCatalog.java \
        src/test/java/dev/davimf/basebot/modules/base/economy/EquipmentCatalogTest.java
git commit -m "feat(eco): EquipmentCatalog — catálogo fixo de equipamentos"
```

---

### Task 2: Migração 035 + `InventoryRepository`

**Files:**
- Create: `src/main/resources/db/sqlite/035_equipment.sql`
- Modify: `src/main/java/dev/davimf/basebot/database/sqlite/SqliteMigrator.java` (append após `034_shop.sql`)
- Create: `src/main/java/dev/davimf/basebot/modules/base/economy/InventoryRepository.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/economy/InventoryRepositoryTest.java`

**Interfaces:**
- Consumes: `EquipmentCatalog` (Task 1), `SqliteManager`, `RepositoryException`.
- Produces:
  - `record Row(long id, String itemKey, EquipmentCatalog.Slot slot, int usosLeft, boolean equipped)`.
  - `enum UseResultType { USED, USED_AND_BROKE, PERMANENT, NOT_FOUND, NOT_OWNER }`; `record UseResult(UseResultType type, int usosLeft)`.
  - `enum DestroyResultType { DESTROYED, NOT_FOUND, NOT_OWNER }`; `record DestroyResult(DestroyResultType type)`.
  - `InventoryRepository(SqliteManager)` com: `long buy(g,u,itemKey)` (resolve slot+usos do catálogo; devolve rowId), `List<Row> list(g,u)` (ordem `slot, created_at`, **pula linhas com key fora do catálogo ou slot inconsistente**), `Row equipped(g,u,Slot)` (null se nenhum), `boolean equip(g,u,rowId)` (transação: valida dono/existência, desequipa o slot, equipa), `UseResult useOnce(g,u,rowId)`, `DestroyResult destroy(g,u,rowId)`.

- [ ] **Step 1: Write the failing test**

`InventoryRepositoryTest.java`:
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.economy.InventoryRepositoryTest"`
Expected: FAIL — migração/classe ausentes.

- [ ] **Step 3: Write the implementation**

`035_equipment.sql` (as duas tabelas do §2 do spec):
```sql
CREATE TABLE IF NOT EXISTS user_inventory (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id   TEXT NOT NULL,
    user_id    TEXT NOT NULL,
    item_key   TEXT NOT NULL,
    slot       TEXT NOT NULL,
    usos_left  INTEGER NOT NULL,
    equipped   INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_inv_user_slot ON user_inventory(guild_id, user_id, slot);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_equipped_slot
    ON user_inventory(guild_id, user_id, slot) WHERE equipped = 1;

CREATE TABLE IF NOT EXISTS user_crime_state (
    guild_id   TEXT NOT NULL,
    user_id    TEXT NOT NULL,
    preso_ate  INTEGER NOT NULL DEFAULT 0,
    ficha_suja INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, user_id)
);
```

Em `SqliteMigrator.java`, trocar a última linha `"/db/sqlite/034_shop.sql"` (que termina o `List.of(`) por:
```java
            "/db/sqlite/034_shop.sql",
            "/db/sqlite/035_equipment.sql"
    );
```

`InventoryRepository.java`:
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Equip;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Inventário do usuário (migração 035). Resultado explícito em useOnce/destroy; slot vem do catálogo. */
public final class InventoryRepository {

    public record Row(long id, String itemKey, Slot slot, int usosLeft, boolean equipped) {}

    public enum UseResultType { USED, USED_AND_BROKE, PERMANENT, NOT_FOUND, NOT_OWNER }
    public record UseResult(UseResultType type, int usosLeft) {}

    public enum DestroyResultType { DESTROYED, NOT_FOUND, NOT_OWNER }
    public record DestroyResult(DestroyResultType type) {}

    private final SqliteManager sqlite;

    public InventoryRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    /** Compra: resolve slot + usos do catálogo, insere instância nova. Devolve rowId (ou -1 se key inválida). */
    public long buy(String g, String u, String itemKey) {
        Equip e = EquipmentCatalog.byKey(itemKey);
        if (e == null) {
            return -1;
        }
        String sql = "INSERT INTO user_inventory (guild_id, user_id, item_key, slot, usos_left, equipped, created_at) "
                + "VALUES (?,?,?,?,?,0,?)";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, g);
            ps.setString(2, u);
            ps.setString(3, e.key());
            ps.setString(4, e.slot().name());
            ps.setInt(5, e.maxUsos());
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        } catch (SQLException ex) {
            throw new RepositoryException("inventory buy " + g + "/" + u, ex);
        }
    }

    /** Itens do usuário; pula linhas cujo item_key sumiu do catálogo ou cujo slot não bate (dados inconsistentes). */
    public List<Row> list(String g, String u) {
        List<Row> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM user_inventory WHERE guild_id=? AND user_id=? ORDER BY slot, created_at")) {
            ps.setString(1, g);
            ps.setString(2, u);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Row row = mapValid(rs);
                    if (row != null) {
                        out.add(row);
                    }
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("inventory list " + g + "/" + u, e);
        }
    }

    public Row equipped(String g, String u, Slot slot) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM user_inventory WHERE guild_id=? AND user_id=? AND slot=? AND equipped=1")) {
            ps.setString(1, g);
            ps.setString(2, u);
            ps.setString(3, slot.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapValid(rs) : null;
            }
        } catch (SQLException e) {
            throw new RepositoryException("inventory equipped " + g + "/" + u, e);
        }
    }

    /** Equipa numa transação: valida dono, desequipa o slot, equipa esta linha. false se não existe/não é do dono/key inválida. */
    public boolean equip(String g, String u, long rowId) {
        try (Connection c = sqlite.getConnection()) {
            boolean prev = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                String slot;
                try (PreparedStatement sel = c.prepareStatement(
                        "SELECT item_key, slot FROM user_inventory WHERE id=? AND guild_id=? AND user_id=?")) {
                    sel.setLong(1, rowId);
                    sel.setString(2, g);
                    sel.setString(3, u);
                    try (ResultSet rs = sel.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return false;
                        }
                        Equip e = EquipmentCatalog.byKey(rs.getString("item_key"));
                        slot = rs.getString("slot");
                        if (e == null || !e.slot().name().equals(slot)) { // slot inconsistente com o catálogo
                            c.rollback();
                            return false;
                        }
                    }
                }
                try (PreparedStatement un = c.prepareStatement(
                        "UPDATE user_inventory SET equipped=0 WHERE guild_id=? AND user_id=? AND slot=? AND equipped=1")) {
                    un.setString(1, g);
                    un.setString(2, u);
                    un.setString(3, slot);
                    un.executeUpdate();
                }
                try (PreparedStatement eq = c.prepareStatement("UPDATE user_inventory SET equipped=1 WHERE id=?")) {
                    eq.setLong(1, rowId);
                    eq.executeUpdate();
                }
                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prev);
            }
        } catch (SQLException e) {
            throw new RepositoryException("inventory equip " + g + "/" + u + "/" + rowId, e);
        }
    }

    /** Consome 1 uso numa transação. Resultado explícito. */
    public UseResult useOnce(String g, String u, long rowId) {
        try (Connection c = sqlite.getConnection()) {
            boolean prev = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                int usos;
                String owner;
                try (PreparedStatement sel = c.prepareStatement(
                        "SELECT user_id, usos_left FROM user_inventory WHERE id=? AND guild_id=?")) {
                    sel.setLong(1, rowId);
                    sel.setString(2, g);
                    try (ResultSet rs = sel.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return new UseResult(UseResultType.NOT_FOUND, 0);
                        }
                        owner = rs.getString("user_id");
                        usos = rs.getInt("usos_left");
                    }
                }
                if (!owner.equals(u)) {
                    c.rollback();
                    return new UseResult(UseResultType.NOT_OWNER, 0);
                }
                if (usos < 0) { // permanente (reservado; v1 não usa)
                    c.rollback();
                    return new UseResult(UseResultType.PERMANENT, usos);
                }
                int left = usos - 1;
                if (left <= 0) {
                    try (PreparedStatement del = c.prepareStatement("DELETE FROM user_inventory WHERE id=?")) {
                        del.setLong(1, rowId);
                        del.executeUpdate();
                    }
                    c.commit();
                    return new UseResult(UseResultType.USED_AND_BROKE, 0);
                }
                try (PreparedStatement up = c.prepareStatement("UPDATE user_inventory SET usos_left=? WHERE id=?")) {
                    up.setInt(1, left);
                    up.setLong(2, rowId);
                    up.executeUpdate();
                }
                c.commit();
                return new UseResult(UseResultType.USED, left);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prev);
            }
        } catch (SQLException e) {
            throw new RepositoryException("inventory useOnce " + g + "/" + u + "/" + rowId, e);
        }
    }

    /** Destrói (remove) a linha. Resultado explícito. */
    public DestroyResult destroy(String g, String u, long rowId) {
        try (Connection c = sqlite.getConnection()) {
            boolean prev = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                String owner;
                try (PreparedStatement sel = c.prepareStatement(
                        "SELECT user_id FROM user_inventory WHERE id=? AND guild_id=?")) {
                    sel.setLong(1, rowId);
                    sel.setString(2, g);
                    try (ResultSet rs = sel.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return new DestroyResult(DestroyResultType.NOT_FOUND);
                        }
                        owner = rs.getString("user_id");
                    }
                }
                if (!owner.equals(u)) {
                    c.rollback();
                    return new DestroyResult(DestroyResultType.NOT_OWNER);
                }
                try (PreparedStatement del = c.prepareStatement("DELETE FROM user_inventory WHERE id=?")) {
                    del.setLong(1, rowId);
                    del.executeUpdate();
                }
                c.commit();
                return new DestroyResult(DestroyResultType.DESTROYED);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prev);
            }
        } catch (SQLException e) {
            throw new RepositoryException("inventory destroy " + g + "/" + u + "/" + rowId, e);
        }
    }

    /** Mapeia a linha; devolve null se o item_key sumiu do catálogo ou o slot não bate. */
    private static Row mapValid(ResultSet rs) throws SQLException {
        String key = rs.getString("item_key");
        String slotStr = rs.getString("slot");
        Equip e = EquipmentCatalog.byKey(key);
        if (e == null || !e.slot().name().equals(slotStr)) {
            return null;
        }
        return new Row(rs.getLong("id"), key, e.slot(), rs.getInt("usos_left"), rs.getInt("equipped") == 1);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.economy.InventoryRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/sqlite/035_equipment.sql \
        src/main/java/dev/davimf/basebot/database/sqlite/SqliteMigrator.java \
        src/main/java/dev/davimf/basebot/modules/base/economy/InventoryRepository.java \
        src/test/java/dev/davimf/basebot/modules/base/economy/InventoryRepositoryTest.java
git commit -m "feat(eco): migração 035 + InventoryRepository (durabilidade + resultado explícito)"
```

---

### Task 3: `CrimeStateRepository` + `JailService`

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/economy/CrimeStateRepository.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/economy/JailService.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/economy/JailServiceTest.java`

**Interfaces:**
- Consumes: `SqliteManager`, `WalletRepository` (existente: `Wallet get`, `tryDebitCash`), `BotContext`, `RepositoryException`, `EconomyDefaults` (constantes novas — Task 3 as adiciona).
- Produces:
  - `CrimeStateRepository(SqliteManager)`: `record State(long presoAte, boolean fichaSuja)`; `State get(g,u)`, `void jail(g,u,ate)`, `void setFicha(g,u,boolean)`, `void release(g,u)` (upsert).
  - `JailService(BotContext)`: `enum Kind{LIVRE,PRESO}`; `record Status(Kind kind, long presoAte)`; `Status resolve(g,u)` (release-por-tempo marca ficha); `boolean fichaSuja(g,u)`; `void jailFor(g,u,millis)`; `String bail(g,u)` (paga BAIL_BASE, libera sem marcar); `String expunge(g,u)` (paga EXPUNGE, limpa ficha); `boolean blockedIfJailed(IReplyCallback event, BotContext ctx)` (responde efêmero e devolve true se preso).
- `EconomyDefaults` novas constantes: `BAIL_BASE=15_000`, `EXPUNGE=30_000`, `FICHA_PENALTY_PCT=15`.

- [ ] **Step 1: Write the failing test**

`JailServiceTest.java` (testa a máquina de estado via repo em DB temp; `blockedIfJailed` é coberto por build/smoke):
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import dev.davimf.basebot.modules.base.economy.CrimeStateRepository.State;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JailServiceTest {
    private SqliteManager sqlite;
    private CrimeStateRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new CrimeStateRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void jailAndReleaseUpsert() {
        long ate = System.currentTimeMillis() + 60_000;
        repo.jail("g", "u", ate);
        State s = repo.get("g", "u");
        assertEquals(ate, s.presoAte());
        assertFalse(s.fichaSuja());
        repo.release("g", "u");
        assertEquals(0, repo.get("g", "u").presoAte());
    }

    @Test
    void setFichaPersists() {
        repo.setFicha("g", "u", true);
        assertTrue(repo.get("g", "u").fichaSuja());
        repo.setFicha("g", "u", false);
        assertFalse(repo.get("g", "u").fichaSuja());
    }

    @Test
    void resolveMarksFichaOnServedSentence() {
        // pena já expirada e não pagou fiança → cumpriu a pena → marca ficha + libera
        repo.jail("g", "u", System.currentTimeMillis() - 1);
        boolean served = JailService.resolveServed(repo, "g", "u", System.currentTimeMillis());
        assertTrue(served);                       // estava preso e a pena passou
        assertTrue(repo.get("g", "u").fichaSuja());
        assertEquals(0, repo.get("g", "u").presoAte());
    }

    @Test
    void resolveKeepsJailWhenStillServing() {
        repo.jail("g", "u", System.currentTimeMillis() + 60_000);
        assertFalse(JailService.resolveServed(repo, "g", "u", System.currentTimeMillis()));
        assertFalse(repo.get("g", "u").fichaSuja());
        assertTrue(repo.get("g", "u").presoAte() > 0);
    }
}
```

> **Nota:** `JailService.resolveServed(repo, g, u, now)` é um helper **estático e puro-de-JDA** (só mexe no repo) extraído justamente pra ser testável; o `resolve(g,u)` de instância chama ele. Assim o teste cobre a regra "cumprir pena marca ficha; fiança não".

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.economy.JailServiceTest"`
Expected: FAIL — classes ausentes.

- [ ] **Step 3: Write the implementation**

`CrimeStateRepository.java`:
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Estado de cadeia/ficha do usuário (migração 035). */
public final class CrimeStateRepository {

    public record State(long presoAte, boolean fichaSuja) {}

    private final SqliteManager sqlite;

    public CrimeStateRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    public State get(String g, String u) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT preso_ate, ficha_suja FROM user_crime_state WHERE guild_id=? AND user_id=?")) {
            ps.setString(1, g);
            ps.setString(2, u);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new State(rs.getLong(1), rs.getInt(2) == 1) : new State(0, false);
            }
        } catch (SQLException e) {
            throw new RepositoryException("crimestate get " + g + "/" + u, e);
        }
    }

    public void jail(String g, String u, long ate) { upsert(g, u, "preso_ate", ate); }
    public void release(String g, String u) { upsert(g, u, "preso_ate", 0); }
    public void setFicha(String g, String u, boolean dirty) { upsert(g, u, "ficha_suja", dirty ? 1 : 0); }

    private void upsert(String g, String u, String col, long value) {
        String sql = "INSERT INTO user_crime_state (guild_id, user_id, " + col + ") VALUES (?,?,?) "
                + "ON CONFLICT (guild_id, user_id) DO UPDATE SET " + col + " = excluded." + col;
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, g);
            ps.setString(2, u);
            ps.setLong(3, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("crimestate upsert " + col + " " + g + "/" + u, e);
        }
    }
}
```

`JailService.java`:
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

/** Cadeia + ficha: resolução lazy (cumprir pena marca ficha), guard, fiança e limpar-ficha. */
public final class JailService {

    public enum Kind { LIVRE, PRESO }
    public record Status(Kind kind, long presoAte) {}

    private final BotContext ctx;
    private final CrimeStateRepository states;
    private final WalletRepository wallets;

    public JailService(BotContext ctx) {
        this.ctx = ctx;
        this.states = new CrimeStateRepository(ctx.database().sqlite());
        this.wallets = new WalletRepository(ctx.database().sqlite());
    }

    /** Helper testável: se preso e a pena passou, marca ficha + libera; devolve true se cumpriu a pena agora. */
    static boolean resolveServed(CrimeStateRepository states, String g, String u, long now) {
        CrimeStateRepository.State s = states.get(g, u);
        if (s.presoAte() > 0 && now >= s.presoAte()) {
            states.setFicha(g, u, true);
            states.release(g, u);
            return true;
        }
        return false;
    }

    public Status resolve(String g, String u) {
        long now = System.currentTimeMillis();
        resolveServed(states, g, u, now); // marca ficha + libera se cumpriu a pena
        CrimeStateRepository.State s = states.get(g, u);
        return s.presoAte() > now ? new Status(Kind.PRESO, s.presoAte()) : new Status(Kind.LIVRE, 0);
    }

    public boolean fichaSuja(String g, String u) { return states.get(g, u).fichaSuja(); }

    public void jailFor(String g, String u, long millis) {
        states.jail(g, u, System.currentTimeMillis() + millis);
    }

    /** true se preso: responde efêmero e o comando deve abortar. */
    public boolean blockedIfJailed(IReplyCallback event, BotContext ctx, String g, String u) {
        Status st = resolve(g, u);
        if (st.kind() == Kind.PRESO) {
            dev.davimf.basebot.util.Replies.ephemeral(event, ctx,
                    "Você está preso — sai <t:" + (st.presoAte() / 1000) + ":R>. Pague `/fianca` pra sair agora.");
            return true;
        }
        return false;
    }

    /** Paga a fiança: libera sem marcar a ficha. Devolve mensagem. */
    public String bail(String g, String u) {
        if (resolve(g, u).kind() != Kind.PRESO) {
            return "Você não está preso.";
        }
        if (!wallets.tryDebitCash(g, u, EconomyDefaults.BAIL_BASE)) {
            return "Saldo insuficiente na carteira pra fiança (precisa de " + EconomyDefaults.BAIL_BASE + ").";
        }
        states.release(g, u); // não marca ficha
        return "Fiança paga. Você está livre — a prisão não foi pra sua ficha.";
    }

    /** Limpa a ficha suja (custa EXPUNGE). */
    public String expunge(String g, String u) {
        if (resolve(g, u).kind() == Kind.PRESO) {
            return "Você está preso — resolva isso primeiro.";
        }
        if (!states.get(g, u).fichaSuja()) {
            return "Sua ficha já está limpa.";
        }
        if (!wallets.tryDebitCash(g, u, EconomyDefaults.EXPUNGE)) {
            return "Saldo insuficiente pra limpar a ficha (precisa de " + EconomyDefaults.EXPUNGE + ").";
        }
        states.setFicha(g, u, false);
        return "Ficha limpa.";
    }
}
```

Adicionar em `EconomyDefaults.java` (após as constantes de ROB):
```java
    public static final long BAIL_BASE = 15_000;
    public static final long EXPUNGE = 30_000;
    public static final int FICHA_PENALTY_PCT = 15;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.economy.JailServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/economy/CrimeStateRepository.java \
        src/main/java/dev/davimf/basebot/modules/base/economy/JailService.java \
        src/main/java/dev/davimf/basebot/modules/base/economy/EconomyDefaults.java \
        src/test/java/dev/davimf/basebot/modules/base/economy/JailServiceTest.java
git commit -m "feat(eco): CrimeStateRepository + JailService (cadeia/ficha/fiança)"
```

---

### Task 4: `EquipmentService` (comprar/equipar) + `/mercado` + `/inventario`

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/economy/EquipmentService.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/economy/MercadoView.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/economy/InventarioView.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/economy/EquipmentComponentHandler.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/MercadoCommand.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/InventarioCommand.java`

**Interfaces:**
- Consumes: `EquipmentCatalog`, `InventoryRepository`, `WalletRepository`, `EconomyConfig`, `EconomyFormat`, `Panels`, `Replies`, `EmbedColor`, `Emojis`, `Durations`, `ComponentId`, `ComponentHandler`, `SlashCommand`.
- Produces: `EquipmentService(BotContext)` — `String buy(g, u, itemKey)` (revalida catálogo+preço+saldo, debita, insere; mensagem), `String equip(g, u, rowId)` (via repo; mensagem), `List<InventoryRepository.Row> inventory(g,u)`; `MercadoView`/`InventarioView` (namespace const `NS="equip"`); `EquipmentComponentHandler` (namespace `equip`).

> Tarefa **build-verified** (padrão do projeto — views/handlers/serviços não têm teste unitário; a lógica testável já está em Task 1–3). Deliverable: compila + os fluxos existem.

- [ ] **Step 1: Write `EquipmentService`**

`EquipmentService.java`:
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Equip;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;
import dev.davimf.basebot.util.Durations;
import dev.davimf.basebot.util.Emojis;

import java.util.List;

/** Casos de uso de equipamento: comprar (revalida catálogo/preço/saldo) e equipar. */
public final class EquipmentService {

    private final BotContext ctx;
    private final InventoryRepository inv;
    private final WalletRepository wallets;

    public EquipmentService(BotContext ctx) {
        this.ctx = ctx;
        this.inv = new InventoryRepository(ctx.database().sqlite());
        this.wallets = new WalletRepository(ctx.database().sqlite());
    }

    public InventoryRepository inv() { return inv; }

    private GuildConfig cfg(String g) { return ctx.database().guildConfig().findOrEmpty(g); }

    public String buy(String g, String u, String itemKey) {
        GuildConfig cfg = cfg(g);
        if (!EconomyConfig.enabled(cfg)) {
            return "Economia desativada.";
        }
        Equip e = EquipmentCatalog.byKey(itemKey); // revalida no clique (não confia na UI)
        if (e == null) {
            return "Item indisponível.";
        }
        if (!wallets.tryDebitCash(g, u, e.price())) {
            return "Saldo insuficiente na carteira (" + EconomyFormat.formatNamed(e.price(), cfg) + ").";
        }
        inv.buy(g, u, itemKey);
        return Emojis.of(Emojis.CHECK_YES, "✅") + " Comprou **" + e.name() + "** (−"
                + EconomyFormat.formatNamed(e.price(), cfg) + "). Equipe no `/inventario`.";
    }

    public String equip(String g, String u, long rowId) {
        return inv.equip(g, u, rowId)
                ? Emojis.of(Emojis.CHECK_YES, "✅") + " Equipado."
                : "Não consegui equipar (item inexistente ou não é seu).";
    }

    public List<InventoryRepository.Row> inventory(String g, String u) { return inv.list(g, u); }

    /** Rótulo curto de um item pra UI (usa o catálogo). */
    public static String label(Equip e) {
        String extra = switch (e.slot()) {
            case WEAPON -> "+" + e.chanceBonus() + "% · " + e.mult() + "x";
            case DELIVERY -> "fuel " + e.fuel();
            default -> e.payoutMin() + "–" + e.payoutMax();
        };
        return e.name() + " · " + extra + " · " + e.maxUsos() + " usos";
    }
}
```

- [ ] **Step 2: Write the views + handler + commands**

`MercadoView.java` — `NS="equip"`; `vitrine(accent, cfg)` mostra um `StringSelectMenu` de slot (`equip:mslot` com opções MINING/COOKING/DELIVERY/WEAPON) + texto; `slotList(accent, slot, cfg)` lista os itens do slot com preço (`EconomyFormat.format(e.price(), cfg)`) num `StringSelectMenu` `equip:buy` cujos valores são os `item_key`. Resultado da compra edita a efêmera. Segue o house style (título `## 🛒 Mercado`, `Panels.divider()`), emoji `Emojis.SALES`.

`InventarioView.java` — `panel(accent, rows, cfg)`: agrupa `rows` por slot; cada item vira um botão/opção `Equipar #<id> — <nome> — <usos>` com custom id `equip:equip:<rowId>`; marca o equipado. Usa `EquipmentService.label` + `EquipmentCatalog.byKey(row.itemKey())`.

`EquipmentComponentHandler.java` (namespace `equip`):
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

/** Runtime do /mercado e /inventario (namespace "equip"). */
public final class EquipmentComponentHandler implements ComponentHandler {

    private final EquipmentService svc;

    public EquipmentComponentHandler(EquipmentService svc) { this.svc = svc; }

    @Override public String namespace() { return MercadoView.NS; } // "equip"

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            return;
        }
        String g = event.getGuild().getId();
        String u = event.getMember().getId();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(g);
        int accent = EmbedColor.resolve(cfg);
        switch (id.action()) {
            case "mslot" -> event.editComponents(MercadoView.slotList(accent,
                    EquipmentCatalog.Slot.valueOf(event.getValues().get(0)), cfg)).useComponentsV2().queue();
            case "buy" -> {
                String msg = svc.buy(g, u, event.getValues().get(0));
                event.editComponents(MercadoView.result(accent, msg)).useComponentsV2().queue();
            }
            case "equip" -> {
                String msg = svc.equip(g, u, parse(event.getValues().get(0)));
                event.editComponents(InventarioView.result(accent, msg)).useComponentsV2().queue();
            }
            default -> { }
        }
    }

    private static long parse(String s) { try { return Long.parseLong(s); } catch (Exception e) { return -1; } }
}
```
> Se preferir botões em vez de select pra equipar, o custom id vira `equip:equip:<rowId>` num `onButton` — mantenha o namespace `equip` e a ação `equip`. Ajuste `MercadoView.result`/`InventarioView.result` como um painel simples (`Panels.container(accent, Panels.text(msg))`).

`MercadoCommand`/`InventarioCommand` — espelham `SaldoCommand` (efêmero, checam `EconomyConfig.enabled`): `/mercado` responde `MercadoView.vitrine(...)`; `/inventario` responde `InventarioView.panel(EmbedColor.resolve(cfg), svc.inventory(g,u), cfg)`. Ambos `setEphemeral(true)`.

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. (Emojis a usar já verificados no projeto: `SALES`, `CHECK_YES`, `CASH`, `GEM`, `EDIT`.)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/economy/EquipmentService.java \
        src/main/java/dev/davimf/basebot/modules/base/economy/MercadoView.java \
        src/main/java/dev/davimf/basebot/modules/base/economy/InventarioView.java \
        src/main/java/dev/davimf/basebot/modules/base/economy/EquipmentComponentHandler.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/MercadoCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/InventarioCommand.java
git commit -m "feat(eco): /mercado + /inventario (comprar/equipar equipamentos)"
```

---

### Task 5: `/fianca` + `/limparficha` + wiring no `BaseModule` + guard no `/trabalhar`

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/FiancaCommand.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/LimparFichaCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/commands/TrabalharCommand.java`

**Interfaces:**
- Consumes: `JailService` (Task 3), `EquipmentService` (Task 4), `EquipmentComponentHandler`, `SlashCommand`, `Replies`.
- Produces: fundação totalmente registrada; `JailService` disponível pros planos 2–4 (o `BaseModule` guarda uma instância `jail` num campo, análogo a `shop`).

- [ ] **Step 1: Write the commands**

`FiancaCommand` — `/fianca` (efêmero): checa guild+`EconomyConfig.enabled`; `Replies.ephemeral(event, ctx, jail.bail(g, u))`. `LimparFichaCommand` — `/limparficha`: `Replies.ephemeral(event, ctx, jail.expunge(g, u))`. Ambos espelham `SaldoCommand` na estrutura, recebendo `JailService` no construtor.

- [ ] **Step 2: Wire into BaseModule**

Em `BaseModule.java`, adicionar campos perto de `private ... ShopService shop;`:
```java
    private dev.davimf.basebot.modules.base.economy.JailService jail;
    private dev.davimf.basebot.modules.base.economy.EquipmentService equipment;
```
No `register(...)`, logo após o bloco da Loja:
```java
        // Equipamentos + cadeia (Base) — fundação de empregos/crime.
        this.jail = new dev.davimf.basebot.modules.base.economy.JailService(ctx);
        this.equipment = new dev.davimf.basebot.modules.base.economy.EquipmentService(ctx);
        registry.command(new dev.davimf.basebot.modules.base.commands.MercadoCommand(equipment));
        registry.command(new dev.davimf.basebot.modules.base.commands.InventarioCommand(equipment));
        registry.command(new dev.davimf.basebot.modules.base.commands.FiancaCommand(jail));
        registry.command(new dev.davimf.basebot.modules.base.commands.LimparFichaCommand(jail));
        registry.component(new dev.davimf.basebot.modules.base.economy.EquipmentComponentHandler(equipment));
```

- [ ] **Step 3: Apply the jail guard to `/trabalhar`**

Em `TrabalharCommand.execute`, após checar `EconomyConfig.enabled` e antes de `Replies.reply(...)`, injetar o guard. `TrabalharCommand` recebe `EconomyService`; o guard precisa de `JailService`. Mudar o construtor de `TrabalharCommand` pra receber também `JailService` (e passar `jail` no `BaseModule`), então:
```java
        if (jail.blockedIfJailed(event, ctx, event.getGuild().getId(), event.getMember().getId())) {
            return;
        }
```
(No `BaseModule`, `new TrabalharCommand(economy, jail)`.)

- [ ] **Step 4: Full build + tests**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL; testes novos (`EquipmentCatalogTest`, `InventoryRepositoryTest`, `JailServiceTest`) verdes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/commands/FiancaCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/LimparFichaCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/TrabalharCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(eco): /fianca + /limparficha + guard de cadeia no /trabalhar + wiring"
```

---

## Self-review (cobertura do spec — Plano 1)
- Catálogo fixo (§1) → Task 1. Migração 035 + inventário com slot/created_at/unique-parcial (§2) → Task 2. `UseResult`/`DestroyResult` + validação de slot (§1/§2 do spec) → Task 2. Cadeia/ficha + fiança/expunge + guard + resolve-lazy (§3) → Task 3 (+ guard aplicado em Task 5). `/mercado`/`/inventario` por rowId + revalida preço (§9) → Task 4. `/fianca`/`/limparficha` + wiring + guard no /trabalhar → Task 5.
- Gated por `eco:enabled` antes de mutação → nos serviços (Task 4) e comandos (Task 5).
- **Fora deste plano** (planos 2–4): empregos, crime armado, org — mas suas dependências (`JailService`, `InventoryRepository`, `EquipmentCatalog`, constantes) ficam prontas aqui.
