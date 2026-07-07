<!-- [OUTLINE START]
Markdown Document: Base Module — Bot Profile Commands (`/bot-name`, `/bot-icon`, `/bot-nick`)
[OUTLINE END] -->



# Base Module — Bot Profile Commands (`/bot-name`, `/bot-icon`, `/bot-nick`)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Module 1 bot-profile commands: `/bot-name` and `/bot-icon` change the **global** bot profile (guarded by a 2-changes-per-hour limiter, per Discord's hard cap) and `/bot-nick` changes the bot's nickname **only in the current guild**.

**Architecture:** A pure, in-memory `ProfileRateLimiter` (TDD) — shared via `BotContext` — enforces "max 2 per rolling hour" per action and reports retry-after time. Commands call JDA's `AccountManager` (global name/avatar) or per-guild `modifyNickname`. Only `/bot-name` and `/bot-icon` touch global state (BOTSPECS Golden Rule); `/bot-nick` is guild-scoped and unthrottled.

**Tech Stack:** Java 22, JDA 6.4.2 (`getSelfUser().getManager().setName/setAvatar`, `Icon.from(InputStream)`, `attachment.getProxy().download()`, `member.modifyNickname`), JUnit 5.

## Global Constraints

- JDK 22; JDA `6.4.2`. Confirmed APIs: `jda.getSelfUser().getManager().setName(String)` / `.setAvatar(Icon)`; `Icon.from(java.io.InputStream)` (throws IOException); `OptionMapping.getAsAttachment()` → `Message.Attachment` with `isImage()` and `getProxy().download()` (`CompletableFuture<InputStream>`); `guild.getSelfMember().modifyNickname(String)`. Permissions `MANAGE_SERVER`, `NICKNAME_MANAGE`.
- BOTSPECS Golden Rule: only `/bot-name` and `/bot-icon` affect the **global** bot profile; everything else is guild-scoped. `/bot-nick` is per-guild.
- BOTSPECS §API Limitations: global profile updates are hard-capped by Discord at **2 per hour** — guard and inform the user.
- Portuguese UI copy. TDD: failing test first; frequent commits; DRY; YAGNI.

---

### Task 1: `ProfileRateLimiter` (2-per-hour guard) + `BotContext` wiring

**Files:**
- Create: `src/main/java/dev/davimf/basebot/ratelimit/ProfileRateLimiter.java`
- Modify: `src/main/java/dev/davimf/basebot/core/BotContext.java` (construct + expose)
- Test: `src/test/java/dev/davimf/basebot/ratelimit/ProfileRateLimiterTest.java`

**Interfaces:**
- Produces: `ProfileRateLimiter(int maxPerWindow, long windowMillis)`; `check(String key, long nowMillis) -> Decision`; `Decision(boolean allowed, long retryAfterMillis)`. `BotContext.profileRateLimiter() -> ProfileRateLimiter` (configured 2 / 3_600_000ms).

- [ ] **Step 1: Write the failing test**

```java
package dev.davimf.basebot.ratelimit;

import dev.davimf.basebot.ratelimit.ProfileRateLimiter.Decision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProfileRateLimiterTest {

    private static final long HOUR = 3_600_000L;
    private static final long T0 = 1_000_000L;

    @Test
    void allowsUpToLimitThenBlocksWithinWindow() {
        ProfileRateLimiter rl = new ProfileRateLimiter(2, HOUR);
        assertTrue(rl.check("name", T0).allowed());
        assertTrue(rl.check("name", T0 + 1_000).allowed());
        Decision third = rl.check("name", T0 + 2_000);
        assertFalse(third.allowed());
        assertTrue(third.retryAfterMillis() > 0);
    }

    @Test
    void allowsAgainOnceWindowHasPassed() {
        ProfileRateLimiter rl = new ProfileRateLimiter(2, HOUR);
        rl.check("name", T0);
        rl.check("name", T0 + 1_000);
        assertTrue(rl.check("name", T0 + HOUR).allowed(), "oldest hit expired -> a slot frees");
    }

    @Test
    void keysAreIndependent() {
        ProfileRateLimiter rl = new ProfileRateLimiter(2, HOUR);
        rl.check("name", T0);
        rl.check("name", T0);
        assertTrue(rl.check("icon", T0).allowed());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.ratelimit.ProfileRateLimiterTest"`
Expected: FAIL — `ProfileRateLimiter` cannot be resolved.

- [ ] **Step 3: Implement + wire into `BotContext`**

`ProfileRateLimiter.java`:

```java
package dev.davimf.basebot.ratelimit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory rolling-window rate limiter for global bot-profile changes. Discord caps
 * username/avatar updates at 2 per hour (BOTSPECS §API Limitations); this enforces that
 * locally and reports how long until the next slot frees so the user can be informed.
 */
public final class ProfileRateLimiter {

    /** Outcome of a {@link #check}: whether it was allowed and, if not, the wait in ms. */
    public record Decision(boolean allowed, long retryAfterMillis) {}

    private final int maxPerWindow;
    private final long windowMillis;
    private final Map<String, Deque<Long>> hits = new HashMap<>();

    public ProfileRateLimiter(int maxPerWindow, long windowMillis) {
        this.maxPerWindow = maxPerWindow;
        this.windowMillis = windowMillis;
    }

    /** Consumes a slot for {@code key} when allowed; otherwise reports the retry-after. */
    public synchronized Decision check(String key, long nowMillis) {
        Deque<Long> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!window.isEmpty() && nowMillis - window.peekFirst() >= windowMillis) {
            window.pollFirst();
        }
        if (window.size() >= maxPerWindow) {
            long retryAfter = windowMillis - (nowMillis - window.peekFirst());
            return new Decision(false, Math.max(0, retryAfter));
        }
        window.addLast(nowMillis);
        return new Decision(true, 0L);
    }
}
```

In `BotContext.java`: add `import dev.davimf.basebot.ratelimit.ProfileRateLimiter;`, a field, construction, and an accessor:

```java
    private final ProfileRateLimiter profileRateLimiter = new ProfileRateLimiter(2, 3_600_000L);
```

```java
    /** Guards the global bot-profile 2-changes-per-hour Discord cap (/bot-name, /bot-icon). */
    public ProfileRateLimiter profileRateLimiter() {
        return profileRateLimiter;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "dev.davimf.basebot.ratelimit.ProfileRateLimiterTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/ratelimit/ProfileRateLimiter.java \
        src/main/java/dev/davimf/basebot/core/BotContext.java \
        src/test/java/dev/davimf/basebot/ratelimit/ProfileRateLimiterTest.java
git commit -m "feat(base): add 2-per-hour bot-profile rate limiter"
```

---

### Task 2: `/bot-name` and `/bot-icon` (global, rate-limited)

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/BotNameCommand.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/BotIconCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java` (register)
- Test: compile (rate-limit logic covered by Task 1).

**Interfaces:** consumes `ctx.profileRateLimiter().check(...)`; JDA `AccountManager.setName/setAvatar`, `Icon.from`.

- [ ] **Step 1: Implement both commands**

`BotNameCommand.java`:

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.ratelimit.ProfileRateLimiter;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /bot-name — changes the GLOBAL bot username (max 2/hour; BOTSPECS Module 1). */
public final class BotNameCommand implements SlashCommand {

    @Override
    public String name() {
        return "bot-name";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("bot-name", "Altera o nome global do bot (limite: 2x por hora).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addOption(OptionType.STRING, "nome", "Novo nome global (2-32 caracteres)", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        String nome = event.getOption("nome", OptionMapping::getAsString);
        if (nome == null || nome.length() < 2 || nome.length() > 32) {
            event.reply("O nome deve ter entre 2 e 32 caracteres.").setEphemeral(true).queue();
            return;
        }
        ProfileRateLimiter.Decision d = ctx.profileRateLimiter().check("name", System.currentTimeMillis());
        if (!d.allowed()) {
            long mins = (d.retryAfterMillis() + 59_999) / 60_000;
            event.reply("Limite de 2 alterações por hora atingido. Tente novamente em ~" + mins + " min.")
                    .setEphemeral(true).queue();
            return;
        }
        event.getJDA().getSelfUser().getManager().setName(nome).queue(
                ok -> {
                    ctx.database().actionLogs().log(
                            event.getGuild() == null ? null : event.getGuild().getId(),
                            event.getUser().getId(), null, "BOT_NAME", nome);
                    event.reply("Nome global alterado para **" + nome + "**. "
                            + "(Afeta todos os servidores; limite do Discord: 2x por hora.)").queue();
                },
                err -> event.reply("Falha ao alterar o nome: " + err.getMessage()).setEphemeral(true).queue());
    }
}
```

`BotIconCommand.java`:

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.ratelimit.ProfileRateLimiter;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.io.IOException;
import java.io.InputStream;

/** /bot-icon — changes the GLOBAL bot avatar (max 2/hour; BOTSPECS Module 1). */
public final class BotIconCommand implements SlashCommand {

