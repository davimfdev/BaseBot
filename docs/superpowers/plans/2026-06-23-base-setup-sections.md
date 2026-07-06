<!-- [OUTLINE START]
Markdown Document: Base Module — `/setup` Remaining Sections (Cargos, Tickets, Bot)
[OUTLINE END] -->



# Base Module — `/setup` Remaining Sections (Cargos, Tickets, Bot)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the `/setup` hub by fully wiring the **Cargos** (logical-role mapping), **Tickets** (category, staff roles, description, emoji), and **Bot** (profile info) sections, persisting through the existing `GuildConfigRepository`.

**Architecture:** Adds a generic `settings: Map<String,String>` to `GuildConfig` for free-form text config (ticket description/emoji; later, Module-4 percentages). Reuses the proven EntitySelect→`GuildConfigEdits`→save pattern from the Logs section, plus a StringSelectMenu (role-key chooser) and a Modal (description/emoji). All pure transforms stay in the TDD-tested `GuildConfigEdits`.

**Tech Stack:** Java 22, JDA 6.4.2 Components V2 — `StringSelectMenu`, `EntitySelectMenu` (ROLE / CHANNEL+CATEGORY / multi), `Modal` + `Label.of(label, TextInput)`, `event.getValue(id)`. JUnit 5.

## Global Constraints

- JDK 22; JDA `6.4.2`. Confirmed JDA 6 facts used below: `StringSelectMenu.create(id).addOption(label,value).build()`; `EntitySelectMenu.create(id, SelectTarget.ROLE).setRequiredRange(min,max).build()`; channel category via `.setChannelTypes(ChannelType.CATEGORY)`; modal inputs MUST be wrapped — `Modal.create(id,title).addComponents(Label.of("Label", TextInput.create(id,TextInputStyle.PARAGRAPH).build())).build()`; read with `event.getValue("id").getAsString()`; open with `buttonEvent.replyModal(modal).queue()`. Selections: `stringEvent.getValues()`, `entityEvent.getMentions().getRoles()/getChannels()`.
- All state is **Guild-specific**; `MANAGE_SERVER` gated (inherited from the `/setup` command).
- Config persists to Postgres `guild_config` via `GuildConfigRepository` (store-agnostic JDBC; Supabase→Neon later).
- Custom-id convention `setup:<action>:<args>`, routed by `ComponentRouter` to the `setup` namespace.
- Portuguese UI copy. TDD: failing test first; frequent commits; DRY; YAGNI.

---

### Task 1: Add `settings` map to `GuildConfig` + `withSetting`

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/database/model/GuildConfig.java` (add field, accessor, empty)
- Modify: `src/main/java/dev/davimf/basebot/database/postgres/JdbcGuildConfigRepository.java` (read/write the new jsonb column)
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/GuildConfigEdits.java` (thread `settings` through all builders; add `withSetting`)
- Create: `src/main/resources/db/postgres/002_guild_settings.sql` (ALTER TABLE)
- Modify: `src/test/java/dev/davimf/basebot/modules/base/setup/GuildConfigEditsTest.java` (add `withSetting` + settings-preserved tests)

**Interfaces:**
- Produces: `GuildConfig` gains `Map<String,String> settings` (8th field, last) + `String setting(String key)`; `GuildConfigEdits.withSetting(GuildConfig, String key, String value) -> GuildConfig`.

- [ ] **Step 1: Update the failing test first** — append to `GuildConfigEditsTest`:

```java
    @Test
    void withSettingStoresAndReadsBack() {
        GuildConfig c = GuildConfigEdits.withSetting(base, "ticket-description", "Abra um ticket");
        assertEquals("Abra um ticket", c.setting("ticket-description"));
    }

    @Test
    void editsPreserveSettings() {
        GuildConfig withSetting = GuildConfigEdits.withSetting(base, "ticket-emoji", "🎫");
        GuildConfig then = GuildConfigEdits.withLogChannel(withSetting, "100");
        assertEquals("🎫", then.setting("ticket-emoji"));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.setup.GuildConfigEditsTest"`
Expected: FAIL — `withSetting` / `setting` not found.

- [ ] **Step 3: Implement**

Replace the `GuildConfig` record (keep the class javadoc) with:

