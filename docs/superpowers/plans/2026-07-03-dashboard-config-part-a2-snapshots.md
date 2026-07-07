# Parte A2 — Snapshots Discord + multi-tenant Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cada instância do bot publica no Neon snapshots mínimos das suas guilds (canais/cargos/permissões) para o dashboard validar canais/cargos **sem** token de bot, com isolamento multi-tenant (um bot só escreve guilds do seu `bot_instance_id`).

**Architecture:** DDL Postgres das 4 tabelas de snapshot (`bot_instances`, `bot_guilds`, `guild_channels_snapshot`, `guild_roles_snapshot`). Um `SnapshotRepository` (JDBC/Postgres, padrão dos repos existentes) com guards multi-tenant no `WHERE`. Um `GuildSnapshotSync` (serviço) mapeia objetos JDA → linhas e escreve via repo. Um `GuildSnapshotListener` (ListenerAdapter) atualiza incrementalmente em join/leave e eventos de canal/cargo (debounce por-guild). `BaseModule` faz o wiring: upsert de `bot_instances` + full-sync no boot + sync periódico (<10 min), e registra o listener.

**Tech Stack:** Java 22, JDBC (`org.postgresql:postgresql`), HikariCP (`PostgresPool`), JDA (eventos + permissões), JUnit 5.

## Decisão de teste (mesma do A1)

Sem harness Postgres no projeto. `SnapshotRepository`/`GuildSnapshotSync`/listener são verificados por `./gradlew build` + smoke. Lógica pura extraída (ex.: `GuildSnapshotSync.botCanAssign(...)`) tem teste JUnit. Aplicação do schema no Neon e verificação com dados reais = **manual** (como no A1).

## Global Constraints

- **Isolamento multi-tenant:** o bot usa **sempre** o `BOT_INSTANCE_ID` do `.env` (nunca id de payload). Toda escrita de `bot_guilds` é guardada por `bot_instance_id` no `WHERE`/`ON CONFLICT`; escritas de channels/roles só ocorrem se a guild pertence a este `bot_instance_id` (guard `EXISTS bot_guilds`). Contrato: `docs/superpowers/dashboard-config-schema-contract.md` §Segurança multi-tenant.
- **IDs do Discord são `TEXT`** (`guild_id`, `channel_id`, `role_id`, `owner_id`, `bot_user_id`, `application_id`). `bot_instances.id`/`bot_guilds.bot_instance_id` são `uuid` (identidade da instância, não snowflake) — passar como `?::uuid`.
- **Frescor:** o sync periódico roda em intervalo **< 10 min** (usar 300 s) pra manter `updated_at`/`last_seen_at` frescos (limiares do contrato: aviso >10 min, bloqueio de write crítico >15 min — usados pela Parte B).
- **Snapshots são só-escrita-pelo-bot / só-leitura-pelo-dashboard.** Nenhum token de bot vai pro dashboard.
- **Sem harness Postgres** — ver "Decisão de teste".
- **Aplicação do schema (005) no Neon é manual** via a tool `dev.davimf.basebot.tools.ApplyPostgresSchema` (idempotente), fora do boot (como o A1).
- Spec: `docs/superpowers/specs/2026-07-02-dashboard-config-integration-design.md` §5.

## File Structure

- `src/main/resources/db/postgres/005_snapshots.sql` — **novo**: DDL das 4 tabelas.
- `src/main/java/.../database/postgres/PostgresMigrator.java` — registra o 005.
- `src/main/java/.../config/BotConfig.java` — novo record `Instance(instanceId, clientName)` + componente no `BotConfig`.
- `src/main/java/.../config/ConfigLoader.java` — carrega `BOT_INSTANCE_ID`/`BOT_CLIENT_NAME`.
- `src/main/java/.../database/postgres/SnapshotRepository.java` — **novo** (JDBC): upserts + replace + guards.
- `src/main/java/.../modules/base/snapshot/GuildSnapshotSync.java` — **novo**: mapeia JDA→linhas, sync por-guild/all.
- `src/test/java/.../modules/base/snapshot/GuildSnapshotSyncTest.java` — **novo**: teste da lógica pura `botCanAssign`.
- `src/main/java/.../modules/base/snapshot/GuildSnapshotListener.java` — **novo**: ListenerAdapter incremental (debounce).
- `src/main/java/.../modules/base/BaseModule.java` — wiring (register listener + onReady).
- `.env.example` / `config.example.yml` — documentar `BOT_INSTANCE_ID`/`BOT_CLIENT_NAME`.

