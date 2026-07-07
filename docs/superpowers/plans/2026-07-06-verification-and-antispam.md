# Verificação (fila + memória) e Canal Anti-spam — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expandir a Verificação (perguntas configuráveis + fila de aprovação + memória por servidor) e adicionar um canal Anti-spam que expulsa e limpa mensagens de quem postar nele.

**Architecture:** Ambas as features estendem o módulo `modules/base/security/`. Config vive em `guild_config` (Postgres/Neon) via `SecurityConfig` (prefixo `sec:`) e numa tabela dedicada `verification_questions` (Neon, DDL aplicada manualmente). Estado operacional (verificados + pendências) vive em SQLite via `VerificationRepository` (migração automática). UI em `/setup → Segurança`; listeners e handlers seguem os módulos de segurança existentes (`AntiNukeListener`, `SecurityComponentHandler`).

**Tech Stack:** Java 22, JDA 5 (Components V2), SQLite (estado local) + Postgres/Neon (config), JUnit 5 (sem Mockito; JDA via `java.lang.reflect.Proxy`, SQLite em memória via `@TempDir`). Build: Gradle (`./gradlew`).

## Global Constraints

- Toda mensagem do bot é um **container Components V2** (helper `Panels`; `.useComponentsV2()` no envio). Ver `VerificationView`/`AntiSpamView`.
- Emojis sempre via registro `Emojis` (`Emojis.of(KEY, fallback)` em texto, `Emojis.button(KEY)` em botões) — nunca Unicode cru.
- Config de guild é lida/escrita via `GuildConfig` + `GuildConfigEdits.withToggle/withSetting` e `ctx.database().guildConfig().save(...)`.
- Migrações SQLite são **listadas explicitamente** em `SqliteMigrator.MIGRATIONS` (append no fim; nunca reordenar). Uma statement por `;` no fim da linha.
- DDL Postgres é aplicada **manualmente** pelo usuário no Neon — o bot **não** roda migração Postgres.
- `ComponentId` custom-id tem limite de **100 chars**; o passo de seleção de usuário é capado a **4** usuários para caber quando embutido no id do modal.
- Punição do anti-spam é **kick** (fixo); quantidade apagada é **10** (fixo).
- Idioma de toda copy visível ao usuário: **português**.

---

## Estrutura de arquivos

**Criar:**
- `src/main/resources/db/sqlite/038_verification.sql` — tabelas `verified_members` + `verification_requests`.
- `src/main/resources/db/postgres/009_verification_questions.sql` — DDL manual (referência; o usuário roda no Neon).
- `src/main/java/dev/davimf/basebot/modules/base/security/VerificationRepository.java` — SQLite (verificados + pendências).
- `src/main/java/dev/davimf/basebot/database/postgres/VerificationQuestionRepository.java` — Postgres (perguntas).
- `src/main/java/dev/davimf/basebot/modules/base/security/VerificationResetListener.java` — esquece verificação em kick/ban.
- `src/main/java/dev/davimf/basebot/modules/base/security/AntiSpamListener.java` — evento no canal-armadilha.
- `src/main/java/dev/davimf/basebot/modules/base/security/AntiSpamService.java` — kick + purga + helper puro.
- `src/main/java/dev/davimf/basebot/modules/base/security/AntiSpamView.java` — aviso permanente.
- Testes: `VerificationRepositoryTest`, `AntiSpamServiceTest`, e extensões em `SecurityConfigTest`.

**Modificar:**
- `SecurityConfig.java` — chaves `sec:verify-userselect`, `sec:antispam`; slots de canal; helper de isenção anti-spam.
- `DatabaseManager.java` — expõe `verification()` e `verificationQuestions()`.
- `SecurityComponentHandler.java` — fluxo de verificação (modal/seleção/fila) + botões Aprovar/Recusar.
- `VerificationListener.java` — auto-cargo na re-entrada.
- `SetupView.java` — sub-telas de verificação e anti-spam.
- `SetupComponentHandler.java` — navegação, toggles, edição de perguntas, seletores de canal, publicar aviso.
- `BaseModule.java` — registra `VerificationResetListener` e `AntiSpamListener`.
- `SqliteMigrator.java` — append da migração 038.

---

# PARTE 1 — VERIFICAÇÃO

## Task 1: Chaves de config da verificação em `SecurityConfig`

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/security/SecurityConfig.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/security/SecurityConfigTest.java`

**Interfaces:**
- Produces: `SecurityConfig.KEY_VERIFY_USERSELECT` (String), `SecurityConfig.verifyUserSelect(GuildConfig)` (boolean, default false), `SecurityConfig.CHANNEL_VERIFY` (String `"verificacao"`).

- [ ] **Step 1: Escrever o teste que falha**

Em `SecurityConfigTest.java`, adicione:

```java
    @Test
    void verifyUserSelectDefaultsFalseAndReads() {
        assertFalse(SecurityConfig.verifyUserSelect(cfg(Map.of(), Map.of())));
        assertTrue(SecurityConfig.verifyUserSelect(
                cfg(Map.of(), Map.of(SecurityConfig.KEY_VERIFY_USERSELECT, true))));
    }
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.security.SecurityConfigTest"`
Expected: FAIL de compilação (`KEY_VERIFY_USERSELECT` / `verifyUserSelect` não existem).

- [ ] **Step 3: Implementar**

Em `SecurityConfig.java`, na seção `// Verificação (Módulo 3)`, ao lado de `KEY_VERIFY`:

```java
    public static final String KEY_VERIFY_USERSELECT = "sec:verify-userselect";
    /** Nome lógico do canal (guild_config.channels) onde a fila de aprovação é postada. */
    public static final String CHANNEL_VERIFY = "verificacao";
```

E na seção `// --- verificação ---`, ao lado de `verify(...)`:

```java
    public static boolean verifyUserSelect(GuildConfig cfg) { return cfg.toggle(KEY_VERIFY_USERSELECT, false); }
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.security.SecurityConfigTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/security/SecurityConfig.java src/test/java/dev/davimf/basebot/modules/base/security/SecurityConfigTest.java
git commit -m "feat(security): verify-userselect toggle + verification channel slot"
```

---

## Task 2: Migração SQLite `038_verification.sql`

**Files:**
- Create: `src/main/resources/db/sqlite/038_verification.sql`
- Modify: `src/main/java/dev/davimf/basebot/database/sqlite/SqliteMigrator.java:92`

**Interfaces:**
- Produces: tabelas SQLite `verified_members(guild_id, user_id, verified_at)` e `verification_requests(guild_id, user_id, message_id, answers, created_at)`, ambas PK `(guild_id, user_id)`.

- [ ] **Step 1: Criar o arquivo de migração**

`src/main/resources/db/sqlite/038_verification.sql`:

```sql
-- Verificação (Base security). verified_members: quem já passou (drive do auto-cargo
-- na re-entrada, por servidor). verification_requests: fila de pendências, UMA por
-- pessoa (PK guild+user impede pedido duplicado / spam da fila).
CREATE TABLE IF NOT EXISTS verified_members (
    guild_id    TEXT NOT NULL,
    user_id     TEXT NOT NULL,
    verified_at INTEGER NOT NULL,
    PRIMARY KEY (guild_id, user_id)
);

CREATE TABLE IF NOT EXISTS verification_requests (
    guild_id   TEXT NOT NULL,
    user_id    TEXT NOT NULL,
    message_id TEXT,
    answers    TEXT,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (guild_id, user_id)
);
```

- [ ] **Step 2: Registrar na lista de migrações**

