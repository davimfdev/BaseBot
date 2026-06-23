# Base Module — Utility Commands (`/lock`, `/unlock`, `/addemoji`, `/listacargo`)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement four remaining Module 1 commands: `/lock` & `/unlock` (toggle @everyone send-messages on a channel), `/addemoji` (add a custom emoji from a link or file), and `/listacargo` (paginated role-member list with optional throttled ghost pings).

**Architecture:** Two pure TDD cores — `EmojiNames` (validate/sanitize) and `Paginator` (chunk a list into pages) — plus a test for the existing `BatchThrottler`. `/addemoji` reuses the `ImageMedia` download→re-upload→erase rule; `/listacargo` reuses `BatchThrottler` for the 5-per-30s ghost-ping cadence. Lock/unlock use JDA permission overrides on the public role.

**Tech Stack:** Java 22, JDA 6.4.2 (`upsertPermissionOverride`, `getPublicRole`, `createEmoji`, `getMembersWithRoles`, button pagination), `ImageMedia`, `BatchThrottler`, JUnit 5.

## Global Constraints

- JDK 22; JDA `6.4.2`. Confirmed APIs: `channel.upsertPermissionOverride(holder).deny(Permission.MESSAGE_SEND)` / `.clear(Permission.MESSAGE_SEND)`; `guild.getPublicRole()`; `guild.createEmoji(name, Icon, Role...)`; `guild.getMembersWithRoles(role)`. Permissions `MANAGE_CHANNEL`, `MANAGE_GUILD_EXPRESSIONS`, `MESSAGE_MANAGE`.
- Image inputs accept **link OR file**, are downloaded + re-uploaded by the bot via `ImageMedia`, then erased ([[image-input-handling-rule]] — `/addemoji`).
- Ghost pings throttled to batches of 5 every 30s (BOTSPECS §API Limitations) via `BatchThrottler`, using `ctx.config().rateLimit()`.
- Guild-scoped; Portuguese UI copy. TDD: failing test first; frequent commits; DRY; YAGNI.

---

### Task 1: `/lock` and `/unlock`

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/LockCommand.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/UnlockCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java` (register)
- Test: compile (no pure logic; JDA permission I/O).

**Interfaces:** JDA `channel.upsertPermissionOverride(guild.getPublicRole()).deny/clear(Permission.MESSAGE_SEND)`.

- [ ] **Step 1: Implement both commands**

`LockCommand.java`:

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /lock — denies @everyone SEND_MESSAGES in the current channel (BOTSPECS Module 1). */
public final class LockCommand implements SlashCommand {

    @Override
    public String name() {
        return "lock";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("lock", "Tranca o canal atual (impede @everyone de enviar mensagens).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || !(event.getChannel() instanceof IPermissionContainer channel)) {
            event.reply("Use este comando em um canal de servidor.").setEphemeral(true).queue();
            return;
        }
        channel.upsertPermissionOverride(event.getGuild().getPublicRole())
                .deny(Permission.MESSAGE_SEND)
                .reason("/lock por " + event.getUser().getAsTag())
                .queue(
                        ok -> {
                            ctx.database().actionLogs().log(event.getGuild().getId(),
                                    event.getUser().getId(), event.getChannel().getId(), "LOCK", null);
                            event.reply("🔒 Canal trancado.").queue();
                        },
                        err -> event.reply("Falha: " + err.getMessage()).setEphemeral(true).queue());
    }
}
```

`UnlockCommand.java`:

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /unlock — neutralizes the @everyone SEND_MESSAGES override in the current channel. */
public final class UnlockCommand implements SlashCommand {

