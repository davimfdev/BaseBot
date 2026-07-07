# Sistema de Logging Completo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cobrir todo o conjunto de eventos de servidor (nativos do Discord + ações do bot) em canais de log por categoria no estilo Components V2, com conteúdo de mensagens persistido por 90 dias e moderador atribuído no audit log.

**Architecture:** Substitui o único `GeneralLoggingListener` por 8 listeners focados que postam via o helper `ChannelLog.post`. Conteúdo de mensagens vive num arquivo SQLite (`message_archive`) com purga diária. Dois utils novos — `ModReason` (embute o moderador no `.reason()`) e `AuditLookup` (busca moderador/motivo no audit log) — enriquecem logs e ações.

**Tech Stack:** Java 22, JDA 6.4.2 (Discord), SQLite (JDBC), JUnit 5, Gradle.

## Global Constraints

- **JDK 22** para compilar/rodar (`./gradlew build`) — o toolchain está pinado; não rode em JDK 26.
- **Todo embed é Container V2** postado via `ChannelLog.post(ctx, guildId, logKey, markdown)` — nunca texto puro, nunca novo builder de embed por call-site.
- **Um canal por categoria**, resolvido por `cfg.channel("<key>")`; canal não configurado → pula em silêncio (já é o comportamento de `ChannelLog.post`).
- **O bot não grava arquivos** — anexos guardados como URL/metadados, nunca bytes.
- **Migração SQLite:** o `SqliteMigrator` divide statements no `;` em fim de linha; **nunca** ponha comentário inline depois de `;` (junta statements e aplica parcial). Arquivos numerados em ordem; o próximo número livre é `023`.
- **Logging guild-scoped:** nunca estado global; sempre ignore DMs (`event.isFromGuild()`) e o próprio bot/bots onde fizer sentido.
- **JDA 6 (6.4.2) gotchas de pacote:** eventos `GuildMemberRoleAddEvent`/`GuildMemberRoleRemoveEvent` ficam em `net.dv8tion.jda.api.events.guild.member` (NÃO `.member.update`); os de update (`...UpdateNickname`/`...UpdateTimeOut`) ficam em `.member.update`. Para qualquer símbolo JDA incerto, confirme com `javap -cp <jar do JDA em ~/.gradle/caches>` em vez de adivinhar o import. Os listeners postam via `ChannelLog`/`Panels`, então a API de componentes do JDA 6 (`net.dv8tion.jda.api.components.*`) não é tocada aqui.
- Pacote raiz: `dev.davimf.basebot`. Working dir: a raiz do repo.

---

### Task 1: Atualizar chaves de log em `SetupLogTypes`

Consolida as logs de mensagem num único canal e adiciona as categorias novas.

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupLogTypes.java:44-69`
- Test: `src/test/java/dev/davimf/basebot/modules/base/setup/SetupLogTypesTest.java`

**Interfaces:**
- Produces: chaves de canal `log-mensagens`, `log-membros`, `log-canais`, `log-cargos`, `log-servidor` (e remoção de `log-msgdel`, `log-msgedit`). Consumidas por todos os listeners adiante via `ChannelLog.post(..., "<key>", ...)`.

- [ ] **Step 1: Atualizar o teste para a nova contagem do módulo Base**

O Base passa de 10 para 13 tipos. Com cap 8, divide em 8 + 5 (ambas Base). Substitua o corpo de `SetupLogTypesTest`:

```java
    @Test
    void pagesAreGroupedByModuleAndChunked() {
        // Base has 13 logs -> with a cap of 8 it splits into 8 + 5 (both Base), then one
        // page each for Tickets (1), Vendas (2) and Facs (7).
        List<List<LogType>> pages = SetupLogTypes.pages(8);
        assertEquals(5, pages.size());
        assertEquals(8, pages.get(0).size());
        assertTrue(pages.get(0).stream().allMatch(t -> t.module().equals("Base")));
        assertEquals(5, pages.get(1).size());
        assertEquals("Base", pages.get(1).get(0).module());
        assertEquals("Tickets", pages.get(2).get(0).module());
        assertEquals("Vendas", pages.get(3).get(0).module());
        assertEquals("Facs", pages.get(4).get(0).module());
    }
```

(Mantenha o segundo teste `everyPageStaysWithinTheCapAndNoModuleMixing` como está.)

- [ ] **Step 2: Rodar o teste e confirmar que falha**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.setup.SetupLogTypesTest"`
Expected: FAIL (`pages.get(1).size()` ainda é 2 com o código atual).

- [ ] **Step 3: Editar `SetupLogTypes.ALL` (bloco Base)**

Substitua as linhas do grupo Base (de `log-comandos` até `log-formularios`) por:

```java
            // 🛠️ Base (logs gerais e moderação)
            new LogType("log-comandos", "Comandos", "Base"),
            new LogType("log-mensagens", "Mensagens (del/edit/fix)", "Base"),
            new LogType("log-entradas", "Entradas (join)", "Base"),
            new LogType("log-saidas", "Saídas (leave)", "Base"),
            new LogType("log-membros", "Membros (apelido/nome/cargos/timeout)", "Base"),
            new LogType("log-voz", "Voz (call)", "Base"),
            new LogType("log-canais", "Canais", "Base"),
            new LogType("log-cargos", "Cargos", "Base"),
            new LogType("log-servidor", "Servidor", "Base"),
            new LogType("log-bans", "Banimentos", "Base"),
            new LogType("log-kicks", "Expulsões", "Base"),
            new LogType("log-moderacao", "Moderação (casos)", "Base"),
            new LogType("log-formularios", "Formulários", "Base"),
```

- [ ] **Step 4: Rodar o teste e confirmar que passa**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.setup.SetupLogTypesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/setup/SetupLogTypes.java src/test/java/dev/davimf/basebot/modules/base/setup/SetupLogTypesTest.java
git commit -m "feat(logs): add member/channel/role/server log keys; consolidate message logs"
```

---

### Task 2: Migração `023_message_archive.sql` + `MessageArchiveRepository`

Arquivo SQLite das mensagens recentes para recuperar conteúdo apagado e o "antes" de edições.

**Files:**
- Create: `src/main/resources/db/sqlite/023_message_archive.sql`
- Create: `src/main/java/dev/davimf/basebot/database/sqlite/MessageArchiveRepository.java`
- Modify: `src/main/java/dev/davimf/basebot/database/DatabaseManager.java` (campo + construção + getter)
- Test: `src/test/java/dev/davimf/basebot/database/sqlite/MessageArchiveRepositoryTest.java`

**Interfaces:**
- Produces:
  - `record Archived(String messageId, String guildId, String channelId, String authorId, String content, String attachments, long createdAt, long updatedAt)`
  - `void upsert(String messageId, String guildId, String channelId, String authorId, String content, String attachments, long createdAt)`
  - `Archived find(String messageId)` — `null` se ausente.
  - `void updateContent(String messageId, String newContent, long updatedAt)`
  - `int purgeOlderThan(long cutoffMs)` — retorna nº de linhas removidas.
  - acessível via `ctx.database().messageArchive()`.

- [ ] **Step 1: Escrever a migração SQL**

Crie `023_message_archive.sql` (sem comentário inline após `;`):

```sql
CREATE TABLE IF NOT EXISTS message_archive (
    message_id TEXT PRIMARY KEY,
    guild_id   TEXT NOT NULL,
    channel_id TEXT NOT NULL,
    author_id  TEXT NOT NULL,
    content    TEXT,
    attachments TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_message_archive_created
    ON message_archive (created_at);

CREATE INDEX IF NOT EXISTS idx_message_archive_guild_channel
    ON message_archive (guild_id, channel_id);
```

- [ ] **Step 2: Escrever o teste de repositório (falhando)**

Crie `MessageArchiveRepositoryTest.java` espelhando o padrão de `MuteRepositoryTest`:

```java
package dev.davimf.basebot.database.sqlite;

import dev.davimf.basebot.config.BotConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MessageArchiveRepositoryTest {

    private SqliteManager sqlite;
    private MessageArchiveRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new MessageArchiveRepository(sqlite);
    }

    @AfterEach
    void tearDown() {
        sqlite.close();
    }

    @Test
    void upsertThenFind() {
        long now = System.currentTimeMillis();
        repo.upsert("m1", "g1", "c1", "u1", "olá", "", now);
        MessageArchiveRepository.Archived a = repo.find("m1");
        assertNotNull(a);
        assertEquals("olá", a.content());
        assertEquals("u1", a.authorId());
    }

    @Test
    void findMissingReturnsNull() {
        assertNull(repo.find("nope"));
    }

    @Test
    void updateContentChangesContent() {
        long now = System.currentTimeMillis();
        repo.upsert("m1", "g1", "c1", "u1", "antes", "", now);
        repo.updateContent("m1", "depois", now + 1000);
        assertEquals("depois", repo.find("m1").content());
    }

    @Test
    void purgeRemovesOnlyOld() {
        long now = System.currentTimeMillis();
        repo.upsert("old", "g1", "c1", "u1", "x", "", now - 1000);
        repo.upsert("new", "g1", "c1", "u1", "y", "", now + 1000);
        int removed = repo.purgeOlderThan(now);
        assertEquals(1, removed);
        assertNull(repo.find("old"));
        assertNotNull(repo.find("new"));
    }
}
```

- [ ] **Step 3: Rodar e confirmar que falha (não compila — classe ausente)**

Run: `./gradlew test --tests "dev.davimf.basebot.database.sqlite.MessageArchiveRepositoryTest"`
Expected: FAIL (compilação: `MessageArchiveRepository` não existe).

- [ ] **Step 4: Implementar `MessageArchiveRepository`**

Use `SqliteManager` (mesma API que `MuteRepository` — ver `ActionLogRepository`/`MuteRepository` para o estilo de `connection()`/try-with-resources). Implementação:

```java
package dev.davimf.basebot.database.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Arquivo das mensagens recentes (retenção via purga). Usado pelos logs de mensagem. */
public final class MessageArchiveRepository {