Em `SqliteMigrator.java`, na lista `MIGRATIONS`, após `"/db/sqlite/037_bot_instance.sql"` troque a vírgula final e adicione:

```java
            "/db/sqlite/037_bot_instance.sql",
            "/db/sqlite/038_verification.sql"
```

- [ ] **Step 3: Compilar (migração roda no boot / nos testes de repo)**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. (A migração é exercida pelo teste da Task 3.)

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/sqlite/038_verification.sql src/main/java/dev/davimf/basebot/database/sqlite/SqliteMigrator.java
git commit -m "feat(db): sqlite migration for verified_members + verification_requests"
```

---

## Task 3: `VerificationRepository` (SQLite) + testes

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/security/VerificationRepository.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/security/VerificationRepositoryTest.java`

**Interfaces:**
- Consumes: `SqliteManager` (Task 2 schema).
- Produces:
  - `record Pending(String guildId, String userId, String messageId, String answers, long createdAt)`
  - `boolean isVerified(String guildId, String userId)`
  - `void markVerified(String guildId, String userId, long atMillis)`
  - `void forget(String guildId, String userId)`
  - `boolean hasPending(String guildId, String userId)`
  - `boolean openRequest(String guildId, String userId, String answers, long createdAt)` — INSERT OR IGNORE; retorna `true` se inseriu, `false` se já havia pendência.
  - `void attachMessage(String guildId, String userId, String messageId)`
  - `Optional<Pending> findPending(String guildId, String userId)`
  - `void closeRequest(String guildId, String userId)`

- [ ] **Step 1: Escrever os testes que falham**

`VerificationRepositoryTest.java`:

```java
package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VerificationRepositoryTest {

    private SqliteManager sqlite;
    private VerificationRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new VerificationRepository(sqlite);
    }

    @AfterEach
    void tearDown() {
        sqlite.close();
    }

    @Test
    void verifiedRoundTrip() {
        assertFalse(repo.isVerified("g1", "u1"));
        repo.markVerified("g1", "u1", 123L);
        assertTrue(repo.isVerified("g1", "u1"));
        assertFalse(repo.isVerified("g2", "u1")); // por servidor
        repo.forget("g1", "u1");
        assertFalse(repo.isVerified("g1", "u1"));
    }

    @Test
    void pendingIsUniquePerUser() {
        assertTrue(repo.openRequest("g1", "u1", "a", 1L));
        assertTrue(repo.hasPending("g1", "u1"));
        assertFalse(repo.openRequest("g1", "u1", "b", 2L)); // já pendente -> ignora
        assertEquals("a", repo.findPending("g1", "u1").orElseThrow().answers());
    }

    @Test
    void attachAndCloseRequest() {
        repo.openRequest("g1", "u1", "a", 1L);
        repo.attachMessage("g1", "u1", "m99");
        assertEquals("m99", repo.findPending("g1", "u1").orElseThrow().messageId());
        repo.closeRequest("g1", "u1");
        assertFalse(repo.hasPending("g1", "u1"));
        assertTrue(repo.findPending("g1", "u1").isEmpty());
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.security.VerificationRepositoryTest"`
Expected: FAIL de compilação (`VerificationRepository` não existe).

- [ ] **Step 3: Implementar o repositório**

`VerificationRepository.java`:

```java
package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Estado local da verificação: quem já passou (por servidor) e a fila de pendências
 *  (uma por pessoa). Ver migração 038_verification.sql. */
public final class VerificationRepository {

    /** Pedido pendente na fila de aprovação. */
    public record Pending(String guildId, String userId, String messageId, String answers, long createdAt) {}

    private final SqliteManager sqlite;

    public VerificationRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public boolean isVerified(String guildId, String userId) {
        String sql = "SELECT 1 FROM verified_members WHERE guild_id = ? AND user_id = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RepositoryException("isVerified " + guildId + "/" + userId, e);
        }
    }

    public void markVerified(String guildId, String userId, long atMillis) {
        String sql = "INSERT INTO verified_members (guild_id, user_id, verified_at) VALUES (?, ?, ?) "
                + "ON CONFLICT (guild_id, user_id) DO UPDATE SET verified_at = excluded.verified_at";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setLong(3, atMillis);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("markVerified " + guildId + "/" + userId, e);
        }
    }

    public void forget(String guildId, String userId) {
        exec("DELETE FROM verified_members WHERE guild_id = ? AND user_id = ?", guildId, userId,
                "forget verified");
    }

    public boolean hasPending(String guildId, String userId) {
        String sql = "SELECT 1 FROM verification_requests WHERE guild_id = ? AND user_id = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RepositoryException("hasPending " + guildId + "/" + userId, e);
        }
    }

    /** Insere o pedido; retorna false (sem inserir) se já existe pendência para o usuário. */
    public boolean openRequest(String guildId, String userId, String answers, long createdAt) {
        String sql = "INSERT OR IGNORE INTO verification_requests "
                + "(guild_id, user_id, message_id, answers, created_at) VALUES (?, ?, NULL, ?, ?)";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setString(3, answers);
            ps.setLong(4, createdAt);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("openRequest " + guildId + "/" + userId, e);
        }
    }

    public void attachMessage(String guildId, String userId, String messageId) {
        String sql = "UPDATE verification_requests SET message_id = ? WHERE guild_id = ? AND user_id = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, messageId);
            ps.setString(2, guildId);
            ps.setString(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("attachMessage " + guildId + "/" + userId, e);
        }
    }

    public Optional<Pending> findPending(String guildId, String userId) {
        String sql = "SELECT message_id, answers, created_at FROM verification_requests "
                + "WHERE guild_id = ? AND user_id = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Pending(guildId, userId,
                        rs.getString("message_id"), rs.getString("answers"), rs.getLong("created_at")));
            }
        } catch (SQLException e) {
            throw new RepositoryException("findPending " + guildId + "/" + userId, e);
        }
    }

    public void closeRequest(String guildId, String userId) {
        exec("DELETE FROM verification_requests WHERE guild_id = ? AND user_id = ?", guildId, userId,
                "closeRequest");
    }

    private void exec(String sql, String guildId, String userId, String what) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException(what + " " + guildId + "/" + userId, e);
        }
    }
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.security.VerificationRepositoryTest"`
Expected: PASS (3 testes).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/security/VerificationRepository.java src/test/java/dev/davimf/basebot/modules/base/security/VerificationRepositoryTest.java
git commit -m "feat(security): VerificationRepository (verified members + pending queue)"
```

---

## Task 4: DDL Postgres + `VerificationQuestionRepository`

Sem Postgres local, este repo (como `ActionTypeRepository`) é validado por build + fluxo manual, não por unit test.

**Files:**
- Create: `src/main/resources/db/postgres/009_verification_questions.sql`
- Create: `src/main/java/dev/davimf/basebot/database/postgres/VerificationQuestionRepository.java`

**Interfaces:**
- Consumes: `PostgresPool`.
- Produces:
  - `record Question(String id, String guildId, int position, String prompt, boolean required)`
  - `List<Question> listByGuild(String guildId)` — `ORDER BY position`
  - `int count(String guildId)`
  - `void add(Question q)`
  - `void delete(String id)`
  - `static String newId()`

- [ ] **Step 1: Escrever a DDL (o usuário roda no Neon)**

`src/main/resources/db/postgres/009_verification_questions.sql`:

```sql
-- Perguntas de verificação (Base · Segurança). Uma linha por pergunta, até 5 por guild,
-- ordenadas por position. Editadas pelo /setup e futuramente pelo dashboard; o bot lê
-- para montar o modal. Aplicar MANUALMENTE no Neon. Idempotente.
CREATE TABLE IF NOT EXISTS verification_questions (
    id          TEXT PRIMARY KEY,
    guild_id    TEXT NOT NULL,
    position    INTEGER NOT NULL DEFAULT 0,
    prompt      TEXT NOT NULL,
    required    BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_verification_questions_guild
    ON verification_questions (guild_id);
```

- [ ] **Step 2: Implementar o repositório (espelhando `ActionTypeRepository`)**

`VerificationQuestionRepository.java`:

```java
package dev.davimf.basebot.database.postgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Perguntas de verificação (config editável pelo /setup e dashboard). Uma linha por
 *  pergunta, ordenadas por position; máx. 5 por guild (imposto na UI). */
public final class VerificationQuestionRepository {

    public record Question(String id, String guildId, int position, String prompt, boolean required) {}

    private final PostgresPool pool;

    public VerificationQuestionRepository(PostgresPool pool) {
        this.pool = pool;
    }

    public List<Question> listByGuild(String guildId) {
        String sql = "SELECT * FROM verification_questions WHERE guild_id = ? ORDER BY position";
        List<Question> out = new ArrayList<>();
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
            throw new RepositoryException("list verification questions " + guildId, e);
        }
    }

    public int count(String guildId) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM verification_questions WHERE guild_id = ?")) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RepositoryException("count verification questions " + guildId, e);
        }
    }

    public void add(Question q) {
        String sql = "INSERT INTO verification_questions (id, guild_id, position, prompt, required) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, q.id());
            ps.setString(2, q.guildId());
            ps.setInt(3, q.position());
            ps.setString(4, q.prompt());
            ps.setBoolean(5, q.required());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("add verification question " + q.id(), e);
        }
    }

    public void delete(String id) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM verification_questions WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("delete verification question " + id, e);
        }
    }

    private static Question map(ResultSet rs) throws SQLException {
        return new Question(rs.getString("id"), rs.getString("guild_id"),
                rs.getInt("position"), rs.getString("prompt"), rs.getBoolean("required"));
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
```

- [ ] **Step 3: Compilar**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/postgres/009_verification_questions.sql src/main/java/dev/davimf/basebot/database/postgres/VerificationQuestionRepository.java
git commit -m "feat(security): Neon verification_questions table + repository"
```

---

## Task 5: Expor os repositórios no `DatabaseManager`

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/database/DatabaseManager.java`

**Interfaces:**
- Consumes: `VerificationRepository` (Task 3), `VerificationQuestionRepository` (Task 4), `SqliteManager`, `PostgresPool`.
- Produces: `ctx.database().verification()` → `VerificationRepository`; `ctx.database().verificationQuestions()` → `VerificationQuestionRepository`.

- [ ] **Step 1: Adicionar campos e getters**

Em `DatabaseManager.java`:

Imports (junto aos demais):
```java
import dev.davimf.basebot.modules.base.security.VerificationRepository;
import dev.davimf.basebot.database.postgres.VerificationQuestionRepository;
```

Campos (após `messageArchive`):
```java
    private final VerificationRepository verification;
    private final VerificationQuestionRepository verificationQuestions;
```

No construtor (após `this.messageArchive = ...`):
```java
        this.verification = new VerificationRepository(sqlite);
        this.verificationQuestions = new VerificationQuestionRepository(postgres);
```

Getters (junto aos demais):
```java
    public VerificationRepository verification() {
        return verification;
    }

    public VerificationQuestionRepository verificationQuestions() {
        return verificationQuestions;
    }
```

- [ ] **Step 2: Compilar**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/davimf/basebot/database/DatabaseManager.java
git commit -m "feat(db): expose verification repositories from DatabaseManager"
```

---

## Task 6: Auto-cargo na re-entrada (`VerificationListener`)

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/security/VerificationListener.java`

**Interfaces:**
- Consumes: `ctx.database().verification().isVerified(...)`, `SecurityConfig.verify(...)`, cargos `membro`/`nao-verificado`.

- [ ] **Step 1: Reescrever `onGuildMemberJoin`**

Substitua o corpo do método por:

```java
    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!SecurityConfig.verify(cfg)) {
            return;
        }
        var guild = event.getGuild();
        // Já verificado neste servidor (e não expulso desde então): concede o cargo direto.
        if (ctx.database().verification().isVerified(guild.getId(), event.getUser().getId())) {
            String memberId = cfg.role("membro");
            Role member = memberId == null ? null : guild.getRoleById(memberId);
            if (member != null && guild.getSelfMember().canInteract(member)) {
                guild.addRoleToMember(event.getMember(), member)
                        .reason("Verificação: auto (re-entrada)").queue(ok -> {}, err -> {});
            }
            return; // não aplica o gate
        }
        String roleId = cfg.role("nao-verificado");
        Role role = roleId == null ? null : guild.getRoleById(roleId);
        if (role != null && guild.getSelfMember().canInteract(role)) {
            guild.addRoleToMember(event.getMember(), role)
                    .reason("Verificação: aguardando").queue(ok -> {}, err -> {});
        }
    }
```

- [ ] **Step 2: Compilar**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/security/VerificationListener.java
git commit -m "feat(security): auto-grant member role for previously verified rejoiners"
```

---

## Task 7: Reset da verificação em kick/ban (`VerificationResetListener`)

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/security/VerificationResetListener.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java:275` (registrar após o `AntiNukeListener`)

**Interfaces:**
- Consumes: `NukeAuditLookup.resolveActor(...)`, `ctx.database().verification().forget(...)`, `SecurityConfig.verify(...)`.

- [ ] **Step 1: Criar o listener**

`VerificationResetListener.java`:

```java
package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Esquece a verificação quando a pessoa é EXPULSA (kick, confirmado no audit log) ou
 *  banida. Saída voluntária mantém a verificação (auto-cargo na re-entrada). */
public final class VerificationResetListener extends ListenerAdapter {

    private final BotContext ctx;

    public VerificationResetListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!SecurityConfig.verify(cfg)) {
            return;
        }
        if (!event.getGuild().getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
            return; // sem audit log não dá pra distinguir kick de saída voluntária
        }
        String userId = event.getUser().getId();
        // resolveActor só chama de volta se houver um KICK recente desse alvo no audit log.
        NukeAuditLookup.resolveActor(event.getGuild(), userId, ActionType.KICK, ctx,
                actor -> ctx.database().verification().forget(event.getGuild().getId(), userId));
    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        ctx.database().verification().forget(event.getGuild().getId(), event.getUser().getId());
    }
}
```

- [ ] **Step 2: Registrar no `BaseModule`**

Em `BaseModule.java`, logo após a linha `registry.listener(new dev.davimf.basebot.modules.base.security.AntiNukeListener(ctx));`:

```java
        // Segurança: reset da verificação quando o membro é expulso/banido.
        registry.listener(new dev.davimf.basebot.modules.base.security.VerificationResetListener(ctx));
```

- [ ] **Step 3: Compilar**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/security/VerificationResetListener.java src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(security): forget verification on moderator kick/ban"
```

---

## Task 8: Fluxo de verificação + fila de aprovação (`SecurityComponentHandler`)

