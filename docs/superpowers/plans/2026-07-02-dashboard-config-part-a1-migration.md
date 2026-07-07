# Parte A1 — Migração de config SQLite → Neon (Postgres) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mover as 5 tabelas de **configuração** hoje no SQLite (`self_role_panels`+`self_role_options`, `level_rewards`, `quiz_questions`, `shop_items`, `fac_action_types`) para o Postgres/Neon, mantendo o contador operacional `shop_items.sold` no SQLite (`shop_stock`), sem mudar comportamento visível do bot.

**Architecture:** Cada tabela ganha DDL Postgres (via `PostgresMigrator` + `ApplyPostgresSchema`) e um repo JDBC que espelha o repo SQLite atual (mesmas assinaturas públicas → serviços/handlers não mudam), backed por `PostgresPool` (padrão `JdbcGuildConfigRepository`/`TicketCategoryRepository`). O `sold` da loja vira uma tabela SQLite `shop_stock`; `reserveStock`/`releaseStock` passam a receber o limite `stock` (config lida do Postgres) como parâmetro. Um script de migração idempotente copia os dados existentes preservando ids.

**Tech Stack:** Java 22, JDBC (`org.postgresql:postgresql`), HikariCP (`PostgresPool`), SQLite (`org.xerial:sqlite-jdbc`, `SqliteManager`), JUnit 5 (sem Mockito), Jackson (já em uso no repo de config).

## Decisão de teste (ler antes de começar)

O projeto **não tem harness de teste Postgres** (sem H2/Testcontainers). Toda a camada Postgres atual (`JdbcGuildConfigRepository`, `TicketCategoryRepository`) é verificada por `./gradlew build` + smoke manual, **sem** teste unitário de banco. Este plano segue esse precedente:
- **Repos JDBC (Postgres):** sem teste de banco; verificados por `./gradlew build` (compila + suíte atual passa) + checklist de smoke na Task 8/final.
- **`ShopStockRepository` (SQLite) e lógica pura (`ShopItem.withSold`/`remaining`/`soldOut`):** teste JUnit real (SQLite em memória — padrão do projeto).
- **Os 4 testes SQLite dos repos que trocam de backend são removidos** (Task correspondente), porque a cobertura de banco deles não é reproduzível sem harness Postgres. Custo aceito e sinalizado.
- **Alternativa (opcional, não neste plano):** adicionar `com.h2database:h2` em modo `PostgreSQL` como harness pra portar esses testes. Reversível; decidir com o dono antes.

## Global Constraints

- **IDs do Discord são sempre `TEXT`/`String`** — nunca número. (`guild_id`, `role_id`, `channel_id`, `message_id`, `user_id`.) `shop_items.id` é numérico interno (não é snowflake) → `BIGINT`.
- **`shop_items.sold` NÃO vai pro Postgres** — é estado operacional; fica no SQLite (`shop_stock`). O Postgres guarda só config (`stock`/`per_user` são limites de config).
- **Sem mudar comportamento visível** — só troca o backend do dado. As assinaturas públicas dos repos ficam idênticas (exceto `reserveStock`/`releaseStock`, que ganham o parâmetro de limite e migram pro `ShopStockRepository`).
- **Soft-delete** onde há histórico: `shop_items` e `self_role_panels` ganham `enabled BOOLEAN NOT NULL DEFAULT true`; `list()` filtra `enabled=true`; `delete` vira `UPDATE ... SET enabled=false`. `find()` **não** filtra (compras/expiração precisam achar item desativado).
- **Postgres migrations são idempotentes** (`CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`) e aplicadas via a tool `ApplyPostgresSchema` (não rodam no boot — ver `PostgresMigrator` javadoc).
- **`unique_choice`** (self-role) permanece com esse nome (já é seguro; não usar `unique`). No Postgres é `BOOLEAN`.
- Contrato de schema: `docs/superpowers/dashboard-config-schema-contract.md`. Spec: `docs/superpowers/specs/2026-07-02-dashboard-config-integration-design.md`.

---

## File Structure

- `src/main/resources/db/postgres/004_config_tables.sql` — **novo**: DDL das 5 tabelas no Postgres.
- `src/main/resources/db/sqlite/036_shop_stock.sql` — **novo**: tabela `shop_stock` (contador operacional).
- `src/main/java/.../database/postgres/PostgresMigrator.java` — registra o 004 na lista.
- `src/main/java/.../modules/base/economy/ShopStockRepository.java` — **novo** (SQLite): contador `sold`.
- `src/main/java/.../modules/base/economy/ShopItem.java` — `withSold(int)`.
- `src/main/java/.../modules/base/economy/ShopItemRepository.java` — passa a Postgres, só config, soft-delete, sem `sold`/reserve.
- `src/main/java/.../modules/base/economy/ShopService.java` — injeta `ShopStockRepository`; `catalog` preenche `sold`; `buy`/`refund` usam o novo reserve com limite.
- `src/main/java/.../modules/base/leveling/LevelRewardRepository.java` — Postgres.
- `src/main/java/.../modules/base/leveling/LevelingService.java:29` — constrói com `postgres()`.
- `src/main/java/.../modules/base/fun/QuizRepository.java` — Postgres.
- `src/main/java/.../modules/base/fun/QuizPlayService.java:31` — constrói com `postgres()`.
- `src/main/java/.../modules/base/selfroles/SelfRolePanelRepository.java` — Postgres (boolean `unique_choice`).
- `src/main/java/.../modules/base/setup/SetupComponentHandler.java:719` e `.../selfroles/SelfRoleComponentHandler.java:97` — constroem com `postgres()`.
- `src/main/java/.../database/sqlite/ActionTypeRepository.java` → move pra `.../database/postgres/ActionTypeRepository.java` (Postgres).
- `src/main/java/.../database/DatabaseManager.java:88` — constrói `ActionTypeRepository(postgres)`.
- `src/main/java/.../database/postgres/ConfigMigrationTool.java` — **novo**: script SQLite→Postgres (id-preserving, `setval`, `sold`→`shop_stock`).
- Testes removidos: `SelfRolePanelRepositoryTest`, `LevelRewardRepositoryTest`, `QuizRepositoryTest`, `ShopItemRepositoryTest`.
- Testes novos: `ShopStockRepositoryTest`, `ShopItemSoldTest`.

---

## Task 1: DDL Postgres das 5 tabelas

**Files:**
- Create: `src/main/resources/db/postgres/004_config_tables.sql`
- Modify: `src/main/java/dev/davimf/basebot/database/postgres/PostgresMigrator.java:44-48`

