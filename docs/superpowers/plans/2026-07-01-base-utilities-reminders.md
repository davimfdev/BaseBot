# Utilidades — Plano 2 (lembrete persistente)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (inline). Steps em checkbox.

**Goal:** `/lembrete criar|listar|cancelar` — o bot lembra o usuário via DM (fallback pro canal) quando o tempo acaba; persistido, à prova de restart.

**Architecture:** Migração 033 + `ReminderRepository` (testado) + `LembreteCommand` (subcomandos) + `ReminderService.sweep()` agendado no `onReady` (a cada 30s: pega os vencidos, **claim** por delete, entrega DM→canal).

**Tech Stack:** Java 22, JDA 6.4.2 (Components V2), SQLite.

## Global Constraints

- **JDK 22.** **Sem commits.** Base ≠ facs. Components V2 + `Panels`. **Migração 033** anexada a `SqliteMigrator.MIGRATIONS`.
- **Delete-first (claim)** antes de entregar → evita entrega dupla entre ciclos do sweep. DM falha (`CANNOT_SEND_TO_USER`) → fallback no canal de origem.

**Símbolos confirmados:** `Durations.parse` → `OptionalLong` (ms); `ctx.jda().openPrivateChannelById(id)` → `CacheRestAction<PrivateChannel>`; `ctx.jda().getTextChannelById(id)`; `ctx.scheduler().repeating`; `Replies.ephemeral/reply`; teste SQLite `new SqliteManager(new BotConfig.Sqlite(path))`.

---

## Task 1: Migração 033 + `Reminder` + `ReminderRepository` + teste

**Files:** Create `resources/db/sqlite/033_reminders.sql`, `modules/base/utility/Reminder.java`, `ReminderRepository.java`; Modify `SqliteMigrator.java`; Test `ReminderRepositoryTest`.

**Produces:** `record Reminder(String id, String guildId, String userId, String channelId, String message, long remindAt)`; `create/due/delete/listByUser/cancel`.

- [ ] **Step 1: Migração** `033_reminders.sql`
```sql
CREATE TABLE IF NOT EXISTS reminders (
    id         TEXT PRIMARY KEY,
    guild_id   TEXT NOT NULL,
    user_id    TEXT NOT NULL,
    channel_id TEXT NOT NULL,
    message    TEXT NOT NULL,
    remind_at  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_reminders_due ON reminders (remind_at);
```
- [ ] **Step 2:** Anexar a `SqliteMigrator.MIGRATIONS` (após `032_quiz.sql`): `"/db/sqlite/033_reminders.sql"`.
- [ ] **Step 3:** `Reminder.java`
```java
package dev.davimf.basebot.modules.base.utility;

/** Um lembrete agendado (migração 033). */
public record Reminder(String id, String guildId, String userId, String channelId, String message, long remindAt) {}
```
- [ ] **Step 4: Teste** `ReminderRepositoryTest.java`
```java
package dev.davimf.basebot.modules.base.utility;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReminderRepositoryTest {
    private SqliteManager sqlite;
    private ReminderRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new ReminderRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void createDueDelete() {
        String id = repo.create("g", "u", "c", "beber água", 1000L);
        assertEquals(8, id.length());
        assertEquals(1, repo.due(2000L).size());
        assertTrue(repo.due(500L).isEmpty());
        repo.delete(id);
        assertTrue(repo.due(2000L).isEmpty());
    }

    @Test
    void listByUserAndCancelOwnership() {
        String id = repo.create("g", "u1", "c", "x", 5000L);
        repo.create("g", "u2", "c", "y", 5000L);
        assertEquals(1, repo.listByUser("g", "u1").size());
        assertFalse(repo.cancel(id, "u2")); // não é dono
        assertTrue(repo.cancel(id, "u1"));
        assertTrue(repo.listByUser("g", "u1").isEmpty());
    }
}
```
- [ ] **Step 5: Run** `./gradlew test --tests "*.ReminderRepositoryTest"` → FAIL.
- [ ] **Step 6:** `ReminderRepository.java`
```java
package dev.davimf.basebot.modules.base.utility;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** SQLite store dos lembretes (migração 033). */
public final class ReminderRepository {

    private final SqliteManager sqlite;

    public ReminderRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    public String create(String guildId, String userId, String channelId, String message, long remindAt) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String sql = "INSERT INTO reminders (id, guild_id, user_id, channel_id, message, remind_at) VALUES (?,?,?,?,?,?)";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, guildId);
            ps.setString(3, userId);
            ps.setString(4, channelId);
            ps.setString(5, message);
            ps.setLong(6, remindAt);
            ps.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new RepositoryException("reminder create " + guildId + "/" + userId, e);
        }
    }

    public List<Reminder> due(long now) {
        return query("SELECT * FROM reminders WHERE remind_at <= ? ORDER BY remind_at", ps -> ps.setLong(1, now));
    }

    public List<Reminder> listByUser(String guildId, String userId) {
        return query("SELECT * FROM reminders WHERE guild_id=? AND user_id=? ORDER BY remind_at", ps -> {
            ps.setString(1, guildId);
            ps.setString(2, userId);
        });
    }

    public void delete(String id) {
        exec("DELETE FROM reminders WHERE id=?", ps -> ps.setString(1, id));
    }

    /** Cancela só se o lembrete for do próprio usuário. */
    public boolean cancel(String id, String userId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM reminders WHERE id=? AND user_id=?")) {
            ps.setString(1, id);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("reminder cancel " + id, e);
        }
    }

    private interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private List<Reminder> query(String sql, Binder binder) {
        List<Reminder> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Reminder(rs.getString("id"), rs.getString("guild_id"), rs.getString("user_id"),
                            rs.getString("channel_id"), rs.getString("message"), rs.getLong("remind_at")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("reminder query", e);
        }
    }

    private void exec(String sql, Binder binder) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("reminder exec", e);
        }
    }
}
```
- [ ] **Step 7: Run** `./gradlew test --tests "*.ReminderRepositoryTest"` → PASS.