Reescreve o botão `verify` para: checar pendência → seleção de usuário e/ou modal → postar na fila. Adiciona a seleção `vusers`, o modal `vsubmit`, e os botões `vapprove`/`vreject`. O handler passa a implementar seleção e modal além de botão.

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/security/SecurityComponentHandler.java`

**Interfaces:**
- Consumes: `ctx.database().verification()`, `ctx.database().verificationQuestions()`, `SecurityConfig.verify/verifyUserSelect/CHANNEL_VERIFY`, `VerificationQuestionRepository.Question`.
- Produces (custom ids no namespace `sec`): `vusers`, `vsubmit:<userIdsJoined>`, `vapprove:<userId>`, `vreject:<userId>`.

- [ ] **Step 1: Ampliar imports e assinatura do handler**

No topo de `SecurityComponentHandler.java`, adicione imports:

```java
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.postgres.VerificationQuestionRepository;
import dev.davimf.basebot.core.component.Panels;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import java.util.List;
import java.util.stream.Collectors;
```

(Alguns imports como `ComponentId`/`Emojis`/`Replies`/`Member`/`Role`/`Permission`/`GuildConfig` já existem — não duplique.)

- [ ] **Step 2: Rotear o novo botão e os botões da fila**

No `switch (id.action())` de `onButton`, troque o caso `verify` e adicione os da fila:

```java
            case "verify" -> verifyStart(event, ctx);
            case "vapprove" -> approve(event, ctx, id.arg(0));
            case "vreject" -> reject(event, ctx, id.arg(0));
```

(Remova o método `verify(...)` antigo; ele é substituído por `verifyStart` + `finalizeRequest` abaixo.)

- [ ] **Step 3: Implementar o início do fluxo**

Adicione ao handler:

```java
    private void verifyStart(ButtonInteractionEvent event, BotContext ctx) {
        var guild = event.getGuild();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        if (!SecurityConfig.verify(cfg)) {
            Replies.ephemeral(event, ctx, "A verificação não está ativa.");
            return;
        }
        String userId = event.getUser().getId();
        if (ctx.database().verification().isVerified(guild.getId(), userId)) {
            Replies.ephemeral(event, ctx, "Você já está verificado neste servidor.");
            return;
        }
        if (ctx.database().verification().hasPending(guild.getId(), userId)) {
            Replies.ephemeral(event, ctx, "Você já tem um pedido em análise, aguarde um moderador.");
            return;
        }
        if (cfg.channel(SecurityConfig.CHANNEL_VERIFY) == null) {
            Replies.ephemeral(event, ctx, "A verificação não tem canal de aprovação configurado. Avise a administração.");
            return;
        }
        List<VerificationQuestionRepository.Question> questions =
                ctx.database().verificationQuestions().listByGuild(guild.getId());
        boolean userSelect = SecurityConfig.verifyUserSelect(cfg);

        if (userSelect) {
            // 1º passo: seleção de usuário (o modal, se houver, vem depois).
            EntitySelectMenu menu = EntitySelectMenu
                    .create(ComponentId.of(AntiRaidService.NS, "vusers"), EntitySelectMenu.SelectTarget.USER)
                    .setPlaceholder("Marque quem você conhece aqui (opcional)")
                    .setRequiredRange(0, 4)
                    .build();
            event.replyComponents(ActionRow.of(menu)).setEphemeral(true).queue();
            return;
        }
        if (!questions.isEmpty()) {
            event.replyModal(verifyModal("", questions)).queue();
            return;
        }
        // Sem perguntas nem seleção: pedido direto.
        finalizeRequest(event, ctx, "*(sem perguntas)*");
    }
```

- [ ] **Step 4: Implementar a seleção de usuário e o modal**

Adicione os métodos `onEntitySelect`/`onModal` ao handler (a interface `ComponentHandler` tem defaults; sobrescrevemos):

```java
    @Override
    public void onEntitySelect(EntitySelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null || !"vusers".equals(id.action())) {
            return;
        }
        String users = event.getMentions().getUsers().stream()
                .limit(4).map(u -> u.getId()).collect(Collectors.joining("-"));
        List<VerificationQuestionRepository.Question> questions =
                ctx.database().verificationQuestions().listByGuild(event.getGuild().getId());
        if (!questions.isEmpty()) {
            event.replyModal(verifyModal(users, questions)).queue();
        } else {
            finalizeRequest(event, ctx, renderAnswers(event.getGuild(), users, List.of(), List.of()));
        }
    }

    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null || !"vsubmit".equals(id.action())) {
            return;
        }
        String users = id.arg(0);
        List<VerificationQuestionRepository.Question> questions =
                ctx.database().verificationQuestions().listByGuild(event.getGuild().getId());
        List<String> answers = new java.util.ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            var v = event.getValue("q" + i);
            answers.add(v == null ? "" : v.getAsString());
        }
        List<String> prompts = questions.stream().map(VerificationQuestionRepository.Question::prompt).toList();
        finalizeRequest(event, ctx, renderAnswers(event.getGuild(), users, prompts, answers));
    }

    private Modal verifyModal(String users, List<VerificationQuestionRepository.Question> questions) {
        Modal.Builder b = Modal.create(ComponentId.of(AntiRaidService.NS, "vsubmit", users), "Verificação");
        for (int i = 0; i < questions.size() && i < 5; i++) {
            var q = questions.get(i);
            b.addComponents(ActionRow.of(TextInput
                    .create("q" + i, q.prompt(), TextInputStyle.PARAGRAPH)
                    .setRequired(q.required())
                    .setMaxLength(300)
                    .build()));
        }
        return b.build();
    }
```

- [ ] **Step 5: Implementar render das respostas e a postagem na fila**

`finalizeRequest` aceita tanto `ButtonInteractionEvent`, `EntitySelectInteractionEvent` quanto `ModalInteractionEvent`; use o tipo comum `IReplyCallback` + `getUser()`/`getGuild()`.

```java
    private String renderAnswers(net.dv8tion.jda.api.entities.Guild guild, String usersJoined,
                                 List<String> prompts, List<String> answers) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < prompts.size(); i++) {
            String a = i < answers.size() && answers.get(i) != null && !answers.get(i).isBlank()
                    ? answers.get(i) : "*(vazio)*";
            sb.append("**").append(prompts.get(i)).append("**\n> ").append(a).append('\n');
        }
        if (usersJoined != null && !usersJoined.isBlank()) {
            String mentions = java.util.Arrays.stream(usersJoined.split("-"))
                    .filter(s -> !s.isBlank()).map(s -> "<@" + s + ">").collect(Collectors.joining(", "));
            sb.append("**Conhece:** ").append(mentions).append('\n');
        }
        return sb.length() == 0 ? "*(sem respostas)*" : sb.toString();
    }

    private void finalizeRequest(net.dv8tion.jda.api.interactions.callbacks.IReplyCallback event,
                                 BotContext ctx, String answers) {
        var guild = ((net.dv8tion.jda.api.interactions.Interaction) event).getGuild();
        var user = event.getUser();
        boolean opened = ctx.database().verification()
                .openRequest(guild.getId(), user.getId(), answers, System.currentTimeMillis());
        if (!opened) {
            event.reply("Você já tem um pedido em análise, aguarde um moderador.").setEphemeral(true).queue();
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        String chId = cfg.channel(SecurityConfig.CHANNEL_VERIFY);
        GuildMessageChannel ch = chId == null ? null : guild.getChannelById(GuildMessageChannel.class, chId);
        if (ch == null) {
            ctx.database().verification().closeRequest(guild.getId(), user.getId());
            event.reply("Canal de aprovação indisponível. Avise a administração.").setEphemeral(true).queue();
            return;
        }
        int accent = dev.davimf.basebot.util.EmbedColor.resolve(cfg);
        Container panel = Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.CHECK_YES, "✅") + " Pedido de verificação"),
                Panels.divider(),
                Panels.text("Candidato: " + user.getAsMention() + " · `" + user.getId() + "`"),
                Panels.text(answers),
                ActionRow.of(
                        Button.success(ComponentId.of(AntiRaidService.NS, "vapprove", user.getId()), "Aprovar")
                                .withEmoji(Emojis.button(Emojis.CHECK_YES)),
                        Button.danger(ComponentId.of(AntiRaidService.NS, "vreject", user.getId()), "Recusar")
                                .withEmoji(Emojis.button(Emojis.CHECK_NO))));
        String gid = guild.getId();
        String uid = user.getId();
        ch.sendMessageComponents(panel).useComponentsV2().queue(
                msg -> ctx.database().verification().attachMessage(gid, uid, msg.getId()),
                err -> {});
        event.reply(Emojis.of(Emojis.CHECK_YES, "✅") + " Pedido enviado! Aguarde a análise de um moderador.")
                .setEphemeral(true).queue();
    }
