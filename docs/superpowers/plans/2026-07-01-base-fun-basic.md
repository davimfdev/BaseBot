# Fun — Plano 1 (básico: simples, social, jokenpo, GIF)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (inline). Steps em checkbox.

**Goal:** Comandos de diversão sem recompensa: `/dado` `/coinflip` `/ship`; `/rep` `/biscoito` (1/dia + ranking); `/jokenpo`; `/toca_aqui` `/abracar` (GIF via nekos.best). (Forca + quiz = Plano 2.)

**Architecture:** Pacote `modules/base/fun/`. Puros/testáveis (`ShipCalc`, `JokenpoResult`, `GifClient.extractUrl`) + `SocialRepository` (gate de cooldown atômico, migração 031) + jokenpo in-memory + `GifClient` (HttpClient + Jackson, async).

**Tech Stack:** Java 22, JDA 6.4.2 (Components V2), SQLite, `java.net.http.HttpClient` + Jackson.

## Global Constraints

- **JDK 22.** **Sem commits.** Base ≠ facs. Components V2 + `Emojis` + house style. **Fun = sem XP/moedas.**
- **Migração 031** anexada a `SqliteMigrator.MIGRATIONS`.
- Saídas lúdicas **públicas** (`event.replyComponents(...).useComponentsV2()`); rankings **efêmeros**; dar rep/biscoito via `Replies.reply` (temporário).
- **Gate atômico** do rep/biscoito (INSERT OR IGNORE → UPDATE condicional). Alvo bot/si mesmo bloqueado.

**Símbolos confirmados:** `Replies.ephemeral/reply(event,ctx,msg)`; `Panels.container/text/divider`; `ComponentId.of/arg`; `EmbedColor.resolve`; `Emojis.of/button`; `SlashCommand{name,data,execute}`; `ctx.scheduler().once`; teste SQLite `new SqliteManager(new BotConfig.Sqlite(path))`; HTTP `HttpClient.newHttpClient()` + `ObjectMapper` (ver `TicketIngestClient`).

---

## Task 1: `ShipCalc` (puro) + teste

**Files:** Create `modules/base/fun/ShipCalc.java`, `test/.../fun/ShipCalcTest.java`.

- [ ] **Step 1: Teste**
```java
package dev.davimf.basebot.modules.base.fun;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipCalcTest {
    @Test
    void stablePerPairRegardlessOfOrder() {
        assertEquals(ShipCalc.percent("111", "222"), ShipCalc.percent("222", "111"));
    }

    @Test
    void withinZeroToHundred() {
        for (int i = 0; i < 50; i++) {
            int p = ShipCalc.percent("u" + i, "x" + (i * 7));
            assertTrue(p >= 0 && p <= 100, "fora da faixa: " + p);
        }
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.ShipCalcTest"` → FAIL.
- [ ] **Step 3: Implementar**
```java
package dev.davimf.basebot.modules.base.fun;

/** Compatibilidade 0–100 estável por par (hash determinístico da dupla ordenada). Puro. */
public final class ShipCalc {

    private ShipCalc() {}

    public static int percent(String idA, String idB) {
        String key = idA.compareTo(idB) <= 0 ? idA + ":" + idB : idB + ":" + idA;
        return Math.floorMod(key.hashCode(), 101);
    }

    /** Barrinha visual de 10 blocos para uma porcentagem. */
    public static String bar(int percent) {
        int filled = Math.round(percent / 10f);
        return "`[" + "█".repeat(filled) + "░".repeat(10 - filled) + "]` " + percent + "%";
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.ShipCalcTest"` → PASS.

---

## Task 2: `/dado` + `/coinflip` + `/ship`

**Files:** Create `modules/base/commands/{DadoCommand,CoinflipCommand,ShipCommand}.java`.