**Interfaces:**
- Produces: as tabelas Postgres `self_role_panels`, `self_role_options`, `level_rewards`, `quiz_questions`, `shop_items`, `fac_action_types` (colunas usadas pelas Tasks 3–7).

- [ ] **Step 1: Escrever a DDL**

Create `src/main/resources/db/postgres/004_config_tables.sql`:

```sql
-- Config tables migrated from SQLite so Neon is the single source of truth for the
-- web dashboard. Operational counters stay in SQLite (shop_items.sold -> shop_stock).
-- Idempotent (IF NOT EXISTS): safe to re-apply.

CREATE TABLE IF NOT EXISTS self_role_panels (
    id            TEXT PRIMARY KEY,
    guild_id      TEXT NOT NULL,
    title         TEXT NOT NULL,
    description   TEXT,
    style         TEXT NOT NULL DEFAULT 'buttons',
    unique_choice BOOLEAN NOT NULL DEFAULT false,
    channel_id    TEXT,
    message_id    TEXT,
    enabled       BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_self_role_panels_guild ON self_role_panels (guild_id);

CREATE TABLE IF NOT EXISTS self_role_options (
    panel_id TEXT NOT NULL,
    role_id  TEXT NOT NULL,
    label    TEXT NOT NULL,
    emoji    TEXT,
    position INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (panel_id, role_id)
);

CREATE TABLE IF NOT EXISTS level_rewards (
    guild_id TEXT NOT NULL,
    level    INTEGER NOT NULL,
    role_id  TEXT NOT NULL,
    PRIMARY KEY (guild_id, level)
);

CREATE TABLE IF NOT EXISTS quiz_questions (
    id       TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL,
    question TEXT NOT NULL,
    correct  TEXT NOT NULL,
    wrong1   TEXT NOT NULL,
    wrong2   TEXT NOT NULL,
    wrong3   TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_quiz_guild ON quiz_questions (guild_id);

CREATE TABLE IF NOT EXISTS shop_items (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    guild_id    TEXT NOT NULL,
    type        TEXT NOT NULL,
    role_id     TEXT,
    name        TEXT NOT NULL,
    description TEXT,
    price       BIGINT NOT NULL,
    duration_s  BIGINT,
    stock       INTEGER,
    per_user    INTEGER,
    enabled     BOOLEAN NOT NULL DEFAULT true,
    created_at  BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_shop_items_guild ON shop_items (guild_id);

CREATE TABLE IF NOT EXISTS fac_action_types (
    id             TEXT PRIMARY KEY,
    guild_id       TEXT NOT NULL,
    name           TEXT NOT NULL,
    max_contingent INTEGER NOT NULL DEFAULT 0,
    min_contingent INTEGER NOT NULL DEFAULT 0,
    dirty_money    INTEGER NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_action_types_guild_name ON fac_action_types (guild_id, name);
```

- [ ] **Step 2: Registrar no migrator**

Modify `PostgresMigrator.java` `MIGRATIONS`:

```java
    public static final List<String> MIGRATIONS = List.of(
            "/db/postgres/001_guild_config.sql",
            "/db/postgres/002_guild_settings.sql",
            "/db/postgres/003_ticket_categories.sql",
            "/db/postgres/004_config_tables.sql"
    );
```

- [ ] **Step 3: Compilar**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Aplicar o schema no Neon**