```

> Nota: se `Emojis.CHECK_NO` não existir no registro, use `Emojis.WARN` no botão Recusar (confira `Emojis` antes; mantenha o padrão de emoji custom).

- [ ] **Step 6: Implementar Aprovar/Recusar**

```java
    private void approve(ButtonInteractionEvent event, BotContext ctx, String targetId) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            Replies.ephemeral(event, ctx, "Apenas quem tem **Gerenciar Servidor** pode aprovar.");
            return;
        }
        var guild = event.getGuild();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        String memberRoleId = cfg.role("membro");
        Role memberRole = memberRoleId == null ? null : guild.getRoleById(memberRoleId);
        if (memberRole == null || !guild.getSelfMember().canInteract(memberRole)) {
            Replies.ephemeral(event, ctx, "Cargo de **membro** não configurado ou acima do meu cargo.");
            return;
        }
        guild.retrieveMemberById(targetId).queue(m -> {
            guild.addRoleToMember(m, memberRole).reason("Verificação aprovada por " + event.getUser().getName()).queue();
            String unvId = cfg.role("nao-verificado");
            Role unv = unvId == null ? null : guild.getRoleById(unvId);
            if (unv != null && m.getRoles().contains(unv) && guild.getSelfMember().canInteract(unv)) {
                guild.removeRoleFromMember(m, unv).reason("Verificação aprovada").queue();
            }
        }, err -> {});
        ctx.database().verification().markVerified(guild.getId(), targetId, System.currentTimeMillis());
        ctx.database().verification().closeRequest(guild.getId(), targetId);
        event.editComponents(Panels.container(dev.davimf.basebot.util.EmbedColor.resolve(cfg),
                Panels.text("## " + Emojis.of(Emojis.CHECK_YES, "✅") + " Verificação aprovada"),
                Panels.divider(),
                Panels.text("<@" + targetId + "> aprovado por " + event.getUser().getAsMention() + "."))).queue();
    }

    private void reject(ButtonInteractionEvent event, BotContext ctx, String targetId) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            Replies.ephemeral(event, ctx, "Apenas quem tem **Gerenciar Servidor** pode recusar.");
            return;
        }
        var guild = event.getGuild();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        ctx.database().verification().closeRequest(guild.getId(), targetId);
        event.editComponents(Panels.container(dev.davimf.basebot.util.EmbedColor.resolve(cfg),
                Panels.text("## " + Emojis.of(Emojis.WARN, "⚠️") + " Verificação recusada"),
                Panels.divider(),
                Panels.text("<@" + targetId + "> recusado por " + event.getUser().getAsMention() + ". Pode tentar de novo."))).queue();
    }
```

- [ ] **Step 7: Verificar assinaturas contra o código real**

Confira, no `ComponentHandler`, as assinaturas exatas de `onEntitySelect`/`onModal` (parâmetros e se são `default`) e ajuste `@Override` se necessário. Confira `Panels.container(int, ...)`, `Panels.text`, `Panels.divider`, e que `event.editComponents(...)` aceita um `Container` V2 (senão use `.useComponentsV2()` no fluxo equivalente ou `editMessageComponents`). Ajuste os nomes de emoji (`CHECK_NO`) conforme o `Emojis` real.

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL (após ajustes de assinatura).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/security/SecurityComponentHandler.java
git commit -m "feat(security): verification questions modal + user-select + approval queue"
```

---

## Task 9: UI de verificação no `/setup → Segurança`

Sub-tela dedicada de verificação: toggle, toggle de seleção de usuário, editar perguntas, definir canal de aprovação, publicar painel.

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupView.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java`

**Interfaces:**
- Consumes: `SecurityConfig.verify/verifyUserSelect/CHANNEL_VERIFY`, `ctx.database().verificationQuestions()`, `channelSelect(action, key, placeholder, currentId)`.
- Produces: nav `secnav:verify`; ações `sectoggle:verify`, `sectoggle:verifyuser`, `verifyqadd`, `verifyqdel:<id>`, `verifychan` (EntitySelect), `verifypanel`.

- [ ] **Step 1: Adicionar a sub-tela em `SetupView`**

Adicione um método `verificationScreen`:

```java
    public static Container verificationScreen(GuildConfig cfg,
            List<dev.davimf.basebot.database.postgres.VerificationQuestionRepository.Question> questions) {
        int accent = EmbedColor.resolve(cfg);
        boolean on = dev.davimf.basebot.modules.base.security.SecurityConfig.verify(cfg);
        boolean us = dev.davimf.basebot.modules.base.security.SecurityConfig.verifyUserSelect(cfg);
        String chId = cfg.channel(dev.davimf.basebot.modules.base.security.SecurityConfig.CHANNEL_VERIFY);
        StringBuilder q = new StringBuilder();
        for (var item : questions) {
            q.append("- `").append(item.prompt()).append("`\n");
        }
        if (questions.isEmpty()) {
            q.append("-# Nenhuma pergunta (clique Verificar concede sem formulário).\n");
        }
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.CHECK_YES, "✅") + " Verificação"),
                Panels.divider(),
                Panels.text(Emojis.of(Emojis.CHECK_YES, "✅") + " **Ativa** · " + (on ? "sim" : "não") + "\n"
                        + Emojis.of(Emojis.SHIELD, "🛡️") + " **Seleção de usuário** · " + (us ? "sim" : "não") + "\n"
                        + "**Canal de aprovação** · " + (chId == null ? "não definido" : "<#" + chId + ">")),
                Panels.divider(),
                Panels.text("**Perguntas** (máx. 5)\n" + q),
                ActionRow.of(dev.davimf.basebot.modules.base.setup.SetupViewChannels
                        .verifyChannelSelect(chId)),
                ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "sectoggle", "verify"), "Ativar: " + (on ? "on" : "off")),
                        Button.secondary(ComponentId.of(NS, "sectoggle", "verifyuser"), "Seleção usuário: " + (us ? "on" : "off")),
                        Button.primary(ComponentId.of(NS, "verifyqadd"), "Adicionar pergunta").withEmoji(Emojis.button(Emojis.EDIT))),
                ActionRow.of(
                        Button.primary(ComponentId.of(NS, "verifypanel"), "Publicar painel").withEmoji(Emojis.button(Emojis.SEND)),
                        Button.secondary(ComponentId.of(NS, "seguranca"), "Voltar")));
    }