- [ ] **Step 1:** `DadoCommand`
```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.concurrent.ThreadLocalRandom;

/** /dado [lados] — rola um dado. */
public final class DadoCommand implements SlashCommand {
    @Override public String name() { return "dado"; }

    @Override public SlashCommandData data() {
        return Commands.slash("dado", "Rola um dado.")
                .addOptions(new net.dv8tion.jda.api.interactions.commands.build.OptionData(
                        OptionType.INTEGER, "lados", "Número de lados (2–1000, default 6)", false));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        OptionMapping o = event.getOption("lados");
        long lados = o == null ? 6 : Math.max(2, Math.min(1000, o.getAsLong()));
        long roll = ThreadLocalRandom.current().nextLong(1, lados + 1);
        int accent = EmbedColor.resolve(ctx.database().guildConfig()
                .findOrEmpty(event.getGuild() == null ? "0" : event.getGuild().getId()));
        event.replyComponents(Panels.container(accent, Panels.text(
                Emojis.of(Emojis.GAME, "🎲") + " Você rolou um **d" + lados + "** e tirou **" + roll + "**!")))
                .useComponentsV2().queue();
    }
}
```
> **Verificar:** `Emojis.GAME` (existe: `game.png`); se faltar, usar outro. `OptionData` import completo.
- [ ] **Step 2:** `CoinflipCommand`
```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.concurrent.ThreadLocalRandom;

/** /coinflip — cara ou coroa. */
public final class CoinflipCommand implements SlashCommand {
    @Override public String name() { return "coinflip"; }

    @Override public SlashCommandData data() {
        return Commands.slash("coinflip", "Cara ou coroa.");
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        String side = ThreadLocalRandom.current().nextBoolean() ? "Cara" : "Coroa";
        int accent = EmbedColor.resolve(ctx.database().guildConfig()
                .findOrEmpty(event.getGuild() == null ? "0" : event.getGuild().getId()));
        event.replyComponents(Panels.container(accent, Panels.text(
                Emojis.of(Emojis.CASH, "🪙") + " Deu **" + side + "**!")))
                .useComponentsV2().queue();
    }
}
```
- [ ] **Step 3:** `ShipCommand`
```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.modules.base.fun.ShipCalc;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /ship @a @b — compatibilidade. */
public final class ShipCommand implements SlashCommand {
    @Override public String name() { return "ship"; }

    @Override public SlashCommandData data() {
        return Commands.slash("ship", "Calcula a compatibilidade entre duas pessoas.")
                .addOptions(new OptionData(OptionType.USER, "pessoa1", "Primeira pessoa", true))
                .addOptions(new OptionData(OptionType.USER, "pessoa2", "Segunda pessoa", true));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        User a = event.getOption("pessoa1", OptionMapping::getAsUser);
        User b = event.getOption("pessoa2", OptionMapping::getAsUser);
        if (a == null || b == null) {
            Replies.ephemeral(event, ctx, "Escolha duas pessoas.");
            return;
        }
        int p = ShipCalc.percent(a.getId(), b.getId());
        String verdict = p >= 90 ? "Casamento à vista! 💍" : p >= 60 ? "Tem química! 🔥"
                : p >= 30 ? "Talvez rolе… 🤔" : "Melhor como amigos. 🙃";
        int accent = EmbedColor.resolve(ctx.database().guildConfig()
                .findOrEmpty(event.getGuild() == null ? "0" : event.getGuild().getId()));
        event.replyComponents(Panels.container(accent, Panels.text(
                        "## " + Emojis.of(Emojis.HEART, "💞") + " Ship\n" + a.getAsMention() + " + " + b.getAsMention()
                                + "\n" + ShipCalc.bar(p) + "\n" + verdict)))
                .useComponentsV2().queue();
    }
}
```
> **Verificar:** `Emojis.HEART`/`GAME`/`CASH` existem. Corrigir o typo "rolе" → "rolar" na implementação real.
- [ ] **Step 4: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 3: Migração 031 + `SocialRepository` (gate atômico) + teste

**Files:** Create `resources/db/sqlite/031_social.sql`, `modules/base/fun/SocialRepository.java`; Modify `SqliteMigrator.java`; Test `SocialRepositoryTest`.

**Produces:** `record GiveResult(boolean ok, long newPoints, long readyAt)`; `record Entry(String userId, long points)`; `give/points/top`.