Aplicar via a tool `ApplyPostgresSchema` (o migrator não roda no boot; ver javadoc). Confirmar que a saída lista `004_config_tables.sql` como aplicada e que uma 2ª aplicação não faz nada (idempotente).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/postgres/004_config_tables.sql src/main/java/dev/davimf/basebot/database/postgres/PostgresMigrator.java
git commit -m "feat(db): Postgres DDL for the 5 migrated config tables"
```

---

## Task 2: `shop_stock` (SQLite) + `ShopStockRepository`

**Files:**
- Create: `src/main/resources/db/sqlite/036_shop_stock.sql`
- Modify: `src/main/java/dev/davimf/basebot/database/sqlite/SqliteMigrator.java` (registrar 036)
- Create: `src/main/java/dev/davimf/basebot/modules/base/economy/ShopStockRepository.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/economy/ShopStockRepositoryTest.java`

**Interfaces:**
- Produces:
  - `ShopStockRepository(SqliteManager sqlite)`
  - `boolean reserveStock(long itemId, Integer stockLimit)` — atômico; `true` = reservado; `false` = esgotado (só quando `stockLimit != null` e já no limite).
  - `void releaseStock(long itemId)` — decrementa com piso 0.
  - `int soldOf(long itemId)` — 0 se não há linha.

- [ ] **Step 1: Escrever o teste que falha**

Create `ShopStockRepositoryTest.java`:

```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShopStockRepositoryTest {

    private SqliteManager sqlite;
    private ShopStockRepository repo;

    @BeforeEach
    void setUp() {
        sqlite = new SqliteManager(new BotConfig.Sqlite("jdbc:sqlite::memory:"));
        new SqliteMigrator(sqlite).migrate();
        repo = new ShopStockRepository(sqlite);
    }

    @AfterEach
    void tearDown() {
        sqlite.close();
    }

    @Test
    void soldStartsAtZero() {
        assertEquals(0, repo.soldOf(1L));
    }

    @Test
    void reserveIncrementsAndRespectsFiniteLimit() {
        assertTrue(repo.reserveStock(1L, 2));   // 1
        assertTrue(repo.reserveStock(1L, 2));   // 2
        assertFalse(repo.reserveStock(1L, 2));  // esgotado
        assertEquals(2, repo.soldOf(1L));
    }

    @Test
    void reserveUnlimitedWhenStockNull() {
        assertTrue(repo.reserveStock(9L, null));
        assertTrue(repo.reserveStock(9L, null));
        assertEquals(2, repo.soldOf(9L));
    }

    @Test
    void releaseHasFloorZero() {
        repo.releaseStock(5L);
        assertEquals(0, repo.soldOf(5L));
        repo.reserveStock(5L, null);
        repo.releaseStock(5L);
        assertEquals(0, repo.soldOf(5L));
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.economy.ShopStockRepositoryTest"`
Expected: FAIL — `ShopStockRepository` não existe (erro de compilação).

- [ ] **Step 3: Criar a migração SQLite**

Create `src/main/resources/db/sqlite/036_shop_stock.sql`:

```sql
-- Operational sold-counter for shop items. Config (price/limits/name) lives in Postgres
-- (shop_items); this stays in SQLite so Neon never takes an operational write per purchase.
CREATE TABLE IF NOT EXISTS shop_stock (
    item_id INTEGER PRIMARY KEY,
    sold    INTEGER NOT NULL DEFAULT 0
);
```

Register in `SqliteMigrator` (append `"/db/sqlite/036_shop_stock.sql"` to the ordered migrations list — segue o padrão dos itens 001–035; nunca reordenar).

- [ ] **Step 4: Implementar o repositório**

Create `ShopStockRepository.java`:

```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Operational sold-counter for shop items (SQLite). The item config (price, stock limit,
 * per-user) lives in Postgres; only this fast-changing counter stays local so Neon never
 * takes an operational write per purchase. Reservation is atomic within SQLite; the stock
 * limit is passed in from the Postgres-backed {@link ShopItemRepository}.
 */
public final class ShopStockRepository {

    private final SqliteManager sqlite;

    public ShopStockRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    /** Reserva 1 unidade atomicamente. {@code true} = reservado; {@code false} = esgotado. */
    public boolean reserveStock(long itemId, Integer stockLimit) {
        try (Connection c = sqlite.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT OR IGNORE INTO shop_stock (item_id, sold) VALUES (?, 0)")) {
                    ins.setLong(1, itemId);
                    ins.executeUpdate();
                }
                String sql = "UPDATE shop_stock SET sold = sold + 1 "
                        + "WHERE item_id = ? AND (? IS NULL OR sold < ?)";
                try (PreparedStatement up = c.prepareStatement(sql)) {
                    up.setLong(1, itemId);
                    if (stockLimit == null) {
                        up.setNull(2, java.sql.Types.INTEGER);
                        up.setNull(3, java.sql.Types.INTEGER);
                    } else {
                        up.setInt(2, stockLimit);
                        up.setInt(3, stockLimit);
                    }
                    boolean ok = up.executeUpdate() == 1;
                    c.commit();
                    return ok;
                }
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            throw new RepositoryException("reserve stock " + itemId, e);
        }
    }

    /** Estorna 1 unidade reservada (piso 0). */
    public void releaseStock(long itemId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE shop_stock SET sold = sold - 1 WHERE item_id = ? AND sold > 0")) {
            ps.setLong(1, itemId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("release stock " + itemId, e);
        }
    }

    /** Quantidade vendida do item (0 quando não há linha). */
    public int soldOf(long itemId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT sold FROM shop_stock WHERE item_id = ?")) {
            ps.setLong(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("soldOf " + itemId, e);
        }
    }
}
```

> Nota: confirmar a assinatura do construtor `SqliteManager` e de `BotConfig.Sqlite` contra um teste SQLite existente (ex.: `ShopItemRepositoryTest` antes de removê-lo, ou `QuizRepositoryTest`) e alinhar o `setUp()` do teste ao padrão real do projeto.

- [ ] **Step 5: Rodar e ver passar**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.economy.ShopStockRepositoryTest"`
Expected: PASS (4 testes).

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/sqlite/036_shop_stock.sql src/main/java/dev/davimf/basebot/database/sqlite/SqliteMigrator.java src/main/java/dev/davimf/basebot/modules/base/economy/ShopStockRepository.java src/test/java/dev/davimf/basebot/modules/base/economy/ShopStockRepositoryTest.java
git commit -m "feat(economy): shop_stock SQLite counter (sold) split from config"
```

---

## Task 3: `LevelRewardRepository` → Postgres

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/leveling/LevelRewardRepository.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/leveling/LevelingService.java:29`
- Delete: `src/test/java/dev/davimf/basebot/modules/base/leveling/LevelRewardRepositoryTest.java`

**Interfaces:**
- Consumes: `PostgresPool` (via `ctx.database().postgres()`), tabela `level_rewards` (Task 1).
- Produces: assinaturas **inalteradas** — `put(String,int,String)`, `remove(String,int)`, `Map<Integer,String> all(String)`.

- [ ] **Step 1: Reescrever o repo para Postgres**

Replace `LevelRewardRepository.java` body (troca `SqliteManager`→`PostgresPool`; SQL idêntico, `excluded`→`EXCLUDED`):

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.postgres.PostgresPool;
import dev.davimf.basebot.database.postgres.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Postgres store para cargos por nível (config editável pelo dashboard). */
public final class LevelRewardRepository {

    private final PostgresPool pool;

    public LevelRewardRepository(PostgresPool pool) { this.pool = pool; }

    public void put(String guildId, int level, String roleId) {
        String sql = "INSERT INTO level_rewards (guild_id, level, role_id) VALUES (?,?,?) "
                + "ON CONFLICT (guild_id, level) DO UPDATE SET role_id = EXCLUDED.role_id";
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setInt(2, level);
            ps.setString(3, roleId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("put reward " + guildId + "/" + level, e);
        }
    }

    public void remove(String guildId, int level) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM level_rewards WHERE guild_id=? AND level=?")) {
            ps.setString(1, guildId);
            ps.setInt(2, level);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("remove reward " + guildId + "/" + level, e);
        }
    }

    /** Mapa nível→roleId ordenado por nível. */
    public Map<Integer, String> all(String guildId) {
        Map<Integer, String> out = new LinkedHashMap<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT level, role_id FROM level_rewards WHERE guild_id=? ORDER BY level")) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getInt("level"), rs.getString("role_id"));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("all rewards " + guildId, e);
        }
    }
}
```

- [ ] **Step 2: Atualizar o call site**

Modify `LevelingService.java:29`:

```java
        this.rewards = new LevelRewardRepository(ctx.database().postgres());
```

- [ ] **Step 3: Remover o teste SQLite obsoleto**

Delete `src/test/java/dev/davimf/basebot/modules/base/leveling/LevelRewardRepositoryTest.java` (dependia de SQLite; a tabela agora é Postgres, sem harness de teste — ver "Decisão de teste").

- [ ] **Step 4: Compilar + suíte**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`; nenhuma referência quebrada a `LevelRewardRepository(sqlite)`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(leveling): level_rewards backed by Postgres"
```

---

## Task 4: `QuizRepository` → Postgres

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/fun/QuizRepository.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/fun/QuizPlayService.java:31`
- Delete: `src/test/java/dev/davimf/basebot/modules/base/fun/QuizRepositoryTest.java`

**Interfaces:**
- Consumes: `PostgresPool`, tabela `quiz_questions` (Task 1), record `QuizQuestion` (inalterado).
- Produces: assinaturas inalteradas — `String add(...)`, `void remove(String)`, `List<QuizQuestion> list(String)`, `Optional<QuizQuestion> random(String)`.

- [ ] **Step 1: Reescrever o repo para Postgres**

Replace `QuizRepository.java` body (troca de backend; `ORDER BY RANDOM()` → Postgres `random()`):

