# Parte A3 — Consumo endurecido (cache + merge-safe + reconcile) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** O bot consome a config do Neon de forma segura e eventualmente-consistente com o dashboard: cache curto na leitura, escritas que **não sobrescrevem** chaves que o dashboard gravou (merge por chave + preservação de `dashboard_access`), e reconcile periódico dos efeitos ativos (AutoMod nativo).

**Architecture:** (1) `save()` do `JdbcGuildConfigRepository` passa a **mesclar** (`||`) os mapas JSONB em vez de sobrescrever → todo `save(fullConfig)` do `/setup` vira merge-safe sem tocar nos 57 call sites. (2) `dashboard_access` ganha coluna no `guild_config` e é **preservada** (o `save()` não a lista no SET) — o bot nunca a escreve. (3) Um `CachingGuildConfigRepository` (decorator) coloca um TTL curto por-guild nas leituras, com evict-on-write. (4) O sync do AutoMod nativo, hoje só no boot, vira uma tarefa periódica lendo a config fresca.

**Tech Stack:** Java 22, JDBC/Postgres (`JdbcGuildConfigRepository`), JUnit 5 (cache testado com relógio e repo fake — sem banco).

## Decisão de teste (mesma do A1/A2)

Sem harness Postgres. O `CachingGuildConfigRepository` é **testável** (Java puro: relógio injetável + repo fake em memória) → tem teste. A mudança no `save()` (merge SQL) e a coluna `dashboard_access` são verificadas por `./gradlew build` + smoke no Neon (sem teste de banco, como o resto da camada Postgres). O reconcile do AutoMod é build + smoke.

## Global Constraints

- **Merge, nunca sobrescrever mapa inteiro.** No `guild_config`, os mapas `channels`/`roles`/`toggles`/`settings` são mesclados por chave. Contrato: `docs/superpowers/dashboard-config-schema-contract.md` §Semântica de merge/delete + invariantes #4 e #8.
- **O bot nunca escreve `dashboard_access`** (só o dashboard). O `save()` não a inclui no `SET` → preservada.
- **Cache = TTL (eventualmente consistente), não invalidação global.** O bot não é o único escritor; o cache expira sozinho (~45 s). Escrita do próprio bot faz **evict** da guild pra ele ver a própria mudança na hora.
- **Reconcile ~2–5 min** para efeitos ativos (AutoMod). Config passiva é lida sob demanda (via cache).
- **Aplicação do schema (006) no Neon é manual** via `dev.davimf.basebot.tools.ApplyPostgresSchema` (idempotente), fora do boot.
- Spec: `docs/superpowers/specs/2026-07-02-dashboard-config-integration-design.md` §3–§4.

## Escopo / Fora de escopo

**Nesta A3:** merge-safe `save()`, coluna `dashboard_access` + preservação, cache de leitura, reconcile periódico do AutoMod.

**Deferido (follow-up, não nesta A3):** reconcile de **republicação de painéis** (self-roles/ticket/hierarquia/recrutamento re-editando a mensagem publicada quando o dashboard muda) — é uma feature maior, com detecção por `updated_at`/`message_id` por tipo de painel; fica pra uma A4. Registrado em `docs/superpowers/FUTURE-IDEAS.md`.

## File Structure

- `src/main/resources/db/postgres/006_dashboard_access.sql` — **novo**: `ALTER TABLE guild_config ADD COLUMN dashboard_access`.
- `src/main/java/.../database/postgres/PostgresMigrator.java` — registra o 006.
- `src/main/java/.../database/postgres/JdbcGuildConfigRepository.java` — `save()` mescla os 4 mapas (`||`).
- `src/main/java/.../database/postgres/CachingGuildConfigRepository.java` — **novo**: decorator com TTL + evict.
- `src/test/java/.../database/postgres/CachingGuildConfigRepositoryTest.java` — **novo**.
- `src/main/java/.../database/DatabaseManager.java` — embrulha o repo JDBC no cache.
- `src/main/java/.../modules/base/BaseModule.java` — AutoMod sync periódico no `onReady`.

---

## Task 1: Coluna `dashboard_access` no `guild_config` (preservada pelo bot)

**Files:**
- Create: `src/main/resources/db/postgres/006_dashboard_access.sql`
- Modify: `src/main/java/dev/davimf/basebot/database/postgres/PostgresMigrator.java` (MIGRATIONS)

**Interfaces:**
- Produces: coluna `guild_config.dashboard_access JSONB NOT NULL DEFAULT '{}'` (escrita só pelo dashboard; o bot preserva).

- [ ] **Step 1: Escrever a migração**

Create `src/main/resources/db/postgres/006_dashboard_access.sql`:

```sql
-- Autorização do dashboard (owner-first + delegação): quem, além do dono, pode configurar
-- este servidor. Escrita SOMENTE pelo dashboard; o bot preserva (nunca lista no SET do save).
-- Forma: {"users": ["<user id>", ...], "roles": ["<role id>", ...]}
ALTER TABLE guild_config
    ADD COLUMN IF NOT EXISTS dashboard_access JSONB NOT NULL DEFAULT '{}'::jsonb;
```

- [ ] **Step 2: Registrar no migrator**

Append `"/db/postgres/006_dashboard_access.sql"` as the 6th entry of `PostgresMigrator.MIGRATIONS` (nunca reordenar).

- [ ] **Step 3: Compilar**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Confirmar que o `save()` NÃO escreve `dashboard_access`**

Abrir `JdbcGuildConfigRepository.save()` e confirmar que nem a lista de colunas do `INSERT` nem o `ON CONFLICT ... DO UPDATE SET` mencionam `dashboard_access`. (Deve ser o caso — é a garantia de preservação.) Nenhuma mudança de código aqui além dessa verificação; a Task 2 mexe no `save()`.

- [ ] **Step 5: Aplicar no Neon (manual)**

`./gradlew shadowJar` então `java -cp build/libs/basebot.jar dev.davimf.basebot.tools.ApplyPostgresSchema` — deve listar `006_dashboard_access.sql`.

---

## Task 2: `save()` merge-safe (mescla os 4 mapas JSONB)

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/database/postgres/JdbcGuildConfigRepository.java` (método `save`)

**Interfaces:**
- Consumes: coluna `dashboard_access` (Task 1).
- Produces: `save(GuildConfig)` com semântica de **merge** nos mapas (preserva chaves que o dashboard gravou e não estão na cópia do bot); `staff_role_ids` e colunas typed continuam replace.

- [ ] **Step 1: Trocar o `ON CONFLICT ... SET` dos 4 mapas para merge**

No método `save`, substituir o SQL do `save` por (muda **apenas** `channels`/`roles`/`toggles`/`settings` de `= EXCLUDED.x` para `= coalesce(guild_config.x, '{}'::jsonb) || EXCLUDED.x`; `staff_role_ids` e as colunas typed continuam `= EXCLUDED.x`):

```java
        String sql = """
                INSERT INTO guild_config
                    (guild_id, log_channel_id, ticket_log_channel_id,
                     channels, roles, toggles, staff_role_ids, settings, updated_at)
                VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, now())
                ON CONFLICT (guild_id) DO UPDATE SET
                    log_channel_id        = EXCLUDED.log_channel_id,
                    ticket_log_channel_id = EXCLUDED.ticket_log_channel_id,
                    channels              = coalesce(guild_config.channels, '{}'::jsonb) || EXCLUDED.channels,
                    roles                 = coalesce(guild_config.roles,    '{}'::jsonb) || EXCLUDED.roles,
                    toggles               = coalesce(guild_config.toggles,  '{}'::jsonb) || EXCLUDED.toggles,
                    staff_role_ids        = EXCLUDED.staff_role_ids,
                    settings              = coalesce(guild_config.settings, '{}'::jsonb) || EXCLUDED.settings,
                    updated_at            = now()
                """;