- [ ] **Step 1: Migração** `031_social.sql`
```sql
CREATE TABLE IF NOT EXISTS social_points (
    guild_id TEXT NOT NULL, user_id TEXT NOT NULL, type TEXT NOT NULL,
    points INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (guild_id, user_id, type)
);
CREATE TABLE IF NOT EXISTS social_gifts (
    guild_id TEXT NOT NULL, giver_id TEXT NOT NULL, type TEXT NOT NULL,
    last_ts INTEGER NOT NULL, PRIMARY KEY (guild_id, giver_id, type)
);
```
- [ ] **Step 2:** Anexar a `SqliteMigrator.MIGRATIONS` (após `030_giveaways.sql`): `"/db/sqlite/031_social.sql"`.
- [ ] **Step 3: Teste** `SocialRepositoryTest.java`
```java
package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SocialRepositoryTest {
    private SqliteManager sqlite;
    private SocialRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new SocialRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void firstGiveSucceedsAndIncrements() {
        SocialRepository.GiveResult r = repo.give("g", "giver", "target", "rep", 1000L, 60_000L);
        assertTrue(r.ok());
        assertEquals(1, r.newPoints());
        assertEquals(1, repo.points("g", "target", "rep"));
    }

    @Test
    void secondGiveWithinCooldownFails() {
        repo.give("g", "giver", "target", "rep", 1000L, 60_000L);
        SocialRepository.GiveResult r = repo.give("g", "giver", "target", "rep", 2000L, 60_000L);
        assertFalse(r.ok());
        assertEquals(61_000L, r.readyAt());
        assertEquals(1, repo.points("g", "target", "rep"));
    }

    @Test
    void giveAgainAfterCooldownSucceeds() {
        repo.give("g", "giver", "t", "rep", 1000L, 60_000L);
        SocialRepository.GiveResult r = repo.give("g", "giver", "t", "rep", 1000L + 60_000L, 60_000L);
        assertTrue(r.ok());
        assertEquals(2, repo.points("g", "t", "rep"));
    }

    @Test
    void topOrdersByPoints() {
        repo.give("g", "x", "a", "rep", 1L, 0L);
        repo.give("g", "y", "b", "rep", 1L, 0L);
        repo.give("g", "z", "b", "rep", 1L, 0L);
        var top = repo.top("g", "rep", 10);
        assertEquals("b", top.get(0).userId());
        assertEquals(2, top.get(0).points());
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.SocialRepositoryTest"` → FAIL.
- [ ] **Step 5: Implementar** `SocialRepository.java`
```java
package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Pontos sociais (rep/biscoito) com gate de cooldown atômico (migração 031). */
public final class SocialRepository {

    public record GiveResult(boolean ok, long newPoints, long readyAt) {}
    public record Entry(String userId, long points) {}

    private final SqliteManager sqlite;

    public SocialRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    public GiveResult give(String g, String giver, String target, String type, long now, long cooldownMs) {
        if (!gate(g, giver, type, now, cooldownMs)) {
            return new GiveResult(false, points(g, target, type), lastTs(g, giver, type) + cooldownMs);
        }
        addPoint(g, target, type);
        return new GiveResult(true, points(g, target, type), 0);
    }

    /** Escrita condicional: primeira vez (INSERT OR IGNORE) ou cooldown vencido (UPDATE). */
    private boolean gate(String g, String giver, String type, long now, long cooldownMs) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ins = c.prepareStatement(
                     "INSERT OR IGNORE INTO social_gifts (guild_id, giver_id, type, last_ts) VALUES (?,?,?,?)")) {
            ins.setString(1, g);
            ins.setString(2, giver);
            ins.setString(3, type);
            ins.setLong(4, now);
            if (ins.executeUpdate() > 0) {
                return true;
            }
        } catch (SQLException e) {
            throw new RepositoryException("social gate insert " + g + "/" + giver, e);
        }
        try (Connection c = sqlite.getConnection();
             PreparedStatement up = c.prepareStatement(
                     "UPDATE social_gifts SET last_ts=? WHERE guild_id=? AND giver_id=? AND type=? AND (? - last_ts) >= ?")) {
            up.setLong(1, now);
            up.setString(2, g);
            up.setString(3, giver);
            up.setString(4, type);
            up.setLong(5, now);
            up.setLong(6, cooldownMs);
            return up.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("social gate update " + g + "/" + giver, e);
        }
    }

    private void addPoint(String g, String user, String type) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO social_points (guild_id, user_id, type, points) VALUES (?,?,?,1) "
                     + "ON CONFLICT (guild_id, user_id, type) DO UPDATE SET points = points + 1")) {
            ps.setString(1, g);
            ps.setString(2, user);
            ps.setString(3, type);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("social addPoint " + g + "/" + user, e);
        }
    }

    public long points(String g, String user, String type) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT points FROM social_points WHERE guild_id=? AND user_id=? AND type=?")) {
            ps.setString(1, g);
            ps.setString(2, user);
            ps.setString(3, type);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RepositoryException("social points " + g + "/" + user, e);
        }
    }

    private long lastTs(String g, String giver, String type) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT last_ts FROM social_gifts WHERE guild_id=? AND giver_id=? AND type=?")) {
            ps.setString(1, g);
            ps.setString(2, giver);
            ps.setString(3, type);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RepositoryException("social lastTs " + g + "/" + giver, e);
        }
    }

    public List<Entry> top(String g, String type, int limit) {
        List<Entry> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT user_id, points FROM social_points WHERE guild_id=? AND type=? "
                     + "ORDER BY points DESC, user_id LIMIT ?")) {
            ps.setString(1, g);
            ps.setString(2, type);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Entry(rs.getString("user_id"), rs.getLong("points")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("social top " + g, e);
        }
    }
}
```
- [ ] **Step 6: Run** `./gradlew test --tests "*.SocialRepositoryTest"` → PASS.