```

> Nota: use o helper `channelSelect(...)` que já existe em `SetupView` (privado) diretamente aqui em vez de `SetupViewChannels`; o exemplo acima é ilustrativo. O correto é: `ActionRow.of(channelSelect("verifychan", SecurityConfig.CHANNEL_VERIFY, "Canal de aprovação", chId))`. Ajuste o import de `SecurityConfig` conforme o restante do arquivo (`dev.davimf.basebot.modules.base.security.SecurityConfig`).

Corrija a linha do canal para:

```java
                ActionRow.of(channelSelect("verifychan",
                        dev.davimf.basebot.modules.base.security.SecurityConfig.CHANNEL_VERIFY,
                        "Canal de aprovação", chId)),
```

E adicione, se faltar, o botão que abre esta tela na `securityScreen` existente (na action row da verificação, troque o `verifypanel` inline por um nav):

```java
                        Button.primary(ComponentId.of(NS, "secnav", "verify"), "Verificação").withEmoji(Emojis.button(Emojis.CHECK_YES)),
```

- [ ] **Step 2: Modal de adicionar pergunta em `SetupView`**

```java
    public static Modal verifyQuestionModal() {
        return Modal.create(ComponentId.of(NS, "verifyqform"), "Nova pergunta de verificação")
                .addComponents(ActionRow.of(TextInput
                        .create("prompt", "Pergunta (máx. 45 caracteres)", TextInputStyle.SHORT)
                        .setRequired(true).setMaxLength(45).build()))
                .build();
    }
```

- [ ] **Step 3: Rotear no `SetupComponentHandler`**

No `switch` de `onButton`:

```java
            case "secnav" -> {
                if ("verify".equals(id.arg(0))) {
                    edit(event, SetupView.verificationScreen(config(ctx, guildId),
                            ctx.database().verificationQuestions().listByGuild(guildId)));
                }
            }
            case "verifyqadd" -> {
                if (ctx.database().verificationQuestions().count(guildId) >= 5) {
                    Replies.ephemeral(event, ctx, "Máximo de 5 perguntas.");
                } else {
                    event.replyModal(SetupView.verifyQuestionModal()).queue();
                }
            }
            case "verifyqdel" -> {
                ctx.database().verificationQuestions().delete(id.arg(0));
                edit(event, SetupView.verificationScreen(config(ctx, guildId),
                        ctx.database().verificationQuestions().listByGuild(guildId)));
            }
```

No `sectoggle`, adicione o arg `verifyuser` ao `switch (arg)` de chaves:

```java
                    case "verifyuser" -> dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_VERIFY_USERSELECT;
```

E, no fim do bloco `sectoggle`, o `edit` re-renderiza `securityScreen`. Para os args de verificação, re-renderize a sub-tela. Ajuste o final do case `sectoggle` para:

```java
                if ("verify".equals(arg) || "verifyuser".equals(arg)) {
                    edit(event, SetupView.verificationScreen(updated,
                            ctx.database().verificationQuestions().listByGuild(guildId)));
                } else {
                    edit(event, SetupView.securityScreen(updated));
                }
```

(Mantenha o `AutoModManager.sync(...)` como está para os args de automod.)

- [ ] **Step 4: Salvar a pergunta (modal) e o canal (select)**

No `onModal`, adicione o roteamento (perto dos outros `if`/`case`):

```java
        if ("verifyqform".equals(id.action())) {
            saveVerifyQuestion(event, ctx);
            return;
        }
```

E o método:

```java
    private void saveVerifyQuestion(ModalInteractionEvent event, BotContext ctx) {
        String guildId = event.getGuild().getId();
        String prompt = value(event, "prompt");
        if (prompt != null && !prompt.isBlank()) {
            int pos = ctx.database().verificationQuestions().count(guildId);
            ctx.database().verificationQuestions().add(
                    new dev.davimf.basebot.database.postgres.VerificationQuestionRepository.Question(
                            dev.davimf.basebot.database.postgres.VerificationQuestionRepository.newId(),
                            guildId, pos, prompt.trim(), true));
        }
        edit(event, SetupView.verificationScreen(config(ctx, guildId),
                ctx.database().verificationQuestions().listByGuild(guildId)));
    }
```

No `onEntitySelect`, adicione:

```java
            case "verifychan" -> {
                saveChannel(event, ctx, id.arg(0), firstChannelId(event));
                edit(event, SetupView.verificationScreen(config(ctx, event.getGuild().getId()),
                        ctx.database().verificationQuestions().listByGuild(event.getGuild().getId())));
            }
```

- [ ] **Step 5: Compilar**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL (ajuste imports de `Modal`/`TextInput`/`TextInputStyle` em `SetupView` se faltarem).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/setup/SetupView.java src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java
git commit -m "feat(setup): verification sub-screen (questions, user-select, approval channel)"
```

---

# PARTE 2 — CANAL ANTI-SPAM

## Task 10: Config + helper de isenção do anti-spam

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/security/SecurityConfig.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/security/SecurityConfigTest.java`

**Interfaces:**
- Produces: `SecurityConfig.KEY_ANTISPAM`, `SecurityConfig.CHANNEL_ANTISPAM` (`"anti-spam"`), `boolean antispam(GuildConfig)` (default false), `boolean isSpamExempt(GuildConfig cfg, boolean bot, boolean owner, boolean admin, Collection<String> roleIds)`.

- [ ] **Step 1: Testes que falham**

Em `SecurityConfigTest.java`:

```java
    @Test
    void antispamDefaultsFalse() {
        assertFalse(SecurityConfig.antispam(cfg(Map.of(), Map.of())));
        assertTrue(SecurityConfig.antispam(cfg(Map.of(), Map.of(SecurityConfig.KEY_ANTISPAM, true))));
    }

    @Test
    void spamExemptionRules() {
        GuildConfig c = cfg(Map.of(SecurityConfig.KEY_EXEMPT_ROLES, "555"), Map.of());
        assertTrue(SecurityConfig.isSpamExempt(c, true, false, false, List.of()));   // bot
        assertTrue(SecurityConfig.isSpamExempt(c, false, true, false, List.of()));   // owner
        assertTrue(SecurityConfig.isSpamExempt(c, false, false, true, List.of()));   // admin
        assertTrue(SecurityConfig.isSpamExempt(c, false, false, false, List.of("555"))); // cargo isento
        assertFalse(SecurityConfig.isSpamExempt(c, false, false, false, List.of("1")));  // membro comum
    }
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.security.SecurityConfigTest"`
Expected: FAIL de compilação.

- [ ] **Step 3: Implementar em `SecurityConfig`**

Adicione uma seção nova:

```java
    // Anti-spam (canal-armadilha)
    public static final String KEY_ANTISPAM = "sec:antispam";
    /** Nome lógico do canal-armadilha em guild_config.channels. */
    public static final String CHANNEL_ANTISPAM = "anti-spam";
    /** Quantas mensagens recentes do autor apagar ao punir. */
    public static final int ANTISPAM_PURGE = 10;

    public static boolean antispam(GuildConfig cfg) { return cfg.toggle(KEY_ANTISPAM, false); }

    /** True quando o autor não deve ser punido pelo anti-spam. */
    public static boolean isSpamExempt(GuildConfig cfg, boolean bot, boolean owner, boolean admin,
                                       Collection<String> roleIds) {
        if (bot || owner || admin) {
            return true;
        }
        Set<String> exempt = exemptRoleIds(cfg);
        for (String r : roleIds) {
            if (exempt.contains(r)) {
                return true;
            }
        }
        return false;
    }
```