```java
package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.database.postgres.PostgresPool;
import dev.davimf.basebot.database.postgres.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Postgres store das perguntas de quiz personalizadas (config editável pelo dashboard). */
public final class QuizRepository {

    private final PostgresPool pool;

    public QuizRepository(PostgresPool pool) { this.pool = pool; }

    public String add(String guildId, String question, String correct, String w1, String w2, String w3) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String sql = "INSERT INTO quiz_questions (id, guild_id, question, correct, wrong1, wrong2, wrong3) "
                + "VALUES (?,?,?,?,?,?,?)";
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, guildId);
            ps.setString(3, question);
            ps.setString(4, correct);
            ps.setString(5, w1);
            ps.setString(6, w2);
            ps.setString(7, w3);
            ps.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new RepositoryException("quiz add " + guildId, e);
        }
    }

    public void remove(String id) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM quiz_questions WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("quiz remove " + id, e);
        }
    }

    public List<QuizQuestion> list(String guildId) {
        List<QuizQuestion> out = new ArrayList<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM quiz_questions WHERE guild_id=? ORDER BY id")) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("quiz list " + guildId, e);
        }
    }

    public Optional<QuizQuestion> random(String guildId) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM quiz_questions WHERE guild_id=? ORDER BY random() LIMIT 1")) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("quiz random " + guildId, e);
        }
    }

    private static QuizQuestion map(ResultSet rs) throws SQLException {
        return new QuizQuestion(rs.getString("id"), rs.getString("guild_id"), rs.getString("question"),
                rs.getString("correct"), rs.getString("wrong1"), rs.getString("wrong2"), rs.getString("wrong3"));
    }
}
```

- [ ] **Step 2: Atualizar o call site**

Modify `QuizPlayService.java:31`:

```java
        this.repo = new QuizRepository(ctx.database().postgres());
```

- [ ] **Step 3: Remover o teste SQLite obsoleto**

Delete `src/test/java/dev/davimf/basebot/modules/base/fun/QuizRepositoryTest.java`.

- [ ] **Step 4: Compilar + suíte**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(fun): quiz_questions backed by Postgres"
```

---

## Task 5: `SelfRolePanelRepository` → Postgres

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/selfroles/SelfRolePanelRepository.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java:719`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/selfroles/SelfRoleComponentHandler.java:97`
- Delete: `src/test/java/dev/davimf/basebot/modules/base/selfroles/SelfRolePanelRepositoryTest.java`

**Interfaces:**
- Consumes: `PostgresPool`, tabelas `self_role_panels`/`self_role_options` (Task 1), records `SelfRolePanel`/`SelfRolePanel.Option` (inalterados).
- Produces: assinaturas inalteradas — `createPanel`, `updatePanel`, `setOptions`, `setPublished`, `delete`, `find`, `list`.

- [ ] **Step 1: Reescrever o repo para Postgres**

Replace `SelfRolePanelRepository.java` body. Diferenças vs SQLite: `PostgresPool`; `unique_choice` é `BOOLEAN` (`setBoolean`/`getBoolean`); `list` filtra `enabled = true`; `delete` vira soft-delete (`UPDATE ... SET enabled=false` no painel; as opções permanecem):

```java
package dev.davimf.basebot.modules.base.selfroles;

import dev.davimf.basebot.database.postgres.PostgresPool;
import dev.davimf.basebot.database.postgres.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Postgres store para painéis de self-role (config editável pelo dashboard). */
public final class SelfRolePanelRepository {

    private final PostgresPool pool;

    public SelfRolePanelRepository(PostgresPool pool) {
        this.pool = pool;
    }

    public String createPanel(String guildId, String title, String description, String style, boolean unique) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO self_role_panels (id, guild_id, title, description, style, unique_choice) "
                     + "VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, guildId);
            ps.setString(3, title);
            ps.setString(4, description);
            ps.setString(5, style);
            ps.setBoolean(6, unique);
            ps.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new RepositoryException("create self-role panel for " + guildId, e);
        }
    }

    public void updatePanel(String id, String title, String description, String style, boolean unique) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE self_role_panels SET title=?, description=?, style=?, unique_choice=? WHERE id=?")) {
            ps.setString(1, title);
            ps.setString(2, description);
            ps.setString(3, style);
            ps.setBoolean(4, unique);
            ps.setString(5, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("update self-role panel " + id, e);
        }
    }

    public void setOptions(String panelId, List<SelfRolePanel.Option> options) {
        try (Connection c = pool.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement del = c.prepareStatement("DELETE FROM self_role_options WHERE panel_id=?")) {
                    del.setString(1, panelId);
                    del.executeUpdate();
                }
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO self_role_options (panel_id, role_id, label, emoji, position) VALUES (?,?,?,?,?)")) {
                    for (SelfRolePanel.Option o : options) {
                        ins.setString(1, panelId);
                        ins.setString(2, o.roleId());
                        ins.setString(3, o.label());
                        ins.setString(4, o.emoji());
                        ins.setInt(5, o.position());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            throw new RepositoryException("set options for panel " + panelId, e);
        }
    }

    public void setPublished(String panelId, String channelId, String messageId) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE self_role_panels SET channel_id=?, message_id=? WHERE id=?")) {
            ps.setString(1, channelId);
            ps.setString(2, messageId);
            ps.setString(3, panelId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("publish panel " + panelId, e);
        }
    }

    public void delete(String panelId) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE self_role_panels SET enabled = false WHERE id=?")) {
            ps.setString(1, panelId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("delete panel " + panelId, e);
        }
    }

    public Optional<SelfRolePanel> find(String id) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM self_role_panels WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(c, rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("find panel " + id, e);
        }
    }

    public List<SelfRolePanel> list(String guildId) {
        List<SelfRolePanel> out = new ArrayList<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM self_role_panels WHERE guild_id=? AND enabled = true ORDER BY created_at")) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(c, rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list panels for " + guildId, e);
        }
    }

    private static SelfRolePanel map(Connection c, ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        return new SelfRolePanel(id, rs.getString("guild_id"), rs.getString("title"),
                rs.getString("description"), rs.getString("style"), rs.getBoolean("unique_choice"),
                rs.getString("channel_id"), rs.getString("message_id"), loadOptions(c, id));
    }

    private static List<SelfRolePanel.Option> loadOptions(Connection c, String panelId) throws SQLException {
        List<SelfRolePanel.Option> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT role_id, label, emoji, position FROM self_role_options WHERE panel_id=? ORDER BY position")) {
            ps.setString(1, panelId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new SelfRolePanel.Option(rs.getString("role_id"), rs.getString("label"),
                            rs.getString("emoji"), rs.getInt("position")));
                }
            }
        }
        return out;
    }
}
```

> Nota: `delete` era hard-delete (removia painel + opções). Vira soft-delete (`enabled=false`) — comportamento visível preservado porque `list()` filtra `enabled=true` e `find()` continua achando pra republicação. As opções ficam (histórico); ao recriar, `createPanel` gera novo id.

- [ ] **Step 2: Atualizar os 2 call sites**

Modify `SetupComponentHandler.java:719` e `SelfRoleComponentHandler.java:97`:

```java
        return new SelfRolePanelRepository(ctx.database().postgres());