---

## Task 4: `SocialView` + `/rep` + `/biscoito`

**Files:** Create `modules/base/fun/SocialView.java`, `SocialGive.java` (helper compartilhado); `modules/base/commands/{RepCommand,BiscoitoCommand}.java`.

**Produces:** `SocialGive.handle(event, ctx, repo, type, label, emoji)` (dá ou mostra ranking).

- [ ] **Step 1:** `SocialView.java` (ranking) e `SocialGive.java` (fluxo compartilhado)
```java
package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.component.Panels;
import net.dv8tion.jda.api.components.container.Container;

import java.util.List;

public final class SocialView {
    private SocialView() {}

    public static Container ranking(int accent, String titleEmoji, String label, List<SocialRepository.Entry> top) {
        StringBuilder sb = new StringBuilder("## " + titleEmoji + " Ranking de " + label + "\n");
        if (top.isEmpty()) {
            sb.append("-# Ninguém pontuou ainda.");
        } else {
            for (int i = 0; i < top.size(); i++) {
                sb.append("\n`").append(i + 1).append(".` <@").append(top.get(i).userId())
                        .append("> · `").append(top.get(i).points()).append("`");
            }
        }
        return Panels.container(accent, Panels.text(sb.toString()));
    }
}
```
```java
package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

/** Fluxo compartilhado de /rep e /biscoito. */
public final class SocialGive {

    private static final long COOLDOWN_MS = 24L * 60 * 60 * 1000;

    private SocialGive() {}

    public static void handle(SlashCommandInteractionEvent event, BotContext ctx, String type,
                              String label, String titleEmoji) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        SocialRepository repo = new SocialRepository(ctx.database().sqlite());
        String g = event.getGuild().getId();
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(g));
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        if (target == null) {
            event.replyComponents(SocialView.ranking(accent, titleEmoji, label,
                            repo.top(g, type, 10)))
                    .useComponentsV2().setEphemeral(true).queue();
            return;
        }
        if (target.getUser().isBot() || target.getId().equals(event.getUser().getId())) {
            Replies.ephemeral(event, ctx, "Você não pode dar " + label + " para si mesmo ou para um bot.");
            return;
        }
        SocialRepository.GiveResult r = repo.give(g, event.getUser().getId(), target.getId(), type,
                System.currentTimeMillis(), COOLDOWN_MS);
        if (!r.ok()) {
            Replies.ephemeral(event, ctx, "Você já deu " + label + " hoje. Tente de novo <t:"
                    + (r.readyAt() / 1000) + ":R>.");
            return;
        }
        Replies.reply(event, ctx, titleEmoji + " " + event.getMember().getAsMention() + " deu " + label
                + " para " + target.getAsMention() + "! Agora ele(a) tem **" + r.newPoints() + "**.");
    }
}
```
- [ ] **Step 2:** `RepCommand` e `BiscoitoCommand`
```java
// RepCommand: name "rep", option USER "usuario" (false), execute →
//   SocialGive.handle(event, ctx, "rep", "reputação", Emojis.of(Emojis.STAR, "⭐"));
// BiscoitoCommand: name "biscoito", option USER "usuario" (false), execute →
//   SocialGive.handle(event, ctx, "cookie", "biscoitos", Emojis.of(Emojis.GIFT, "🍪"));
```
Escrever os dois arquivos completos no molde do `SaldoCommand` (opção `usuario` opcional; corpo = a linha do `SocialGive.handle(...)`). Descrições: "Dá reputação a alguém (ou vê o ranking)." e "Dá um biscoito a alguém (ou vê o ranking)."
- [ ] **Step 3: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 5: `JokenpoResult` (puro) + teste