```java
public record GuildConfig(
        String guildId,
        String logChannelId,
        String ticketLogChannelId,
        Map<String, String> channels,
        Map<String, String> roles,
        Map<String, Boolean> toggles,
        List<String> staffRoleIds,
        Map<String, String> settings
) {

    public static GuildConfig empty(String guildId) {
        return new GuildConfig(guildId, null, null, Map.of(), Map.of(), Map.of(), List.of(), Map.of());
    }

    public boolean toggle(String key, boolean def) {
        return toggles.getOrDefault(key, def);
    }

    public String channel(String key) {
        return channels.get(key);
    }

    public String role(String key) {
        return roles.get(key);
    }

    public String setting(String key) {
        return settings.get(key);
    }
}
```

`src/main/resources/db/postgres/002_guild_settings.sql`:

```sql
-- Free-form text settings per guild (ticket description/emoji, percentages, etc.).
ALTER TABLE guild_config
    ADD COLUMN IF NOT EXISTS settings JSONB NOT NULL DEFAULT '{}'::jsonb;
```

In `JdbcGuildConfigRepository.java`: add `settings` to the SELECT column list and the INSERT column list / values, bind it, and map it.
- `find` SELECT: add `, settings` to the column list.
- `map(...)`: add as the 8th constructor arg: `read(rs.getString("settings"), STR_MAP, Map.of())`.
- `save` INSERT: add `settings` to columns, add `?::jsonb` to VALUES, add `settings = EXCLUDED.settings` to the ON CONFLICT SET, and bind `ps.setString(8, write(cfg.settings()));` (renumber: settings is param 8, then any later params shift — there are none after staff_role_ids today, so staff stays 7, settings 8).

Concretely, the `save` SQL becomes:

```java
        String sql = """
                INSERT INTO guild_config
                    (guild_id, log_channel_id, ticket_log_channel_id,
                     channels, roles, toggles, staff_role_ids, settings, updated_at)
                VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, now())
                ON CONFLICT (guild_id) DO UPDATE SET
                    log_channel_id        = EXCLUDED.log_channel_id,
                    ticket_log_channel_id = EXCLUDED.ticket_log_channel_id,
                    channels              = EXCLUDED.channels,
                    roles                 = EXCLUDED.roles,
                    toggles               = EXCLUDED.toggles,
                    staff_role_ids        = EXCLUDED.staff_role_ids,
                    settings              = EXCLUDED.settings,
                    updated_at            = now()
                """;
```

and after `ps.setString(7, write(cfg.staffRoleIds()));` add `ps.setString(8, write(cfg.settings()));`. The `find` SELECT becomes:

```java
        String sql = """
                SELECT guild_id, log_channel_id, ticket_log_channel_id,
                       channels, roles, toggles, staff_role_ids, settings
                  FROM guild_config
                 WHERE guild_id = ?
                """;
```

and `map(...)`:

```java
    private GuildConfig map(ResultSet rs) throws SQLException {
        return new GuildConfig(
                rs.getString("guild_id"),
                rs.getString("log_channel_id"),
                rs.getString("ticket_log_channel_id"),
                read(rs.getString("channels"), STR_MAP, Map.of()),
                read(rs.getString("roles"), STR_MAP, Map.of()),
                read(rs.getString("toggles"), BOOL_MAP, Map.of()),
                read(rs.getString("staff_role_ids"), STR_LIST, List.of()),
                read(rs.getString("settings"), STR_MAP, Map.of())
        );
    }
```

In `GuildConfigEdits.java`: every `new GuildConfig(...)` now needs the 8th arg `c.settings()`. Update all six existing methods to append `, c.settings()` and add:

```java
    public static GuildConfig withSetting(GuildConfig c, String key, String value) {
        Map<String, String> m = new HashMap<>(c.settings());
        m.put(key, value);
        return new GuildConfig(c.guildId(), c.logChannelId(), c.ticketLogChannelId(),
                c.channels(), c.roles(), c.toggles(), c.staffRoleIds(), Map.copyOf(m));
    }
```

(For example `withLogChannel` becomes `new GuildConfig(c.guildId(), channelId, c.ticketLogChannelId(), c.channels(), c.roles(), c.toggles(), c.staffRoleIds(), c.settings());` and likewise for the other five.)

- [ ] **Step 4: Run tests + compile**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.setup.GuildConfigEditsTest"`
Expected: PASS (8 tests).

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/database/model/GuildConfig.java \
        src/main/java/dev/davimf/basebot/database/postgres/JdbcGuildConfigRepository.java \
        src/main/java/dev/davimf/basebot/modules/base/setup/GuildConfigEdits.java \
        src/main/resources/db/postgres/002_guild_settings.sql \
        src/test/java/dev/davimf/basebot/modules/base/setup/GuildConfigEditsTest.java
