<!-- [OUTLINE START]
Markdown Document: Base Module — Moderation & Role Core Implementation Plan
[OUTLINE END] -->



# Base Module — Moderation & Role Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the functional spine of Module 1 (Base & Utility): role-hierarchy-validated moderation (`/kick`, `/ban`, `/unban`), role management (`/addcargo`, `/removecargo`), and message purging (`/clear`, `/cl`) with the Discord 14-day bulk-delete rule handled gracefully.

**Architecture:** Two pure, fully unit-tested logic cores — `RoleHierarchy` (the "moderator AND bot must outrank the target" rule) and `MessagePurge` (14-day partition) — wrapped by thin JDA command classes that adapt live `Member`/`Message` objects to those cores. Commands register through the existing `BaseModule` / `CommandManager` framework and log to SQLite via `ActionLogRepository`.

**Tech Stack:** Java 22, JDA 6.4.2 (Components V2 era; `net.dv8tion.jda.api.components.*`), JUnit 5, existing BaseBot framework (`SlashCommand`, `BotContext`).

## Global Constraints

- JDK 22; JDA `6.4.2`. Verify unknown JDA symbols with `javap -cp <cached JDA jar>` — do **not** guess packages. (Buttons/ActionRow are under `net.dv8tion.jda.api.components.*`; there is no `addActionRow`.)
- All state is **Guild-specific**; commands must no-op outside a guild. (BOTSPECS Golden Rule)
- Role commands MUST validate **both** the moderator's and the bot's hierarchy against the target (BOTSPECS Module 1, `/addcargo`).
- Bulk delete (`/cl`, `/clear`) MUST ignore/handle gracefully messages older than 14 days (BOTSPECS §API Limitations).
- Map IDs, never names (BOTSPECS §State Management).
- Command UI copy is Portuguese (matches existing `/ping`, `/pix`).
- TDD: failing test first; frequent commits; DRY; YAGNI.

---

### Task 1: `RoleHierarchy` validation core

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/moderation/RoleHierarchy.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/moderation/RoleHierarchyTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `RoleHierarchy.actorOutranks(int actorTop, int targetTop, boolean actorIsOwner) -> boolean`
  - `RoleHierarchy.canModerate(int actorTop, int targetTop, boolean actorIsOwner, int botTop) -> boolean`

- [ ] **Step 1: Write the failing test**

```java
package dev.davimf.basebot.modules.base.moderation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleHierarchyTest {

    @Test
    void higherActorOutranksLowerTarget() {
        assertTrue(RoleHierarchy.actorOutranks(5, 3, false));
    }

    @Test
    void equalPositionsCannotAct() {
        assertFalse(RoleHierarchy.actorOutranks(3, 3, false));
    }

    @Test
    void ownerBypassesPosition() {
        assertTrue(RoleHierarchy.actorOutranks(1, 9, true));
    }

    @Test
    void canModerateRequiresBothActorAndBotToOutrank() {
        assertTrue(RoleHierarchy.canModerate(5, 3, false, 6));   // actor>target, bot>target
        assertFalse(RoleHierarchy.canModerate(5, 3, false, 2));  // bot does NOT outrank target
        assertFalse(RoleHierarchy.canModerate(3, 5, false, 6));  // actor does NOT outrank target
    }

    @Test
    void ownerStillBlockedWhenBotCannotOutrank() {
        assertFalse(RoleHierarchy.canModerate(1, 5, true, 4));   // owner ok, but bot<target
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.moderation.RoleHierarchyTest"`
Expected: FAIL — `RoleHierarchy` cannot be resolved.

- [ ] **Step 3: Write minimal implementation**