**Files:** Create `modules/base/fun/JokenpoResult.java`, `test/.../fun/JokenpoResultTest.java`.

- [ ] **Step 1: Teste**
```java
package dev.davimf.basebot.modules.base.fun;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JokenpoResultTest {
    @Test
    void ties() {
        for (JokenpoResult.Choice c : JokenpoResult.Choice.values()) {
            assertEquals(JokenpoResult.Outcome.TIE, JokenpoResult.decide(c, c));
        }
    }

    @Test
    void wins() {
        assertEquals(JokenpoResult.Outcome.WIN_A,
                JokenpoResult.decide(JokenpoResult.Choice.PEDRA, JokenpoResult.Choice.TESOURA));
        assertEquals(JokenpoResult.Outcome.WIN_A,
                JokenpoResult.decide(JokenpoResult.Choice.PAPEL, JokenpoResult.Choice.PEDRA));
        assertEquals(JokenpoResult.Outcome.WIN_A,
                JokenpoResult.decide(JokenpoResult.Choice.TESOURA, JokenpoResult.Choice.PAPEL));
        assertEquals(JokenpoResult.Outcome.WIN_B,
                JokenpoResult.decide(JokenpoResult.Choice.TESOURA, JokenpoResult.Choice.PEDRA));
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.JokenpoResultTest"` → FAIL.
- [ ] **Step 3: Implementar**
```java
package dev.davimf.basebot.modules.base.fun;

/** Lógica pura do jokenpo. */
public final class JokenpoResult {

    public enum Choice { PEDRA, PAPEL, TESOURA }
    public enum Outcome { WIN_A, WIN_B, TIE }

    private JokenpoResult() {}

    public static Outcome decide(Choice a, Choice b) {
        if (a == b) {
            return Outcome.TIE;
        }
        boolean aWins = (a == Choice.PEDRA && b == Choice.TESOURA)
                || (a == Choice.PAPEL && b == Choice.PEDRA)
                || (a == Choice.TESOURA && b == Choice.PAPEL);
        return aWins ? Outcome.WIN_A : Outcome.WIN_B;
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.JokenpoResultTest"` → PASS.

---

## Task 6: Jokenpo — `JokenpoView` + `JokenpoService` + `/jokenpo` + handler

**Files:** Create `modules/base/fun/{JokenpoView,JokenpoService,JokenpoComponentHandler}.java`, `modules/base/commands/JokenpoCommand.java`.

**Consumes:** `JokenpoResult`. **Produces:** namespace `jkp`; partida in-memory.