    public record Archived(String messageId, String guildId, String channelId,
                           String authorId, String content, String attachments,
                           long createdAt, long updatedAt) {}

    private final SqliteManager sqlite;

    public MessageArchiveRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public void upsert(String messageId, String guildId, String channelId, String authorId,
                       String content, String attachments, long createdAt) {
        String sql = "INSERT INTO message_archive"
                + " (message_id, guild_id, channel_id, author_id, content, attachments, created_at, updated_at)"
                + " VALUES (?,?,?,?,?,?,?,?)"
                + " ON CONFLICT(message_id) DO UPDATE SET content=excluded.content,"
                + " attachments=excluded.attachments, updated_at=excluded.updated_at";
        try (Connection c = sqlite.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, messageId);
            ps.setString(2, guildId);
            ps.setString(3, channelId);
            ps.setString(4, authorId);
            ps.setString(5, content);
            ps.setString(6, attachments);
            ps.setLong(7, createdAt);
            ps.setLong(8, createdAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("upsert message_archive", e);
        }
    }

    public Archived find(String messageId) {
        String sql = "SELECT * FROM message_archive WHERE message_id=?";
        try (Connection c = sqlite.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Archived(rs.getString("message_id"), rs.getString("guild_id"),
                        rs.getString("channel_id"), rs.getString("author_id"),
                        rs.getString("content"), rs.getString("attachments"),
                        rs.getLong("created_at"), rs.getLong("updated_at"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("find message_archive", e);
        }
    }

    public void updateContent(String messageId, String newContent, long updatedAt) {
        String sql = "UPDATE message_archive SET content=?, updated_at=? WHERE message_id=?";
        try (Connection c = sqlite.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newContent);
            ps.setLong(2, updatedAt);
            ps.setString(3, messageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateContent message_archive", e);
        }
    }

    public int purgeOlderThan(long cutoffMs) {
        String sql = "DELETE FROM message_archive WHERE created_at < ?";
        try (Connection c = sqlite.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, cutoffMs);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("purge message_archive", e);
        }
    }
}
```

> **Verificar:** confirme o nome do método de obtenção de conexão em `SqliteManager` (abra `ActionLogRepository`/`MuteRepository`). Se for diferente de `connection()`, ajuste as 4 ocorrências.

- [ ] **Step 5: Registrar no `DatabaseManager`**

Adicione o campo, a construção e o getter (espelhando `mutes`):

```java
    private final MessageArchiveRepository messageArchive;
```
No construtor, após `this.actionLogs = new ActionLogRepository(sqlite);`:
```java
        this.messageArchive = new MessageArchiveRepository(sqlite);
```
Getter:
```java
    public MessageArchiveRepository messageArchive() {
        return messageArchive;
    }
```

- [ ] **Step 6: Rodar o teste e confirmar que passa**

Run: `./gradlew test --tests "dev.davimf.basebot.database.sqlite.MessageArchiveRepositoryTest"`
Expected: PASS (4 testes).

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/db/sqlite/023_message_archive.sql src/main/java/dev/davimf/basebot/database/sqlite/MessageArchiveRepository.java src/main/java/dev/davimf/basebot/database/DatabaseManager.java src/test/java/dev/davimf/basebot/database/sqlite/MessageArchiveRepositoryTest.java
git commit -m "feat(logs): message_archive table + repository (90d retention store)"
```

---

### Task 3: Util `ModReason` (moderador embutido no reason)

**Files:**
- Create: `src/main/java/dev/davimf/basebot/util/ModReason.java`
- Test: `src/test/java/dev/davimf/basebot/util/ModReasonTest.java`

**Interfaces:**
- Produces: `static String of(net.dv8tion.jda.api.entities.User moderator, String motivo)` e `static String of(String moderatorTag, String motivo)` → `"{tag} • {motivo}"`, ou só `"{tag}"` quando motivo nulo/vazio. Truncado a 480 chars (limite do header de audit do Discord é 512).

- [ ] **Step 1: Escrever o teste (falhando)**

```java
package dev.davimf.basebot.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ModReasonTest {

    @Test
    void joinsModeratorAndReason() {
        assertEquals("davi#0 • spam", ModReason.of("davi#0", "spam"));
    }

    @Test
    void blankReasonGivesModeratorOnly() {
        assertEquals("davi#0", ModReason.of("davi#0", "  "));
        assertEquals("davi#0", ModReason.of("davi#0", null));
    }

    @Test
    void truncatesToAuditLimit() {
        String longReason = "x".repeat(1000);
        assertEquals(480, ModReason.of("m", longReason).length());
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `./gradlew test --tests "dev.davimf.basebot.util.ModReasonTest"`
Expected: FAIL (classe ausente).

- [ ] **Step 3: Implementar `ModReason`**

```java
package dev.davimf.basebot.util;

import net.dv8tion.jda.api.entities.User;

/** Constrói o texto de motivo do audit log do Discord embutindo o moderador humano,
 *  já que o bot é o ator registrado. Formato: "{moderador} • {motivo}". */
public final class ModReason {

    private static final int MAX = 480;

    private ModReason() {}

    public static String of(User moderator, String motivo) {
        return of(moderator.getAsTag(), motivo);
    }