(`Collection`/`Set` já estão importados no arquivo.)

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.security.SecurityConfigTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/security/SecurityConfig.java src/test/java/dev/davimf/basebot/modules/base/security/SecurityConfigTest.java
git commit -m "feat(security): anti-spam config keys + exemption helper"
```

---

## Task 11: `AntiSpamService` (helper puro + kick + purga) + teste do helper

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/security/AntiSpamService.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/security/AntiSpamServiceTest.java`

**Interfaces:**
- Produces:
  - `static <T> List<T> selectMostRecent(List<T> items, ToLongFunction<T> epochMillis, int limit)` — ordena desc por timestamp e corta em `limit`.
  - `static void handle(BotContext ctx, Guild guild, Member author)` — kick + purga assíncrona (JDA; sem unit test).

- [ ] **Step 1: Teste do helper puro**

`AntiSpamServiceTest.java`:

```java
package dev.davimf.basebot.modules.base.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToLongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AntiSpamServiceTest {

    @Test
    void selectsTenMostRecentDescending() {
        List<Long> ts = List.of(1L, 5L, 3L, 9L, 2L, 8L, 4L, 7L, 6L, 10L, 11L, 0L);
        ToLongFunction<Long> id = Long::longValue;
        List<Long> got = AntiSpamService.selectMostRecent(ts, id, 10);
        assertEquals(List.of(11L, 10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L, 2L), got);
    }

    @Test
    void returnsAllWhenFewerThanLimit() {
        List<Long> ts = List.of(3L, 1L, 2L);
        List<Long> got = AntiSpamService.selectMostRecent(ts, Long::longValue, 10);
        assertEquals(List.of(3L, 2L, 1L), got);
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.security.AntiSpamServiceTest"`
Expected: FAIL de compilação.

- [ ] **Step 3: Implementar o serviço**

`AntiSpamService.java`:

```java
package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;

/** Anti-spam do canal-armadilha: expulsa o autor e apaga suas N mensagens mais recentes
 *  varrendo os canais de texto visíveis. A seleção das mais recentes é pura e testável. */
public final class AntiSpamService {

    private AntiSpamService() {}

    /** Ordena {@code items} do mais recente (maior timestamp) ao mais antigo e retorna até {@code limit}. */
    public static <T> List<T> selectMostRecent(List<T> items, ToLongFunction<T> epochMillis, int limit) {
        List<T> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingLong(epochMillis).reversed());
        return sorted.size() > limit ? new ArrayList<>(sorted.subList(0, limit)) : sorted;
    }

    /** Expulsa {@code author} e apaga suas mensagens recentes. Best-effort e assíncrono. */
    public static void handle(BotContext ctx, Guild guild, Member author) {
        String reason = "Anti-spam: mensagem no canal proibido";
        if (guild.getSelfMember().hasPermission(Permission.KICK_MEMBERS)
                && guild.getSelfMember().canInteract(author)) {
            purgeThenKick(ctx, guild, author, reason);
        } else {
            purge(ctx, guild, author.getId());
        }
    }

    private static void purgeThenKick(BotContext ctx, Guild guild, Member author, String reason) {
        // Apaga primeiro (o membro ainda existe), depois expulsa.
        purge(ctx, guild, author.getId());
        guild.kick(author).reason(reason).queue(ok -> {}, err -> {});
    }

    private static void purge(BotContext ctx, Guild guild, String authorId) {
        List<Message> collected = new ArrayList<>();
        List<TextChannel> channels = guild.getTextChannels().stream()
                .filter(ch -> guild.getSelfMember().hasPermission(ch,
                        Permission.MESSAGE_HISTORY, Permission.MESSAGE_MANAGE))
                .toList();
        collectRecursive(ctx, guild, authorId, channels, 0, collected);
    }

    private static void collectRecursive(BotContext ctx, Guild guild, String authorId,
                                         List<TextChannel> channels, int idx, List<Message> acc) {
        if (idx >= channels.size()) {
            deleteSelected(guild, acc);
            return;
        }
        TextChannel ch = channels.get(idx);
        ch.getHistory().retrievePast(50).queue(msgs -> {
            for (Message m : msgs) {
                if (m.getAuthor().getId().equals(authorId)) {
                    acc.add(m);
                }
            }
            collectRecursive(ctx, guild, authorId, channels, idx + 1, acc);
        }, err -> collectRecursive(ctx, guild, authorId, channels, idx + 1, acc));
    }

    private static void deleteSelected(Guild guild, List<Message> all) {
        List<Message> recent = selectMostRecent(all, m -> m.getTimeCreated().toInstant().toEpochMilli(),
                SecurityConfig.ANTISPAM_PURGE);
        for (Message m : recent) {
            m.delete().reason("Anti-spam: limpeza").queue(ok -> {}, err -> {});
        }
    }
}
```

> Nota: confira o import/nome de `TextChannel` (`net.dv8tion.jda.api.entities.channel.concrete.TextChannel`) e `guild.kick(Member)` na versão de JDA do projeto (ver `ModerationService`/`AntiNukeService` para a assinatura exata de kick). Ajuste se o projeto usa `guild.kick(UserSnowflake)`.

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.security.AntiSpamServiceTest"`
Expected: PASS (2 testes).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/security/AntiSpamService.java src/test/java/dev/davimf/basebot/modules/base/security/AntiSpamServiceTest.java
git commit -m "feat(security): AntiSpamService (kick + cross-channel purge of 10 recent)"
```

---

## Task 12: `AntiSpamView` (aviso permanente)

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/security/AntiSpamView.java`

**Interfaces:**
- Produces: `static Container panel(int accent)`.

- [ ] **Step 1: Criar a view (espelhando `VerificationView`)**

```java
package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.container.Container;

/** Aviso permanente do canal anti-spam: deixa claro que qualquer mensagem = expulsão. */
public final class AntiSpamView {

    private AntiSpamView() {}

    public static Container panel(int accent) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.WARN, "⚠️") + " Canal monitorado"),
                Panels.divider(),
                Panels.text("**NÃO envie mensagens neste canal.**"),
                Panels.text("> Qualquer mensagem aqui resulta em **expulsão automática** do servidor "
                        + "e remoção das suas mensagens recentes."));
    }
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/security/AntiSpamView.java
git commit -m "feat(security): AntiSpamView standing warning panel"
```

---

## Task 13: `AntiSpamListener` + registro

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/security/AntiSpamListener.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java` (após o `VerificationResetListener`)

**Interfaces:**
- Consumes: `SecurityConfig.antispam/CHANNEL_ANTISPAM/isSpamExempt`, `AntiSpamService.handle(...)`.

- [ ] **Step 1: Criar o listener**

```java
package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;

/** Canal-armadilha anti-spam: qualquer mensagem de membro não isento → kick + purga. */
public final class AntiSpamListener extends ListenerAdapter {

    private final BotContext ctx;

    public AntiSpamListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.isWebhookMessage()) {
            return;
        }
        var guild = event.getGuild();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        if (!SecurityConfig.antispam(cfg)) {
            return;
        }
        String trap = cfg.channel(SecurityConfig.CHANNEL_ANTISPAM);
        if (trap == null || !trap.equals(event.getChannel().getId())) {
            return;
        }
        Member author = event.getMember();
        if (author == null) {
            return;
        }
        boolean bot = event.getAuthor().isBot();
        boolean owner = author.isOwner();
        boolean admin = author.hasPermission(Permission.ADMINISTRATOR);
        List<String> roleIds = author.getRoles().stream().map(ISnowflake::getId).toList();
        if (SecurityConfig.isSpamExempt(cfg, bot, owner, admin, roleIds)) {
            return;
        }
        AntiSpamService.handle(ctx, guild, author);
    }
}
```

- [ ] **Step 2: Registrar no `BaseModule`**

Após a linha do `VerificationResetListener` (Task 7):

```java
        // Segurança: canal-armadilha anti-spam (kick + purga de mensagens recentes).
        registry.listener(new dev.davimf.basebot.modules.base.security.AntiSpamListener(ctx));