```

- [ ] **Step 3: Remover o teste SQLite obsoleto**

Delete `src/test/java/dev/davimf/basebot/modules/base/selfroles/SelfRolePanelRepositoryTest.java`.

- [ ] **Step 4: Compilar + suíte**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(selfroles): panels backed by Postgres + soft-delete"
```

---

## Task 6: `ActionTypeRepository` → Postgres (move de pacote)

**Files:**
- Create: `src/main/java/dev/davimf/basebot/database/postgres/ActionTypeRepository.java`
- Delete: `src/main/java/dev/davimf/basebot/database/sqlite/ActionTypeRepository.java`
- Modify: `src/main/java/dev/davimf/basebot/database/DatabaseManager.java` (import + construção :88)

**Interfaces:**
- Consumes: `PostgresPool`, tabela `fac_action_types` (Task 1).
- Produces: `ActionTypeRepository(PostgresPool)`; record aninhado `ActionType(String id, String guildId, String name, int maxContingent, int minContingent, int dirtyMoney)` (inalterado); métodos `listByGuild`, `find`, `count`, `upsert`, `delete`, `static String newId()` (inalterados).

- [ ] **Step 1: Criar a versão Postgres no pacote `postgres`**

Create `src/main/java/dev/davimf/basebot/database/postgres/ActionTypeRepository.java` (mesmo corpo, novo pacote, `PostgresPool`; `excluded`→`EXCLUDED`):

```java
package dev.davimf.basebot.database.postgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Postgres store for saved action types (BOTSPECS Module 4), config editável pelo dashboard.
 * Managed in {@code /setup → Ações} and consumed by {@code /painel-acoes}.
 */
public final class ActionTypeRepository {

    /** A saved action type. */
    public record ActionType(String id, String guildId, String name,
                             int maxContingent, int minContingent, int dirtyMoney) {}

    private final PostgresPool pool;

    public ActionTypeRepository(PostgresPool pool) {
        this.pool = pool;
    }

    public List<ActionType> listByGuild(String guildId) {
        String sql = "SELECT * FROM fac_action_types WHERE guild_id = ? ORDER BY name";
        List<ActionType> out = new ArrayList<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list action types " + guildId, e);
        }
    }

    public Optional<ActionType> find(String id) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM fac_action_types WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("find action type " + id, e);
        }
    }

    public int count(String guildId) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM fac_action_types WHERE guild_id = ?")) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RepositoryException("count action types " + guildId, e);
        }
    }

    public void upsert(ActionType type) {
        String sql = """
                INSERT INTO fac_action_types
                    (id, guild_id, name, max_contingent, min_contingent, dirty_money)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    name           = EXCLUDED.name,
                    max_contingent = EXCLUDED.max_contingent,
                    min_contingent = EXCLUDED.min_contingent,
                    dirty_money    = EXCLUDED.dirty_money
                """;
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, type.id());
            ps.setString(2, type.guildId());
            ps.setString(3, type.name());
            ps.setInt(4, type.maxContingent());
            ps.setInt(5, type.minContingent());
            ps.setInt(6, type.dirtyMoney());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("upsert action type " + type.id(), e);
        }
    }

    public void delete(String id) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM fac_action_types WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("delete action type " + id, e);
        }
    }

    private static ActionType map(ResultSet rs) throws SQLException {
        return new ActionType(rs.getString("id"), rs.getString("guild_id"), rs.getString("name"),
                rs.getInt("max_contingent"), rs.getInt("min_contingent"), rs.getInt("dirty_money"));
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
```

- [ ] **Step 2: Deletar a versão SQLite e ajustar o `DatabaseManager`**

Delete `src/main/java/dev/davimf/basebot/database/sqlite/ActionTypeRepository.java`.

In `DatabaseManager.java`: trocar o import `dev.davimf.basebot.database.sqlite.ActionTypeRepository` por `dev.davimf.basebot.database.postgres.ActionTypeRepository`, e a linha :88:

```java
        this.actionTypes = new ActionTypeRepository(postgres);
```

- [ ] **Step 3: Ajustar imports dos consumidores**

Qualquer classe que importava `dev.davimf.basebot.database.sqlite.ActionTypeRepository` (ou `ActionTypeRepository.ActionType`) passa a importar de `...database.postgres.ActionTypeRepository`. Localizar:

Run: `./gradlew compileJava` e corrigir os imports que o compilador apontar (o record `ActionType` e `newId()` continuam com a mesma forma; só muda o pacote).

- [ ] **Step 4: Compilar + suíte**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(facs): action_types backed by Postgres (moved to postgres package)"
```

---

## Task 7: `ShopItemRepository` → Postgres (config-only) + split do `sold`

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/economy/ShopItem.java` (add `withSold`)
- Modify: `src/main/java/dev/davimf/basebot/modules/base/economy/ShopItemRepository.java` (Postgres, sem `sold`/reserve, soft-delete)
- Modify: `src/main/java/dev/davimf/basebot/modules/base/economy/ShopService.java` (injeta `ShopStockRepository`; `catalog` preenche sold; `buy`/`refund` usam reserve com limite)
- Test: `src/test/java/dev/davimf/basebot/modules/base/economy/ShopItemSoldTest.java`
- Delete: `src/test/java/dev/davimf/basebot/modules/base/economy/ShopItemRepositoryTest.java`

**Interfaces:**
- Consumes: `PostgresPool`, `ShopStockRepository` (Task 2), tabela `shop_items` (Task 1), record `ShopItem`.
- Produces:
  - `ShopItem.withSold(int newSold)` → cópia com `sold` trocado.
  - `ShopItemRepository(PostgresPool)`: `long insert(ShopItem)`, `List<ShopItem> list(String)` (só `enabled`, `sold=0`), `int count(String)`, `ShopItem find(String,long)` (sem filtro enabled, `sold=0`), `boolean delete(String,long)` (soft). **Sem** `reserveStock`/`releaseStock`.

- [ ] **Step 1: Teste da lógica pura de `sold`**

Create `ShopItemSoldTest.java`:

```java
package dev.davimf.basebot.modules.base.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShopItemSoldTest {

    private ShopItem item(Integer stock, int sold) {
        return new ShopItem(1L, "g", ShopItem.Type.ROLE_PERM, "r", "n", "d",
                100L, null, stock, null, sold, 0L);
    }

    @Test
    void withSoldReplacesCounterOnly() {
        ShopItem base = item(5, 0);
        ShopItem filled = base.withSold(3);
        assertEquals(3, filled.sold());
        assertEquals(base.id(), filled.id());
        assertEquals(base.stock(), filled.stock());
        assertEquals(2, filled.remaining());
        assertFalse(filled.soldOut());
    }

    @Test
    void soldOutWhenFinite() {
        assertTrue(item(2, 2).soldOut());
        assertEquals(0, item(2, 2).remaining());
    }

    @Test
    void unlimitedNeverSoldOut() {
        assertFalse(item(null, 999).soldOut());
        assertEquals(Integer.MAX_VALUE, item(null, 999).remaining());
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.economy.ShopItemSoldTest"`
Expected: FAIL — `withSold` não existe.

- [ ] **Step 3: Adicionar `withSold` ao record**

Modify `ShopItem.java` (adicionar método; record inalterado):

```java
    /** Cópia com o contador {@code sold} substituído (preenchido a partir do shop_stock). */
    public ShopItem withSold(int newSold) {
        return new ShopItem(id, guildId, type, roleId, name, description,
                price, durationS, stock, perUser, newSold, createdAt);
    }
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.economy.ShopItemSoldTest"`
Expected: PASS.

- [ ] **Step 5: Reescrever `ShopItemRepository` para Postgres (config-only)**

Replace `ShopItemRepository.java` body:

```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.postgres.PostgresPool;
import dev.davimf.basebot.database.postgres.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Postgres store dos itens da loja (config). O contador vendido fica no SQLite ({@link ShopStockRepository}). */
public final class ShopItemRepository {

    private final PostgresPool pool;

    public ShopItemRepository(PostgresPool pool) { this.pool = pool; }

    public long insert(ShopItem it) {
        String sql = "INSERT INTO shop_items (guild_id, type, role_id, name, description, price, "
                + "duration_s, stock, per_user, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, it.guildId());
            ps.setString(2, it.type().name());
            ps.setString(3, it.roleId());
            ps.setString(4, it.name());
            ps.setString(5, it.description());
            ps.setLong(6, it.price());
            setNullableLong(ps, 7, it.durationS());
            setNullableInt(ps, 8, it.stock());
            setNullableInt(ps, 9, it.perUser());
            ps.setLong(10, it.createdAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new RepositoryException("insert shop item " + it.guildId(), e);
        }
    }

    /** Itens ativos (enabled) do servidor. {@code sold} vem zerado — preencher via ShopStockRepository. */
    public List<ShopItem> list(String g) {
        List<ShopItem> out = new ArrayList<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM shop_items WHERE guild_id=? AND enabled = true ORDER BY price, id")) {
            ps.setString(1, g);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list shop items " + g, e);
        }
    }

    public int count(String g) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM shop_items WHERE guild_id=? AND enabled = true")) {
            ps.setString(1, g);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("count shop items " + g, e);
        }
    }

    /** Busca por id (sem filtrar enabled — compras/expiração precisam achar item desativado). */
    public ShopItem find(String g, long id) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM shop_items WHERE guild_id=? AND id=?")) {
            ps.setString(1, g);
            ps.setLong(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new RepositoryException("find shop item " + g + "/" + id, e);
        }
    }

    /** Soft-delete: desativa o item (histórico de compras continua válido). */
    public boolean delete(String g, long id) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE shop_items SET enabled = false WHERE guild_id=? AND id=? AND enabled = true")) {
            ps.setString(1, g);
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("delete shop item " + g + "/" + id, e);
        }
    }

    private static ShopItem map(ResultSet rs) throws SQLException {
        return new ShopItem(
                rs.getLong("id"),
                rs.getString("guild_id"),
                ShopItem.Type.valueOf(rs.getString("type")),
                rs.getString("role_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getLong("price"),
                nullableLong(rs, "duration_s"),
                nullableInt(rs, "stock"),
                nullableInt(rs, "per_user"),
                0,                       // sold preenchido pelo ShopStockRepository na camada de serviço
                rs.getLong("created_at"));
    }

    private static void setNullableLong(PreparedStatement ps, int i, Long v) throws SQLException {
        if (v == null) { ps.setNull(i, java.sql.Types.BIGINT); } else { ps.setLong(i, v); }
    }

    private static void setNullableInt(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) { ps.setNull(i, java.sql.Types.INTEGER); } else { ps.setInt(i, v); }
    }

    private static Long nullableLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private static Integer nullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }
}
```

- [ ] **Step 6: Ligar `ShopService` ao `ShopStockRepository`**

Modify `ShopService.java`:

1. Campos + construtor (linha ~18-27):

```java
    private final BotContext ctx;
    private final ShopItemRepository items;
    private final ShopStockRepository stock;
    private final ShopPurchaseRepository purchases;
    private final WalletRepository wallets;

    public ShopService(BotContext ctx) {
        this.ctx = ctx;
        this.items = new ShopItemRepository(ctx.database().postgres());
        this.stock = new ShopStockRepository(ctx.database().sqlite());
        this.purchases = new ShopPurchaseRepository(ctx.database().sqlite());
        this.wallets = new WalletRepository(ctx.database().sqlite());
    }
```

2. `catalog` preenche `sold` (linha 33):

```java
    public List<ShopItem> catalog(Guild g) {
        List<ShopItem> out = new ArrayList<>();
        for (ShopItem it : items.list(g.getId())) {
            out.add(it.withSold(stock.soldOf(it.id())));
        }
        return out;
    }
```

E adicionar `import java.util.ArrayList;` no topo.

3. Reserva/estorno em `buy` (linhas 88 e 92) passam o limite `item.stock()`:

```java
        // Reservar estoque → debitar → entregar.
        if (!stock.reserveStock(itemId, item.stock())) {
            return "Item esgotado.";
        }
        if (!wallets.tryDebitCash(gid, uid, item.price())) {
            stock.releaseStock(itemId);
            return "Saldo insuficiente na carteira.";
        }
```

4. `refund` (linha 127):

```java
        stock.releaseStock(item.id());
```

- [ ] **Step 7: Remover o teste SQLite obsoleto**

Delete `src/test/java/dev/davimf/basebot/modules/base/economy/ShopItemRepositoryTest.java` (dependia de SQLite + coluna `sold` no `shop_items`).