    public static String of(String moderatorTag, String motivo) {
        String s = (motivo == null || motivo.isBlank())
                ? moderatorTag
                : moderatorTag + " • " + motivo;
        return s.length() > MAX ? s.substring(0, MAX) : s;
    }
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `./gradlew test --tests "dev.davimf.basebot.util.ModReasonTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/util/ModReason.java src/test/java/dev/davimf/basebot/util/ModReasonTest.java
git commit -m "feat(logs): ModReason util (moderator embedded in audit reason)"
```

---

### Task 4: Util `AuditLookup` (moderador + motivo do audit log)

Generaliza o `logFromAudit` do atual `GeneralLoggingListener` num helper reutilizável.

**Files:**
- Create: `src/main/java/dev/davimf/basebot/util/AuditLookup.java`

**Interfaces:**
- Produces:
  - `record Actor(String moderatorMention, String reason)` — `reason` já formatado (`"*sem motivo*"` quando vazio).
  - `static void lookup(Guild guild, String targetId, ActionType type, java.util.function.Consumer<Actor> onFound)` — busca a entrada de audit recente (~15s) cujo `targetId` bate; chama `onFound` só se achar. Falha em silêncio (sem `VIEW_AUDIT_LOG`).

> Sem teste unitário: depende de chamada assíncrona ao JDA (`retrieveAuditLogs`). Validação por build + manual (Task 16).

- [ ] **Step 1: Implementar `AuditLookup`**

```java
package dev.davimf.basebot.util;

import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;

import java.time.OffsetDateTime;
import java.util.function.Consumer;

/** Busca o autor humano + motivo de uma ação no audit log do Discord, para enriquecer
 *  logs de eventos nativos (movido à força, mute/deafen, pin, kick, ban, etc.).
 *  Requer VIEW_AUDIT_LOG; falha em silêncio. */
public final class AuditLookup {

    public record Actor(String moderatorMention, String reason) {}

    private AuditLookup() {}

    public static void lookup(Guild guild, String targetId, ActionType type, Consumer<Actor> onFound) {
        guild.retrieveAuditLogs().type(type).limit(6).queue(entries -> {
            AuditLogEntry hit = entries.stream()
                    .filter(e -> targetId.equals(e.getTargetId()))
                    .filter(e -> e.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(15)))
                    .findFirst().orElse(null);
            if (hit == null) {
                return;
            }
            String mod = hit.getUser() == null ? "—" : hit.getUser().getAsMention();
            String reason = (hit.getReason() == null || hit.getReason().isBlank())
                    ? "*sem motivo*" : hit.getReason();
            onFound.accept(new Actor(mod, reason));
        }, err -> { });
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/davimf/basebot/util/AuditLookup.java
git commit -m "feat(logs): AuditLookup util (moderator+reason from audit log)"
```

---

### Task 5: Ligar os intents necessários

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/BotApplication.java:90-97`

**Interfaces:**
- Produces: eventos de membro, conteúdo de mensagem, convites e eventos agendados passam a disparar.

- [ ] **Step 1: Editar o bloco `enableIntents(...)`**

Descomente os privilegiados e adicione os dois não-privilegiados:

```java
                .enableIntents(
                        GatewayIntent.GUILD_MEMBERS,         // membros: apelido/nome/cargos/timeout (privilegiado)
                        GatewayIntent.GUILD_MODERATION,      // bans/kicks logging
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT,       // arquivo de mensagens (privilegiado)
                        GatewayIntent.GUILD_VOICE_STATES,    // voice moderation + traffic logging
                        GatewayIntent.GUILD_EXPRESSIONS,     // emojis/stickers + /addemoji
                        GatewayIntent.GUILD_INVITES,         // log de convites
                        GatewayIntent.SCHEDULED_EVENTS,      // log de eventos agendados
                        GatewayIntent.DIRECT_MESSAGES)
```

> **Verificar:** o nome exato do intent de eventos agendados na versão de JDA do projeto (JDA 6.4.2) pode ser `GatewayIntent.SCHEDULED_EVENTS`. Confirme no autocompletar/javadoc e ajuste se necessário. Idem para a necessidade de `setMemberCachePolicy(MemberCachePolicy.ALL)` (linha 98, comentada) — descomente se os lookups de membro vierem vazios em testes manuais.

- [ ] **Step 2: Build**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/davimf/basebot/BotApplication.java
git commit -m "feat(logs): enable GUILD_MEMBERS, MESSAGE_CONTENT, GUILD_INVITES, SCHEDULED_EVENTS intents"
```

> **NOTA OPERACIONAL (não é código):** habilitar `GUILD_MEMBERS` e `MESSAGE_CONTENT` no Discord Dev Portal (Bot → Privileged Gateway Intents). Sem isso, os listeners correspondentes não disparam.

---

### Task 6: `CommandLoggingListener` (extrair de GeneralLoggingListener)

A partir daqui cada listener é uma classe nova em `modules/base/listeners/`. O `GeneralLoggingListener` só é **removido** na Task 14, depois que todos os substitutos existem — assim nada de logging some no meio.

> Listeners JDA não têm harness de teste no projeto (convenção atual: listeners não são testados por unidade). A verificação de cada um é `./gradlew compileJava` + o checklist manual da Task 16.

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/listeners/CommandLoggingListener.java`

**Interfaces:**
- Consumes: `ChannelLog.post`, `ctx.database().actionLogs()`.

- [ ] **Step 1: Criar a classe**

Copie a lógica de `onSlashCommandInteraction` do `GeneralLoggingListener` (linhas 58-69):

```java
package dev.davimf.basebot.modules.base.listeners;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.ChannelLog;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Loga a execução de slash commands em log-comandos + grava action_logs. */
public final class CommandLoggingListener extends ListenerAdapter {

    private final BotContext ctx;

    public CommandLoggingListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            return;
        }
        ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(), null,
                "COMMAND_EXEC", "/" + event.getFullCommandName());
        ChannelLog.post(ctx, event.getGuild().getId(), "log-comandos",
                "## 💬 Comando\n**Comando** · `/" + event.getFullCommandName() + "`\n"
                        + "👤 **Por** · " + event.getUser().getAsMention() + "\n"
                        + "📍 **Canal** · " + (event.getChannel() == null ? "—" : event.getChannel().getAsMention()));
    }
}
```

- [ ] **Step 2: Build + Commit**

Run: `./gradlew compileJava` → BUILD SUCCESSFUL.
```bash
git add src/main/java/dev/davimf/basebot/modules/base/listeners/CommandLoggingListener.java
git commit -m "feat(logs): extract CommandLoggingListener"
```

---

### Task 7: `MembershipLoggingListener`

Entradas, saídas, expulsão (kick via audit), apelido, nome/global-name, cargos, timeout.

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/listeners/MembershipLoggingListener.java`

**Interfaces:**
- Consumes: `ChannelLog.post`, `AuditLookup.lookup`, `ctx.database().actionLogs()`.

- [ ] **Step 1: Criar a classe com os handlers**

Eventos JDA (verifique os imports exatos no autocompletar): `GuildMemberJoinEvent`, `GuildMemberRemoveEvent`, `GuildMemberUpdateNicknameEvent`, `GuildMemberRoleAddEvent`, `GuildMemberRoleRemoveEvent`, `GuildMemberUpdateTimeOutEvent`, e (globais de usuário) `UserUpdateNameEvent` / `UserUpdateGlobalNameEvent`.

```java
package dev.davimf.basebot.modules.base.listeners;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.AuditLookup;
import dev.davimf.basebot.util.ChannelLog;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.TimeFormat;

import java.util.stream.Collectors;

/** Logs de membro: entradas, saídas/kick, apelido, cargos, timeout. (log-entradas,
 *  log-saidas, log-kicks, log-membros). */
public final class MembershipLoggingListener extends ListenerAdapter {

    private final BotContext ctx;

    public MembershipLoggingListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        User u = event.getUser();
        ChannelLog.post(ctx, event.getGuild().getId(), "log-entradas",
                "## 📥 Entrou\n👤 **Membro** · " + u.getAsMention() + " · `" + u.getId() + "`\n"
                        + "🕒 **Conta criada** · " + TimeFormat.RELATIVE.format(u.getTimeCreated()));
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        User u = event.getUser();
        ChannelLog.post(ctx, event.getGuild().getId(), "log-saidas",
                "## 📤 Saiu\n👤 **Membro** · " + u.getAsTag() + " · `" + u.getId() + "`");
        AuditLookup.lookup(event.getGuild(), u.getId(), ActionType.KICK, actor ->
                ChannelLog.post(ctx, event.getGuild().getId(), "log-kicks",
                        "## 👢 Expulso (kick)\n👤 **Membro** · " + u.getAsTag() + " · `" + u.getId() + "`\n"
                                + "🛡️ **Responsável** · " + actor.moderatorMention()
                                + "\n📝 **Motivo** · " + actor.reason()));
    }

    @Override
    public void onGuildMemberUpdateNickname(GuildMemberUpdateNicknameEvent event) {
        String before = event.getOldNickname() == null ? "*nenhum*" : event.getOldNickname();
        String after = event.getNewNickname() == null ? "*nenhum*" : event.getNewNickname();
        ChannelLog.post(ctx, event.getGuild().getId(), "log-membros",
                "## 🏷️ Apelido alterado\n👤 **Membro** · " + event.getMember().getAsMention()
                        + "\n**Antes** · " + before + "\n**Depois** · " + after);
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        String roles = event.getRoles().stream().map(Role::getAsMention).collect(Collectors.joining(", "));
        AuditLookup.lookup(event.getGuild(), event.getUser().getId(), ActionType.MEMBER_ROLE_UPDATE, actor ->
                ChannelLog.post(ctx, event.getGuild().getId(), "log-membros",
                        "## ➕ Cargos adicionados\n👤 **Membro** · " + event.getMember().getAsMention()
                                + "\n**Cargos** · " + roles + "\n🛡️ **Responsável** · " + actor.moderatorMention()));
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        String roles = event.getRoles().stream().map(Role::getAsMention).collect(Collectors.joining(", "));
        AuditLookup.lookup(event.getGuild(), event.getUser().getId(), ActionType.MEMBER_ROLE_UPDATE, actor ->
                ChannelLog.post(ctx, event.getGuild().getId(), "log-membros",
                        "## ➖ Cargos removidos\n👤 **Membro** · " + event.getMember().getAsMention()
                                + "\n**Cargos** · " + roles + "\n🛡️ **Responsável** · " + actor.moderatorMention()));
    }

    @Override
    public void onGuildMemberUpdateTimeOut(GuildMemberUpdateTimeOutEvent event) {
        boolean applied = event.getNewTimeOutEnd() != null;
        String body = applied
                ? "## ⏳ Timeout aplicado\n👤 **Membro** · " + event.getMember().getAsMention()
                        + "\n**Até** · " + TimeFormat.RELATIVE.format(event.getNewTimeOutEnd())
                : "## ✅ Timeout removido\n👤 **Membro** · " + event.getMember().getAsMention();
        AuditLookup.lookup(event.getGuild(), event.getUser().getId(), ActionType.MEMBER_UPDATE, actor ->
                ChannelLog.post(ctx, event.getGuild().getId(), "log-membros",
                        body + "\n🛡️ **Responsável** · " + actor.moderatorMention()
                                + "\n📝 **Motivo** · " + actor.reason()));
    }
}
```