- [ ] **Step 1:** `JokenpoView.java`
```java
package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;

public final class JokenpoView {
    public static final String NS = "jkp";

    private JokenpoView() {}

    public static Container challenge(int accent, String challengerMention, String targetMention) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.GAME, "✊") + " Jokenpô\n" + challengerMention + " desafiou "
                        + targetMention + "!\nOs dois escolhem em segredo:"),
                Panels.divider(),
                ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "pick", "PEDRA"), "Pedra"),
                        Button.secondary(ComponentId.of(NS, "pick", "PAPEL"), "Papel"),
                        Button.secondary(ComponentId.of(NS, "pick", "TESOURA"), "Tesoura")));
    }

    public static Container result(int accent, String line) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.GAME, "✊") + " Jokenpô — resultado"),
                Panels.divider(),
                Panels.text(line));
    }
}
```
- [ ] **Step 2:** `JokenpoService.java`
```java
package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Coordena partidas de jokenpo (in-memory, uma por mensagem-desafio). */
public final class JokenpoService {

    /** Partida: escolhas com sincronização no próprio objeto. */
    static final class Match {
        final String challengerId;
        final String targetId;
        JokenpoResult.Choice choiceA;
        JokenpoResult.Choice choiceB;
        Match(String challengerId, String targetId) { this.challengerId = challengerId; this.targetId = targetId; }
        synchronized boolean vote(String userId, JokenpoResult.Choice c) {
            if (userId.equals(challengerId)) { choiceA = c; }
            else if (userId.equals(targetId)) { choiceB = c; }
            else { return false; }
            return true;
        }
        synchronized boolean complete() { return choiceA != null && choiceB != null; }
    }

    private final BotContext ctx;
    private final ConcurrentHashMap<String, Match> matches = new ConcurrentHashMap<>();

    public JokenpoService(BotContext ctx) { this.ctx = ctx; }

    public void register(String messageId, String challengerId, String targetId) {
        matches.put(messageId, new Match(challengerId, targetId));
        ctx.scheduler().once(() -> matches.remove(messageId), 60, TimeUnit.SECONDS);
    }

    public void onPick(ButtonInteractionEvent event, JokenpoResult.Choice choice) {
        String messageId = event.getMessageId();
        Match m = matches.get(messageId);
        if (m == null) {
            Replies.ephemeral(event, ctx, "Esse jogo já encerrou.");
            return;
        }
        String uid = event.getUser().getId();
        if (!uid.equals(m.challengerId) && !uid.equals(m.targetId)) {
            Replies.ephemeral(event, ctx, "Você não faz parte deste jogo.");
            return;
        }
        if (!m.vote(uid, choice)) {
            return;
        }
        Replies.ephemeral(event, ctx, "Você escolheu **" + choice.name().toLowerCase() + "**.");
        if (m.complete() && matches.remove(messageId, m)) {
            reveal(event, m);
        }
    }

    private void reveal(ButtonInteractionEvent event, Match m) {
        int accent = EmbedColor.resolve(ctx.database().guildConfig()
                .findOrEmpty(event.getGuild() == null ? "0" : event.getGuild().getId()));
        JokenpoResult.Outcome o = JokenpoResult.decide(m.choiceA, m.choiceB);
        String line = "<@" + m.challengerId + "> escolheu **" + m.choiceA.name().toLowerCase() + "**\n"
                + "<@" + m.targetId + "> escolheu **" + m.choiceB.name().toLowerCase() + "**\n\n"
                + switch (o) {
                    case WIN_A -> "🏆 <@" + m.challengerId + "> venceu!";
                    case WIN_B -> "🏆 <@" + m.targetId + "> venceu!";
                    case TIE -> "🤝 Empate!";
                };
        if (event.getChannel() instanceof TextChannel ch) {
            ch.editMessageComponentsById(event.getMessageId(), JokenpoView.result(accent, line))
                    .useComponentsV2().queue(ok -> { }, err -> { });
        }
    }
}
```
> **Verificar:** `event.getMessageId()` no `ButtonInteractionEvent`; `editMessageComponentsById` no `TextChannel`.
- [ ] **Step 3:** `JokenpoCommand.java`
```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.fun.JokenpoService;
import dev.davimf.basebot.modules.base.fun.JokenpoView;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /jokenpo @alvo — pedra, papel e tesoura. */
public final class JokenpoCommand implements SlashCommand {
    private final JokenpoService service;
    public JokenpoCommand(JokenpoService service) { this.service = service; }

    @Override public String name() { return "jokenpo"; }

    @Override public SlashCommandData data() {
        return Commands.slash("jokenpo", "Desafia alguém para pedra, papel e tesoura.")
                .addOptions(new OptionData(OptionType.USER, "usuario", "Quem você desafia", true));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        Member alvo = event.getOption("usuario", OptionMapping::getAsMember);
        if (alvo == null || alvo.getUser().isBot() || alvo.getId().equals(event.getUser().getId())) {
            Replies.ephemeral(event, ctx, "Escolha outra pessoa (não bot, não você).");
            return;
        }
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));
        String challenger = event.getMember().getId();
        String target = alvo.getId();
        event.replyComponents(JokenpoView.challenge(accent, event.getMember().getAsMention(), alvo.getAsMention()))
                .useComponentsV2()
                .setAllowedMentions(java.util.List.of(net.dv8tion.jda.api.entities.Message.MentionType.USER))
                .queue(hook -> hook.retrieveOriginal().queue(msg -> service.register(msg.getId(), challenger, target)));
    }
}
```
> **Verificar:** `event.replyComponents(...).queue(InteractionHook -> hook.retrieveOriginal().queue(...))` para pegar o id da mensagem enviada; se a API divergir, usar `event.getHook().retrieveOriginal()` após `queue()`.
- [ ] **Step 4:** `JokenpoComponentHandler.java`
```java
package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

public final class JokenpoComponentHandler implements ComponentHandler {
    private final JokenpoService service;
    public JokenpoComponentHandler(JokenpoService service) { this.service = service; }

    @Override public String namespace() { return JokenpoView.NS; }

    @Override public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"pick".equals(id.action())) {
            return;
        }
        try {
            service.onPick(event, JokenpoResult.Choice.valueOf(id.arg(0)));
        } catch (IllegalArgumentException ignored) {
            // escolha inválida
        }
    }
}
```
- [ ] **Step 5: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 7: `GifClient` + `/toca_aqui` + `/abracar`