```java
package dev.davimf.basebot.modules.base.moderation;

/**
 * Pure role-hierarchy rules for moderation (BOTSPECS Module 1). Positions are Discord
 * role positions (higher = more authority); the guild owner bypasses the position check
 * for the *actor* side, but the bot must always physically outrank the target to act.
 */
public final class RoleHierarchy {

    private RoleHierarchy() {}

    /** True if the actor may act on the target by position (owner bypasses position). */
    public static boolean actorOutranks(int actorTop, int targetTop, boolean actorIsOwner) {
        return actorIsOwner || actorTop > targetTop;
    }

    /** Full gate: the actor must outrank the target AND the bot must outrank the target. */
    public static boolean canModerate(int actorTop, int targetTop, boolean actorIsOwner, int botTop) {
        return actorOutranks(actorTop, targetTop, actorIsOwner) && botTop > targetTop;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.moderation.RoleHierarchyTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/moderation/RoleHierarchy.java \
        src/test/java/dev/davimf/basebot/modules/base/moderation/RoleHierarchyTest.java
git commit -m "feat(base): add role-hierarchy validation core"
```

---

### Task 2: Moderation adapter + `/kick`, `/ban`, `/unban`

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/moderation/Moderation.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/KickCommand.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/BanCommand.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/UnbanCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java` (register the three commands)
- Test: `src/test/java/dev/davimf/basebot/modules/base/moderation/ModerationTopPositionTest.java`

**Interfaces:**
- Consumes: `RoleHierarchy` (Task 1); `SlashCommand`, `BotContext`, `ActionLogRepository`.
- Produces:
  - `Moderation.topPosition(List<Integer> rolePositions) -> int` (highest position, 0 if none).
  - `Moderation.canModerate(net.dv8tion.jda.api.entities.Member actor, Member target, Member self) -> boolean`.
  - `KickCommand`, `BanCommand`, `UnbanCommand` (each `implements SlashCommand`).

- [ ] **Step 1: Write the failing test** (unit-tests the pure `topPosition` reducer; the JDA adapter is covered by compile + manual)

```java
package dev.davimf.basebot.modules.base.moderation;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ModerationTopPositionTest {

    @Test
    void returnsHighestPosition() {
        assertEquals(7, Moderation.topPosition(List.of(2, 7, 5)));
    }

    @Test
    void returnsZeroWhenNoRoles() {
        assertEquals(0, Moderation.topPosition(List.of()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.moderation.ModerationTopPositionTest"`
Expected: FAIL — `Moderation` cannot be resolved.

- [ ] **Step 3: Write the adapter, the three commands, and register them**

`Moderation.java`:

```java
package dev.davimf.basebot.modules.base.moderation;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.List;

/** Adapts live JDA members to the pure {@link RoleHierarchy} rules (BOTSPECS Module 1). */
public final class Moderation {

    private Moderation() {}

    /** Highest role position among the given positions; 0 (== @everyone) when empty. */
    public static int topPosition(List<Integer> rolePositions) {
        return rolePositions.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private static int topPosition(Member member) {
        return topPosition(member.getRoles().stream().map(Role::getPosition).toList());
    }

    /** True if {@code actor} and the bot ({@code self}) both outrank {@code target}. */
    public static boolean canModerate(Member actor, Member target, Member self) {
        return RoleHierarchy.canModerate(
                topPosition(actor), topPosition(target), actor.isOwner(), topPosition(self));
    }
}
```

`KickCommand.java`:

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /kick — removes a member, validating moderator + bot hierarchy (BOTSPECS Module 1). */
public final class KickCommand implements SlashCommand {

    @Override
    public String name() {
        return "kick";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("kick", "Expulsa um membro do servidor.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS))
                .addOption(OptionType.USER, "usuario", "Membro a expulsar", true)
                .addOption(OptionType.STRING, "motivo", "Motivo", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        if (target == null) {
            event.reply("Esse usuário não está no servidor.").setEphemeral(true).queue();
            return;
        }
        Member self = event.getGuild().getSelfMember();
        if (!Moderation.canModerate(event.getMember(), target, self)) {
            event.reply("Hierarquia insuficiente: você ou o bot não estão acima desse membro.")
                    .setEphemeral(true).queue();
            return;
        }
        String reason = event.getOption("motivo", "Sem motivo informado.", OptionMapping::getAsString);
        event.getGuild().kick(target).reason(reason).queue(
                ok -> {
                    ctx.database().actionLogs().log(event.getGuild().getId(),
                            event.getUser().getId(), target.getId(), "KICK", reason);
                    event.reply("Membro expulso: " + target.getUser().getAsTag()).queue();
                },
                err -> event.reply("Falha ao expulsar: " + err.getMessage()).setEphemeral(true).queue());
    }
}
```

`BanCommand.java`:

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.concurrent.TimeUnit;

/** /ban — bans a user (works by ID even if they left), validating hierarchy when present. */
public final class BanCommand implements SlashCommand {

    @Override
    public String name() {
        return "ban";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("ban", "Bane um usuário do servidor.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS))
                .addOption(OptionType.USER, "usuario", "Usuário a banir", true)
                .addOption(OptionType.STRING, "motivo", "Motivo", false)
                .addOption(OptionType.INTEGER, "dias", "Dias de mensagens a apagar (0-7)", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        User target = event.getOption("usuario", OptionMapping::getAsUser);
        if (target == null) {
            event.reply("Usuário inválido.").setEphemeral(true).queue();
            return;
        }
        Member targetMember = event.getOption("usuario", OptionMapping::getAsMember);
        Member self = event.getGuild().getSelfMember();
        if (targetMember != null && !Moderation.canModerate(event.getMember(), targetMember, self)) {
            event.reply("Hierarquia insuficiente: você ou o bot não estão acima desse membro.")
                    .setEphemeral(true).queue();
            return;
        }
        String reason = event.getOption("motivo", "Sem motivo informado.", OptionMapping::getAsString);
        long days = event.getOption("dias", 0L, OptionMapping::getAsLong);
        int clamped = (int) Math.max(0, Math.min(7, days));

        event.getGuild().ban(target, clamped, TimeUnit.DAYS).reason(reason).queue(
                ok -> {
                    ctx.database().actionLogs().log(event.getGuild().getId(),
                            event.getUser().getId(), target.getId(), "BAN", reason);
                    event.reply("Usuário banido: " + target.getAsTag()).queue();
                },
                err -> event.reply("Falha ao banir: " + err.getMessage()).setEphemeral(true).queue());
    }
}
```

`UnbanCommand.java`:

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /unban — lifts a ban by user. */
public final class UnbanCommand implements SlashCommand {

    @Override
    public String name() {
        return "unban";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("unban", "Remove o banimento de um usuário.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS))
                .addOption(OptionType.USER, "usuario", "Usuário a desbanir", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        User target = event.getOption("usuario", OptionMapping::getAsUser);
        if (target == null) {
            event.reply("Usuário inválido.").setEphemeral(true).queue();
            return;
        }
        event.getGuild().unban(target).queue(
                ok -> {
                    ctx.database().actionLogs().log(event.getGuild().getId(),
                            event.getUser().getId(), target.getId(), "UNBAN", null);
                    event.reply("Banimento removido: " + target.getAsTag()).queue();
                },
                err -> event.reply("Falha ao desbanir: " + err.getMessage()).setEphemeral(true).queue());
    }
}
```

In `BaseModule.java`, add imports and register the commands inside `register(...)` after `new PingCommand()`:

```java
import dev.davimf.basebot.modules.base.commands.BanCommand;
import dev.davimf.basebot.modules.base.commands.KickCommand;
import dev.davimf.basebot.modules.base.commands.UnbanCommand;
```

```java
        registry.command(new KickCommand());
        registry.command(new BanCommand());
        registry.command(new UnbanCommand());
```

- [ ] **Step 4: Run the unit test, then compile everything**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.moderation.ModerationTopPositionTest"`
Expected: PASS (2 tests).

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/moderation/Moderation.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/KickCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/BanCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/UnbanCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/BaseModule.java \
        src/test/java/dev/davimf/basebot/modules/base/moderation/ModerationTopPositionTest.java
git commit -m "feat(base): add /kick /ban /unban with hierarchy validation"
```

---

### Task 3: `MessagePurge` 14-day partition core

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/moderation/MessagePurge.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/moderation/MessagePurgeTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `MessagePurge.BULK_MAX_AGE_MILLIS` (long constant, 14 days).
  - `MessagePurge.Partition` (record: `List<Long> bulkDeletable`, `List<Long> tooOld`).
  - `MessagePurge.partitionByAge(List<Long> timestampsMillis, long nowMillis) -> Partition`.

- [ ] **Step 1: Write the failing test**

```java
package dev.davimf.basebot.modules.base.moderation;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MessagePurgeTest {

    private static final long NOW = 1_000_000_000_000L;
    private static final long DAY = 24L * 60 * 60 * 1000;

    @Test
    void recentMessagesAreAllBulkDeletable() {
        List<Long> ts = List.of(NOW - DAY, NOW - 2 * DAY, NOW - 13 * DAY);
        MessagePurge.Partition p = MessagePurge.partitionByAge(ts, NOW);
        assertEquals(3, p.bulkDeletable().size());
        assertEquals(0, p.tooOld().size());
    }

    @Test
    void messagesOlderThan14DaysAreTooOld() {
        List<Long> ts = List.of(NOW - 15 * DAY, NOW - 30 * DAY);
        MessagePurge.Partition p = MessagePurge.partitionByAge(ts, NOW);
        assertEquals(0, p.bulkDeletable().size());
        assertEquals(2, p.tooOld().size());
    }

    @Test
    void exactly14DaysIsTooOld() {
        List<Long> ts = List.of(NOW - 14 * DAY);
        MessagePurge.Partition p = MessagePurge.partitionByAge(ts, NOW);
        assertEquals(0, p.bulkDeletable().size());
        assertEquals(1, p.tooOld().size());
    }

    @Test
    void mixedAgesSplitCorrectly() {
        List<Long> ts = List.of(NOW - DAY, NOW - 20 * DAY, NOW - 5 * DAY);
        MessagePurge.Partition p = MessagePurge.partitionByAge(ts, NOW);
        assertEquals(2, p.bulkDeletable().size());
        assertEquals(1, p.tooOld().size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.moderation.MessagePurgeTest"`
Expected: FAIL — `MessagePurge` cannot be resolved.

- [ ] **Step 3: Write minimal implementation**

```java
package dev.davimf.basebot.modules.base.moderation;

import java.util.ArrayList;
import java.util.List;

/**
 * Partitions messages by age for bulk deletion. Discord refuses to bulk-delete messages
 * 14 days or older, so {@code /clear} must skip them gracefully (BOTSPECS §API Limitations).
 */
public final class MessagePurge {

    public static final long BULK_MAX_AGE_MILLIS = 14L * 24 * 60 * 60 * 1000;

    private MessagePurge() {}

    /** A message is bulk-deletable only if strictly younger than 14 days. */
    public static Partition partitionByAge(List<Long> timestampsMillis, long nowMillis) {
        List<Long> deletable = new ArrayList<>();
        List<Long> tooOld = new ArrayList<>();
        for (long ts : timestampsMillis) {
            if (nowMillis - ts < BULK_MAX_AGE_MILLIS) {
                deletable.add(ts);
            } else {
                tooOld.add(ts);
            }
        }
        return new Partition(deletable, tooOld);
    }

    public record Partition(List<Long> bulkDeletable, List<Long> tooOld) {}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.moderation.MessagePurgeTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/moderation/MessagePurge.java \
        src/test/java/dev/davimf/basebot/modules/base/moderation/MessagePurgeTest.java
git commit -m "feat(base): add 14-day message purge partition core"
```

---

### Task 4: `/clear` and `/cl` commands

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/ClearCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java` (register both names)
- Test: covered by Task 3 (`MessagePurge`) + compile; no new unit test (JDA history I/O is integration).

**Interfaces:**
- Consumes: `MessagePurge` (Task 3); `SlashCommand`, `BotContext`.
- Produces: `ClearCommand` with a constructor `ClearCommand(String name)` so `"clear"` and `"cl"` register as two commands sharing one implementation.

> **JDA verification note:** before writing, confirm the bulk-delete method on a guild text channel with
> `javap -cp <JDA jar> net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel | grep -iE "purgeMessages|deleteMessages"`.
> The code below uses `channel.purgeMessages(List<Message>)`, which exists on `MessageChannel` and
> handles splitting; we pre-filter with `MessagePurge` so only <14-day messages are passed.

- [ ] **Step 1: Implement the command**

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.MessagePurge;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/**
 * /clear (and alias /cl) — bulk-deletes recent messages, skipping any 14 days or older
 * (Discord cannot bulk-delete those). Registered twice under the two names.
 */
public final class ClearCommand implements SlashCommand {

    private final String name;

    public ClearCommand(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash(name, "Apaga mensagens recentes do canal (até 14 dias).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE))
                .addOption(OptionType.INTEGER, "quantidade", "Quantas mensagens (1-100)", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || !(event.getChannel() instanceof TextChannel channel)) {
            event.reply("Use este comando em um canal de texto do servidor.").setEphemeral(true).queue();
            return;
        }
        long requested = event.getOption("quantidade", 0L, OptionMapping::getAsLong);
        int amount = (int) Math.max(1, Math.min(100, requested));

        event.deferReply(true).queue();
        channel.getHistory().retrievePast(amount).queue(messages -> {
            MessagePurge.Partition p = MessagePurge.partitionByAge(
                    messages.stream().map(m -> m.getTimeCreated().toInstant().toEpochMilli()).toList(),
                    System.currentTimeMillis());

            List<Message> deletable = messages.stream()
                    .filter(m -> System.currentTimeMillis() - m.getTimeCreated().toInstant().toEpochMilli()
                            < MessagePurge.BULK_MAX_AGE_MILLIS)
                    .toList();

            int skipped = p.tooOld().size();
            if (deletable.isEmpty()) {
                event.getHook().sendMessage("Nada para apagar (todas as mensagens têm 14+ dias). "
                        + "Ignoradas: " + skipped).queue();
                return;
            }
            channel.purgeMessages(deletable);
            ctx.database().actionLogs().log(event.getGuild().getId(),
                    event.getUser().getId(), channel.getId(), "CLEAR",
                    "deleted=" + deletable.size() + " skipped=" + skipped);
            event.getHook().sendMessage("Apagadas: " + deletable.size()
                    + (skipped > 0 ? " | Ignoradas (14+ dias): " + skipped : "")).queue();
        }, err -> event.getHook().sendMessage("Falha ao buscar mensagens: " + err.getMessage()).queue());
    }
}
```

In `BaseModule.java`, add the import and register both names:

```java
import dev.davimf.basebot.modules.base.commands.ClearCommand;
```

```java
        registry.command(new ClearCommand("clear"));
        registry.command(new ClearCommand("cl"));
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. (If `purgeMessages` does not resolve, re-check the javap note above and adjust to the available bulk-delete method.)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/commands/ClearCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(base): add /clear and /cl with 14-day skip handling"
```

---

### Task 5: `/addcargo` and `/removecargo`

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/AddCargoCommand.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/RemoveCargoCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java` (register both)
- Test: covered by Task 1 (`RoleHierarchy`) + Task 2 (`Moderation`) + compile.

**Interfaces:**
- Consumes: `Moderation.canModerate(...)` (Task 2); plus a new `Moderation.canManageRole(Member actor, Role role, Member self)` helper added here.
- Produces: `AddCargoCommand`, `RemoveCargoCommand` (each `implements SlashCommand`).

- [ ] **Step 1: Add the role-management guard to `Moderation` and write the commands**

Append to `Moderation.java` (inside the class):

```java
    /** True if both the actor and the bot may assign/remove {@code role} (by position). */
    public static boolean canManageRole(Member actor, Role role, Member self) {
        int rolePos = role.getPosition();
        return RoleHierarchy.actorOutranks(topPosition(actor), rolePos, actor.isOwner())
                && self.canInteract(role);
    }
```

`AddCargoCommand.java`:

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /addcargo — gives a member a role, validating actor + bot hierarchy (BOTSPECS Module 1). */
public final class AddCargoCommand implements SlashCommand {

    @Override
    public String name() {
        return "addcargo";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("addcargo", "Adiciona um cargo a um membro.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
                .addOption(OptionType.USER, "usuario", "Membro", true)
                .addOption(OptionType.ROLE, "cargo", "Cargo a adicionar", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        Role role = event.getOption("cargo", OptionMapping::getAsRole);
        if (target == null || role == null) {
            event.reply("Membro ou cargo inválido.").setEphemeral(true).queue();
            return;
        }
        Member self = event.getGuild().getSelfMember();
        if (!Moderation.canManageRole(event.getMember(), role, self)) {
            event.reply("Hierarquia insuficiente para gerenciar esse cargo.")
                    .setEphemeral(true).queue();
            return;
        }
        event.getGuild().addRoleToMember(target, role).queue(
                ok -> {
                    ctx.database().actionLogs().log(event.getGuild().getId(),
                            event.getUser().getId(), target.getId(), "ADD_ROLE", role.getId());
                    event.reply("Cargo " + role.getName() + " adicionado a "
                            + target.getUser().getAsTag()).queue();
                },
                err -> event.reply("Falha: " + err.getMessage()).setEphemeral(true).queue());
    }
}
```

`RemoveCargoCommand.java`:

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /removecargo — removes a role from a member, validating actor + bot hierarchy. */
public final class RemoveCargoCommand implements SlashCommand {

    @Override
    public String name() {
        return "removecargo";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("removecargo", "Remove um cargo de um membro.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
                .addOption(OptionType.USER, "usuario", "Membro", true)
                .addOption(OptionType.ROLE, "cargo", "Cargo a remover", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        Role role = event.getOption("cargo", OptionMapping::getAsRole);
        if (target == null || role == null) {
            event.reply("Membro ou cargo inválido.").setEphemeral(true).queue();
            return;
        }
        Member self = event.getGuild().getSelfMember();
        if (!Moderation.canManageRole(event.getMember(), role, self)) {
            event.reply("Hierarquia insuficiente para gerenciar esse cargo.")
                    .setEphemeral(true).queue();
            return;
        }
        event.getGuild().removeRoleFromMember(target, role).queue(
                ok -> {
                    ctx.database().actionLogs().log(event.getGuild().getId(),
                            event.getUser().getId(), target.getId(), "REMOVE_ROLE", role.getId());
                    event.reply("Cargo " + role.getName() + " removido de "
                            + target.getUser().getAsTag()).queue();
                },
                err -> event.reply("Falha: " + err.getMessage()).setEphemeral(true).queue());
    }
}
```

In `BaseModule.java`, add imports and register:

```java
import dev.davimf.basebot.modules.base.commands.AddCargoCommand;
import dev.davimf.basebot.modules.base.commands.RemoveCargoCommand;
```

```java
        registry.command(new AddCargoCommand());
        registry.command(new RemoveCargoCommand());
```

- [ ] **Step 2: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — compiles, all unit tests pass (RoleHierarchy, Moderation, MessagePurge + prior crypto/pix), `basebot.jar` produced.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/moderation/Moderation.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/AddCargoCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/RemoveCargoCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(base): add /addcargo /removecargo with hierarchy validation"
```

---

## Self-Review

**Spec coverage (Module 1 subset targeted by this plan):**
- `/kick`, `/ban`, `/unban` → Task 2. ✓
- `/addcargo`, `/removecargo` with bot+moderator hierarchy validation → Task 1 (rule) + Task 5 (commands). ✓
- `/cl`, `/clear` with 14-day bulk-delete handling → Task 3 (rule) + Task 4 (commands). ✓
- General command-exec logging already exists (`GeneralLoggingListener`); each command also writes a typed `action_logs` row.
- **Deferred to later Base plans** (out of scope here): `/setup` hub, `/bot-name|icon|nick`, `/disconnect`, `/voice-move`, `/mute|unmute|mutecall|unmutecall`, `/lock`, `/unlock`, `/listacargo`, `/embed`, `/editembed`, `/addemoji`, `/formulario`, and the expanded event-logging listeners (message edit/delete, joins/leaves, voice traffic).

**Placeholder scan:** No TBD/"add error handling" placeholders; every command has explicit success/failure handling and complete code.

**Type consistency:** `RoleHierarchy.actorOutranks/canModerate`, `Moderation.topPosition(List<Integer>)` / `canModerate(Member,Member,Member)` / `canManageRole(Member,Role,Member)`, `MessagePurge.partitionByAge(...)` / `Partition.bulkDeletable()/tooOld()` / `BULK_MAX_AGE_MILLIS`, and `ClearCommand(String name)` are referenced identically in every defining and consuming task. ✓