```

(O corpo do método — os `ps.setString(...)` e o `executeUpdate()` — fica igual; só a string `sql` muda.)

- [ ] **Step 2: Comentário explicando a semântica**

Logo acima do `String sql`, adicionar:

```java
        // Merge por chave (||) nos mapas JSONB: um save(fullConfig) do /setup nunca apaga chaves
        // que o dashboard gravou e que não estão nesta cópia. GuildConfigEdits só faz put (nunca
        // remove chave), então merge não perde capacidade. dashboard_access não é tocada (preservada).
        // Caveat: se o dashboard REMOVE uma chave e o bot salva uma cópia (possivelmente do cache)
        // que ainda a tem, o merge a re-adiciona — aceito (last-write-wins por chave, janela <= TTL).
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` (compila; a suíte atual segue verde — `save()` não tem teste de banco).

---

## Task 3: `CachingGuildConfigRepository` (TTL + evict-on-write)

**Files:**
- Create: `src/main/java/dev/davimf/basebot/database/postgres/CachingGuildConfigRepository.java`
- Test: `src/test/java/dev/davimf/basebot/database/postgres/CachingGuildConfigRepositoryTest.java`
- Modify: `src/main/java/dev/davimf/basebot/database/DatabaseManager.java`

**Interfaces:**
- Consumes: `GuildConfigRepository` (delegate), `java.util.function.LongSupplier` (relógio).
- Produces: `CachingGuildConfigRepository(GuildConfigRepository delegate)` (TTL 45 s, relógio real) e `CachingGuildConfigRepository(GuildConfigRepository delegate, long ttlMillis, LongSupplier nowMillis)` (para teste). Implementa `GuildConfigRepository`.

- [ ] **Step 1: Teste que falha**

Create `CachingGuildConfigRepositoryTest.java`:

```java
package dev.davimf.basebot.database.postgres;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class CachingGuildConfigRepositoryTest {

    /** Repo fake que conta leituras e devolve um valor mutável por guild. */
    private static final class FakeRepo implements GuildConfigRepository {
        final AtomicInteger finds = new AtomicInteger();
        GuildConfig current = base("g1", "old");

        static GuildConfig base(String g, String name) {
            return new GuildConfig(g, null, null, Map.of("log-mensagens", name),
                    Map.of(), Map.of(), List.of(), Map.of());
        }

        @Override public Optional<GuildConfig> find(String guildId) {
            finds.incrementAndGet();
            return Optional.of(current);
        }
        @Override public void save(GuildConfig config) { this.current = config; }
        @Override public void setToggle(String guildId, String key, boolean value) { }
    }

    @Test
    void secondReadWithinTtlHitsCache() {
        FakeRepo fake = new FakeRepo();
        AtomicLong clock = new AtomicLong(1_000_000L);
        var cache = new CachingGuildConfigRepository(fake, 45_000L, clock::get);

        cache.find("g1");
        cache.find("g1");
        assertEquals(1, fake.finds.get(), "segunda leitura dentro do TTL não bate no delegate");
    }

    @Test
    void readAfterTtlRefetches() {
        FakeRepo fake = new FakeRepo();
        AtomicLong clock = new AtomicLong(1_000_000L);
        var cache = new CachingGuildConfigRepository(fake, 45_000L, clock::get);

        cache.find("g1");
        clock.addAndGet(46_000L);
        cache.find("g1");
        assertEquals(2, fake.finds.get(), "após o TTL, re-busca no delegate");
    }

    @Test
    void saveEvictsSoNextReadIsFresh() {
        FakeRepo fake = new FakeRepo();
        AtomicLong clock = new AtomicLong(1_000_000L);
        var cache = new CachingGuildConfigRepository(fake, 45_000L, clock::get);

        cache.find("g1");                          // popula cache (finds=1)
        cache.save(FakeRepo.base("g1", "new"));    // evict
        Optional<GuildConfig> after = cache.find("g1"); // re-busca (finds=2)
        assertEquals(2, fake.finds.get());
        assertEquals("new", after.orElseThrow().channel("log-mensagens"));
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests "dev.davimf.basebot.database.postgres.CachingGuildConfigRepositoryTest"`
Expected: FAIL — `CachingGuildConfigRepository` não existe.

- [ ] **Step 3: Implementar o decorator**

Create `CachingGuildConfigRepository.java`:

```java
package dev.davimf.basebot.database.postgres;

import dev.davimf.basebot.database.model.GuildConfig;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Short-TTL read cache over a {@link GuildConfigRepository}. The bot is not the only writer
 * (the dashboard writes the same Neon rows), so this uses expiry, not global invalidation:
 * a guild's config is re-read at most once per {@code ttlMillis}. Writes by THIS bot evict the
 * guild so it immediately sees its own change. Eventually consistent with the dashboard within
 * the TTL — matches the design's "config passiva ~imediata / efeitos ativos reconciliados".
 */
public final class CachingGuildConfigRepository implements GuildConfigRepository {

    private static final long DEFAULT_TTL_MILLIS = 45_000L;

    private record Entry(Optional<GuildConfig> value, long expiresAt) {}

    private final GuildConfigRepository delegate;
    private final long ttlMillis;
    private final LongSupplier nowMillis;
    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    public CachingGuildConfigRepository(GuildConfigRepository delegate) {
        this(delegate, DEFAULT_TTL_MILLIS, System::currentTimeMillis);
    }

    public CachingGuildConfigRepository(GuildConfigRepository delegate, long ttlMillis, LongSupplier nowMillis) {
        this.delegate = delegate;
        this.ttlMillis = ttlMillis;
        this.nowMillis = nowMillis;
    }

    @Override
    public Optional<GuildConfig> find(String guildId) {
        long now = nowMillis.getAsLong();
        Entry entry = cache.get(guildId);
        if (entry != null && now < entry.expiresAt()) {
            return entry.value();
        }
        Optional<GuildConfig> fresh = delegate.find(guildId);
        cache.put(guildId, new Entry(fresh, now + ttlMillis));
        return fresh;
    }

    @Override
    public void save(GuildConfig config) {
        delegate.save(config);
        cache.remove(config.guildId());
    }

    @Override
    public void setToggle(String guildId, String key, boolean value) {
        delegate.setToggle(guildId, key, value);
        cache.remove(guildId);
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --tests "dev.davimf.basebot.database.postgres.CachingGuildConfigRepositoryTest"`
Expected: PASS (3 testes).

- [ ] **Step 5: Embrulhar no `DatabaseManager`**

In `DatabaseManager.java`, trocar a construção do `guildConfig` (linha ~83) para embrulhar o JDBC no cache:

```java
        this.guildConfig = new CachingGuildConfigRepository(new JdbcGuildConfigRepository(postgres));
```

(O campo/getter `guildConfig` continua tipado como `GuildConfigRepository` — a interface — então nada mais muda.)

- [ ] **Step 6: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` com a suíte.

---

## Task 4: Reconcile periódico do AutoMod nativo

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java` (`onReady`)

**Interfaces:**
- Consumes: `AutoModManager.sync(Guild, GuildConfig)` (já existe), `ctx.database().guildConfig()` (agora com cache), `ctx.scheduler().repeating`.

- [ ] **Step 1: Tornar o sync do AutoMod periódico**

No `onReady(BotContext ctx)`, hoje há um bloco `ctx.scheduler().executor().execute(() -> { ... for guilds: AutoModManager.sync ... })` que roda **uma vez** no boot. Adicionar (logo após esse bloco) um reconcile periódico que re-sincroniza lendo a config fresca (via cache, ≤ TTL de atraso):

```java
        // Reconcile dos efeitos ativos: re-sincroniza o AutoMod nativo de cada guild a cada 5 min,
        // pra refletir mudanças feitas pelo dashboard (config lida via cache curto).
        ctx.scheduler().repeating(() -> {
            if (ctx.jda() == null) {
                return;
            }
            for (net.dv8tion.jda.api.entities.Guild g : ctx.jda().getGuilds()) {
                dev.davimf.basebot.modules.base.security.AutoModManager.sync(g,
                        ctx.database().guildConfig().findOrEmpty(g.getId()));
            }
        }, 300, 300, java.util.concurrent.TimeUnit.SECONDS);
```

(Manter o bloco `execute(...)` de boot existente; este é o reconcile recorrente. `TimeUnit` já está importado no arquivo — pode usar `TimeUnit.SECONDS` direto.)

- [ ] **Step 2: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` com a suíte.

---

## Smoke final (após aplicar o 006 no Neon)

1. Aplicar o 006 (`ApplyPostgresSchema`); conferir a coluna `guild_config.dashboard_access` existe (default `{}`).
2. **Merge-safe:** no Neon, num guild existente, setar manualmente uma chave em `channels` (ex.: `channels = channels || '{"log-teste":"123"}'`). No bot, mexer em OUTro campo via `/setup` (ex.: um toggle). Reconferir que `channels->>'log-teste'` **continua** lá (não foi apagado pelo save).
3. **dashboard_access preservada:** setar `dashboard_access = '{"users":["456"]}'` no Neon; fazer uma edição de `/setup`; reconferir que `dashboard_access` continua `{"users":["456"]}`.
4. **Cache:** mudar um valor de config direto no Neon; o bot reflete em ≤ ~45 s (não instantâneo — é o TTL). Uma mudança feita pelo próprio `/setup` reflete na hora (evict).
5. **Reconcile AutoMod:** mudar `sec:automod*` no Neon; em ≤ ~5 min as regras nativas do AutoMod re-sincronizam.

---

## Self-Review (executado ao escrever o plano)

**1. Cobertura do spec (A3):** cache TTL → T3; reconcile efeitos ativos (AutoMod) → T4; escritas merge-safe preservando dashboard_access → T2 (merge dos mapas) + T1 (coluna + `save()` a preserva por omissão); `dashboard_access` no schema → T1. Republicação de painéis → **deferida** (documentada). ✅

**2. Placeholders:** nenhum "TBD"; todo passo traz o código. O `save()` da T2 mostra o SQL completo; o decorator e o teste da T3 vêm inteiros.

**3. Consistência de tipos:** `CachingGuildConfigRepository implements GuildConfigRepository` (find/save/setToggle; `findOrEmpty` é default da interface — herdado, usa o `find` cacheado). O construtor de teste `(delegate, ttlMillis, nowMillis:LongSupplier)` casa com o teste. `DatabaseManager.guildConfig` continua `GuildConfigRepository`. O `save()` mantém a mesma assinatura/params; só o SQL muda.

**Notas p/ o dono:** (a) o merge-on-save tem o caveat da re-adição de uma chave que o dashboard removeu dentro da janela do TTL — aceito (last-write-wins por chave). (b) `dashboard_access` fica **fora** do modelo `GuildConfig` de propósito: o bot não a lê nem usa — só a preserva no banco; adicioná-la ao record só se um recurso do bot precisar lê-la. (c) republicação de painéis pelo reconcile é a peça deferida (A4).