**Files:** Create `modules/base/fun/GifClient.java`, `test/.../fun/GifClientTest.java`, `modules/base/commands/{TocaAquiCommand,AbracarCommand}.java`.

- [ ] **Step 1: Teste** `GifClientTest`
```java
package dev.davimf.basebot.modules.base.fun;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class GifClientTest {
    @Test
    void extractsUrlFromNekosBestJson() {
        String json = "{\"results\":[{\"artist_href\":\"a\",\"source_url\":\"s\",\"url\":\"https://nekos.best/x.gif\"}]}";
        assertEquals(Optional.of("https://nekos.best/x.gif"), GifClient.extractUrl(json));
    }

    @Test
    void emptyOnBadJson() {
        assertEquals(Optional.empty(), GifClient.extractUrl("nope"));
        assertEquals(Optional.empty(), GifClient.extractUrl("{\"results\":[]}"));
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.GifClientTest"` → FAIL.
- [ ] **Step 3: Implementar** `GifClient.java`
```java
package dev.davimf.basebot.modules.base.fun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Busca GIFs de ação no nekos.best (grátis, sem chave). Categorias: pat, hug, etc. */
public final class GifClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public CompletableFuture<Optional<String>> fetch(String category) {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://nekos.best/api/v2/" + category))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> extractUrl(resp.body()))
                .exceptionally(e -> Optional.empty());
    }

    static Optional<String> extractUrl(String json) {
        try {
            JsonNode url = JSON.readTree(json).path("results").path(0).path("url");
            return url.isTextual() && !url.asText().isBlank() ? Optional.of(url.asText()) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.GifClientTest"` → PASS.
- [ ] **Step 5:** `TocaAquiCommand` e `AbracarCommand` (compartilham o padrão async):
```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.modules.base.fun.GifClient;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /toca_aqui @u — GIF de carinho (nekos.best pat). */
public final class TocaAquiCommand implements SlashCommand {
    private final GifClient gif;
    public TocaAquiCommand(GifClient gif) { this.gif = gif; }

    @Override public String name() { return "toca_aqui"; }

    @Override public SlashCommandData data() {
        return Commands.slash("toca_aqui", "Faz um carinho em alguém.")
                .addOptions(new OptionData(OptionType.USER, "usuario", "Quem recebe o carinho", true));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        User alvo = event.getOption("usuario", OptionMapping::getAsUser);
        if (alvo == null) {
            Replies.ephemeral(event, ctx, "Escolha alguém.");
            return;
        }
        int accent = EmbedColor.resolve(ctx.database().guildConfig()
                .findOrEmpty(event.getGuild() == null ? "0" : event.getGuild().getId()));
        String texto = event.getUser().getAsMention() + " fez um carinho em " + alvo.getAsMention() + " 🥰";
        event.deferReply().queue();
        gif.fetch("pat").thenAccept(url -> {
            if (url.isPresent()) {
                event.getHook().editOriginalComponents(Panels.container(accent, Panels.text(texto),
                                MediaGallery.of(MediaGalleryItem.fromUrl(url.get()))))
                        .useComponentsV2().queue(ok -> { }, err -> { });
            } else {
                event.getHook().editOriginalComponents(Panels.container(accent, Panels.text(texto)))
                        .useComponentsV2().queue(ok -> { }, err -> { });
            }
        });
    }
}
```
`AbracarCommand` = idêntico com `name()="abracar"`, descrição "Abraça alguém.", texto "abraçou", categoria `"hug"`, emoji 🤗. Escrever o arquivo completo.
> **Verificar:** `event.deferReply().queue()` + `event.getHook().editOriginalComponents(...).useComponentsV2()`; `MediaGallery`/`MediaGalleryItem` (usados no `PixDispatch`). O `thenAccept` roda numa thread do HttpClient — as chamadas JDA `.queue()` são thread-safe.
- [ ] **Step 6: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 8: Registro no `BaseModule` + build + smoke