    @Override
    public String name() {
        return "bot-icon";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("bot-icon", "Altera o avatar global do bot (limite: 2x por hora).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addOption(OptionType.ATTACHMENT, "imagem", "Imagem (PNG/JPG/GIF)", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        Message.Attachment att = event.getOption("imagem", OptionMapping::getAsAttachment);
        if (att == null || !att.isImage()) {
            event.reply("Envie uma imagem válida.").setEphemeral(true).queue();
            return;
        }
        ProfileRateLimiter.Decision d = ctx.profileRateLimiter().check("icon", System.currentTimeMillis());
        if (!d.allowed()) {
            long mins = (d.retryAfterMillis() + 59_999) / 60_000;
            event.reply("Limite de 2 alterações por hora atingido. Tente novamente em ~" + mins + " min.")
                    .setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        att.getProxy().download().thenAccept(stream -> {
            try (InputStream in = stream) {
                Icon icon = Icon.from(in);
                event.getJDA().getSelfUser().getManager().setAvatar(icon).queue(
                        ok -> {
                            ctx.database().actionLogs().log(
                                    event.getGuild() == null ? null : event.getGuild().getId(),
                                    event.getUser().getId(), null, "BOT_ICON", att.getFileName());
                            event.getHook().sendMessage("Avatar global atualizado. "
                                    + "(Afeta todos os servidores; limite do Discord: 2x por hora.)").queue();
                        },
                        err -> event.getHook().sendMessage("Falha ao atualizar o avatar: "
                                + err.getMessage()).queue());
            } catch (IOException e) {
                event.getHook().sendMessage("Falha ao processar a imagem.").queue();
            }
        }).exceptionally(t -> {
            event.getHook().sendMessage("Falha ao baixar a imagem.").queue();
            return null;
        });
    }
}
```

In `BaseModule.java`, add imports + register (a "Bot profile" block):

```java
import dev.davimf.basebot.modules.base.commands.BotIconCommand;
import dev.davimf.basebot.modules.base.commands.BotNameCommand;
```

```java
        // Bot profile (BOTSPECS Module 1) — /bot-name + /bot-icon are GLOBAL (2x/hour cap).
        registry.command(new BotNameCommand());
        registry.command(new BotIconCommand());
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/commands/BotNameCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/BotIconCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(base): add /bot-name and /bot-icon with 2/hour guard"
```

---

### Task 3: `/bot-nick` (guild-scoped, unthrottled)

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/BotNickCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java` (register)
- Test: compile + full build.

**Interfaces:** JDA `guild.getSelfMember().modifyNickname(String)`. No rate limit (per-guild, not the global cap).

- [ ] **Step 1: Implement the command**

`BotNickCommand.java`:

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * /bot-nick — changes the bot's nickname ONLY in the current guild (BOTSPECS Module 1
 * Golden Rule: this never touches the global profile, so it is not rate-limited).
 */
public final class BotNickCommand implements SlashCommand {

    @Override
    public String name() {
        return "bot-nick";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("bot-nick", "Altera o apelido do bot apenas neste servidor.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.NICKNAME_MANAGE))
                .addOption(OptionType.STRING, "apelido", "Novo apelido (vazio para remover)", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        String apelido = event.getOption("apelido", OptionMapping::getAsString);
        event.getGuild().getSelfMember().modifyNickname(apelido).queue(
                ok -> {
                    ctx.database().actionLogs().log(event.getGuild().getId(),
                            event.getUser().getId(), null, "BOT_NICK", apelido);
                    event.reply(apelido == null || apelido.isBlank()
                            ? "Apelido removido neste servidor."
                            : "Apelido alterado para **" + apelido + "** neste servidor.").queue();
                },
                err -> event.reply("Falha: " + err.getMessage()).setEphemeral(true).queue());
    }
}
```

In `BaseModule.java`, add import + register:

```java
import dev.davimf.basebot.modules.base.commands.BotNickCommand;
```

```java
        registry.command(new BotNickCommand());
```

- [ ] **Step 2: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — compiles, all unit tests pass, `basebot.jar` produced.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/commands/BotNickCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(base): add /bot-nick (guild-scoped nickname)"
```

---

## Self-Review

**Spec coverage:**
- `/bot-name`, `/bot-icon` — global profile, 2-per-hour cap enforced + user informed → Task 1 (limiter) + Task 2. ✓
- `/bot-nick` — guild-scoped nickname, not global, unthrottled → Task 3. ✓
- Golden Rule honored: only `/bot-name` + `/bot-icon` touch global state. ✓
- **Deferred** (remaining Module 1): `/lock`·`/unlock`, `/listacargo`, `/embed`·`/editembed`, `/addemoji`, `/formulario`.

**Placeholder scan:** No stubs; all three commands fully implemented including async avatar download and rate-limit messaging.

**Type consistency:** `ProfileRateLimiter.check(String,long)` → `Decision(allowed, retryAfterMillis)`; `BotContext.profileRateLimiter()`; JDA `getSelfUser().getManager().setName/setAvatar`, `Icon.from(InputStream)`, `att.getProxy().download()`, `guild.getSelfMember().modifyNickname` — referenced identically across tasks. ✓