git commit -m "feat(base): add free-form settings map to guild config"
```

---

### Task 2: Cargos section (logical-role mapping)

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupRoleKeys.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java`
- Test: compile (the persist path uses Task 1's tested `withRole`).

**Interfaces:**
- Produces: `SetupRoleKeys.OPTIONS` (`List<Map.Entry<String,String>>` of value→label); handler now serves `setup:rolekey` (StringSelect) and `setup:setrole:<key>` (RoleSelect).

- [ ] **Step 1: Define the role keys + wire the section**

`SetupRoleKeys.java`:

```java
package dev.davimf.basebot.modules.base.setup;

import java.util.List;
import java.util.Map;

/** Logical role slots that {@code /setup} can map to server roles (BOTSPECS Module 1). */
public final class SetupRoleKeys {

    private SetupRoleKeys() {}

    /** key -> human label. Keys are the stable identifiers stored in guild_config.roles. */
    public static final List<Map.Entry<String, String>> OPTIONS = List.of(
            Map.entry("moderador", "Moderador"),
            Map.entry("staff", "Staff"),
            Map.entry("mutado", "Cargo de Mutado")
    );

    public static String labelFor(String key) {
        return OPTIONS.stream().filter(e -> e.getKey().equals(key))
                .map(Map.Entry::getValue).findFirst().orElse(key);
    }
}
```

In `SetupComponentHandler.java`, change the `roles` branch in `onButton` from the placeholder to:

```java
            case "roles" -> showRolesPanel(event);
```

Add the imports:

```java
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.entities.Role;
```

Add the panel builder + a `StringSelect` handler + the role-persist branch:

```java
    private void showRolesPanel(ButtonInteractionEvent event) {
        StringSelectMenu.Builder menu = StringSelectMenu
                .create(ComponentId.of(SetupView.NS, "rolekey"))
                .setPlaceholder("Qual cargo lógico configurar?");
        for (var e : SetupRoleKeys.OPTIONS) {
            menu.addOption(e.getValue(), e.getKey());
        }
        event.reply("Escolha qual função deseja mapear a um cargo:")
                .addComponents(ActionRow.of(menu.build()))
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"rolekey".equals(id.action())) {
            return;
        }
        String key = event.getValues().get(0);
        EntitySelectMenu roleMenu = EntitySelectMenu
                .create(ComponentId.of(SetupView.NS, "setrole", key), EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder("Cargo para: " + SetupRoleKeys.labelFor(key))
                .setRequiredRange(1, 1)
                .build();
        event.reply("Selecione o cargo do servidor para **" + SetupRoleKeys.labelFor(key) + "**:")
                .addComponents(ActionRow.of(roleMenu))
                .setEphemeral(true)
                .queue();
    }
```

In `onEntitySelect`, add a `setrole` branch:

```java
            case "setrole" -> persistRole(event, ctx, id.arg(0));
```

and the method:

```java
    private void persistRole(EntitySelectInteractionEvent event, BotContext ctx, String key) {
        if (event.getGuild() == null || key == null) {
            event.reply("Requisição inválida.").setEphemeral(true).queue();
            return;
        }
        List<Role> roles = event.getMentions().getRoles();
        if (roles.isEmpty()) {
            event.reply("Nenhum cargo selecionado.").setEphemeral(true).queue();
            return;
        }
        String roleId = roles.get(0).getId();
        String guildId = event.getGuild().getId();
        GuildConfig updated = GuildConfigEdits.withRole(
                ctx.database().guildConfig().findOrEmpty(guildId), key, roleId);
        ctx.database().guildConfig().save(updated);
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), roleId, "SETUP_ROLE", key);
        event.reply("Cargo de **" + SetupRoleKeys.labelFor(key) + "** definido: <@&" + roleId + ">")
                .setEphemeral(true).queue();
    }
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/setup/SetupRoleKeys.java \
        src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java
git commit -m "feat(base): wire /setup Cargos section (role mapping)"
```

---

### Task 3: Tickets section (category, staff roles, description/emoji)

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java`
- Test: compile (persist via Task 1's tested `withChannel`/`withStaffRoles`/`withSetting`).

**Interfaces:**
- Consumes: `withChannel("tickets-category", id)`, `withStaffRoles(list)`, `withSetting("ticket-description"|"ticket-emoji", val)`.
- Handler serves: `setup:setcategory` (channel CATEGORY), `setup:setstaff` (role multi), `setup:ticketinfo` (button→modal and modal→persist).

- [ ] **Step 1: Wire the Tickets section**

In `SetupComponentHandler.java`, add imports:

```java
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
```

Restructure `onButton` so it handles both `section` and `ticketinfo` actions:

```java
    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if ("ticketinfo".equals(id.action())) {
            openTicketInfoModal(event);
            return;
        }
        if (!"section".equals(id.action())) {
            return;
        }
        switch (String.valueOf(id.arg(0))) {
            case "logs" -> showLogsPanel(event);
            case "roles" -> showRolesPanel(event);
            case "tickets" -> showTicketsPanel(event);
            case "bot" -> showBotPanel(event, ctx);
            default -> placeholder(event, "Desconhecido", "Seção inválida.");
        }
    }