> **Mudança de nome de usuário (`UserUpdateNameEvent`/`UserUpdateGlobalNameEvent`):** esses eventos são globais (o `User` compartilha N guildas). Para logar em cada guilda relevante, itere `event.getJDA().getMutualGuilds(...)` ou `getUser().getMutualGuilds()` e poste em `log-membros` de cada uma onde o membro está. **Verifique** os nomes exatos (`getOldName`/`getNewName`, `getOldGlobalName`/`getNewGlobalName`) no javadoc da versão e adicione os dois handlers seguindo o mesmo formato de "Antes/Depois". Se o volume de guildas for alto, restrinja às guildas configuradas com `log-membros`.

- [ ] **Step 2: Build + Commit**

Run: `./gradlew compileJava` → BUILD SUCCESSFUL.
```bash
git add src/main/java/dev/davimf/basebot/modules/base/listeners/MembershipLoggingListener.java
git commit -m "feat(logs): MembershipLoggingListener (join/leave/kick/nick/roles/timeout)"
```

---

### Task 8: `BanLoggingListener`

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/listeners/BanLoggingListener.java`

- [ ] **Step 1: Criar a classe**

Eventos: `GuildBanEvent`, `GuildUnbanEvent`.

```java
package dev.davimf.basebot.modules.base.listeners;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.AuditLookup;
import dev.davimf.basebot.util.ChannelLog;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Logs de banimento/desbanimento em log-bans, com moderador+motivo do audit. */
public final class BanLoggingListener extends ListenerAdapter {

    private final BotContext ctx;

    public BanLoggingListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        ctx.database().actionLogs().log(event.getGuild().getId(), null, event.getUser().getId(), "BAN", null);
        AuditLookup.lookup(event.getGuild(), event.getUser().getId(), ActionType.BAN, actor ->
                ChannelLog.post(ctx, event.getGuild().getId(), "log-bans",
                        "## 🔨 Banido\n👤 **Membro** · " + event.getUser().getAsTag() + " · `"
                                + event.getUser().getId() + "`\n🛡️ **Responsável** · " + actor.moderatorMention()
                                + "\n📝 **Motivo** · " + actor.reason()));
    }

    @Override
    public void onGuildUnban(GuildUnbanEvent event) {
        AuditLookup.lookup(event.getGuild(), event.getUser().getId(), ActionType.UNBAN, actor ->
                ChannelLog.post(ctx, event.getGuild().getId(), "log-bans",
                        "## ♻️ Desbanido\n👤 **Membro** · " + event.getUser().getAsTag() + " · `"
                                + event.getUser().getId() + "`\n🛡️ **Responsável** · " + actor.moderatorMention()));
    }
}
```

- [ ] **Step 2: Build + Commit**

Run: `./gradlew compileJava` → BUILD SUCCESSFUL.
```bash
git add src/main/java/dev/davimf/basebot/modules/base/listeners/BanLoggingListener.java
git commit -m "feat(logs): BanLoggingListener (ban/unban with audit)"
```

---

### Task 9: `MessageLoggingListener` + arquivo de mensagens

Upsert no recebimento; delete com conteúdo; edit antes→depois; bulk; pin (best-effort).

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/listeners/MessageLoggingListener.java`

**Interfaces:**
- Consumes: `ctx.database().messageArchive()`, `ChannelLog.post`, `AuditLookup.lookup`.

- [ ] **Step 1: Criar a classe**

Eventos: `MessageReceivedEvent`, `MessageUpdateEvent`, `MessageDeleteEvent`, `MessageBulkDeleteEvent`.

```java
package dev.davimf.basebot.modules.base.listeners;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.sqlite.MessageArchiveRepository;
import dev.davimf.basebot.util.ChannelLog;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.stream.Collectors;

/** Logs de mensagem (apagada/editada/bulk/fixada) em log-mensagens, recuperando conteúdo
 *  do arquivo SQLite (message_archive). */
public final class MessageLoggingListener extends ListenerAdapter {

    private final BotContext ctx;

    public MessageLoggingListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) {
            return;
        }
        Message m = event.getMessage();
        // System message de "mensagem fixada": loga o pin (quem fica via audit, na Step 2).
        String attachments = m.getAttachments().stream()
                .map(a -> a.getUrl()).collect(Collectors.joining("\n"));
        ctx.database().messageArchive().upsert(m.getId(), event.getGuild().getId(),
                event.getChannel().getId(), event.getAuthor().getId(),
                m.getContentDisplay(), attachments, System.currentTimeMillis());
    }

    @Override
    public void onMessageUpdate(MessageUpdateEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) {
            return;
        }
        MessageArchiveRepository archive = ctx.database().messageArchive();
        MessageArchiveRepository.Archived old = archive.find(event.getMessageId());
        String before = old == null || old.content() == null || old.content().isBlank()
                ? "*desconhecido*" : trim(old.content());
        String now = event.getMessage().getContentDisplay();
        archive.updateContent(event.getMessageId(), now, System.currentTimeMillis());
        if (before.equals(now)) {
            return; // edição sem mudança de texto (ex.: embed) — ignora
        }
        ChannelLog.post(ctx, event.getGuild().getId(), "log-mensagens",
                "## ✏️ Mensagem editada\n👤 **Autor** · " + event.getAuthor().getAsMention()
                        + "\n📍 **Canal** · " + event.getChannel().getAsMention()
                        + "\n**Antes** · " + before
                        + "\n**Depois** · " + (now.isBlank() ? "*vazio*" : trim(now)));
    }

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        if (!event.isFromGuild()) {
            return;
        }
        MessageArchiveRepository.Archived old = ctx.database().messageArchive().find(event.getMessageId());
        String autor = old == null ? "*desconhecido*" : "<@" + old.authorId() + ">";
        String conteudo = old == null || old.content() == null || old.content().isBlank()
                ? "*sem texto / não arquivado*" : trim(old.content());
        String anexos = old == null || old.attachments() == null || old.attachments().isBlank()
                ? "" : "\n📎 **Anexos** · " + old.attachments();
        ChannelLog.post(ctx, event.getGuild().getId(), "log-mensagens",
                "## 🗑️ Mensagem apagada\n👤 **Autor** · " + autor
                        + "\n📍 **Canal** · " + event.getChannel().getAsMention()
                        + "\n💬 **Conteúdo** · " + conteudo + anexos);
    }

    @Override
    public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-mensagens",
                "## 🧹 Deleção em massa\n📍 **Canal** · " + event.getChannel().getAsMention()
                        + "\n🔢 **Quantidade** · `" + event.getMessageIds().size() + "` mensagens");
    }

    private static String trim(String s) {
        return s.length() > 800 ? s.substring(0, 800) + "…" : s;
    }
}
```

- [ ] **Step 2: Adicionar detecção de fixar/desfixar (pin)**

Pin gera uma system message; o "por quem" vem do audit. Adicione no início de `onMessageReceived`, **antes** do `upsert`:

```java
        if (m.getType() == MessageType.CHANNEL_PINNED_ADD) {
            dev.davimf.basebot.util.AuditLookup.lookup(event.getGuild(),
                    null, net.dv8tion.jda.api.audit.ActionType.MESSAGE_PIN, actor ->
                    ChannelLog.post(ctx, event.getGuild().getId(), "log-mensagens",
                            "## 📌 Mensagem fixada\n📍 **Canal** · " + event.getChannel().getAsMention()
                                    + "\n🛡️ **Por** · " + actor.moderatorMention()));
            return;
        }
```

> **AuditLookup com targetId nulo:** ajuste `AuditLookup.lookup` para, quando `targetId == null`, casar apenas pela janela de tempo (pular o filtro de `targetId`). Edite o `filter(e -> targetId.equals(...))` para `filter(e -> targetId == null || targetId.equals(e.getTargetId()))`.
>
> **Limitação a documentar:** o JDA não emite evento confiável de **desfixar** (unpin). Cobrir unpin exigiria polling do audit (`MESSAGE_UNPIN`), fora do escopo aqui — registre como limitação conhecida no commit e siga. Fixar é coberto pela system message acima.

- [ ] **Step 3: Build + Commit**

Run: `./gradlew compileJava` → BUILD SUCCESSFUL.
```bash
git add src/main/java/dev/davimf/basebot/modules/base/listeners/MessageLoggingListener.java
git commit -m "feat(logs): MessageLoggingListener with archive (delete/edit/bulk/pin)"
```

---

### Task 10: `VoiceLoggingListener`

Tráfego de call + movido à força + server mute/deafen + stream + câmera.

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/listeners/VoiceLoggingListener.java`

- [ ] **Step 1: Criar a classe**

Eventos: `GuildVoiceUpdateEvent`, `GuildVoiceGuildMuteEvent`, `GuildVoiceGuildDeafenEvent`, `GuildVoiceStreamEvent`, `GuildVoiceVideoEvent`.

```java
package dev.davimf.basebot.modules.base.listeners;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.AuditLookup;
import dev.davimf.basebot.util.ChannelLog;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildDeafenEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildMuteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceStreamEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceVideoEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Logs de voz em log-voz: tráfego, movido à força (substitui o log de mudança normal),
 *  server mute/deafen, stream e câmera. */
public final class VoiceLoggingListener extends ListenerAdapter {

    private final BotContext ctx;