---

## Task 1: DDL Postgres das 4 tabelas de snapshot

**Files:**
- Create: `src/main/resources/db/postgres/005_snapshots.sql`
- Modify: `src/main/java/dev/davimf/basebot/database/postgres/PostgresMigrator.java` (MIGRATIONS)

**Interfaces:**
- Produces: tabelas `bot_instances`, `bot_guilds`, `guild_channels_snapshot`, `guild_roles_snapshot`.

- [ ] **Step 1: Escrever a DDL**

Create `src/main/resources/db/postgres/005_snapshots.sql`:

```sql
-- Snapshots that each bot instance publishes so the dashboard can validate channels/roles
-- without a bot token. Written only by the bot; read by the dashboard. Idempotent.

CREATE TABLE IF NOT EXISTS bot_instances (
    id             UUID PRIMARY KEY,
    client_name    TEXT,
    bot_user_id    TEXT NOT NULL,
    application_id TEXT,
    active         BOOLEAN NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS bot_guilds (
    guild_id        TEXT PRIMARY KEY,
    bot_instance_id UUID NOT NULL REFERENCES bot_instances(id),
    guild_name      TEXT,
    owner_id        TEXT,
    bot_present     BOOLEAN NOT NULL DEFAULT true,
    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_bot_guilds_instance ON bot_guilds (bot_instance_id);

CREATE TABLE IF NOT EXISTS guild_channels_snapshot (
    guild_id     TEXT NOT NULL,
    channel_id   TEXT NOT NULL,
    name         TEXT,
    type         TEXT NOT NULL,
    parent_id    TEXT,
    position     INT,
    bot_can_view BOOLEAN NOT NULL DEFAULT false,
    bot_can_send BOOLEAN NOT NULL DEFAULT false,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (guild_id, channel_id)
);

CREATE TABLE IF NOT EXISTS guild_roles_snapshot (
    guild_id       TEXT NOT NULL,
    role_id        TEXT NOT NULL,
    name           TEXT,
    position       INT,
    managed        BOOLEAN NOT NULL DEFAULT false,
    bot_can_assign BOOLEAN NOT NULL DEFAULT false,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (guild_id, role_id)
);
```

- [ ] **Step 2: Registrar no migrator**

Modify `PostgresMigrator.MIGRATIONS` — append as the 5th entry (nunca reordenar):

```java
    public static final List<String> MIGRATIONS = List.of(
            "/db/postgres/001_guild_config.sql",
            "/db/postgres/002_guild_settings.sql",
            "/db/postgres/003_ticket_categories.sql",
            "/db/postgres/004_config_tables.sql",
            "/db/postgres/005_snapshots.sql"
    );
```

- [ ] **Step 3: Compilar**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Aplicar no Neon (manual)**

`java -cp build/libs/basebot.jar dev.davimf.basebot.tools.ApplyPostgresSchema` (após `./gradlew shadowJar`). Confirmar `Aplicada: /db/postgres/005_snapshots.sql`; 2ª execução não faz nada.

---

## Task 2: Config — `BOT_INSTANCE_ID` / `BOT_CLIENT_NAME`

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/config/BotConfig.java`
- Modify: `src/main/java/dev/davimf/basebot/config/ConfigLoader.java`
- Modify: `.env.example`, `config.example.yml`

**Interfaces:**
- Produces: `BotConfig.Instance(String instanceId, String clientName)` com `boolean hasInstance()`; `ctx.config().instance()`.

- [ ] **Step 1: Adicionar o record `Instance` ao `BotConfig`**

In `BotConfig.java`, add `Instance instance` as a component and the nested record:

```java
public record BotConfig(
        Discord discord,
        Postgres postgres,
        Sqlite sqlite,
        Tickets tickets,
        RateLimit rateLimit,
        Instance instance
) {

    /** Identidade da instância deste bot (1 bot por cliente) — usada para os snapshots. */
    public record Instance(String instanceId, String clientName) {
        /** True quando um BOT_INSTANCE_ID (uuid) está configurado; sem ele os snapshots ficam off. */
        public boolean hasInstance() {
            return instanceId != null && !instanceId.isBlank();
        }
    }
```

(Manter os demais records `Discord`/`Postgres`/`Sqlite`/`Tickets`/`RateLimit` inalterados; só adicionar o componente `instance` e o record `Instance`.)

- [ ] **Step 2: Carregar no `ConfigLoader`**

In `ConfigLoader.load(Path file)`, add `JsonNode instance = root.path("instance");` near the other `root.path(...)` reads, and append the `Instance` to the `new BotConfig(...)` call as the last argument:

```java
                new BotConfig.RateLimit(
                        envLong(dotenv, "EMBED_DEBOUNCE_MS", longVal(rl, "embedDebounceMillis", 5_000L)),
                        envInt(dotenv, "GHOST_PING_BATCH", intVal(rl, "ghostPingBatchSize", 5)),
                        envInt(dotenv, "GHOST_PING_INTERVAL_S", intVal(rl, "ghostPingBatchIntervalSeconds", 30))
                ),
                new BotConfig.Instance(
                        env(dotenv, "BOT_INSTANCE_ID", str(instance, "instanceId", null)),
                        env(dotenv, "BOT_CLIENT_NAME", str(instance, "clientName", null))
                )
        );