    @Override
    public String name() {
        return "unlock";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("unlock", "Destranca o canal atual.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || !(event.getChannel() instanceof IPermissionContainer channel)) {
            event.reply("Use este comando em um canal de servidor.").setEphemeral(true).queue();
            return;
        }
        channel.upsertPermissionOverride(event.getGuild().getPublicRole())
                .clear(Permission.MESSAGE_SEND)
                .reason("/unlock por " + event.getUser().getAsTag())
                .queue(
                        ok -> {
                            ctx.database().actionLogs().log(event.getGuild().getId(),
                                    event.getUser().getId(), event.getChannel().getId(), "UNLOCK", null);
                            event.reply("🔓 Canal destrancado.").queue();
                        },
                        err -> event.reply("Falha: " + err.getMessage()).setEphemeral(true).queue());
    }
}
```

In `BaseModule.java`, add imports + register (a "Channel utilities" block):

```java
import dev.davimf.basebot.modules.base.commands.LockCommand;
import dev.davimf.basebot.modules.base.commands.UnlockCommand;
```

```java
        // Channel utilities (BOTSPECS Module 1).
        registry.command(new LockCommand());
        registry.command(new UnlockCommand());
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/commands/LockCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/UnlockCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(base): add /lock and /unlock channel commands"
```

---

### Task 2: `EmojiNames` (TDD) + `/addemoji`

**Files:**
- Create: `src/main/java/dev/davimf/basebot/util/EmojiNames.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/AddEmojiCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java` (register)
- Test: `src/test/java/dev/davimf/basebot/util/EmojiNamesTest.java`

**Interfaces:** Produces `EmojiNames.isValid(String) -> boolean`, `EmojiNames.sanitize(String) -> String`. `/addemoji` uses `ImageMedia` (link or file) + `guild.createEmoji`.

- [ ] **Step 1: Write the failing test**

```java
package dev.davimf.basebot.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmojiNamesTest {

    @Test
    void validNamesAreAlnumUnderscore2to32() {
        assertTrue(EmojiNames.isValid("cool_emoji"));
        assertTrue(EmojiNames.isValid("ab"));
        assertFalse(EmojiNames.isValid("a"));            // too short
        assertFalse(EmojiNames.isValid("has space"));    // space
        assertFalse(EmojiNames.isValid("emoji!"));       // punctuation
        assertFalse(EmojiNames.isValid(null));
    }

    @Test
    void sanitizeReplacesInvalidCharsAndClampsLength() {
        assertEquals("my_emoji_", EmojiNames.sanitize("my emoji!"));
        assertEquals("a_", EmojiNames.sanitize("a"));    // padded to min length 2
        assertEquals(32, EmojiNames.sanitize("x".repeat(40)).length());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.util.EmojiNamesTest"`
Expected: FAIL — `EmojiNames` cannot be resolved.

- [ ] **Step 3: Implement `EmojiNames` + `/addemoji`**

`EmojiNames.java`:

```java
package dev.davimf.basebot.util;

/** Discord custom-emoji name rules: 2-32 chars of letters, digits and underscores. */
public final class EmojiNames {

    private EmojiNames() {}

    public static boolean isValid(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{2,32}");
    }

    /** Coerces arbitrary input into a valid emoji name (invalid chars -> '_', clamped 2-32). */
    public static String sanitize(String raw) {
        if (raw == null) {
            return "emoji";
        }
        String s = raw.trim().replaceAll("[^A-Za-z0-9_]", "_");
        if (s.length() > 32) {
            s = s.substring(0, 32);
        }
        while (s.length() < 2) {
            s = s + "_";
        }
        return s;
    }
}
```

`AddEmojiCommand.java`:

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.util.EmojiNames;
import dev.davimf.basebot.util.ImageMedia;
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

/** /addemoji — adds a custom emoji from a link or file (BOTSPECS Module 1). */
public final class AddEmojiCommand implements SlashCommand {

    @Override
    public String name() {
        return "addemoji";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("addemoji", "Adiciona um emoji personalizado ao servidor.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_GUILD_EXPRESSIONS))
                .addOption(OptionType.STRING, "nome", "Nome do emoji (2-32, letras/números/_)", true)
                .addOption(OptionType.ATTACHMENT, "imagem", "Arquivo de imagem", false)
                .addOption(OptionType.STRING, "link", "URL de uma imagem", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        String rawName = event.getOption("nome", OptionMapping::getAsString);
        String name = EmojiNames.sanitize(rawName);
        if (!EmojiNames.isValid(name)) {
            event.reply("Nome de emoji inválido.").setEphemeral(true).queue();
            return;
        }
        Message.Attachment att = event.getOption("imagem", OptionMapping::getAsAttachment);
        String link = event.getOption("link", OptionMapping::getAsString);
        if ((att == null) == (link == null)) {
            event.reply("Forneça **uma** imagem: um anexo OU um link.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();
        ctx.scheduler().executor().execute(() -> createEmoji(event, ctx, name, att, link));
    }

    private void createEmoji(SlashCommandInteractionEvent event, BotContext ctx,
                             String name, Message.Attachment att, String link) {
        ImageMedia.Image image = null;
        try {
            image = (att != null) ? ImageMedia.fromAttachment(att) : ImageMedia.fromUrl(link);
            Icon icon = Icon.from(image.bytes());
            ImageMedia.Image fetched = image;
            event.getGuild().createEmoji(name, icon).queue(
                    emoji -> {
                        ctx.database().actionLogs().log(event.getGuild().getId(),
                                event.getUser().getId(), emoji.getId(), "ADD_EMOJI", name);
                        fetched.erase();
                        event.getHook().sendMessage("Emoji adicionado: " + emoji.getAsMention()).queue();
                    },
                    err -> {
                        fetched.erase();
                        event.getHook().sendMessage("Falha ao adicionar o emoji: " + err.getMessage()).queue();
                    });
        } catch (IOException e) {
            if (image != null) {
                image.erase();
            }
            event.getHook().sendMessage(e.getMessage()).queue();
        }
    }
}
```

In `BaseModule.java`, add import + register:

```java
import dev.davimf.basebot.modules.base.commands.AddEmojiCommand;
```

```java
        registry.command(new AddEmojiCommand());
```

- [ ] **Step 4: Run test + compile**

Run: `./gradlew test --tests "dev.davimf.basebot.util.EmojiNamesTest"`
Expected: PASS (2 tests).

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/util/EmojiNames.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/AddEmojiCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/BaseModule.java \
        src/test/java/dev/davimf/basebot/util/EmojiNamesTest.java
git commit -m "feat(base): add /addemoji (link or file) with name validation"
```

---

### Task 3: `Paginator` (TDD) + `/listacargo` (paginated list + nav)

**Files:**
- Create: `src/main/java/dev/davimf/basebot/util/Paginator.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/listacargo/ListaCargoView.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/ListaCargoCommand.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/listacargo/ListaCargoComponentHandler.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java` (register command + component)
- Test: `src/test/java/dev/davimf/basebot/util/PaginatorTest.java`

**Interfaces:**
- `Paginator.pageCount(int total, int pageSize) -> int`; `Paginator.page(List<T>, int pageIndex, int pageSize) -> List<T>`.
- `ListaCargoView.PAGE_SIZE` (=20); `ListaCargoView.embed(Role role, List<Member> members, int pageIndex)`; `ListaCargoView.navRow(String roleId, int pageIndex, int pageCount)`.
- Component namespace `listacargo`, button action `nav` with args `roleId`, `pageIndex`.

- [ ] **Step 1: Write the failing test**

```java
package dev.davimf.basebot.util;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PaginatorTest {

    @Test
    void pageCountRoundsUpAndIsAtLeastOne() {
        assertEquals(1, Paginator.pageCount(0, 20));
        assertEquals(1, Paginator.pageCount(20, 20));
        assertEquals(2, Paginator.pageCount(21, 20));
        assertEquals(3, Paginator.pageCount(45, 20));
    }

    @Test
    void pageReturnsTheRightSlice() {
        List<Integer> items = List.of(0, 1, 2, 3, 4);
        assertEquals(List.of(0, 1), Paginator.page(items, 0, 2));
        assertEquals(List.of(2, 3), Paginator.page(items, 1, 2));
        assertEquals(List.of(4), Paginator.page(items, 2, 2));
    }

    @Test
    void pageOutOfRangeIsEmpty() {
        assertEquals(List.of(), Paginator.page(List.of(1, 2), 5, 2));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.util.PaginatorTest"`
Expected: FAIL — `Paginator` cannot be resolved.

- [ ] **Step 3: Implement `Paginator`, the view, the command and the nav handler**

`Paginator.java`:

```java
package dev.davimf.basebot.util;

import java.util.List;

/** Stateless list pagination helper. */
public final class Paginator {

    private Paginator() {}

    public static int pageCount(int total, int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be > 0");
        }
        if (total <= 0) {
            return 1;
        }
        return (total + pageSize - 1) / pageSize;
    }

    public static <T> List<T> page(List<T> items, int pageIndex, int pageSize) {
        int from = Math.max(0, pageIndex) * pageSize;
        if (from >= items.size()) {
            return List.of();
        }
        int to = Math.min(items.size(), from + pageSize);
        return items.subList(from, to);
    }
}
```

`ListaCargoView.java`:

```java
package dev.davimf.basebot.modules.base.listacargo;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.util.Paginator;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;

import java.util.List;

/** Builds the {@code /listacargo} paginated embed + prev/next navigation row. */
public final class ListaCargoView {

    public static final String NS = "listacargo";
    public static final int PAGE_SIZE = 20;

    private ListaCargoView() {}

    public static MessageEmbed embed(Role role, List<Member> members, int pageIndex) {
        int pages = Paginator.pageCount(members.size(), PAGE_SIZE);
        List<Member> slice = Paginator.page(members, pageIndex, PAGE_SIZE);
        StringBuilder sb = new StringBuilder();
        if (slice.isEmpty()) {
            sb.append("*Nenhum membro com este cargo.*");
        } else {
            for (Member m : slice) {
                sb.append("• ").append(m.getAsMention()).append('\n');
            }
        }
        return new EmbedBuilder()
                .setTitle("Membros de " + role.getName() + " (" + members.size() + ")")
                .setColor(role.getColorRaw())
                .setDescription(sb.toString())
                .setFooter("Página " + (pageIndex + 1) + "/" + pages)
                .build();
    }

    public static ActionRow navRow(String roleId, int pageIndex, int pageCount) {
        Button prev = Button.secondary(ComponentId.of(NS, "nav", roleId, String.valueOf(pageIndex - 1)), "◀")
                .withDisabled(pageIndex <= 0);
        Button next = Button.secondary(ComponentId.of(NS, "nav", roleId, String.valueOf(pageIndex + 1)), "▶")
                .withDisabled(pageIndex >= pageCount - 1);
        return ActionRow.of(prev, next);
    }
}
```

`ListaCargoCommand.java`:

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.listacargo.ListaCargoView;
import dev.davimf.basebot.util.Paginator;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/** /listacargo — paginated list of members holding a role (BOTSPECS Module 1). */
public final class ListaCargoCommand implements SlashCommand {

    @Override
    public String name() {
        return "listacargo";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("listacargo", "Lista os membros de um cargo (paginado).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE))
                .addOption(OptionType.ROLE, "cargo", "Cargo a listar", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        Role role = event.getOption("cargo", OptionMapping::getAsRole);
        if (role == null) {
            event.reply("Cargo inválido.").setEphemeral(true).queue();
            return;
        }
        List<Member> members = event.getGuild().getMembersWithRoles(role);
        int pages = Paginator.pageCount(members.size(), ListaCargoView.PAGE_SIZE);
        event.replyEmbeds(ListaCargoView.embed(role, members, 0))
                .addComponents(ListaCargoView.navRow(role.getId(), 0, pages))
                .queue();
    }
}
```

`ListaCargoComponentHandler.java`:

```java
package dev.davimf.basebot.modules.base.listacargo;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.util.Paginator;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.util.List;

/** Prev/next navigation for the {@code /listacargo} embed (stateless: re-reads members). */
public final class ListaCargoComponentHandler implements ComponentHandler {

    @Override
    public String namespace() {
        return ListaCargoView.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"nav".equals(id.action()) || event.getGuild() == null) {
            return;
        }
        Role role = event.getGuild().getRoleById(id.arg(0));
        if (role == null) {
            event.reply("Cargo não encontrado.").setEphemeral(true).queue();
            return;
        }
        int requested = parsePage(id.arg(1));
        List<Member> members = event.getGuild().getMembersWithRoles(role);
        int pages = Paginator.pageCount(members.size(), ListaCargoView.PAGE_SIZE);
        int page = Math.max(0, Math.min(requested, pages - 1));
        event.editMessageEmbeds(ListaCargoView.embed(role, members, page))
                .setComponents(ListaCargoView.navRow(role.getId(), page, pages))
                .queue();
    }

    private int parsePage(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
```

In `BaseModule.java`, add imports + register:

```java
import dev.davimf.basebot.modules.base.commands.ListaCargoCommand;
import dev.davimf.basebot.modules.base.listacargo.ListaCargoComponentHandler;
```

```java
        registry.command(new ListaCargoCommand());
        registry.component(new ListaCargoComponentHandler());
```

- [ ] **Step 4: Run test + compile**

Run: `./gradlew test --tests "dev.davimf.basebot.util.PaginatorTest"`
Expected: PASS (3 tests).

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/util/Paginator.java \
        src/main/java/dev/davimf/basebot/modules/base/listacargo/ListaCargoView.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/ListaCargoCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/listacargo/ListaCargoComponentHandler.java \
        src/main/java/dev/davimf/basebot/modules/base/BaseModule.java \
        src/test/java/dev/davimf/basebot/util/PaginatorTest.java
git commit -m "feat(base): add /listacargo paginated role-member list"
```

---

### Task 4: Throttled ghost pings on `/listacargo` (+ `BatchThrottler` test)

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/commands/ListaCargoCommand.java` (ghost-ping option)
- Test: `src/test/java/dev/davimf/basebot/ratelimit/BatchThrottlerTest.java`

**Interfaces:** consumes `BatchThrottler` (batches of `ctx.config().rateLimit().ghostPingBatchSize()` every `ghostPingBatchIntervalSeconds()`), `ctx.scheduler().executor()`.

- [ ] **Step 1: Write the failing test for `BatchThrottler`**

```java
package dev.davimf.basebot.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchThrottlerTest {

    @Test
    void emitsItemsInOrderedBatches() throws Exception {
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        try {
            BatchThrottler throttler = new BatchThrottler(exec, 2, 10, TimeUnit.MILLISECONDS);
            List<List<Integer>> batches = Collections.synchronizedList(new ArrayList<>());
            throttler.run(List.of(1, 2, 3, 4, 5), b -> batches.add(new ArrayList<>(b)))
                    .get(5, TimeUnit.SECONDS);
            assertEquals(3, batches.size());
            assertEquals(List.of(1, 2), batches.get(0));
            assertEquals(List.of(3, 4), batches.get(1));
            assertEquals(List.of(5), batches.get(2));
        } finally {
            exec.shutdownNow();
        }
    }
}
```

- [ ] **Step 2: Run test to verify it passes** (BatchThrottler already exists from the scaffold)

Run: `./gradlew test --tests "dev.davimf.basebot.ratelimit.BatchThrottlerTest"`
Expected: PASS (1 test). If it fails, fix `BatchThrottler` partition/scheduling to satisfy the test.

- [ ] **Step 3: Add the ghost-ping option to `/listacargo`**

In `ListaCargoCommand.java`, add imports:

```java
import dev.davimf.basebot.ratelimit.BatchThrottler;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import java.util.concurrent.TimeUnit;
```

Add the option in `data()` after the role option:

```java
                .addOption(OptionType.BOOLEAN, "ghost_ping", "Mencionar os membros em lotes (5 a cada 30s)", false)
```

At the end of `execute(...)`, after the `event.replyEmbeds(...).queue();` call, add the ghost-ping dispatch:

```java
        boolean ghostPing = Boolean.TRUE.equals(event.getOption("ghost_ping", OptionMapping::getAsBoolean));
        if (ghostPing && !members.isEmpty() && event.getChannel() instanceof MessageChannel channel) {
            BatchThrottler throttler = new BatchThrottler(
                    ctx.scheduler().executor(),
                    ctx.config().rateLimit().ghostPingBatchSize(),
                    ctx.config().rateLimit().ghostPingBatchIntervalSeconds(),
                    TimeUnit.SECONDS);
            ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(),
                    role.getId(), "GHOST_PING", String.valueOf(members.size()));
            throttler.run(members, batch -> {
                StringBuilder mentions = new StringBuilder();
                for (Member m : batch) {
                    mentions.append(m.getAsMention()).append(' ');
                }
                // Ghost ping: send the mention, then delete it shortly after so a
                // notification fires without leaving a visible message.
                channel.sendMessage(mentions.toString().trim())
                        .queue(msg -> msg.delete().queueAfter(2, TimeUnit.SECONDS, x -> {}, x -> {}));
            });
        }
```

- [ ] **Step 4: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — compiles, all unit tests pass, `basebot.jar` produced.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/commands/ListaCargoCommand.java \
        src/test/java/dev/davimf/basebot/ratelimit/BatchThrottlerTest.java
git commit -m "feat(base): add throttled ghost pings to /listacargo"
```

---

## Self-Review

**Spec coverage:**
- `/lock`, `/unlock` — modify @everyone SEND_MESSAGES override → Task 1. ✓
- `/addemoji` — add custom emoji from link or file (download+re-upload+erase) → Task 2 (+ ImageMedia + EmojiNames). ✓
- `/listacargo` — paginator embed with optional throttled ghost pings (batches of 5 / 30s) → Tasks 3-4 (+ Paginator + BatchThrottler). ✓
- **Deferred** (final Module 1 pieces): `/embed`, `/editembed` (webhook embed builder, preserves select menus), `/formulario` (configurable modal form).

**Placeholder scan:** No stubs; all four commands fully implemented.

**Type consistency:** `EmojiNames.isValid/sanitize`; `Paginator.pageCount/page`; `ListaCargoView.NS/PAGE_SIZE/embed/navRow`; component `listacargo:nav:<roleId>:<page>`; `BatchThrottler.run(list, consumer)`; `ImageMedia` link/file→erase — referenced identically across tasks. ✓