    public VoiceLoggingListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        AudioChannel left = event.getChannelLeft();
        AudioChannel joined = event.getChannelJoined();
        String member = event.getMember().getAsMention();
        String guildId = event.getGuild().getId();
        if (left == null && joined != null) {
            ChannelLog.post(ctx, guildId, "log-voz", "## 🔊 Entrou em call\n" + member + " entrou em " + joined.getAsMention());
        } else if (left != null && joined == null) {
            ChannelLog.post(ctx, guildId, "log-voz", "## 🔇 Saiu da call\n" + member + " saiu de " + left.getAsMention());
        } else if (left != null) {
            // Mudança de call: se foi forçada por moderador (audit MEMBER_VOICE_MOVE recente),
            // posta "movido por moderador" no lugar do log de mudança normal.
            String origemDestino = left.getAsMention() + " → " + joined.getAsMention();
            AuditLookup.lookup(event.getGuild(), event.getMember().getId(), ActionType.MEMBER_VOICE_MOVE, actor ->
                    ChannelLog.post(ctx, guildId, "log-voz",
                            "## �move Movido à força\n👤 **Membro** · " + member + "\n**Trajeto** · " + origemDestino
                                    + "\n🛡️ **Responsável** · " + actor.moderatorMention()));
            // Log de mudança voluntária (só relevante quando NÃO houve audit recente):
            ChannelLog.post(ctx, guildId, "log-voz", "## 🔁 Mudou de call\n" + member + " · " + origemDestino);
        }
    }

    @Override
    public void onGuildVoiceGuildMute(GuildVoiceGuildMuteEvent event) {
        String body = event.isGuildMuted() ? "## 🔇 Silenciado no servidor" : "## 🔈 Dessilenciado no servidor";
        AuditLookup.lookup(event.getGuild(), event.getMember().getId(), ActionType.MEMBER_UPDATE, actor ->
                ChannelLog.post(ctx, event.getGuild().getId(), "log-voz",
                        body + "\n👤 **Membro** · " + event.getMember().getAsMention()
                                + "\n🛡️ **Responsável** · " + actor.moderatorMention()));
    }

    @Override
    public void onGuildVoiceGuildDeafen(GuildVoiceGuildDeafenEvent event) {
        String body = event.isGuildDeafened() ? "## 🔕 Ensurdecido no servidor" : "## 🔔 Desensurdecido no servidor";
        AuditLookup.lookup(event.getGuild(), event.getMember().getId(), ActionType.MEMBER_UPDATE, actor ->
                ChannelLog.post(ctx, event.getGuild().getId(), "log-voz",
                        body + "\n👤 **Membro** · " + event.getMember().getAsMention()
                                + "\n🛡️ **Responsável** · " + actor.moderatorMention()));
    }

    @Override
    public void onGuildVoiceStream(GuildVoiceStreamEvent event) {
        String body = event.isStream() ? "## 📡 Transmissão ligada" : "## 📴 Transmissão desligada";
        ChannelLog.post(ctx, event.getGuild().getId(), "log-voz",
                body + "\n👤 **Membro** · " + event.getMember().getAsMention());
    }

    @Override
    public void onGuildVoiceVideo(GuildVoiceVideoEvent event) {
        String body = event.isVideo() ? "## 📷 Câmera ligada" : "## 📷 Câmera desligada";
        ChannelLog.post(ctx, event.getGuild().getId(), "log-voz",
                body + "\n👤 **Membro** · " + event.getMember().getAsMention());
    }
}
```

> **Refinamento do "movido à força" (recomendado na implementação):** como `onGuildVoiceUpdate` não sabe de antemão se houve audit, a forma mais limpa é mover o log de "🔁 Mudou de call" para dentro do callback de erro/ausência do `AuditLookup`. Como `AuditLookup.lookup` só chama `onFound` quando acha, considere estender `AuditLookup` com um overload `lookup(guild, target, type, onFound, onAbsent)` para postar o log voluntário só quando NÃO houve movimentação forçada — evitando logar os dois. Ajuste `AuditLookup` (Task 4) com esse overload se optar por isso. Corrija também o título `## �move` para um emoji válido (ex.: `## ↪️ Movido à força`).

- [ ] **Step 2: Build + Commit**

Run: `./gradlew compileJava` → BUILD SUCCESSFUL.
```bash
git add src/main/java/dev/davimf/basebot/modules/base/listeners/VoiceLoggingListener.java
git commit -m "feat(logs): VoiceLoggingListener (traffic/force-move/mute/deafen/stream/camera)"
```

---

### Task 11: `ChannelLoggingListener`

Canal criado / deletado / atualizado (nome, categoria, tópico, permissões).

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/listeners/ChannelLoggingListener.java`

- [ ] **Step 1: Criar a classe**

Eventos: `ChannelCreateEvent`, `ChannelDeleteEvent`, `ChannelUpdateNameEvent`, `ChannelUpdateParentEvent`, `ChannelUpdateTopicEvent`, e overrides de permissão `PermissionOverrideCreateEvent`/`PermissionOverrideUpdateEvent`/`PermissionOverrideDeleteEvent` (pacote `net.dv8tion.jda.api.events.guild.override`).

```java
package dev.davimf.basebot.modules.base.listeners;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.AuditLookup;
import dev.davimf.basebot.util.ChannelLog;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateParentEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateTopicEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Logs de canais em log-canais: criado, deletado, atualizado (nome/categoria/tópico/perms). */
public final class ChannelLoggingListener extends ListenerAdapter {

    private final BotContext ctx;

    public ChannelLoggingListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onChannelCreate(ChannelCreateEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-canais",
                "## 📁 Canal criado\n**Canal** · " + event.getChannel().getAsMention()
                        + "\n**Tipo** · `" + event.getChannel().getType() + "`");
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-canais",
                "## 🗑️ Canal deletado\n**Nome** · `" + event.getChannel().getName()
                        + "`\n**Tipo** · `" + event.getChannel().getType() + "`");
    }

    @Override
    public void onChannelUpdateName(ChannelUpdateNameEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-canais",
                "## ✏️ Canal renomeado\n**Antes** · `" + event.getOldValue()
                        + "`\n**Depois** · `" + event.getNewValue() + "`");
    }

    @Override
    public void onChannelUpdateParent(ChannelUpdateParentEvent event) {
        String antes = event.getOldValue() == null ? "*nenhuma*" : event.getOldValue().getName();
        String depois = event.getNewValue() == null ? "*nenhuma*" : event.getNewValue().getName();
        ChannelLog.post(ctx, event.getGuild().getId(), "log-canais",
                "## 📂 Categoria alterada\n**Canal** · " + event.getChannel().getAsMention()
                        + "\n**Antes** · `" + antes + "`\n**Depois** · `" + depois + "`");
    }

    @Override
    public void onChannelUpdateTopic(ChannelUpdateTopicEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-canais",
                "## 📝 Tópico alterado\n**Canal** · " + event.getChannel().getAsMention()
                        + "\n**Depois** · " + (event.getNewValue() == null ? "*removido*" : event.getNewValue()));
    }
}
```

- [ ] **Step 2: Adicionar logs de permissão (overrides)**

Adicione handlers para os 3 eventos de override, enriquecendo com o moderador via `AuditLookup` (ActionType `CHANNEL_OVERRIDE_CREATE`/`CHANNEL_OVERRIDE_UPDATE`/`CHANNEL_OVERRIDE_DELETE`), postando "## 🔐 Permissões alteradas" com o canal e o alvo do override (`event.getPermissionOverride().getPermissionHolder()`).

> **Verificar:** os nomes exatos dos eventos de override e seus getters no JDA da versão do projeto. Mantenha o mesmo formato V2 das embeds acima.

- [ ] **Step 3: Build + Commit**

Run: `./gradlew compileJava` → BUILD SUCCESSFUL.
```bash
git add src/main/java/dev/davimf/basebot/modules/base/listeners/ChannelLoggingListener.java
git commit -m "feat(logs): ChannelLoggingListener (create/delete/update/perms)"
```

---

### Task 12: `RoleLoggingListener`

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/listeners/RoleLoggingListener.java`

- [ ] **Step 1: Criar a classe**

Eventos: `RoleCreateEvent`, `RoleDeleteEvent`, `RoleUpdateNameEvent`, `RoleUpdateColorEvent`, `RoleUpdatePermissionsEvent`.