```

- [ ] **Step 3: Verificar que só o `ConfigLoader` constrói `BotConfig`**

Run: `grep -rn "new BotConfig(" src` — deve listar **apenas** `ConfigLoader`. Se algum teste construir `new BotConfig(...)`, adicionar o novo argumento lá também.

- [ ] **Step 4: Documentar as env vars**

In `.env.example`, add:

```
# Snapshots do dashboard (1 bot por cliente). Gere um UUID estável por instância.
BOT_INSTANCE_ID=
BOT_CLIENT_NAME=
```

In `config.example.yml`, add a commented `instance:` block mirrorando (instanceId/clientName) — seguindo o estilo das outras seções do arquivo.

- [ ] **Step 5: Compilar**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

---

## Task 3: `SnapshotRepository` (JDBC + guards multi-tenant)

**Files:**
- Create: `src/main/java/dev/davimf/basebot/database/postgres/SnapshotRepository.java`

**Interfaces:**
- Consumes: `PostgresPool`, `BOT_INSTANCE_ID` (String uuid), tabelas da Task 1.
- Produces:
  - `SnapshotRepository(PostgresPool pool, String botInstanceId)`
  - records aninhados `ChannelRow(String channelId, String name, String type, String parentId, Integer position, boolean canView, boolean canSend)` e `RoleRow(String roleId, String name, Integer position, boolean managed, boolean canAssign)`
  - `void upsertInstance(String botUserId, String applicationId, String clientName)`
  - `boolean upsertGuild(String guildId, String guildName, String ownerId)` — instance-guarded; true se afetou linha
  - `void markAbsent(String guildId)`
  - `void replaceChannels(String guildId, List<ChannelRow> rows)` — só se a guild pertence à instância
  - `void replaceRoles(String guildId, List<RoleRow> rows)` — idem

- [ ] **Step 1: Implementar o repositório**

Create `SnapshotRepository.java`:

```java
package dev.davimf.basebot.database.postgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Publishes per-guild Discord snapshots so the dashboard can validate channels/roles without a
 * bot token. Every write is scoped to this bot's {@code botInstanceId}: a compromised instance
 * cannot touch another client's rows (v1 = guards in WHERE; RLS is the future hardening).
 */
public final class SnapshotRepository {

    public record ChannelRow(String channelId, String name, String type, String parentId,
                             Integer position, boolean canView, boolean canSend) {}

    public record RoleRow(String roleId, String name, Integer position, boolean managed, boolean canAssign) {}

    private final PostgresPool pool;
    private final String botInstanceId;

    public SnapshotRepository(PostgresPool pool, String botInstanceId) {
        this.pool = pool;
        this.botInstanceId = botInstanceId;
    }