- [ ] **Step 8: Compilar + suíte**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. Conferir que nenhum outro ponto chama `items.reserveStock`/`items.releaseStock` (o compilador acusa se sim → migrar pra `stock.`).

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "refactor(economy): shop_items config on Postgres; sold via shop_stock"
```

---

## Task 8: Script de migração de dados SQLite → Postgres

**Files:**
- Create: `src/main/java/dev/davimf/basebot/database/postgres/ConfigMigrationTool.java`

**Interfaces:**
- Consumes: `SqliteManager` (fonte), `PostgresPool` (destino), `SqliteManager` (destino `shop_stock`).
- Produces: `static Result migrate(SqliteManager sqlite, PostgresPool pg)` → contagens por tabela; idempotente (upsert por PK); ajusta a sequence do `shop_items`; move `sold`→`shop_stock`.

- [ ] **Step 1: Implementar a ferramenta**

Create `ConfigMigrationTool.java`. Copia as 5 tabelas preservando ids (upsert `ON CONFLICT DO NOTHING` → idempotente), seed do `shop_stock` com o `sold` atual, e `setval` da identity do `shop_items`:

```java
package dev.davimf.basebot.database.postgres;

import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * One-shot, idempotent copy of the 5 config tables from local SQLite to Postgres/Neon.
 * Preserves ids (operational SQLite tables reference them), seeds shop_stock with the current
 * sold counters, and advances the shop_items identity sequence. Run with the bot stopped.
 * Re-running is safe: rows already present are skipped (ON CONFLICT DO NOTHING).
 */
public final class ConfigMigrationTool {

    public record Result(int panels, int options, int levelRewards, int quiz, int shopItems, int actionTypes) {}

    private ConfigMigrationTool() {}