```

- [ ] **Step 3: Compilar**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/security/AntiSpamListener.java src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(security): anti-spam trap channel listener"
```

---

## Task 14: UI do anti-spam no `/setup → Segurança`

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupView.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java`

**Interfaces:**
- Consumes: `SecurityConfig.antispam/CHANNEL_ANTISPAM`, `AntiSpamView.panel`, `channelSelect(...)`.
- Produces: nav `secnav:antispam`; ações `sectoggle:antispam`, `antispamchan` (EntitySelect), `antispampanel`.

- [ ] **Step 1: Sub-tela em `SetupView`**

```java
    public static Container antispamScreen(GuildConfig cfg) {
        int accent = EmbedColor.resolve(cfg);
        boolean on = dev.davimf.basebot.modules.base.security.SecurityConfig.antispam(cfg);
        String chId = cfg.channel(dev.davimf.basebot.modules.base.security.SecurityConfig.CHANNEL_ANTISPAM);
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.WARN, "⚠️") + " Anti-spam (canal-armadilha)"),
                Panels.divider(),
                Panels.text(Emojis.of(Emojis.SHIELD, "🛡️") + " **Ativo** · " + (on ? "sim" : "não") + "\n"
                        + "**Canal** · " + (chId == null ? "não definido" : "<#" + chId + ">") + "\n"
                        + "-# Qualquer mensagem no canal → kick + apaga as 10 mensagens recentes do autor."),
                ActionRow.of(channelSelect("antispamchan",
                        dev.davimf.basebot.modules.base.security.SecurityConfig.CHANNEL_ANTISPAM,
                        "Canal-armadilha", chId)),
                ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "sectoggle", "antispam"), "Ativar: " + (on ? "on" : "off")),
                        Button.primary(ComponentId.of(NS, "antispampanel"), "Publicar aviso").withEmoji(Emojis.button(Emojis.SEND)),
                        Button.secondary(ComponentId.of(NS, "seguranca"), "Voltar")));
    }
```

E adicione o botão de nav na `securityScreen` (numa action row de segurança):

```java
                        Button.primary(ComponentId.of(NS, "secnav", "antispam"), "Anti-spam").withEmoji(Emojis.button(Emojis.WARN)),
```

- [ ] **Step 2: Rotear no `SetupComponentHandler`**

No case `secnav` (Task 9 Step 3), adicione o ramo antispam:

```java
            case "secnav" -> {
                if ("verify".equals(id.arg(0))) {
                    edit(event, SetupView.verificationScreen(config(ctx, guildId),
                            ctx.database().verificationQuestions().listByGuild(guildId)));
                } else if ("antispam".equals(id.arg(0))) {
                    edit(event, SetupView.antispamScreen(config(ctx, guildId)));
                }
            }
            case "antispampanel" -> publishAntispam(event, ctx, guildId);
```

No `sectoggle`, adicione a chave:

```java
                    case "antispam" -> dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_ANTISPAM;
```

E no final do `sectoggle` (onde escolhe a tela a re-renderizar), inclua o antispam:

```java
                if ("verify".equals(arg) || "verifyuser".equals(arg)) {
                    edit(event, SetupView.verificationScreen(updated,
                            ctx.database().verificationQuestions().listByGuild(guildId)));
                } else if ("antispam".equals(arg)) {
                    edit(event, SetupView.antispamScreen(updated));
                } else {
                    edit(event, SetupView.securityScreen(updated));
                }
```

- [ ] **Step 3: Publicar o aviso + salvar canal**

Método de publicação (espelhando `publishVerify`):

```java
    private void publishAntispam(ButtonInteractionEvent event, BotContext ctx, String guildId) {
        if (!(event.getChannel() instanceof net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel ch)) {
            Replies.ephemeral(event, ctx, "Use num canal de texto.");
            return;
        }
        int accent = EmbedColor.resolve(config(ctx, guildId));
        ch.sendMessageComponents(dev.davimf.basebot.modules.base.security.AntiSpamView.panel(accent))
                .useComponentsV2()
                .queue(ok -> Replies.ephemeral(event, ctx, "Aviso do anti-spam publicado."),
                        err -> Replies.ephemeral(event, ctx, "Falha ao publicar: " + err.getMessage()));
    }
```

No `onEntitySelect`, adicione:

```java
            case "antispamchan" -> {
                saveChannel(event, ctx, id.arg(0), firstChannelId(event));
                edit(event, SetupView.antispamScreen(config(ctx, event.getGuild().getId())));
            }
```

- [ ] **Step 4: Compilar + rodar toda a suíte**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL; todos os testes passam.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/setup/SetupView.java src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java
git commit -m "feat(setup): anti-spam sub-screen (toggle, channel, publish warning)"
```

---

## Verificação manual (pós-implementação)

Como os listeners dependem de gateway/intents (`GUILD_MEMBERS`, `GUILD_MESSAGES`, `MESSAGE_CONTENT` não requerido) e do audit log, faça um smoke manual num servidor de teste, conforme o padrão da Fase 3:

**Pré-requisito:** aplicar `009_verification_questions.sql` no Neon.

1. **Verificação:** `/setup → Segurança → Verificação` → ativar, definir canal de aprovação, adicionar 1-2 perguntas, ligar seleção de usuário, publicar painel. Entrar com uma conta alt → clicar Verificar → responder → confirmar que o pedido aparece na fila. Tentar clicar de novo → deve barrar ("pedido em análise"). Aprovar → alt recebe cargo `membro`, sai de `nao-verificado`. Sair e reentrar com a alt → recebe `membro` automaticamente. Expulsar (kick) a alt → reentrar → volta a cair na fila.
2. **Anti-spam:** `/setup → Segurança → Anti-spam` → ativar, definir canal, publicar aviso. Com a alt, mandar mensagem no canal → alt é expulsa e mensagens recentes dela some. Com um mod (Administrador/cargo isento), mandar mensagem → não é punido.

---

## Self-Review (feito ao escrever)

- **Cobertura do spec:** Perguntas configuráveis (Task 4/9) ✓; texto+seleção configuráveis (Task 8/9) ✓; fila de aprovação com Aprovar/Recusar (Task 8) ✓; memória por servidor (Task 3/6) ✓; pendência única anti-spam-da-fila (Task 3/8) ✓; reset em kick/ban (Task 7) ✓; DDL Neon manual + SQLite auto (Task 2/4) ✓; canal anti-spam kick+10 (Task 10-13) ✓; aviso permanente (Task 12/14) ✓; isenções (Task 10/13) ✓.
- **Consistência de tipos:** `VerificationRepository`/`VerificationQuestionRepository`/`AntiSpamService` — nomes e assinaturas usados nas Tasks 6/8/9/13/14 batem com as definições nas Tasks 3/4/11.
- **Pontos a validar na implementação (marcados como Nota):** assinaturas exatas de `ComponentHandler.onEntitySelect/onModal`, `Panels`/`Emojis` (`CHECK_NO`), `event.editComponents` com V2, e a assinatura de `guild.kick(...)`/`TextChannel` na versão de JDA. Confirmar contra o código antes de dar por pronto.