```java
package dev.davimf.basebot.modules.base.listeners;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.ChannelLog;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateColorEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateNameEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdatePermissionsEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Logs de cargos em log-cargos: criado, deletado, atualizado (nome/cor/permissões). */
public final class RoleLoggingListener extends ListenerAdapter {

    private final BotContext ctx;

    public RoleLoggingListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onRoleCreate(RoleCreateEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-cargos",
                "## 🎭 Cargo criado\n**Cargo** · " + event.getRole().getAsMention());
    }

    @Override
    public void onRoleDelete(RoleDeleteEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-cargos",
                "## 🗑️ Cargo deletado\n**Nome** · `" + event.getRole().getName() + "`");
    }

    @Override
    public void onRoleUpdateName(RoleUpdateNameEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-cargos",
                "## ✏️ Cargo renomeado\n**Antes** · `" + event.getOldValue()
                        + "`\n**Depois** · `" + event.getNewValue() + "`");
    }

    @Override
    public void onRoleUpdateColor(RoleUpdateColorEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-cargos",
                "## 🎨 Cor do cargo alterada\n**Cargo** · " + event.getRole().getAsMention());
    }

    @Override
    public void onRoleUpdatePermissions(RoleUpdatePermissionsEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-cargos",
                "## 🔐 Permissões do cargo alteradas\n**Cargo** · " + event.getRole().getAsMention());
    }
}
```

- [ ] **Step 2: Build + Commit**

Run: `./gradlew compileJava` → BUILD SUCCESSFUL.
```bash
git add src/main/java/dev/davimf/basebot/modules/base/listeners/RoleLoggingListener.java
git commit -m "feat(logs): RoleLoggingListener (create/delete/update)"
```

---

### Task 13: `GuildLoggingListener`

Servidor (nome/foto/banner), emojis/stickers, convites, eventos agendados, threads.

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/listeners/GuildLoggingListener.java`

- [ ] **Step 1: Criar a classe (servidor + convites + emojis)**

Eventos: `GuildUpdateNameEvent`, `GuildUpdateIconEvent`, `GuildUpdateBannerEvent`, `GuildInviteCreateEvent`, `GuildInviteDeleteEvent`, `EmojiAddedEvent`, `EmojiRemovedEvent`, `EmojiUpdateNameEvent` (pacote `events.emoji`).

```java
package dev.davimf.basebot.modules.base.listeners;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.ChannelLog;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateBannerEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateIconEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateNameEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteCreateEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteDeleteEvent;
import net.dv8tion.jda.api.events.emoji.EmojiAddedEvent;
import net.dv8tion.jda.api.events.emoji.EmojiRemovedEvent;
import net.dv8tion.jda.api.events.emoji.update.EmojiUpdateNameEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.TimeFormat;

/** Logs do servidor em log-servidor: nome/foto/banner, emojis, convites, eventos
 *  agendados e threads. */
public final class GuildLoggingListener extends ListenerAdapter {

    private final BotContext ctx;

    public GuildLoggingListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onGuildUpdateName(GuildUpdateNameEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-servidor",
                "## 🏛️ Nome do servidor alterado\n**Antes** · `" + event.getOldValue()
                        + "`\n**Depois** · `" + event.getNewValue() + "`");
    }

    @Override
    public void onGuildUpdateIcon(GuildUpdateIconEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-servidor", "## 🖼️ Foto do servidor alterada");
    }

    @Override
    public void onGuildUpdateBanner(GuildUpdateBannerEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-servidor", "## 🎌 Banner do servidor alterado");
    }

    @Override
    public void onGuildInviteCreate(GuildInviteCreateEvent event) {
        var inv = event.getInvite();
        ChannelLog.post(ctx, event.getGuild().getId(), "log-servidor",
                "## 🔗 Convite criado\n**Código** · `" + event.getCode() + "`"
                        + "\n📍 **Canal** · " + event.getChannel().getAsMention()
                        + "\n👤 **Por** · " + (inv.getInviter() == null ? "—" : inv.getInviter().getAsMention())
                        + "\n⏳ **Expira** · " + (inv.getMaxAge() == 0 ? "nunca" : "em " + inv.getMaxAge() + "s")
                        + "\n🔢 **Usos máx.** · " + (inv.getMaxUses() == 0 ? "ilimitado" : inv.getMaxUses()));
    }

    @Override
    public void onGuildInviteDelete(GuildInviteDeleteEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-servidor",
                "## 🔗 Convite removido\n**Código** · `" + event.getCode() + "`");
    }

    @Override
    public void onEmojiAdded(EmojiAddedEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-servidor",
                "## 😀 Emoji adicionado\n**Nome** · `" + event.getEmoji().getName() + "`");
    }

    @Override
    public void onEmojiRemoved(EmojiRemovedEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-servidor",
                "## 😶 Emoji removido\n**Nome** · `" + event.getEmoji().getName() + "`");
    }

    @Override
    public void onEmojiUpdateName(EmojiUpdateNameEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-servidor",
                "## ✏️ Emoji renomeado\n**Antes** · `" + event.getOldValue()
                        + "`\n**Depois** · `" + event.getNewValue() + "`");
    }
}
```

- [ ] **Step 2: Adicionar stickers, eventos agendados e threads**

No mesmo arquivo, adicione handlers (verifique os nomes exatos na versão de JDA):
- **Stickers:** `GuildStickerAddedEvent`, `GuildStickerRemovedEvent`, `GuildStickerUpdateNameEvent` (pacote `events.sticker`) → "## 🏷️ Sticker adicionado/removido/renomeado".
- **Eventos agendados:** `ScheduledEventCreateEvent`, `ScheduledEventUpdateStatusEvent` (iniciado/cancelado via `getNewStatus()`), `ScheduledEventDeleteEvent`, e os update genéricos → "## 📅 Evento agendado criado/iniciado/cancelado/editado/removido" com `event.getScheduledEvent().getName()`.
- **Threads/tópicos:** `ChannelCreateEvent`/`ChannelDeleteEvent` quando `getChannel().getType().isThread()` (cuidado: já há `ChannelLoggingListener` — para evitar duplicar, filtre threads no `ChannelLoggingListener` (`!type.isThread()`) e trate-as aqui), e `ChannelUpdateArchivedEvent` (arquivada/desarquivada via `getNewValue()`) → "## 🧵 Thread criada/deletada/arquivada/desarquivada" em log-servidor.

> Mantenha cada handler no formato V2 (`## Título\n**Campo** · valor`). Onde houver moderador relevante, use `AuditLookup`.

- [ ] **Step 3: Evitar duplicação de threads no ChannelLoggingListener**

Em `ChannelLoggingListener.onChannelCreate`/`onChannelDelete` (Task 11), adicione no topo:
```java
        if (event.getChannel().getType().isThread()) {
            return; // threads são logadas em GuildLoggingListener (log-servidor)
        }
```

- [ ] **Step 4: Build + Commit**

Run: `./gradlew compileJava` → BUILD SUCCESSFUL.
```bash
git add src/main/java/dev/davimf/basebot/modules/base/listeners/GuildLoggingListener.java src/main/java/dev/davimf/basebot/modules/base/listeners/ChannelLoggingListener.java
git commit -m "feat(logs): GuildLoggingListener (server/emoji/sticker/invite/event/thread)"
```

---

### Task 14: Registrar listeners + purga; remover `GeneralLoggingListener`

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java` (registrations ~180; scheduling ~92)
- Delete: `src/main/java/dev/davimf/basebot/modules/base/listeners/GeneralLoggingListener.java`

**Interfaces:**
- Consumes: todos os listeners das Tasks 6-13; `ctx.database().messageArchive()`; `ctx.scheduler()`.

- [ ] **Step 1: Trocar o registro do listener**

Em `BaseModule`, substitua a linha 180 (`registry.listener(new GeneralLoggingListener(ctx));`) por:

```java
        registry.listener(new CommandLoggingListener(ctx));
        registry.listener(new MembershipLoggingListener(ctx));
        registry.listener(new BanLoggingListener(ctx));
        registry.listener(new MessageLoggingListener(ctx));
        registry.listener(new VoiceLoggingListener(ctx));
        registry.listener(new ChannelLoggingListener(ctx));
        registry.listener(new RoleLoggingListener(ctx));
        registry.listener(new GuildLoggingListener(ctx));
```

Atualize os imports: remova `import dev.davimf.basebot.modules.base.listeners.GeneralLoggingListener;` (linha 63) e adicione os 8 imports correspondentes em `modules.base.listeners`.

- [ ] **Step 2: Agendar a purga diária do arquivo de mensagens**

Junto do bloco de scheduling (perto da linha 92, onde `muteService::sweepExpired` é agendado), adicione:

```java
        // Purga do arquivo de mensagens: retenção de 90 dias. Roda no boot e a cada 12h.
        long ninetyDaysMs = 90L * 24 * 60 * 60 * 1000;
        ctx.scheduler().repeating(
                () -> ctx.database().messageArchive().purgeOlderThan(System.currentTimeMillis() - ninetyDaysMs),
                1, 12 * 60 * 60, TimeUnit.SECONDS);
```

> **Verificar:** a assinatura de `ctx.scheduler().repeating(...)` (no padrão atual: `repeating(Runnable, long initialDelay, long period, TimeUnit)`). Confirme em `BaseModule:93` e ajuste a unidade/valores se necessário. `TimeUnit` já deve estar importado.

- [ ] **Step 3: Deletar `GeneralLoggingListener`**

```bash
git rm src/main/java/dev/davimf/basebot/modules/base/listeners/GeneralLoggingListener.java
```

- [ ] **Step 4: Build completo**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (compila + todos os testes passam).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(logs): wire focused listeners + message purge; remove GeneralLoggingListener"
```

