<!-- [OUTLINE START]
Markdown Document: Base Module — `/setup` Configuration Hub Implementation Plan
[OUTLINE END] -->



# Base Module — `/setup` Configuration Hub Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `/setup`, the guild configuration hub (BOTSPECS Module 1): a Components V2 panel that reads/writes per-guild config in Postgres, with the **Logs** section fully wired end-to-end (pick log channels → persist) and a fully unit-tested pure config-edit core the remaining sections reuse.

**Architecture:** A pure, immutable `GuildConfigEdits` transformer (TDD) sits between the existing `GuildConfigRepository` (Postgres) and the JDA UI. `SetupView` builds the hub embed + buttons; `SetupCommand` opens it; `SetupComponentHandler` routes the section buttons and persists EntitySelectMenu choices through `GuildConfigEdits`. All config is guild-scoped (Golden Rule).

**Tech Stack:** Java 22, JDA 6.4.2 Components V2 (`net.dv8tion.jda.api.components.*`: `Button`, `ActionRow`, `EntitySelectMenu`), existing `BotContext`/`GuildConfigRepository`/`GuildConfig`, JUnit 5.

## Global Constraints

- JDK 22; JDA `6.4.2`. JDA 6 component packages: `EntitySelectMenu` is `net.dv8tion.jda.api.components.selections.EntitySelectMenu`; build via `EntitySelectMenu.create(id, SelectTarget.CHANNEL).setChannelTypes(ChannelType.TEXT).build()`. `EntitySelectInteractionEvent.getMentions().getChannels()/getRoles()` yields selections. Reply with components via `.addComponents(ActionRow.of(...))`. Verify any new symbol with `javap -cp <cached JDA jar>`.
- All state is **Guild-specific**; `/setup` no-ops outside a guild and requires `MANAGE_SERVER`. (BOTSPECS Golden Rule)
- Config is stored in Postgres `guild_config` via the existing `GuildConfigRepository` (Supabase today → Neon later; the bot only speaks JDBC, so this code is store-agnostic).
- Custom-id convention: `setup:<action>:<args>` routed by `ComponentRouter` to the `setup` namespace.
- Command UI copy is Portuguese.
- TDD: failing test first; frequent commits; DRY; YAGNI.

---