---

## Task 2: `ReminderService` (sweep)

**Files:** Create `modules/base/utility/ReminderService.java`.

**Produces:** `sweep()` (chamado pelo scheduler); `ReminderRepository repo()`.

- [ ] **Step 1: Implementar**
```java
package dev.davimf.basebot.modules.base.utility;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.List;

/** Dispara os lembretes vencidos: DM (fallback pro canal). À prova de restart. */
public final class ReminderService {

    private final BotContext ctx;
    private final ReminderRepository repo;

    public ReminderService(BotContext ctx) {
        this.ctx = ctx;
        this.repo = new ReminderRepository(ctx.database().sqlite());
    }

    public ReminderRepository repo() { return repo; }

    public void sweep() {
        if (ctx.jda() == null) {
            return;
        }
        for (Reminder r : repo.due(System.currentTimeMillis())) {
            repo.delete(r.id()); // claim: evita entrega dupla no próximo ciclo
            deliver(r);
        }
    }

    private void deliver(Reminder r) {
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(r.guildId()));
        String text = "⏰ **Lembrete:** " + r.message();
        ctx.jda().openPrivateChannelById(r.userId()).queue(
                pc -> pc.sendMessageComponents(Panels.container(accent, Panels.text(text))).useComponentsV2()
                        .queue(ok -> { }, err -> fallback(r, accent, text)),
                err -> fallback(r, accent, text));
    }

    private void fallback(Reminder r, int accent, String text) {
        TextChannel ch = ctx.jda().getTextChannelById(r.channelId());
        if (ch != null) {
            ch.sendMessageComponents(Panels.container(accent, Panels.text("<@" + r.userId() + "> " + text)))
                    .useComponentsV2().setAllowedMentions(List.of(Message.MentionType.USER))
                    .queue(ok -> { }, err -> { });
        }
    }
}
```
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 3: `/lembrete criar|listar|cancelar`

**Files:** Create `modules/base/commands/LembreteCommand.java`.

**Consumes:** `ReminderService` (→ `repo()`), `Durations`.