---

### Task 15: Padronizar `.reason()` com o moderador nos comandos

Aplicar `ModReason` para que o audit log do Discord atribua o moderador humano.

**Files (modificar a chamada de ação em cada um):**
- Adicionar `.reason()` (faltando): `VoiceMoveCommand.java:71`, `AddCargoCommand.java:62`, `RemoveCargoCommand.java:62`, `UnmuteCommand.java:76`, `BotNickCommand.java:51`.
- Trocar o reason existente por `ModReason.of(...)`: `BanCommand`, `KickCommand`, `TimeoutCommand`, `UntimeoutCommand`, `TempbanCommand`, `SoftbanCommand`, `MuteCommand`, `MuteCallCommand`, `UnmuteCallCommand`, `LockCommand`, `UnlockCommand`, `SlowmodeCommand`, `NukeCommand`, `PdCommand`, e `ModerationService` (timeout/kick/ban/mute).

**Interfaces:**
- Consumes: `ModReason.of(User, String)` (Task 3).

- [ ] **Step 1: Comandos que já têm motivo — embutir o moderador**

Padrão da troca (exemplo em `BanCommand.java:77`):
```java
// antes:
event.getGuild().ban(target, clamped, TimeUnit.DAYS).reason(reason).queue(
// depois:
event.getGuild().ban(target, clamped, TimeUnit.DAYS)
        .reason(dev.davimf.basebot.util.ModReason.of(event.getUser(), reason)).queue(
```
Aplique a mesma transformação a cada comando da lista (o "executor" é `event.getUser()`; em `ModerationService`/`PdCommand` use o moderador disponível no contexto). Adicione `import dev.davimf.basebot.util.ModReason;` onde preferir import explícito.

- [ ] **Step 2: Comandos sem `.reason()` — adicionar**

- `VoiceMoveCommand.java:71`:
```java
event.getGuild().moveVoiceMember(target, channel)
        .reason(ModReason.of(event.getUser(), "Movido via /move")).queue(
```
- `AddCargoCommand.java:62`:
```java
event.getGuild().addRoleToMember(target, role)
        .reason(ModReason.of(event.getUser(), "Cargo adicionado via comando")).queue(
```
- `RemoveCargoCommand.java:62`:
```java
event.getGuild().removeRoleFromMember(target, role)
        .reason(ModReason.of(event.getUser(), "Cargo removido via comando")).queue(
```
- `UnmuteCommand.java:76`:
```java
event.getGuild().removeRoleFromMember(target, role)
        .reason(ModReason.of(event.getUser(), "Unmute")).queue(
```
- `BotNickCommand.java:51` (auto-nick do bot; motivo simples):
```java
event.getGuild().getSelfMember().modifyNickname(apelido)
        .reason(ModReason.of(event.getUser(), "Apelido do bot")).queue(
```

- [ ] **Step 3: Build completo**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/
git commit -m "feat(logs): embed moderator in audit reason across moderation commands"
```

---

### Task 16: Expor os módulos ativos no `BotContext`

O setup rápido (Tasks 17-18) só configura os módulos ligados no bot. Hoje a lista ativa é
`BotApplication.modules`; vamos expô-la no contexto.

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/core/BotContext.java`
- Modify: `src/main/java/dev/davimf/basebot/BotApplication.java` (após montar `modules`)

**Interfaces:**
- Produces: `java.util.Set<String> ctx.activeModules()` — nomes de módulo (`BotModule.name()`:
  `Base`/`Tickets`/`Sales`/`Facs`). Consumido por `QuickLogSetup` (Task 17).

- [ ] **Step 1: Adicionar campo + getter + setter no `BotContext`**

Após o campo `private volatile JDA jda;`:
```java
    private volatile java.util.Set<String> activeModules = java.util.Set.of();
```
Métodos (perto de `jda()`/`setJda(...)`):
```java
    /** Nomes dos módulos ligados neste bot (futuro: por plano do cliente). */
    public java.util.Set<String> activeModules() {
        return activeModules;
    }

    /** Definido uma vez no bootstrap, a partir da lista de módulos registrados. */
    public void setActiveModules(java.util.Set<String> activeModules) {
        this.activeModules = java.util.Set.copyOf(activeModules);
    }
```

- [ ] **Step 2: Popular no `BotApplication`**

No `start()`, logo após o loop que registra os módulos (perto da linha 87), adicione:
```java
        context.setActiveModules(modules.stream()
                .map(BotModule::name)
                .collect(java.util.stream.Collectors.toSet()));
```

- [ ] **Step 3: Build + Commit**

Run: `./gradlew compileJava` → BUILD SUCCESSFUL.
```bash
git add src/main/java/dev/davimf/basebot/core/BotContext.java src/main/java/dev/davimf/basebot/BotApplication.java
git commit -m "feat(setup): expose active modules via BotContext.activeModules()"
```

---

### Task 17: `QuickLogSetup` — criar categorias/canais e gravar as chaves