**Files:** Modify `modules/base/BaseModule.java`.

- [ ] **Step 1:** No `register(...)`, após os sorteios:
```java
        // Fun / Social (Base) — só diversão, sem XP/moedas.
        registry.command(new dev.davimf.basebot.modules.base.commands.DadoCommand());
        registry.command(new dev.davimf.basebot.modules.base.commands.CoinflipCommand());
        registry.command(new dev.davimf.basebot.modules.base.commands.ShipCommand());
        registry.command(new dev.davimf.basebot.modules.base.commands.RepCommand());
        registry.command(new dev.davimf.basebot.modules.base.commands.BiscoitoCommand());
        dev.davimf.basebot.modules.base.fun.JokenpoService jokenpo =
                new dev.davimf.basebot.modules.base.fun.JokenpoService(ctx);
        registry.command(new dev.davimf.basebot.modules.base.commands.JokenpoCommand(jokenpo));
        registry.component(new dev.davimf.basebot.modules.base.fun.JokenpoComponentHandler(jokenpo));
        dev.davimf.basebot.modules.base.fun.GifClient gifClient = new dev.davimf.basebot.modules.base.fun.GifClient();
        registry.command(new dev.davimf.basebot.modules.base.commands.TocaAquiCommand(gifClient));
        registry.command(new dev.davimf.basebot.modules.base.commands.AbracarCommand(gifClient));
```
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL (todos os testes: `ShipCalcTest`, `SocialRepositoryTest`, `JokenpoResultTest`, `GifClientTest`).
- [ ] **Step 3: Smoke (servidor de teste):**
  - `/dado`, `/dado lados:20`, `/coinflip`, `/ship @a @b` (mesma % ao repetir).
  - `/rep @alt` (uma vez → ok; de novo → cooldown com `<t:…:R>`); `/rep` sem alvo → ranking (efêmero); idem `/biscoito`.
  - `/jokenpo @alt`: os dois clicam (escolha efêmera); ao completar, a mensagem revela o vencedor. Terceiro clicando → "não faz parte".
  - `/toca_aqui @alt` e `/abracar @alt` → GIF do nekos.best (ou fallback em texto se a API falhar).

## Self-Review
- **Cobertura do spec (Plano 1):** ship puro (T1); dado/coinflip/ship (T2); social gate atômico (T3); rep/biscoito + ranking (T4); jokenpo puro (T5); jokenpo interativo, revela-uma-vez via `remove(key,value)` + voto `synchronized` (T6); GIF nekos.best async + extractUrl puro (T7); registro (T8). Forca + quiz = Plano 2. ✓
- **Consistência:** `ShipCalc.percent/bar`, `SocialRepository.give/points/top` + `GiveResult/Entry`, `JokenpoResult.decide/Choice/Outcome`, `JokenpoView.NS`, `GifClient.fetch/extractUrl`. ✓
- **Pontos a confirmar no build (inline):** `Emojis.GAME/HEART/HELP`; `event.replyComponents(...).queue(hook -> hook.retrieveOriginal()...)`; `editOriginalComponents(...).useComponentsV2()`.