- [ ] **Step 1: Implementar**
```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.utility.Reminder;
import dev.davimf.basebot.modules.base.utility.ReminderService;
import dev.davimf.basebot.util.Durations;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;
import java.util.OptionalLong;

/** /lembrete criar|listar|cancelar. */
public final class LembreteCommand implements SlashCommand {
    private final ReminderService service;
    public LembreteCommand(ReminderService service) { this.service = service; }

    @Override public String name() { return "lembrete"; }

    @Override public SlashCommandData data() {
        return Commands.slash("lembrete", "Cria lembretes que o bot te envia por DM.")
                .addSubcommands(
                        new SubcommandData("criar", "Cria um lembrete")
                                .addOptions(new OptionData(OptionType.STRING, "tempo", "Ex.: 2h, 30m, 1d", true))
                                .addOptions(new OptionData(OptionType.STRING, "mensagem", "O que lembrar", true)),
                        new SubcommandData("listar", "Mostra seus lembretes ativos"),
                        new SubcommandData("cancelar", "Cancela um lembrete")
                                .addOptions(new OptionData(OptionType.STRING, "id", "ID do lembrete", true)));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        switch (event.getSubcommandName() == null ? "" : event.getSubcommandName()) {
            case "criar" -> criar(event, ctx);
            case "listar" -> listar(event, ctx);
            case "cancelar" -> cancelar(event, ctx);
            default -> Replies.ephemeral(event, ctx, "Subcomando inválido.");
        }
    }

    private void criar(SlashCommandInteractionEvent event, BotContext ctx) {
        OptionalLong dur = Durations.parse(event.getOption("tempo", "", OptionMapping::getAsString));
        if (dur.isEmpty()) {
            Replies.ephemeral(event, ctx, "Tempo inválido. Ex.: `2h`, `30m`, `1d`.");
            return;
        }
        String msg = event.getOption("mensagem", "", OptionMapping::getAsString).trim();
        if (msg.length() > 500) {
            msg = msg.substring(0, 500);
        }
        long remindAt = System.currentTimeMillis() + dur.getAsLong();
        service.repo().create(event.getGuild().getId(), event.getUser().getId(), event.getChannel().getId(),
                msg, remindAt);
        Replies.reply(event, ctx, "⏰ Lembrete criado! Vou te avisar <t:" + (remindAt / 1000) + ":R>.");
    }

    private void listar(SlashCommandInteractionEvent event, BotContext ctx) {
        List<Reminder> mine = service.repo().listByUser(event.getGuild().getId(), event.getUser().getId());
        if (mine.isEmpty()) {
            Replies.ephemeral(event, ctx, "Você não tem lembretes ativos.");
            return;
        }
        StringBuilder sb = new StringBuilder("## ⏰ Seus lembretes\n");
        for (Reminder r : mine) {
            String m = r.message().length() > 60 ? r.message().substring(0, 60) + "…" : r.message();
            sb.append("\n`").append(r.id()).append("` · <t:").append(r.remindAt() / 1000).append(":R> · ").append(m);
        }
        Replies.ephemeral(event, ctx, sb.toString());
    }

    private void cancelar(SlashCommandInteractionEvent event, BotContext ctx) {
        String id = event.getOption("id", "", OptionMapping::getAsString);
        boolean ok = service.repo().cancel(id, event.getUser().getId());
        Replies.ephemeral(event, ctx, ok ? "Lembrete cancelado." : "Lembrete não encontrado (ou não é seu).");
    }
}
```
> **Verificar:** overload `event.getOption(name, default, resolver)`; se não existir, `OptionMapping o = event.getOption(name); String v = o==null?"":o.getAsString();`.
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 4: Registro no `BaseModule` + build + smoke

**Files:** Modify `modules/base/BaseModule.java`.

- [ ] **Step 1:** Campo:
```java
    private dev.davimf.basebot.modules.base.utility.ReminderService reminders;
```
- [ ] **Step 2:** No `register(...)`, após a enquete:
```java
        // Lembretes (Base) — persistidos, DM à prova de restart.
        this.reminders = new dev.davimf.basebot.modules.base.utility.ReminderService(ctx);
        registry.command(new dev.davimf.basebot.modules.base.commands.LembreteCommand(reminders));
```
- [ ] **Step 3:** No `onReady(...)`:
```java
        // Lembretes: dispara os vencidos a cada 30s.
        if (reminders != null) {
            ctx.scheduler().repeating(reminders::sweep, 30, 30, TimeUnit.SECONDS);
        }
```
- [ ] **Step 4: Run** `./gradlew build` → BUILD SUCCESSFUL (todos os testes, incl. `ReminderRepositoryTest`).
- [ ] **Step 5: Smoke (servidor de teste):**
  - `/lembrete criar tempo:1m mensagem:"testar"` → confirma com `<t:…:R>`; em ~1 min chega **na DM** (ou no canal se a DM estiver bloqueada).
  - `/lembrete listar` → mostra o(s) ativo(s) com id; `/lembrete cancelar id:<id>` → cancela; cancelar id de outro → recusa.
  - **Restart do bot com um lembrete futuro** → ainda dispara na hora certa (persistido).

## Self-Review
- **Cobertura do spec (Plano 2):** migração 033 + repo testado (T1); sweep DM+fallback, claim por delete (T2); criar/listar/cancelar (T3); registro + agendamento (T4). ✓
- **Anti-duplicação:** `sweep` faz `delete` (claim) **antes** de entregar. **Ownership:** `cancel(id, userId)` só apaga o do dono. **DM à prova de bloqueio:** callback de erro → fallback no canal. ✓
- **Consistência:** `Reminder(id,guildId,userId,channelId,message,remindAt)`, `ReminderRepository.create/due/delete/listByUser/cancel`, `ReminderService.sweep/repo`. ✓
- **Pontos a confirmar no build (inline):** overload de `getOption` com default; `openPrivateChannelById`.