    public void upsertInstance(String botUserId, String applicationId, String clientName) {
        String sql = """
                INSERT INTO bot_instances (id, client_name, bot_user_id, application_id, active)
                VALUES (?::uuid, ?, ?, ?, true)
                ON CONFLICT (id) DO UPDATE SET
                    client_name    = EXCLUDED.client_name,
                    bot_user_id    = EXCLUDED.bot_user_id,
                    application_id = EXCLUDED.application_id,
                    active         = true
                """;
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, botInstanceId);
            ps.setString(2, clientName);
            ps.setString(3, botUserId);
            ps.setString(4, applicationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("upsert bot_instance " + botInstanceId, e);
        }
    }

    /** Upsert guarded so this instance never overwrites a guild owned by another instance. */
    public boolean upsertGuild(String guildId, String guildName, String ownerId) {
        String sql = """
                INSERT INTO bot_guilds (guild_id, bot_instance_id, guild_name, owner_id, bot_present, last_seen_at)
                VALUES (?, ?::uuid, ?, ?, true, now())
                ON CONFLICT (guild_id) DO UPDATE SET
                    guild_name   = EXCLUDED.guild_name,
                    owner_id     = EXCLUDED.owner_id,
                    bot_present  = true,
                    last_seen_at = now()
                WHERE bot_guilds.bot_instance_id = ?::uuid
                """;
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, botInstanceId);
            ps.setString(3, guildName);
            ps.setString(4, ownerId);
            ps.setString(5, botInstanceId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("upsert bot_guild " + guildId, e);
        }
    }

    public void markAbsent(String guildId) {
        String sql = "UPDATE bot_guilds SET bot_present = false, last_seen_at = now() "
                + "WHERE guild_id = ? AND bot_instance_id = ?::uuid";
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, botInstanceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("mark absent " + guildId, e);
        }
    }

    public void replaceChannels(String guildId, List<ChannelRow> rows) {
        try (Connection c = pool.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                if (!ownsGuild(c, guildId)) {
                    c.rollback();
                    return;
                }
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM guild_channels_snapshot WHERE guild_id = ?")) {
                    del.setString(1, guildId);
                    del.executeUpdate();
                }
                String ins = "INSERT INTO guild_channels_snapshot "
                        + "(guild_id, channel_id, name, type, parent_id, position, bot_can_view, bot_can_send, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?, now())";
                try (PreparedStatement ps = c.prepareStatement(ins)) {
                    for (ChannelRow r : rows) {
                        ps.setString(1, guildId);
                        ps.setString(2, r.channelId());
                        ps.setString(3, r.name());
                        ps.setString(4, r.type());
                        ps.setString(5, r.parentId());
                        setNullableInt(ps, 6, r.position());
                        ps.setBoolean(7, r.canView());
                        ps.setBoolean(8, r.canSend());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            throw new RepositoryException("replace channels " + guildId, e);
        }
    }

    public void replaceRoles(String guildId, List<RoleRow> rows) {
        try (Connection c = pool.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                if (!ownsGuild(c, guildId)) {
                    c.rollback();
                    return;
                }
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM guild_roles_snapshot WHERE guild_id = ?")) {
                    del.setString(1, guildId);
                    del.executeUpdate();
                }
                String ins = "INSERT INTO guild_roles_snapshot "
                        + "(guild_id, role_id, name, position, managed, bot_can_assign, updated_at) "
                        + "VALUES (?,?,?,?,?,?, now())";
                try (PreparedStatement ps = c.prepareStatement(ins)) {
                    for (RoleRow r : rows) {
                        ps.setString(1, guildId);
                        ps.setString(2, r.roleId());
                        ps.setString(3, r.name());
                        setNullableInt(ps, 4, r.position());
                        ps.setBoolean(5, r.managed());
                        ps.setBoolean(6, r.canAssign());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            throw new RepositoryException("replace roles " + guildId, e);
        }
    }

    /** Guard multi-tenant: a guild pertence a esta instância? */
    private boolean ownsGuild(Connection c, String guildId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM bot_guilds WHERE guild_id = ? AND bot_instance_id = ?::uuid")) {
            ps.setString(1, guildId);
            ps.setString(2, botInstanceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void setNullableInt(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) { ps.setNull(i, java.sql.Types.INTEGER); } else { ps.setInt(i, v); }
    }
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

---

## Task 4: `GuildSnapshotSync` (mapeamento JDA → linhas) + teste da lógica pura

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/snapshot/GuildSnapshotSync.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/snapshot/GuildSnapshotSyncTest.java`

**Interfaces:**
- Consumes: `BotContext` (`ctx.database().postgres()`, `ctx.config().instance()`, `ctx.jda()`), `SnapshotRepository` (Task 3).
- Produces:
  - `GuildSnapshotSync(BotContext ctx)`
  - `static boolean botCanAssign(boolean hasManageRoles, boolean canInteract, boolean managed)`
  - `void bootstrapInstance()` (upsert bot_instances a partir do self user)
  - `void syncGuild(Guild g)`
  - `void syncAll()`
  - `void markAbsent(String guildId)`

- [ ] **Step 1: Teste da lógica pura de atribuibilidade**

Create `GuildSnapshotSyncTest.java`:

```java
package dev.davimf.basebot.modules.base.snapshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuildSnapshotSyncTest {

    @Test
    void canAssignRequiresAllThree() {
        assertTrue(GuildSnapshotSync.botCanAssign(true, true, false));
    }

    @Test
    void cannotAssignWithoutManageRoles() {
        assertFalse(GuildSnapshotSync.botCanAssign(false, true, false));
    }

    @Test
    void cannotAssignAboveHierarchy() {
        assertFalse(GuildSnapshotSync.botCanAssign(true, false, false));
    }

    @Test
    void cannotAssignManagedRole() {
        assertFalse(GuildSnapshotSync.botCanAssign(true, true, true));
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.snapshot.GuildSnapshotSyncTest"`
Expected: FAIL — `GuildSnapshotSync` não existe.

- [ ] **Step 3: Implementar o serviço**

Create `GuildSnapshotSync.java`:

```java
package dev.davimf.basebot.modules.base.snapshot;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.postgres.SnapshotRepository;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Publishes each guild's channel/role snapshot to Postgres so the dashboard can validate
 * channels/roles without a bot token. Full-sync on boot + periodically; incremental via the
 * listener. Skips entirely when no BOT_INSTANCE_ID is configured.
 */
public final class GuildSnapshotSync {

    private static final Logger log = LoggerFactory.getLogger(GuildSnapshotSync.class);

    private final BotContext ctx;
    private final SnapshotRepository repo;
    private final String clientName;

    public GuildSnapshotSync(BotContext ctx) {
        this.ctx = ctx;
        this.repo = new SnapshotRepository(ctx.database().postgres(),
                ctx.config().instance().instanceId());
        this.clientName = ctx.config().instance().clientName();
    }

    /** Um cargo é atribuível pelo bot se ele tem Gerenciar Cargos, está acima na hierarquia e o cargo não é gerenciado. */
    public static boolean botCanAssign(boolean hasManageRoles, boolean canInteract, boolean managed) {
        return hasManageRoles && canInteract && !managed;
    }

    /** Registra/atualiza esta instância no boot (precisa de JDA vivo). */
    public void bootstrapInstance() {
        if (ctx.jda() == null) {
            return;
        }
        String botUserId = ctx.jda().getSelfUser().getId();
        repo.upsertInstance(botUserId, null, clientName);
    }

    public void syncAll() {
        if (ctx.jda() == null) {
            return;
        }
        for (Guild g : ctx.jda().getGuilds()) {
            try {
                syncGuild(g);
            } catch (Exception e) {
                log.warn("snapshot sync falhou para guild {}", g.getId(), e);
            }
        }
    }

    public void syncGuild(Guild g) {
        // bot_guilds primeiro: estabelece/atualiza o vínculo guild->instância (guard interno no repo).
        repo.upsertGuild(g.getId(), g.getName(), g.getOwnerId());
        Member self = g.getSelfMember();
        boolean manageRoles = self.hasPermission(Permission.MANAGE_ROLES);

        List<SnapshotRepository.ChannelRow> channels = new ArrayList<>();
        for (GuildChannel ch : g.getChannels()) {
            boolean canView = self.hasPermission(ch, Permission.VIEW_CHANNEL);
            boolean canSend = ch instanceof GuildMessageChannel
                    && self.hasPermission(ch, Permission.MESSAGE_SEND);
            String parentId = (ch instanceof ICategorizableChannel cat && cat.getParentCategory() != null)
                    ? cat.getParentCategory().getId() : null;
            channels.add(new SnapshotRepository.ChannelRow(
                    ch.getId(), ch.getName(), ch.getType().name(), parentId,
                    ch.getPositionRaw(), canView, canSend));
        }
        repo.replaceChannels(g.getId(), channels);

        List<SnapshotRepository.RoleRow> roles = new ArrayList<>();
        for (Role r : g.getRoles()) {
            boolean canAssign = botCanAssign(manageRoles, self.canInteract(r), r.isManaged());
            roles.add(new SnapshotRepository.RoleRow(
                    r.getId(), r.getName(), r.getPositionRaw(), r.isManaged(), canAssign));
        }
        repo.replaceRoles(g.getId(), roles);
    }

    public void markAbsent(String guildId) {
        repo.markAbsent(guildId);
    }
}
```

> Nota: `Category` está importado porque categorias também aparecem em `getChannels()`; elas não são `GuildMessageChannel` (canSend=false) e não são `ICategorizableChannel` (parentId=null) — o mapeamento acima já trata isso sem ramo especial. Se o compilador acusar import não usado de `Category`, removê-lo.

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.snapshot.GuildSnapshotSyncTest"`
Expected: PASS (4 testes).

- [ ] **Step 5: Compilar tudo**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`. Se algum tipo/method de JDA divergir (ex.: `getPositionRaw`, `ICategorizableChannel`), ajustar conforme o compilador — os padrões vêm de `ChannelLoggingListener`/`AntiNukeService`.

---

## Task 5: `GuildSnapshotListener` (incremental + debounce)

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/snapshot/GuildSnapshotListener.java`

**Interfaces:**
- Consumes: `BotContext` (scheduler), `GuildSnapshotSync` (Task 4).
- Produces: `GuildSnapshotListener(BotContext ctx, GuildSnapshotSync sync)` — um `ListenerAdapter`.

- [ ] **Step 1: Implementar o listener**

Create `GuildSnapshotListener.java`:

```java
package dev.davimf.basebot.modules.base.snapshot;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateParentEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateNameEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdatePermissionsEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the Discord snapshots fresh between periodic syncs: on join it registers + syncs the
 * guild, on leave it marks it absent, and on channel/role changes it re-syncs that guild
 * (debounced ~5s per guild so a bulk edit collapses into one resync).
 */
public final class GuildSnapshotListener extends ListenerAdapter {

    private static final long DEBOUNCE_SECONDS = 5;

    private final BotContext ctx;
    private final GuildSnapshotSync sync;
    private final Map<String, Future<?>> pending = new ConcurrentHashMap<>();

    public GuildSnapshotListener(BotContext ctx, GuildSnapshotSync sync) {
        this.ctx = ctx;
        this.sync = sync;
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        scheduleResync(event.getGuild());
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        String guildId = event.getGuild().getId();
        ctx.scheduler().executor().execute(() -> sync.markAbsent(guildId));
    }

    @Override public void onChannelCreate(ChannelCreateEvent e) { scheduleResync(e.getGuild()); }
    @Override public void onChannelDelete(ChannelDeleteEvent e) { scheduleResync(e.getGuild()); }
    @Override public void onChannelUpdateName(ChannelUpdateNameEvent e) { scheduleResync(e.getGuild()); }
    @Override public void onChannelUpdateParent(ChannelUpdateParentEvent e) { scheduleResync(e.getGuild()); }
    @Override public void onRoleCreate(RoleCreateEvent e) { scheduleResync(e.getGuild()); }
    @Override public void onRoleDelete(RoleDeleteEvent e) { scheduleResync(e.getGuild()); }
    @Override public void onRoleUpdateName(RoleUpdateNameEvent e) { scheduleResync(e.getGuild()); }
    @Override public void onRoleUpdatePermissions(RoleUpdatePermissionsEvent e) { scheduleResync(e.getGuild()); }

    /** Coalesce a burst of events for one guild into a single resync. */
    private void scheduleResync(Guild guild) {
        String guildId = guild.getId();
        Future<?> prev = pending.remove(guildId);
        if (prev != null) {
            prev.cancel(false);
        }
        Future<?> f = ctx.scheduler().executor().schedule(() -> {
            pending.remove(guildId);
            Guild g = ctx.jda() == null ? null : ctx.jda().getGuildById(guildId);
            if (g != null) {
                sync.syncGuild(g);
            }
        }, DEBOUNCE_SECONDS, TimeUnit.SECONDS);
        pending.put(guildId, f);
    }
}
```

> `ChannelCreateEvent`/`ChannelDeleteEvent`/`ChannelUpdate*`/`Role*` expõem `getGuild()` (mesmos eventos usados em `ChannelLoggingListener`/`RoleLoggingListener`). `GuildJoinEvent`/`GuildLeaveEvent` estão em `net.dv8tion.jda.api.events.guild`.

- [ ] **Step 2: Compilar**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

---

## Task 6: Wiring no `BaseModule` (register listener + boot/periodic sync)

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java`

**Interfaces:**
- Consumes: `GuildSnapshotSync` (Task 4), `GuildSnapshotListener` (Task 5).

- [ ] **Step 1: Campo + construção no `register`**

In `BaseModule.java`, add a field near the other service fields:

```java
    private dev.davimf.basebot.modules.base.snapshot.GuildSnapshotSync guildSnapshot;
```

In `register(ModuleRegistry registry, BotContext ctx)`, construct the sync and register the listener **only when a BOT_INSTANCE_ID is configured** (senão os snapshots ficam off e o bot roda normal). Add near where other listeners are registered:

```java
        if (ctx.config().instance().hasInstance()) {
            this.guildSnapshot = new dev.davimf.basebot.modules.base.snapshot.GuildSnapshotSync(ctx);
            registry.listener(new dev.davimf.basebot.modules.base.snapshot.GuildSnapshotListener(ctx, guildSnapshot));
        }
```

- [ ] **Step 2: Boot + periódico no `onReady`**

In `onReady(BotContext ctx)`, add (mirroring the existing scheduled blocks; `TimeUnit` já importado):

```java
        // Snapshots do dashboard: registra a instância + full-sync no boot; sync periódico (<10min).
        if (guildSnapshot != null) {
            ctx.scheduler().once(() -> {
                guildSnapshot.bootstrapInstance();
                guildSnapshot.syncAll();
            }, 8, TimeUnit.SECONDS);
            ctx.scheduler().repeating(guildSnapshot::syncAll, 300, 300, TimeUnit.SECONDS);
        }
```

- [ ] **Step 3: Build completo**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` com a suíte (incl. `GuildSnapshotSyncTest`).

---

## Smoke final (após aplicar o 005 no Neon + setar `BOT_INSTANCE_ID`)

1. Definir `BOT_INSTANCE_ID` (um UUID) e opcional `BOT_CLIENT_NAME` no `.env`; subir o bot.
2. `bot_instances`: 1 linha com o id/bot_user_id.
3. `bot_guilds`: 1 linha por guild do bot, `bot_present=true`, `last_seen_at` recente.
4. `guild_channels_snapshot`/`guild_roles_snapshot`: linhas coerentes; um canal onde o bot vê → `bot_can_view=true`; um cargo abaixo do bot e não-managed → `bot_can_assign=true`; `@everyone`/cargos gerenciados → `bot_can_assign=false`.
5. Criar/renomear um canal → em ~5s o snapshot reflete (listener). Remover o bot de um servidor de teste → `bot_present=false`.
6. Multi-tenant: com um segundo `bot_instance_id` fictício, um UPDATE manual tentando alterar `bot_guilds` de outra instância via a app não deve afetar (guard no `WHERE`).

---

## Self-Review (executado ao escrever o plano)

**1. Cobertura do spec (A2):** DDL 4 tabelas → T1; env BOT_INSTANCE_ID/BOT_CLIENT_NAME + upsert bot_instances no boot → T2/T4/T6; GuildSnapshotSync (canais/cargos/perms) → T4; eventos + periódico + boot → T5/T6; guard multi-tenant no WHERE (bot_guilds instance-guard + ownsGuild p/ channels/roles) → T3. ✅

**2. Placeholders:** nenhum "TBD"; todo passo de código traz o código. Pontos que dependem de convenção/JDA (import `Category`, nomes `getPositionRaw`/`ICategorizableChannel`) vêm com instrução de ajustar via compilador citando os arquivos-referência — não placeholders de lógica.

**3. Consistência de tipos:** `SnapshotRepository.ChannelRow`/`RoleRow` casam com o que `GuildSnapshotSync.syncGuild` constrói; `botCanAssign(boolean,boolean,boolean)` idêntico no teste (T4) e no uso; `SnapshotRepository(PostgresPool, String)` recebe `ctx.config().instance().instanceId()`; `application_id` gravado como `null` na v1 (coluna nullable) — documentado.

**Notas p/ o dono:** (a) `application_id` fica null na v1 (evita chamada async no boot); dá pra preencher depois via `retrieveApplicationInfo`. (b) aplicar o `005` no Neon e setar `BOT_INSTANCE_ID` são passos manuais antes do smoke. (c) endurecimento multi-tenant além do guard-no-WHERE (RLS/role por instância) é futuro, fora do A2.