```

Add the Tickets panel + modal opener:

```java
    private void showTicketsPanel(ButtonInteractionEvent event) {
        EntitySelectMenu category = EntitySelectMenu
                .create(ComponentId.of(SetupView.NS, "setcategory"), EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.CATEGORY)
                .setPlaceholder("Categoria onde os tickets serão criados")
                .setRequiredRange(1, 1)
                .build();
        EntitySelectMenu staff = EntitySelectMenu
                .create(ComponentId.of(SetupView.NS, "setstaff"), EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder("Cargos de staff com acesso aos tickets")
                .setRequiredRange(1, 25)
                .build();
        event.reply("Configuração de tickets:")
                .addComponents(
                        ActionRow.of(category),
                        ActionRow.of(staff),
                        ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary(
                                ComponentId.of(SetupView.NS, "ticketinfo"), "Definir descrição/emoji")))
                .setEphemeral(true)
                .queue();
    }

    private void openTicketInfoModal(ButtonInteractionEvent event) {
        TextInput desc = TextInput.create("desc", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Texto exibido no painel de tickets")
                .setRequired(false)
                .setMaxLength(200)
                .build();
        TextInput emoji = TextInput.create("emoji", TextInputStyle.SHORT)
                .setPlaceholder("Ex.: 🎫")
                .setRequired(false)
                .setMaxLength(8)
                .build();
        Modal modal = Modal.create(ComponentId.of(SetupView.NS, "ticketinfo"), "Descrição & Emoji dos Tickets")
                .addComponents(Label.of("Descrição", desc), Label.of("Emoji", emoji))
                .build();
        event.replyModal(modal).queue();
    }
```

In `onEntitySelect`, add the two ticket branches:

```java
            case "setcategory" -> persistChannelSetting(event, ctx, "tickets-category", "Categoria de tickets");
            case "setstaff" -> persistStaffRoles(event, ctx);
```

Add the persist methods + the modal handler:

```java
    private void persistChannelSetting(EntitySelectInteractionEvent event, BotContext ctx,
                                       String key, String label) {
        if (event.getGuild() == null) {
            event.reply("Use em um servidor.").setEphemeral(true).queue();
            return;
        }
        List<GuildChannel> channels = event.getMentions().getChannels();
        if (channels.isEmpty()) {
            event.reply("Nada selecionado.").setEphemeral(true).queue();
            return;
        }
        String channelId = channels.get(0).getId();
        String guildId = event.getGuild().getId();
        GuildConfig updated = GuildConfigEdits.withChannel(
                ctx.database().guildConfig().findOrEmpty(guildId), key, channelId);
        ctx.database().guildConfig().save(updated);
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), channelId, "SETUP_CHANNEL", key);
        event.reply(label + " definida: <#" + channelId + ">").setEphemeral(true).queue();
    }

    private void persistStaffRoles(EntitySelectInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use em um servidor.").setEphemeral(true).queue();
            return;
        }
        List<String> roleIds = event.getMentions().getRoles().stream()
                .map(net.dv8tion.jda.api.entities.Role::getId).toList();
        if (roleIds.isEmpty()) {
            event.reply("Nenhum cargo selecionado.").setEphemeral(true).queue();
            return;
        }
        String guildId = event.getGuild().getId();
        GuildConfig updated = GuildConfigEdits.withStaffRoles(
                ctx.database().guildConfig().findOrEmpty(guildId), roleIds);
        ctx.database().guildConfig().save(updated);
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), null,
                "SETUP_STAFF_ROLES", String.valueOf(roleIds.size()));
        event.reply("Cargos de staff definidos: " + roleIds.size()).setEphemeral(true).queue();
    }

    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"ticketinfo".equals(id.action()) || event.getGuild() == null) {
            return;
        }
        String desc = event.getValue("desc") == null ? "" : event.getValue("desc").getAsString();
        String emoji = event.getValue("emoji") == null ? "" : event.getValue("emoji").getAsString();
        String guildId = event.getGuild().getId();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        cfg = GuildConfigEdits.withSetting(cfg, "ticket-description", desc);
        cfg = GuildConfigEdits.withSetting(cfg, "ticket-emoji", emoji);
        ctx.database().guildConfig().save(cfg);
        event.reply("Descrição e emoji dos tickets atualizados.").setEphemeral(true).queue();
    }
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java
git commit -m "feat(base): wire /setup Tickets section (category, staff, description, emoji)"
```

---

### Task 4: Bot section + hub embed refresh

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java` (`showBotPanel`)
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupView.java` (show ticket category + description in the hub)
- Test: compile + full build.

**Interfaces:**
- Produces: `showBotPanel(ButtonInteractionEvent, BotContext)`; updated `SetupView.hubEmbed`.

- [ ] **Step 1: Implement the Bot panel + enrich the hub embed**

Add to `SetupComponentHandler.java`:

```java
    private void showBotPanel(ButtonInteractionEvent event, BotContext ctx) {
        net.dv8tion.jda.api.entities.SelfUser self = event.getJDA().getSelfUser();
        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder()
                .setTitle("🤖 Perfil do Bot")
                .setColor(0x5865F2)
                .setThumbnail(self.getEffectiveAvatarUrl())
                .addField("Nome atual", self.getName(), true)
                .addField("ID", self.getId(), true)
                .setDescription("O **nome** e o **avatar** são globais (afetam o bot em todos os "
                        + "servidores) e o Discord limita a **2 alterações por hora**. "
                        + "Use `/bot-name` e `/bot-icon` para alterá-los, e `/bot-nick` para o "
                        + "apelido apenas neste servidor.");
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
```

Replace the existing `bot` placeholder branch usage — it is already routed to `showBotPanel(event, ctx)` in Task 3's `onButton`. (No further change needed; the old `placeholder(...)` for bot/roles/tickets is now unused — remove the `placeholder` method only if no branch calls it; the `default` branch still uses it, so keep it.)

In `SetupView.java`, enrich `hubEmbed` to surface ticket config. Replace the `addField` chain's tail so it reads:

```java
                .addField("📋 Logs gerais", channelOrUnset(cfg.logChannelId()), true)
                .addField("🎫 Logs de tickets", channelOrUnset(cfg.ticketLogChannelId()), true)
                .addField("📂 Categoria de tickets", channelOrUnset(cfg.channel("tickets-category")), true)
                .addField("👥 Cargos configurados", String.valueOf(cfg.roles().size()), true)
                .addField("🛡️ Staff de tickets", String.valueOf(cfg.staffRoleIds().size()), true)
                .addField("📝 Descrição definida",
                        cfg.setting("ticket-description") == null
                                || cfg.setting("ticket-description").isBlank() ? "Não" : "Sim", true)
                .build();
```

(Note: `channelOrUnset` already handles null; `cfg.channel(...)` returns null when unset.)

- [ ] **Step 2: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — compiles, all unit tests pass, `basebot.jar` produced.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java \
        src/main/java/dev/davimf/basebot/modules/base/setup/SetupView.java
git commit -m "feat(base): add /setup Bot section + enrich hub summary"
```

---

## Self-Review

**Spec coverage (`/setup` sections):**
- **Cargos** — map logical roles (moderador/staff/mutado) to server roles, persisted → Task 2. ✓
- **Tickets** — category (parent id), allowed staff roles, description, emoji suffix (BOTSPECS Module 2 setup fields) → Task 3 (+ `settings` from Task 1). ✓
- **Bot** — profile info with the global-scope + 2/hour limit warning and command guidance → Task 4. ✓
- Logs section already complete (prior plan). The hub embed now reflects all sections.

**Placeholder scan:** The `default` switch branch keeps the small `placeholder(...)` helper for genuinely invalid section ids; all four real sections are fully implemented (no in-development stubs remain).

**Type consistency:** `GuildConfig` 8-arg constructor + `setting(key)`; `GuildConfigEdits.withSetting/withRole/withChannel/withStaffRoles`; custom-ids `setup:rolekey`, `setup:setrole:<key>`, `setup:setcategory`, `setup:setstaff`, `setup:ticketinfo`; `SetupRoleKeys.OPTIONS/labelFor`; modal input ids `desc`/`emoji` match `event.getValue("desc"|"emoji")`. All referenced identically across tasks. ✓