    public static Result migrate(SqliteManager sqlite, PostgresPool pg) {
        try (Connection s = sqlite.getConnection(); Connection p = pg.getConnection()) {
            boolean prevAuto = p.getAutoCommit();
            p.setAutoCommit(false);
            try {
                int panels = copyPanels(s, p);
                int options = copyOptions(s, p);
                int rewards = copyLevelRewards(s, p);
                int quiz = copyQuiz(s, p);
                int shop = copyShopItems(s, p);        // also seeds shop_stock in SQLite
                seedShopStock(s);
                advanceShopSequence(p);
                int types = copyActionTypes(s, p);
                p.commit();
                return new Result(panels, options, rewards, quiz, shop, types);
            } catch (SQLException e) {
                p.rollback();
                throw e;
            } finally {
                p.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            throw new RepositoryException("config migration", e);
        }
    }

    private static int copyPanels(Connection s, Connection p) throws SQLException {
        String ins = "INSERT INTO self_role_panels "
                + "(id, guild_id, title, description, style, unique_choice, channel_id, message_id) "
                + "VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING";
        int n = 0;
        try (PreparedStatement rd = s.prepareStatement("SELECT * FROM self_role_panels");
             ResultSet rs = rd.executeQuery();
             PreparedStatement w = p.prepareStatement(ins)) {
            while (rs.next()) {
                w.setString(1, rs.getString("id"));
                w.setString(2, rs.getString("guild_id"));
                w.setString(3, rs.getString("title"));
                w.setString(4, rs.getString("description"));
                w.setString(5, rs.getString("style"));
                w.setBoolean(6, rs.getInt("unique_choice") == 1);
                w.setString(7, rs.getString("channel_id"));
                w.setString(8, rs.getString("message_id"));
                n += w.executeUpdate();
            }
        }
        return n;
    }

    private static int copyOptions(Connection s, Connection p) throws SQLException {
        String ins = "INSERT INTO self_role_options (panel_id, role_id, label, emoji, position) "
                + "VALUES (?,?,?,?,?) ON CONFLICT (panel_id, role_id) DO NOTHING";
        int n = 0;
        try (PreparedStatement rd = s.prepareStatement("SELECT * FROM self_role_options");
             ResultSet rs = rd.executeQuery();
             PreparedStatement w = p.prepareStatement(ins)) {
            while (rs.next()) {
                w.setString(1, rs.getString("panel_id"));
                w.setString(2, rs.getString("role_id"));
                w.setString(3, rs.getString("label"));
                w.setString(4, rs.getString("emoji"));
                w.setInt(5, rs.getInt("position"));
                n += w.executeUpdate();
            }
        }
        return n;
    }

    private static int copyLevelRewards(Connection s, Connection p) throws SQLException {
        String ins = "INSERT INTO level_rewards (guild_id, level, role_id) VALUES (?,?,?) "
                + "ON CONFLICT (guild_id, level) DO NOTHING";
        int n = 0;
        try (PreparedStatement rd = s.prepareStatement("SELECT * FROM level_rewards");
             ResultSet rs = rd.executeQuery();
             PreparedStatement w = p.prepareStatement(ins)) {
            while (rs.next()) {
                w.setString(1, rs.getString("guild_id"));
                w.setInt(2, rs.getInt("level"));
                w.setString(3, rs.getString("role_id"));
                n += w.executeUpdate();
            }
        }
        return n;
    }

    private static int copyQuiz(Connection s, Connection p) throws SQLException {
        String ins = "INSERT INTO quiz_questions (id, guild_id, question, correct, wrong1, wrong2, wrong3) "
                + "VALUES (?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING";
        int n = 0;
        try (PreparedStatement rd = s.prepareStatement("SELECT * FROM quiz_questions");
             ResultSet rs = rd.executeQuery();
             PreparedStatement w = p.prepareStatement(ins)) {
            while (rs.next()) {
                w.setString(1, rs.getString("id"));
                w.setString(2, rs.getString("guild_id"));
                w.setString(3, rs.getString("question"));
                w.setString(4, rs.getString("correct"));
                w.setString(5, rs.getString("wrong1"));
                w.setString(6, rs.getString("wrong2"));
                w.setString(7, rs.getString("wrong3"));
                n += w.executeUpdate();
            }
        }
        return n;
    }

    private static int copyShopItems(Connection s, Connection p) throws SQLException {
        String ins = "INSERT INTO shop_items "
                + "(id, guild_id, type, role_id, name, description, price, duration_s, stock, per_user, created_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING";
        int n = 0;
        try (PreparedStatement rd = s.prepareStatement("SELECT * FROM shop_items");
             ResultSet rs = rd.executeQuery();
             PreparedStatement w = p.prepareStatement(ins)) {
            while (rs.next()) {
                w.setLong(1, rs.getLong("id"));
                w.setString(2, rs.getString("guild_id"));
                w.setString(3, rs.getString("type"));
                w.setString(4, rs.getString("role_id"));
                w.setString(5, rs.getString("name"));
                w.setString(6, rs.getString("description"));
                w.setLong(7, rs.getLong("price"));
                setNullableLong(w, 8, rs, "duration_s");
                setNullableInt(w, 9, rs, "stock");
                setNullableInt(w, 10, rs, "per_user");
                w.setLong(11, rs.getLong("created_at"));
                n += w.executeUpdate();
            }
        }
        return n;
    }

    /** Seed shop_stock (SQLite) com o sold atual da própria shop_items do SQLite. Idempotente. */
    private static void seedShopStock(Connection s) throws SQLException {
        try (PreparedStatement st = s.prepareStatement(
                "INSERT OR IGNORE INTO shop_stock (item_id, sold) SELECT id, sold FROM shop_items")) {
            st.executeUpdate();
        }
    }

    /** Avança a identity da shop_items no Postgres pro max(id)+1 pra novos inserts não colidirem. */
    private static void advanceShopSequence(Connection p) throws SQLException {
        try (PreparedStatement st = p.prepareStatement(
                "SELECT setval(pg_get_serial_sequence('shop_items','id'), "
                + "COALESCE((SELECT MAX(id) FROM shop_items), 0) + 1, false)")) {
            st.executeQuery();
        }
    }

    private static int copyActionTypes(Connection s, Connection p) throws SQLException {
        String ins = "INSERT INTO fac_action_types "
                + "(id, guild_id, name, max_contingent, min_contingent, dirty_money) "
                + "VALUES (?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING";
        int n = 0;
        try (PreparedStatement rd = s.prepareStatement("SELECT * FROM fac_action_types");
             ResultSet rs = rd.executeQuery();
             PreparedStatement w = p.prepareStatement(ins)) {
            while (rs.next()) {
                w.setString(1, rs.getString("id"));
                w.setString(2, rs.getString("guild_id"));
                w.setString(3, rs.getString("name"));
                w.setInt(4, rs.getInt("max_contingent"));
                w.setInt(5, rs.getInt("min_contingent"));
                w.setInt(6, rs.getInt("dirty_money"));
                n += w.executeUpdate();
            }
        }
        return n;
    }

    private static void setNullableLong(PreparedStatement w, int i, ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        if (rs.wasNull()) { w.setNull(i, java.sql.Types.BIGINT); } else { w.setLong(i, v); }
    }

    private static void setNullableInt(PreparedStatement w, int i, ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        if (rs.wasNull()) { w.setNull(i, java.sql.Types.INTEGER); } else { w.setInt(i, v); }
    }
}
```

- [ ] **Step 2: Ponto de entrada de execução**

A ferramenta é chamada uma vez, manualmente, com o bot parado. Adicionar um gatilho pontual (ex.: uma flag de arg em `BotApplication` `--migrate-config`, ou um `main` dedicado) que instancia `SqliteManager`/`PostgresPool` a partir do `BotConfig` e chama `ConfigMigrationTool.migrate(...)`, logando o `Result`. Seguir o padrão de bootstrap existente em `BotApplication` para carregar `BotConfig`. (Manter mínimo; é operacional, não faz parte do fluxo normal.)

- [ ] **Step 3: Compilar**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Verificação manual (com dados reais)**

Com o bot parado e o schema 004 aplicado:
1. Contar linhas no SQLite (fonte) por tabela: `self_role_panels`, `self_role_options`, `level_rewards`, `quiz_questions`, `shop_items`, `fac_action_types`.
2. Rodar a migração; conferir que o `Result` bate com as contagens da fonte.
3. Rodar **de novo**; conferir que o `Result` volta zeros (nada duplicado — `ON CONFLICT DO NOTHING`).
4. Conferir `SELECT COUNT(*)` no Postgres = contagem da fonte, por tabela.
5. Conferir `shop_stock` (SQLite): `sold` por item bate com o `shop_items.sold` original.
6. Inserir um item novo pela loja (`/setup`) e conferir que o id gerado é `> max(id migrado)` (sequence avançada).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(db): id-preserving SQLite->Postgres config migration tool"
```

---

## Smoke final (após todas as tasks)

Com o schema aplicado e os dados migrados, num servidor de teste:
- **Self-roles:** `/setup → Auto-cargos` cria/edita/publica um painel; conferir que aparece e o toggle de cargo funciona; "apagar" some da lista (soft-delete) mas a mensagem publicada ainda pode ser reeditada.
- **Nível:** definir um cargo por nível; subir de nível concede o cargo.
- **Quiz:** adicionar pergunta; `/quiz` sorteia entre as do servidor.
- **Loja:** criar item com estoque finito; comprar até esgotar (`Item esgotado.`); conferir `shop_stock.sold`; item CUSTOM registra em `log-loja`; renovação de ROLE_TEMP estende.
- **Ações (facs):** `/setup → Ações` cria/edita/remove um tipo; `/painel-acoes` usa.

---

## Self-Review (executado ao escrever o plano)

**1. Cobertura do spec (Parte A, itens do A1):**
- DDL das 5 tabelas → Task 1. ✅
- Repos JDBC → Tasks 3–7. ✅
- `shop_stock` no SQLite → Task 2. ✅
- Migração preservando ids + `setval` + `sold`→`shop_stock` → Task 8. ✅
- Soft-delete (`enabled`) shop_items/self_roles → Tasks 5, 7 + DDL Task 1. ✅
- `is_unique`/`unique_choice` (nome seguro, BOOLEAN) → Task 1 + Task 5. ✅
- IDs como TEXT → Task 1 (constraint global). ✅
- *(Fora do A1, nas próximas: snapshots/multi-tenant = A2; cache/reconcile/merge-safe/dashboard_access = A3.)*

**2. Placeholders:** nenhum "TBD/TODO"; todo passo de código traz o código. Os 2 pontos que dependem de convenção local (assinatura de `SqliteManager`/`BotConfig.Sqlite` no `setUp` do teste — Task 2; ponto de entrada da migração — Task 8) trazem instrução explícita de alinhar ao padrão existente, não placeholder de lógica.

**3. Consistência de tipos:** `ShopItem` mantém 12 componentes (inclui `sold`); Postgres `map()` seta `sold=0`; `withSold` preenche; `ShopStockRepository.reserveStock(long, Integer)` recebe `item.stock()` (`Integer`). `ShopItemRepository` perde `reserveStock`/`releaseStock` (migraram pro stock repo) — Task 7 Step 8 confere via compilador que não há chamador órfão. `ActionType`/`QuizQuestion`/`SelfRolePanel` records inalterados.

**Nota de decisão pendente pro dono:** a remoção dos 4 testes SQLite (sem harness Postgres) é o único ponto de perda de cobertura — decidir se quer o harness H2-PG antes de executar (ver "Decisão de teste").