Cria `logs {modulo}` + canais `📂・{log}` só para módulos ativos, gravando cada chave.
Idempotente. Helpers puros são testados; a orquestração JDA é validada manualmente (Task 19).

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/setup/QuickLogSetup.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/setup/QuickLogSetupTest.java`

**Interfaces:**
- Consumes: `SetupLogTypes.ALL`, `ctx.activeModules()` (Task 16), `GuildConfigEdits.withChannel`,
  `ctx.database().guildConfig()`, `ctx.scheduler().executor()`.
- Produces:
  - `record Summary(int created, int skipped, int categories)`
  - `static String channelName(String logKey)` → `"📂・" + chave sem "log-"`.
  - `static String categoryName(String moduleLabel)` → `"logs " + label minúsculo`.
  - `static String logModuleFor(String botModuleName)` → `Sales`→`Vendas`, resto idêntico.
  - `static java.util.List<SetupLogTypes.LogType> typesForActive(java.util.Set<String> activeBotModuleNames)`.
  - `static Summary run(net.dv8tion.jda.api.entities.Guild guild, dev.davimf.basebot.core.BotContext ctx)` (bloqueante; rodar OFF-thread).

- [ ] **Step 1: Escrever o teste dos helpers puros (falhando)**

```java
package dev.davimf.basebot.modules.base.setup;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class QuickLogSetupTest {

    @Test
    void channelNameStripsLogPrefix() {
        assertEquals("📂・mensagens", QuickLogSetup.channelName("log-mensagens"));
        assertEquals("📂・comandos", QuickLogSetup.channelName("log-comandos"));
    }

    @Test
    void categoryNameIsLowercased() {
        assertEquals("logs base", QuickLogSetup.categoryName("Base"));
        assertEquals("logs vendas", QuickLogSetup.categoryName("Vendas"));
    }

    @Test
    void salesMapsToVendas() {
        assertEquals("Vendas", QuickLogSetup.logModuleFor("Sales"));
        assertEquals("Base", QuickLogSetup.logModuleFor("Base"));
    }

    @Test
    void typesForActiveFiltersByMappedModule() {
        // Bot só com Base + Sales -> traz tipos de Base e de Vendas, nada de Tickets/Facs.
        List<SetupLogTypes.LogType> types = QuickLogSetup.typesForActive(Set.of("Base", "Sales"));
        assertTrue(types.stream().allMatch(t -> t.module().equals("Base") || t.module().equals("Vendas")));
        assertTrue(types.stream().anyMatch(t -> t.module().equals("Base")));
        assertTrue(types.stream().anyMatch(t -> t.module().equals("Vendas")));
        assertFalse(types.stream().anyMatch(t -> t.module().equals("Facs")));
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.setup.QuickLogSetupTest"`
Expected: FAIL (classe ausente).

- [ ] **Step 3: Implementar `QuickLogSetup`**

```java
package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.setup.SetupLogTypes.LogType;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Setup rápido das logs: cria as categorias `logs {modulo}` e os canais `📂・{log}` para os
 *  módulos ativos, restringe a visibilidade ao @everyone e grava cada canal na sua chave.
 *  Idempotente: pula tipos já configurados com canal vivo e reusa categorias existentes. */
public final class QuickLogSetup {

    public record Summary(int created, int skipped, int categories) {}

    private QuickLogSetup() {}

    public static String channelName(String logKey) {
        return "📂・" + logKey.replaceFirst("^log-", "");
    }

    public static String categoryName(String moduleLabel) {
        return "logs " + moduleLabel.toLowerCase(Locale.ROOT);
    }

    public static String logModuleFor(String botModuleName) {
        return "Sales".equals(botModuleName) ? "Vendas" : botModuleName;
    }

    public static List<LogType> typesForActive(Set<String> activeBotModuleNames) {
        Set<String> labels = activeBotModuleNames.stream()
                .map(QuickLogSetup::logModuleFor).collect(Collectors.toSet());
        return SetupLogTypes.ALL.stream()
                .filter(t -> labels.contains(t.module()))
                .collect(Collectors.toList());
    }

    /** BLOQUEANTE — chame fora da thread do JDA (ex.: ctx.scheduler().executor()). Usa
     *  .complete() para criar canais em sequência e gravar as chaves uma a uma. */
    public static Summary run(Guild guild, BotContext ctx) {
        int created = 0;
        int skipped = 0;
        Set<String> categoriesTouched = new java.util.HashSet<>();
        for (LogType type : typesForActive(ctx.activeModules())) {
            GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
            String existing = cfg.channel(type.key());
            if (existing != null && guild.getTextChannelById(existing) != null) {
                skipped++;
                continue; // já configurado e vivo
            }
            String catName = categoryName(type.module());
            Category category = guild.getCategoriesByName(catName, true).stream().findFirst()
                    .orElseGet(() -> guild.createCategory(catName)
                            .addPermissionOverride(guild.getPublicRole(), null,
                                    EnumSet.of(Permission.VIEW_CHANNEL))
                            .complete());
            categoriesTouched.add(category.getId());
            TextChannel channel = guild.createTextChannel(channelName(type.key()), category).complete();
            GuildConfig updated = GuildConfigEdits.withChannel(cfg, type.key(), channel.getId());
            ctx.database().guildConfig().save(updated);
            created++;
        }
        return new Summary(created, skipped, categoriesTouched.size());
    }
}
```

> **Verificar:** o pacote de `Category`/`TextChannel` no JDA 6.4.2 (`...entities.channel.concrete`) e a assinatura de `createCategory(...).addPermissionOverride(role, allow, deny)`. Confirme no autocompletar; ajuste imports se diferirem. `GuildConfig.channel(key)` e `GuildConfigEdits.withChannel(cfg, key, id)` já existem (usados em `SetupComponentHandler.saveChannel`).

- [ ] **Step 4: Rodar o teste e confirmar que passa**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.setup.QuickLogSetupTest"`
Expected: PASS (4 testes).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/setup/QuickLogSetup.java src/test/java/dev/davimf/basebot/modules/base/setup/QuickLogSetupTest.java
git commit -m "feat(setup): QuickLogSetup — auto-create log categories/channels (idempotent)"
```

---

### Task 18: Botão ⚡ Setup rápido na tela de Logs + handler

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupView.java` (método `logsPage`)
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java:75-145` (switch de `onButton`)

**Interfaces:**
- Consumes: `QuickLogSetup.run` (Task 17), `ctx.activeModules()`, `SetupView.logsPage`, `ctx.scheduler().executor()`.

- [ ] **Step 1: Adicionar o botão na `logsPage`**

Na `SetupView.logsPage(...)`, junto da fileira de botões de navegação de página que já existe
(os botões `logpage` ◀/▶), adicione um botão primário de setup rápido. Siga o padrão de
`Button`/`ActionRow` já usado nesse arquivo (JDA 6: `net.dv8tion.jda.api.components.buttons.Button`,
`ActionRow.of(...)`):
```java
Button.primary(ComponentId.of(NS, "logquicksetup").raw(), "⚡ Setup rápido")
```
Coloque-o na mesma `ActionRow` dos botões de página (ou numa fileira própria acima/abaixo),
conforme couber no layout atual do container.

> **Verificar:** o nome do construtor de id (`ComponentId.of(NS, "logquicksetup")`) e como
> `logsPage` injeta botões hoje — replique exatamente o que os botões `logpage` fazem (mesma
> classe `Button`, mesmo jeito de adicionar ao `Container`).

- [ ] **Step 2: Tratar o botão no `onButton`**

No switch de `SetupComponentHandler.onButton`, adicione um caso (depois de `case "logpage"`):
```java
            case "logquicksetup" -> {
                event.deferEdit().queue();
                net.dv8tion.jda.api.entities.Guild guild = event.getGuild();
                ctx.scheduler().executor().submit(() -> {
                    QuickLogSetup.Summary s = QuickLogSetup.run(guild, ctx);
                    ctx.database().actionLogs().log(guildId, event.getUser().getId(), null,
                            "LOG_QUICK_SETUP", s.created() + " criados / " + s.skipped() + " já existiam");
                    GuildConfig refreshed = ctx.database().guildConfig().findOrEmpty(guildId);
                    event.getHook().editOriginalComponents(SetupView.logsPage(refreshed, 0))
                            .useComponentsV2().queue();
                });
            }
```
Adicione os imports necessários (`QuickLogSetup`, `GuildConfig` já deve estar importado).

> **Confirmação ao usuário:** o re-render da tela (selects já preenchidos com os canais
> criados) É a confirmação visual. Se quiser uma mensagem efêmera de resumo, use o helper
> `Replies` do projeto (V2, conforme a regra "todo bot message é Container V2") — verifique a
> assinatura de `Replies` em uso neste handler antes de chamar a partir do `getHook()`.

- [ ] **Step 3: Build completo**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (compila + testes).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/setup/SetupView.java src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java
git commit -m "feat(setup): ⚡ Setup rápido button wires QuickLogSetup into /setup → Logs"
```

---

### Task 19: Verificação manual (smoke test)

Sem harness para listeners JDA; valide num servidor de testes com os intents habilitados.

**Pré-requisitos:** `GUILD_MEMBERS` e `MESSAGE_CONTENT` ligados no Dev Portal; bot com permissão `VIEW_AUDIT_LOG`; canais de log configurados em `/setup → Logs` (Base, 2 páginas).

- [ ] **Step 1: Subir o bot**

Run: `./gradlew run` (ou o entrypoint do projeto). Expected: conecta sem erro de intent.

- [ ] **Step 2: Checklist por categoria** (cada um deve gerar a embed V2 no canal certo)

- [ ] Membros: trocar apelido, adicionar/remover cargo, aplicar/remover timeout → `log-membros` com responsável.
- [ ] Entrar/sair, kick, ban/unban → `log-entradas`/`log-saidas`/`log-kicks`/`log-bans` com moderador+motivo.
- [ ] Voz: entrar/sair/mudar; **mover à força** (deve mostrar moderador, não o log normal); server mute/deafen; iniciar/parar transmissão; ligar/desligar câmera → `log-voz`.
- [ ] Mensagens: apagar (mostra conteúdo); editar (antes→depois); apagar em massa (`/purge`/`/clear`); fixar → `log-mensagens`.
- [ ] Canais: criar/deletar/renomear/mudar categoria/tópico/permissões → `log-canais`.
- [ ] Cargos: criar/deletar/renomear/cor/permissões → `log-cargos`.
- [ ] Servidor: nome/foto/banner, emoji add/del/rename, sticker, criar convite, criar/iniciar/cancelar evento, criar/arquivar thread → `log-servidor`.
- [ ] Audit do Discord: uma ação do bot (ex.: `/ban`) mostra o moderador no campo "Motivo" do registro de auditoria.
- [ ] **Setup rápido:** clicar **⚡ Setup rápido** em `/setup → Logs` cria `logs {modulo}` + canais `📂・{log}` só dos módulos ativos, ocultos do @everyone, e os selects re-renderizam preenchidos. Clicar de novo **não** duplica (idempotente).

- [ ] **Step 3: Confirmar a retenção**

Verifique que linhas de `message_archive` existem após enviar mensagens e que `purgeOlderThan` roda no boot (log/efeito). (Retenção de 90d é validada por unidade na Task 2.)

- [ ] **Step 4: Atualizar memória/PROGRESS**

Atualize `docs/superpowers/PROGRESS.md` marcando a "Logging completion" como concluída e anotando a limitação de unpin e a necessidade dos intents no Dev Portal.

---

## Self-Review (cobertura do spec)

- §1 Listeners focados → Tasks 6-13 (8 listeners) + Task 14 (registro/remoção). ✓
- §2 Chaves de log → Task 1. ✓
- §2.5 Setup rápido (módulos ativos, categorias `logs {modulo}`, canais `📂・{log}`,
  idempotente, ocultos do @everyone) → Tasks 16 (`activeModules`), 17 (`QuickLogSetup`),
  18 (botão + handler). ✓
- §3 Matriz de eventos: membros (T7), voz incl. movido/mute/stream/câmera (T10), mensagens incl. bulk/pin (T9), canais (T11), cargos (T12), servidor/emoji/convite/evento/thread (T13), ban/kick (T7/T8). ✓
- §4 Arquivo de mensagens + 90d + purga → Task 2 + Task 14 Step 2. ✓
- §5 `ModReason` + `AuditLookup` + aplicação → Tasks 3, 4, 15. ✓
- §6 Intents → Task 5. ✓
- §7 Análise de imagem → fora de escopo (não há task; correto). ✓

**Limitações conhecidas (documentadas no plano):** unpin não tem evento JDA confiável (Task 9); nomes exatos de alguns eventos/intents JDA 6.4.2 devem ser confirmados no autocompletar durante a execução (Tasks 5, 7, 11, 13). Essas verificações estão explícitas como notas dentro das tasks.