### Task 1: `GuildConfigEdits` immutable transformer

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/setup/GuildConfigEdits.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/setup/GuildConfigEditsTest.java`

**Interfaces:**
- Consumes: `GuildConfig` record (existing).
- Produces (all return a new `GuildConfig`, never mutate the input):
  - `withLogChannel(GuildConfig, String channelId)`
  - `withTicketLogChannel(GuildConfig, String channelId)`
  - `withChannel(GuildConfig, String key, String channelId)`
  - `withRole(GuildConfig, String key, String roleId)`
  - `withToggle(GuildConfig, String key, boolean value)`
  - `withStaffRoles(GuildConfig, List<String> roleIds)`

- [ ] **Step 1: Write the failing test**

```java
package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuildConfigEditsTest {

    private final GuildConfig base = GuildConfig.empty("g1");

    @Test
    void withLogChannelSetsItAndKeepsGuildId() {
        GuildConfig c = GuildConfigEdits.withLogChannel(base, "100");
        assertEquals("100", c.logChannelId());
        assertEquals("g1", c.guildId());
    }

    @Test
    void withTicketLogChannelSetsItIndependently() {
        GuildConfig c = GuildConfigEdits.withTicketLogChannel(
                GuildConfigEdits.withLogChannel(base, "100"), "200");
        assertEquals("100", c.logChannelId());
        assertEquals("200", c.ticketLogChannelId());
    }

    @Test
    void withRoleAddsEntryWithoutMutatingOriginal() {
        GuildConfig c = GuildConfigEdits.withRole(base, "staff", "55");
        assertEquals("55", c.role("staff"));
        assertTrue(base.roles().isEmpty(), "original must be unchanged");
    }

    @Test
    void withToggleSetsFlag() {
        GuildConfig c = GuildConfigEdits.withToggle(base, "laundering", true);
        assertTrue(c.toggle("laundering", false));
    }

    @Test
    void withStaffRolesReplacesList() {
        GuildConfig c = GuildConfigEdits.withStaffRoles(base, java.util.List.of("1", "2"));
        assertEquals(java.util.List.of("1", "2"), c.staffRoleIds());
    }

    @Test
    void withChannelStoresUnderKey() {
        GuildConfig c = GuildConfigEdits.withChannel(base, "tickets-category", "999");
        assertEquals("999", c.channel("tickets-category"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.setup.GuildConfigEditsTest"`
Expected: FAIL — `GuildConfigEdits` cannot be resolved.

- [ ] **Step 3: Write minimal implementation**

```java
package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.database.model.GuildConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, immutable transformations of {@link GuildConfig}. Each method returns a new
 * config with one field changed, copying maps so callers (and the dashboard's stored
 * row) are never mutated in place. Used by {@code /setup} between load and save.
 */
public final class GuildConfigEdits {

    private GuildConfigEdits() {}

    public static GuildConfig withLogChannel(GuildConfig c, String channelId) {
        return new GuildConfig(c.guildId(), channelId, c.ticketLogChannelId(),
                c.channels(), c.roles(), c.toggles(), c.staffRoleIds());
    }

    public static GuildConfig withTicketLogChannel(GuildConfig c, String channelId) {
        return new GuildConfig(c.guildId(), c.logChannelId(), channelId,
                c.channels(), c.roles(), c.toggles(), c.staffRoleIds());
    }

    public static GuildConfig withChannel(GuildConfig c, String key, String channelId) {
        Map<String, String> m = new HashMap<>(c.channels());
        m.put(key, channelId);
        return new GuildConfig(c.guildId(), c.logChannelId(), c.ticketLogChannelId(),
                Map.copyOf(m), c.roles(), c.toggles(), c.staffRoleIds());
    }

    public static GuildConfig withRole(GuildConfig c, String key, String roleId) {
        Map<String, String> m = new HashMap<>(c.roles());
        m.put(key, roleId);
        return new GuildConfig(c.guildId(), c.logChannelId(), c.ticketLogChannelId(),
                c.channels(), Map.copyOf(m), c.toggles(), c.staffRoleIds());
    }

    public static GuildConfig withToggle(GuildConfig c, String key, boolean value) {
        Map<String, Boolean> m = new HashMap<>(c.toggles());
        m.put(key, value);
        return new GuildConfig(c.guildId(), c.logChannelId(), c.ticketLogChannelId(),
                c.channels(), c.roles(), Map.copyOf(m), c.staffRoleIds());
    }

    public static GuildConfig withStaffRoles(GuildConfig c, List<String> roleIds) {
        return new GuildConfig(c.guildId(), c.logChannelId(), c.ticketLogChannelId(),
                c.channels(), c.roles(), c.toggles(), List.copyOf(roleIds));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.setup.GuildConfigEditsTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/setup/GuildConfigEdits.java \
        src/test/java/dev/davimf/basebot/modules/base/setup/GuildConfigEditsTest.java
git commit -m "feat(base): add immutable GuildConfig edit transformer"
```

---

### Task 2: `SetupView` hub panel builder

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupView.java`
- Test: compile-only (JDA embed/component builders); logic is covered by Task 1.

**Interfaces:**
- Consumes: `GuildConfig`, `ComponentId.of(...)`, JDA `Button`/`ActionRow`/`EmbedBuilder`.
- Produces:
  - `SetupView.hubEmbed(GuildConfig) -> net.dv8tion.jda.api.entities.MessageEmbed`
  - `SetupView.hubRow() -> net.dv8tion.jda.api.components.actionrow.ActionRow`

- [ ] **Step 1: Implement**

```java
package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** Builds the {@code /setup} hub: a summary embed + the section buttons (BOTSPECS Module 1). */
public final class SetupView {

    public static final String NS = "setup";

    private SetupView() {}

    public static MessageEmbed hubEmbed(GuildConfig cfg) {
        return new EmbedBuilder()
                .setTitle("⚙️ Configuração do Servidor")
                .setColor(0x5865F2)
                .setDescription("Use os botões abaixo para configurar cada seção. "
                        + "Todas as configurações são específicas deste servidor.")
                .addField("📋 Logs gerais", channelOrUnset(cfg.logChannelId()), true)
                .addField("🎫 Logs de tickets", channelOrUnset(cfg.ticketLogChannelId()), true)
                .addField("👥 Cargos configurados", String.valueOf(cfg.roles().size()), true)
                .addField("🛡️ Cargos de staff (tickets)", String.valueOf(cfg.staffRoleIds().size()), true)
                .build();
    }

    public static ActionRow hubRow() {
        return ActionRow.of(
                Button.primary(ComponentId.of(NS, "section", "logs"), "Logs"),
                Button.primary(ComponentId.of(NS, "section", "roles"), "Cargos"),
                Button.primary(ComponentId.of(NS, "section", "tickets"), "Tickets"),
                Button.secondary(ComponentId.of(NS, "section", "bot"), "Bot")
        );
    }

    private static String channelOrUnset(String channelId) {
        return channelId == null || channelId.isBlank() ? "*Não definido*" : "<#" + channelId + ">";
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/setup/SetupView.java
git commit -m "feat(base): add /setup hub view builder"
```

---

### Task 3: `SetupCommand` (`/setup`) and registration

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/SetupCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java` (import + register)
- Test: compile-only.

**Interfaces:**
- Consumes: `SlashCommand`, `BotContext`, `GuildConfigRepository.findOrEmpty(...)`, `SetupView`.
- Produces: `SetupCommand implements SlashCommand` (name `"setup"`).

- [ ] **Step 1: Implement the command**

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.setup.SetupView;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /setup — opens the guild configuration hub (BOTSPECS Module 1). */
public final class SetupCommand implements SlashCommand {

    @Override
    public String name() {
        return "setup";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("setup", "Abre o painel de configuração do servidor.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        event.replyEmbeds(SetupView.hubEmbed(cfg))
                .addComponents(SetupView.hubRow())
                .setEphemeral(true)
                .queue();
    }
}
```

In `BaseModule.java`, add the import and register after the role commands:

```java
import dev.davimf.basebot.modules.base.commands.SetupCommand;
```

```java
        // Configuration hub (BOTSPECS Module 1).
        registry.command(new SetupCommand());
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/commands/SetupCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(base): add /setup command opening the config hub"
```

---

### Task 4: `SetupComponentHandler` — Logs section wired + section routing

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java` (register component)
- Test: compile + full build (persistence is Postgres I/O; the pure edit path is covered by Task 1).

**Interfaces:**
- Consumes: `ComponentHandler`, `ComponentId`, `BotContext`, `GuildConfigEdits`, `GuildConfigRepository`, JDA `EntitySelectMenu`/`ActionRow`/`ChannelType`/`Mentions`.
- Produces: `SetupComponentHandler implements ComponentHandler` (namespace `"setup"`).

Custom-ids: hub buttons `setup:section:<logs|roles|tickets|bot>`; channel selects `setup:setlog` and `setup:setticketlog`.

- [ ] **Step 1: Implement the handler**

```java
package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;

import java.util.List;

/**
 * Drives the {@code /setup} hub interactions (BOTSPECS Module 1). The Logs section is
 * fully wired: pick a channel via an EntitySelectMenu, then persist it through
 * {@link GuildConfigEdits} + the Postgres-backed {@code GuildConfigRepository}. The
 * Cargos/Tickets/Bot sections are scaffolded with informative placeholders.
 */
public final class SetupComponentHandler implements ComponentHandler {

    @Override
    public String namespace() {
        return SetupView.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"section".equals(id.action())) {
            return;
        }
        switch (String.valueOf(id.arg(0))) {
            case "logs" -> showLogsPanel(event);
            case "roles" -> placeholder(event, "Cargos",
                    "Em breve: mapear cargos lógicos (staff, moderador) a cargos do servidor.");
            case "tickets" -> placeholder(event, "Tickets",
                    "Em breve: categoria, descrição, emoji e cargos de staff dos tickets.");
            case "bot" -> placeholder(event, "Bot",
                    "Perfil global do bot via /bot-name e /bot-icon (limite do Discord: 2x por hora).");
            default -> placeholder(event, "Desconhecido", "Seção inválida.");
        }
    }

    @Override
    public void onEntitySelect(EntitySelectInteractionEvent event, ComponentId id, BotContext ctx) {
        switch (id.action()) {
            case "setlog" -> persistLogChannel(event, ctx, false);
            case "setticketlog" -> persistLogChannel(event, ctx, true);
            default -> { /* not ours */ }
        }
    }

    private void showLogsPanel(ButtonInteractionEvent event) {
        EntitySelectMenu logMenu = EntitySelectMenu
                .create(ComponentId.of(SetupView.NS, "setlog"), EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT)
                .setPlaceholder("Canal de logs gerais")
                .setRequiredRange(1, 1)
                .build();
        EntitySelectMenu ticketMenu = EntitySelectMenu
                .create(ComponentId.of(SetupView.NS, "setticketlog"), EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT)
                .setPlaceholder("Canal de logs de tickets")
                .setRequiredRange(1, 1)
                .build();
        event.reply("Selecione os canais de log:")
                .addComponents(ActionRow.of(logMenu), ActionRow.of(ticketMenu))
                .setEphemeral(true)
                .queue();
    }

    private void persistLogChannel(EntitySelectInteractionEvent event, BotContext ctx, boolean ticketLog) {
        if (event.getGuild() == null) {
            event.reply("Use em um servidor.").setEphemeral(true).queue();
            return;
        }
        List<GuildChannel> channels = event.getMentions().getChannels();
        if (channels.isEmpty()) {
            event.reply("Nenhum canal selecionado.").setEphemeral(true).queue();
            return;
        }
        String channelId = channels.get(0).getId();
        String guildId = event.getGuild().getId();

        GuildConfig current = ctx.database().guildConfig().findOrEmpty(guildId);
        GuildConfig updated = ticketLog
                ? GuildConfigEdits.withTicketLogChannel(current, channelId)
                : GuildConfigEdits.withLogChannel(current, channelId);
        ctx.database().guildConfig().save(updated);
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), channelId,
                ticketLog ? "SETUP_TICKET_LOG" : "SETUP_LOG", channelId);

        event.reply((ticketLog ? "Canal de logs de tickets" : "Canal de logs gerais")
                + " definido: <#" + channelId + ">").setEphemeral(true).queue();
    }

    private void placeholder(ButtonInteractionEvent event, String section, String detail) {
        event.reply("**" + section + "** — " + detail).setEphemeral(true).queue();
    }
}
```

> **JDA verification note:** before compiling, confirm the EntitySelectMenu Builder's
> min/max method name with
> `javap -cp <JDA jar> 'net.dv8tion.jda.api.components.selections.EntitySelectMenu$Builder' | grep -iE "setRequiredRange|setMinValues|setMaxValues"`.
> If `setRequiredRange(int,int)` is absent, use `.setMinValues(1).setMaxValues(1)` instead.

In `BaseModule.java`, add the import and register the component handler inside `register(...)`:

```java
import dev.davimf.basebot.modules.base.setup.SetupComponentHandler;
```

```java
        registry.component(new SetupComponentHandler());
```

- [ ] **Step 2: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — compiles, all unit tests pass (GuildConfigEdits + prior), `basebot.jar` produced.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java \
        src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(base): wire /setup hub + Logs section persistence"
```

---

## Self-Review

**Spec coverage (`/setup` — Main configuration hub: Logs, Roles, Tickets, Bot):**
- Hub panel with the four sections → Task 2 (`SetupView`) + Task 3 (`SetupCommand`). ✓
- **Logs** section fully functional (set general + ticket log channels, persisted to Postgres) → Task 1 (edit core) + Task 4 (handler). ✓
- **Roles / Tickets / Bot** sections → routed with informative placeholders in Task 4; full implementation deferred to a follow-up plan (each reuses `GuildConfigEdits.withRole/withStaffRoles/withChannel` from Task 1 + the same EntitySelect→persist pattern proven by Logs).
- Guild-scoped + `MANAGE_SERVER` gated. ✓

**Placeholder scan:** The Roles/Tickets/Bot replies are deliberate, user-facing "section in development" messages (scope markers), not code-completeness gaps; the Logs path and all helpers are fully implemented.

**Type consistency:** `GuildConfigEdits.withLogChannel/withTicketLogChannel/withRole/withChannel/withToggle/withStaffRoles`, `SetupView.NS`/`hubEmbed`/`hubRow`, and the custom-ids `setup:section:*`, `setup:setlog`, `setup:setticketlog` are referenced identically across defining and consuming tasks. ✓
