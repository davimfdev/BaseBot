# Manager Permissions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a configurable, role-based permission system for faction management, organized by management category (gerência), replacing the raw Discord-permission gates on facs management.

**Architecture:** A stateless `ManagerPermissions` utility owns five capabilities (`acoes`, `financeiro`, `farm`, `recrutamento`, `punicoes`), each granting a set of "principals" (hierarchy role keys and/or `role:<id>`) stored as a CSV in `guild_config.settings` (`perm:<cap>`). A central `can(member, cfg, capability)` (Administrator bypasses; absent config falls back to coded defaults) replaces the scattered `hasPermission(...)` gates. Configured via a new `/setup → Permissões` screen, oriented by category.

**Tech Stack:** Java 22, JDA 6.4.2 (Components V2), HikariCP, JUnit 5, Gradle. Spec: `docs/superpowers/specs/2026-06-28-manager-permissions-design.md`.

## Global Constraints

- Build/run on JDK 22 (`./gradlew` is pinned); JDA package is `net.dv8tion.jda.api.*`.
- No DB migration — config rides the existing flexible `guild_config.settings` map.
- Components V2 only for any new bot message (house style: `## title` + `Panels.divider()` + sectioned body, `code` for ids, `-#` subtext). Custom emojis go in `.withEmoji(Emoji.fromFormatted(...))`, never label text.
- Custom-id args must not contain `:` (the `ComponentId` separator). Encode `role:<id>` principals with `ManagerPermissions.customIdToken` before putting them in a custom id.
- Discord **Administrator** is the only un-removable bypass. `lider`/`sub-lider`/`gerente-geral` are in every capability's *editable* defaults.
- Out of scope (do NOT touch their gates): base moderation (`/ban`,`/kick`,`/mute`,voice), Catálogo `/tabela`, ticket staff, `/farm` submit, `/produzir fazer`+`lista`, `/solicitar-cargo` request, `/orçamento`.

---

### Task 1: `ManagerPermissions` core + unit tests

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/facs/perms/ManagerPermissions.java`
- Test: `src/test/java/dev/davimf/basebot/modules/facs/perms/ManagerPermissionsTest.java`

**Interfaces:**
- Produces (used by every later task):
  - `enum ManagerPermissions.Capability { ACOES, FINANCEIRO, FARM, RECRUTAMENTO, PUNICOES }` with `String key()`, `String label()`, `String shortLabel()`, `String settingKey()`, `List<String> defaultPrincipals()`, `static Capability fromKey(String)`.
  - `static List<String> CATEGORY_KEYS` (hierarchy management role keys, in order).
  - `static List<String> principals(GuildConfig, Capability)`
  - `static boolean grants(GuildConfig, Capability, String principal)`
  - `static Set<String> allowedRoleIds(GuildConfig, Capability)`
  - `static boolean isAllowed(Set<String> memberRoleIds, boolean isAdministrator, Set<String> allowed)`
  - `static boolean can(Member, GuildConfig, Capability)`
  - `static String grant(GuildConfig, Capability, String principal)` / `revoke(...)` → new CSV
  - `static List<String> freeRolePrincipals(GuildConfig)`
  - `static String customIdToken(String principal)` / `static String principalFromToken(String token)`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/davimf/basebot/modules/facs/perms/ManagerPermissionsTest.java`:

```java
package dev.davimf.basebot.modules.facs.perms;

import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions.Capability;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ManagerPermissionsTest {

    private static GuildConfig cfg(Map<String, String> roles, Map<String, String> settings) {
        return new GuildConfig("g1", null, null, Map.of(), roles, Map.of(), List.of(), settings);
    }

    @Test
    void defaultsApplyWhenUnset() {
        List<String> p = ManagerPermissions.principals(cfg(Map.of(), Map.of()), Capability.FINANCEIRO);
        assertTrue(p.containsAll(List.of("gerente-vendas", "lider", "sub-lider", "gerente-geral")));
        assertEquals(List.of("lider", "sub-lider", "gerente-geral"),
                ManagerPermissions.principals(cfg(Map.of(), Map.of()), Capability.PUNICOES));
    }

    @Test
    void allowedRoleIdsResolvesHierarchyKeysAndSkipsUnconfigured() {
        GuildConfig c = cfg(Map.of("gerente-vendas", "100", "lider", "200"), Map.of());
        assertEquals(Set.of("100", "200"), ManagerPermissions.allowedRoleIds(c, Capability.FINANCEIRO));
    }

    @Test
    void allowedRoleIdsResolvesRawRolePrincipal() {
        GuildConfig c = cfg(Map.of(), Map.of("perm:farm", "role:999"));
        assertEquals(Set.of("999"), ManagerPermissions.allowedRoleIds(c, Capability.FARM));
    }

    @Test
    void grantMaterializesDefaultsThenAddsPrincipal() {
        GuildConfig c = cfg(Map.of(), Map.of());
        String csv = ManagerPermissions.grant(c, Capability.PUNICOES, "role:5");
        GuildConfig c2 = cfg(Map.of(), Map.of("perm:punicoes", csv));
        assertTrue(ManagerPermissions.grants(c2, Capability.PUNICOES, "role:5"));
        assertTrue(ManagerPermissions.grants(c2, Capability.PUNICOES, "lider"));
    }

    @Test
    void revokeToEmptyMeansAdministratorOnly() {
        GuildConfig c = cfg(Map.of(), Map.of("perm:acoes", "gerente-elite"));
        String csv = ManagerPermissions.revoke(c, Capability.ACOES, "gerente-elite");
        assertEquals("", csv);
        assertTrue(ManagerPermissions.principals(cfg(Map.of(), Map.of("perm:acoes", csv)),
                Capability.ACOES).isEmpty());
    }

    @Test
    void isAllowedAdministratorBypassesAndMembershipMatches() {
        assertTrue(ManagerPermissions.isAllowed(Set.of(), true, Set.of()));
        assertTrue(ManagerPermissions.isAllowed(Set.of("100"), false, Set.of("100")));
        assertFalse(ManagerPermissions.isAllowed(Set.of("x"), false, Set.of("100")));
    }

    @Test
    void customIdTokenRoundTrips() {
        assertEquals("role-7", ManagerPermissions.customIdToken("role:7"));
        assertEquals("role:7", ManagerPermissions.principalFromToken("role-7"));
        assertEquals("gerente-farm", ManagerPermissions.customIdToken("gerente-farm"));
        assertEquals("gerente-farm", ManagerPermissions.principalFromToken("gerente-farm"));
    }

    @Test
    void freeRolePrincipalsAreCollected() {
        GuildConfig c = cfg(Map.of(), Map.of("perm:farm", "gerente-farm,role:42", "perm:acoes", "role:42"));
        assertEquals(List.of("role:42"), ManagerPermissions.freeRolePrincipals(c));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*ManagerPermissionsTest"`
Expected: FAIL — `ManagerPermissions` does not compile / cannot be resolved.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/dev/davimf/basebot/modules/facs/perms/ManagerPermissions.java`:

```java
package dev.davimf.basebot.modules.facs.perms;

import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Role-based faction-management permissions (design 2026-06-28). Each {@link Capability} is
 * one management area; a guild grants it to "principals" — hierarchy role keys and/or
 * {@code role:<id>} raw ids — stored as a CSV in guild_config.settings under
 * {@code perm:<capability>}. Discord Administrator always bypasses; an absent setting falls
 * back to the capability's coded defaults.
 */
public final class ManagerPermissions {

    /** Top management — in every capability's default set (editable). */
    private static final List<String> LEADERSHIP = List.of("lider", "sub-lider", "gerente-geral");

    /** Management categories shown in /setup, in hierarchy order. */
    public static final List<String> CATEGORY_KEYS = List.of(
            "lider", "sub-lider", "gerente-geral",
            "gerente-vendas", "gerente-elite", "gerente-recrutamento", "gerente-farm");

    public enum Capability {
        ACOES("acoes", "Ações / Escalações", "Ações", "gerente-elite"),
        FINANCEIRO("financeiro", "Financeiro", "Financeiro", "gerente-vendas"),
        FARM("farm", "Farm & Produção", "Farm", "gerente-farm"),
        RECRUTAMENTO("recrutamento", "Recrutamento & Sets", "Recrut./Sets", "gerente-recrutamento"),
        PUNICOES("punicoes", "Punições", "Punições", null);

        private final String key;
        private final String label;
        private final String shortLabel;
        private final String domainOwner;

        Capability(String key, String label, String shortLabel, String domainOwner) {
            this.key = key;
            this.label = label;
            this.shortLabel = shortLabel;
            this.domainOwner = domainOwner;
        }

        public String key() { return key; }
        public String label() { return label; }
        public String shortLabel() { return shortLabel; }
        public String settingKey() { return "perm:" + key; }

        public List<String> defaultPrincipals() {
            List<String> out = new ArrayList<>();
            if (domainOwner != null) {
                out.add(domainOwner);
            }
            out.addAll(LEADERSHIP);
            return out;
        }

        public static Capability fromKey(String key) {
            for (Capability c : values()) {
                if (c.key.equals(key)) {
                    return c;
                }
            }
            return null;
        }
    }

    private ManagerPermissions() {}

    public static List<String> principals(GuildConfig cfg, Capability cap) {
        String raw = cfg.setting(cap.settingKey());
        if (raw == null) {
            return cap.defaultPrincipals();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    public static boolean grants(GuildConfig cfg, Capability cap, String principal) {
        return principals(cfg, cap).contains(principal);
    }

    public static Set<String> allowedRoleIds(GuildConfig cfg, Capability cap) {
        Set<String> ids = new LinkedHashSet<>();
        for (String p : principals(cfg, cap)) {
            if (p.startsWith("role:")) {
                String id = p.substring("role:".length());
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            } else {
                String id = cfg.role(p);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    public static boolean isAllowed(Set<String> memberRoleIds, boolean isAdministrator, Set<String> allowed) {
        if (isAdministrator) {
            return true;
        }
        for (String id : memberRoleIds) {
            if (allowed.contains(id)) {
                return true;
            }
        }
        return false;
    }

    public static boolean can(Member member, GuildConfig cfg, Capability cap) {
        if (member == null) {
            return false;
        }
        if (member.hasPermission(Permission.ADMINISTRATOR)) {
            return true;
        }
        Set<String> allowed = allowedRoleIds(cfg, cap);
        for (Role r : member.getRoles()) {
            if (allowed.contains(r.getId())) {
                return true;
            }
        }
        return false;
    }

    public static String grant(GuildConfig cfg, Capability cap, String principal) {
        List<String> ps = new ArrayList<>(principals(cfg, cap));
        if (!ps.contains(principal)) {
            ps.add(principal);
        }
        return String.join(",", ps);
    }

    public static String revoke(GuildConfig cfg, Capability cap, String principal) {
        List<String> ps = new ArrayList<>(principals(cfg, cap));
        ps.remove(principal);
        return String.join(",", ps);
    }

    public static List<String> freeRolePrincipals(GuildConfig cfg) {
        Set<String> out = new LinkedHashSet<>();
        for (Capability c : Capability.values()) {
            for (String p : principals(cfg, c)) {
                if (p.startsWith("role:")) {
                    out.add(p);
                }
            }
        }
        return new ArrayList<>(out);
    }

    /** Custom-id-safe token for a principal (the ComponentId separator is ':'). */
    public static String customIdToken(String principal) {
        return principal.startsWith("role:") ? "role-" + principal.substring("role:".length()) : principal;
    }

    public static String principalFromToken(String token) {
        return token.startsWith("role-") ? "role:" + token.substring("role-".length()) : token;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*ManagerPermissionsTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/facs/perms/ManagerPermissions.java src/test/java/dev/davimf/basebot/modules/facs/perms/ManagerPermissionsTest.java
git commit -m "feat(facs): ManagerPermissions capability matrix + tests

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: `/setup → Permissões` views

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupView.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/setup/SetupPermissionsViewTest.java`

**Interfaces:**
- Consumes: `ManagerPermissions.*` (Task 1).
- Produces:
  - `static Container SetupView.permissionsHub(GuildConfig cfg, Guild guild)`
  - `static Container SetupView.permissionsDetail(GuildConfig cfg, String principal, String label)`
  - Hub section select gains an option `("Permissões","permissoes",…)`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/davimf/basebot/modules/base/setup/SetupPermissionsViewTest.java`:

```java
package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.utils.data.SerializableData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** The permissions detail screen must serialize cleanly as a V2 container. */
class SetupPermissionsViewTest {

    private static GuildConfig cfg() {
        return new GuildConfig("g1", null, null, Map.of(), Map.of("gerente-farm", "100"),
                Map.of(), List.of(), Map.of());
    }

    @Test
    void detailSerializes() {
        Container c = SetupView.permissionsDetail(cfg(), "gerente-farm", "Gerente de Farm");
        assertDoesNotThrow(() -> ((SerializableData) c).toData());
    }

    @Test
    void detailForUnconfiguredRoleSerializes() {
        Container c = SetupView.permissionsDetail(cfg(), "gerente-elite", "Gerente de Elite");
        assertDoesNotThrow(() -> ((SerializableData) c).toData());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*SetupPermissionsViewTest"`
Expected: FAIL — `permissionsDetail` not found.

- [ ] **Step 3: Add imports + the two view methods + hub option**

In `SetupView.java`, ensure these imports exist (add any missing):

```java
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
```

In the `hub(...)` method's section select builder, add this option (after the `"bot"` option):

```java
                .addOption("Permissões", "permissoes", "O que cada categoria de gerência pode fazer")
```

Add these two methods (place them after the `bot(...)` method):

```java
    // --- Permissões ------------------------------------------------------------

    public static Container permissionsHub(GuildConfig cfg, Guild guild) {
        int accent = EmbedColor.resolve(cfg);
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## 🔐 Permissões de Gerência"));
        kids.add(Panels.divider());
        kids.add(Panels.text("> Escolha uma categoria de gerência para definir o que ela pode fazer."));

        StringSelectMenu.Builder menu = StringSelectMenu.create(ComponentId.of(NS, "permcat"))
                .setPlaceholder("Escolha a categoria de gerência");
        for (String key : ManagerPermissions.CATEGORY_KEYS) {
            menu.addOption(SetupRoleKeys.labelFor(key), key);
        }
        for (String principal : ManagerPermissions.freeRolePrincipals(cfg)) {
            String id = principal.substring("role:".length());
            Role r = guild == null ? null : guild.getRoleById(id);
            menu.addOption(trim(r == null ? "Cargo " + id : "Cargo: " + r.getName(), 100),
                    ManagerPermissions.customIdToken(principal));
        }
        kids.add(ActionRow.of(menu.build()));
        kids.add(ActionRow.of(
                Button.success(ComponentId.of(NS, "permaddrole"), "➕ Adicionar outro cargo"),
                Button.secondary(ComponentId.of(NS, "nav", "hub"), "◀ Voltar")));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    public static Container permissionsDetail(GuildConfig cfg, String principal, String label) {
        int accent = EmbedColor.resolve(cfg);
        String roleId = principal.startsWith("role:") ? principal.substring("role:".length())
                : cfg.role(principal);

        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## 🔐 " + label));
        kids.add(Panels.divider());
        kids.add(Panels.text(roleId != null
                ? "🎭 **Cargo** · <@&" + roleId + ">"
                : "-# Cargo não configurado em `/setup → Cargos` — configure-o para que esta categoria valha."));
        kids.add(Panels.divider());
        kids.add(Panels.text("-# Toque para ligar/desligar cada área:"));

        String token = ManagerPermissions.customIdToken(principal);
        List<Button> buttons = new ArrayList<>();
        for (ManagerPermissions.Capability cap : ManagerPermissions.Capability.values()) {
            boolean on = ManagerPermissions.grants(cfg, cap, principal);
            Button b = on
                    ? Button.success(ComponentId.of(NS, "permtoggle", token, cap.key()), cap.shortLabel())
                            .withEmoji(Emoji.fromFormatted(Emojis.CHECK_YES))
                    : Button.secondary(ComponentId.of(NS, "permtoggle", token, cap.key()), cap.shortLabel())
                            .withEmoji(Emoji.fromFormatted(Emojis.CHECK_NO));
            buttons.add(b);
        }
        kids.add(ActionRow.of(buttons));
        kids.add(ActionRow.of(Button.secondary(ComponentId.of(NS, "nav", "permissoes"), "◀ Voltar")));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*SetupPermissionsViewTest"`
Expected: PASS (2 tests). (5 capability buttons fit one ActionRow — Discord's max is 5.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/setup/SetupView.java src/test/java/dev/davimf/basebot/modules/base/setup/SetupPermissionsViewTest.java
git commit -m "feat(setup): Permissões screens (hub + category detail)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Wire `/setup → Permissões` interactions

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java`

**Interfaces:**
- Consumes: `SetupView.permissionsHub/permissionsDetail` (Task 2), `ManagerPermissions.*` + `GuildConfigEdits.withSetting` (Task 1 / existing).
- Produces: handles custom ids `setup:permissoes`, `setup:permcat`, `setup:permaddrole`, `setup:permrole`, `setup:permtoggle:<token>:<cap>`.

- [ ] **Step 1: Add imports**

In `SetupComponentHandler.java` add (if missing):

```java
import dev.davimf.basebot.modules.base.setup.SetupRoleKeys;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions.Capability;
import net.dv8tion.jda.api.entities.Role;
```
(`SetupRoleKeys` is same-package — the import is optional. `Replies`, `EntitySelectMenu`, `EntitySelectInteractionEvent`, `ComponentId`, `Container` already imported.)

- [ ] **Step 2: Route the new button + select actions**

In `onButton(...)`, inside the `switch (id.action())`, add cases (next to `"botcolor"`):

```java
            case "permaddrole" -> edit(event, permAddRolePrompt(guildId));
```

In `onButton(...)`, the `nav` case already calls `edit(event, switch (id.arg(0)) {...})`. Add a branch to that inner switch:

```java
                case "permissoes" -> SetupView.permissionsHub(config(ctx, guildId), event.getGuild());
```

Add a NEW top-level case to `onButton`'s switch for the toggle:

```java
            case "permtoggle" -> {
                String principal = ManagerPermissions.principalFromToken(id.arg(0));
                Capability cap = Capability.fromKey(id.arg(1));
                if (cap != null) {
                    GuildConfig cfg = config(ctx, guildId);
                    String csv = ManagerPermissions.grants(cfg, cap, principal)
                            ? ManagerPermissions.revoke(cfg, cap, principal)
                            : ManagerPermissions.grant(cfg, cap, principal);
                    GuildConfig updated = GuildConfigEdits.withSetting(cfg, cap.settingKey(), csv);
                    ctx.database().guildConfig().save(updated);
                    ctx.database().actionLogs().log(guildId, event.getUser().getId(), null,
                            "PERM_TOGGLE", cap.key() + ":" + principal);
                    edit(event, SetupView.permissionsDetail(updated, principal, principalLabel(principal, event.getGuild())));
                }
            }
```

In `onStringSelect(...)`, inside its `switch (id.action())`, add the "section" branch option and a `permcat` case. The existing `section` case builds a `screen` switch — add:

```java
                    case "permissoes" -> SetupView.permissionsHub(config(ctx, guildId), event.getGuild());
```

…and add a new top-level case:

```java
            case "permcat" -> {
                String principal = ManagerPermissions.principalFromToken(event.getValues().get(0));
                edit(event, SetupView.permissionsDetail(config(ctx, guildId), principal,
                        principalLabel(principal, event.getGuild())));
            }
```

In `onEntitySelect(...)`, inside its `switch (id.action())`, add:

```java
            case "permrole" -> {
                String principal = "role:" + firstRoleId(event);
                edit(event, SetupView.permissionsDetail(config(ctx, event.getGuild().getId()), principal,
                        principalLabel(principal, event.getGuild())));
            }
```
(`firstRoleId(event)` already exists — it's used by `setrole`.)

- [ ] **Step 3: Add the two private helpers**

Add to `SetupComponentHandler` (near the other private helpers):

```java
    private Container permAddRolePrompt(String guildId) {
        EntitySelectMenu menu = EntitySelectMenu
                .create(ComponentId.of(NS, "permrole"), EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder("Escolha o cargo a configurar")
                .setRequiredRange(1, 1)
                .build();
        return Panels.container(EmbedColor.resolve(config(ctx, guildId)),
                Panels.text("## 🔐 Adicionar cargo às permissões"),
                Panels.divider(),
                Panels.text("> Selecione um cargo do servidor para definir o que ele pode fazer."),
                ActionRow.of(menu));
    }

    private String principalLabel(String principal, net.dv8tion.jda.api.entities.Guild guild) {
        if (principal.startsWith("role:")) {
            String id = principal.substring("role:".length());
            Role r = guild == null ? null : guild.getRoleById(id);
            return r == null ? "Cargo " + id : r.getName();
        }
        return SetupRoleKeys.labelFor(principal);
    }
```
(Ensure `Panels`, `EmbedColor`, `EntitySelectMenu`, `ActionRow`, `Container`, `NS` are imported/available — they already are in this handler.)

- [ ] **Step 4: Build + run the full test suite**

Run: `./gradlew compileJava test`
Expected: BUILD SUCCESSFUL; all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java
git commit -m "feat(setup): wire Permissões hub, category select, free-role add, and toggles

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: Gate Ações on the `acoes` capability

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/facs/commands/PainelAcoesCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/facs/actions/ActionService.java` (`managerGate`)

**Interfaces:**
- Consumes: `ManagerPermissions.can(member, cfg, Capability.ACOES)` (Task 1).

- [ ] **Step 1: PainelAcoesCommand — drop default-perm, gate in code**

In `PainelAcoesCommand.java`: delete the `.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))` line from `data()` (and remove the now-unused `Permission` / `DefaultMemberPermissions` imports). Add imports:

```java
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions.Capability;
import dev.davimf.basebot.util.Replies;
```

Replace the body of `execute(...)` with:

```java
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), cfg, Capability.ACOES)) {
            Replies.ephemeral(event, ctx, "Você não tem permissão de **Ações** para usar este painel.");
            return;
        }
        service.postManagementPanel(event);
```

- [ ] **Step 2: ActionService.managerGate — use the capability**

In `ActionService.java`, replace the whole `managerGate` method with:

```java
    private boolean managerGate(IReplyCallback event) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId(event));
        if (!ManagerPermissions.can(event.getMember(), cfg, ManagerPermissions.Capability.ACOES)) {
            Replies.ephemeral(event, accent(guildId(event)),
                    "Apenas a gerência de **Ações** pode usar isto.");
            return false;
        }
        return true;
    }
```

Add `import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;` to `ActionService.java`. If `net.dv8tion.jda.api.Permission` is now unused there, remove that import. (`GuildConfig`, `Replies`, `IReplyCallback`, `accent`, `guildId` already exist.)

- [ ] **Step 3: Build + test**

Run: `./gradlew compileJava test`
Expected: BUILD SUCCESSFUL; tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/facs/commands/PainelAcoesCommand.java src/main/java/dev/davimf/basebot/modules/facs/actions/ActionService.java
git commit -m "feat(facs): gate Ações on the acoes capability

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Gate Financeiro on the `financeiro` capability

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/facs/commands/PainelFinanceiroCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/facs/economy/FinanceService.java` (`onButton` gate)
- Modify: `src/main/java/dev/davimf/basebot/modules/facs/commands/RelatorioCommand.java`

**Interfaces:**
- Consumes: `ManagerPermissions.can(member, cfg, Capability.FINANCEIRO)`.

- [ ] **Step 1: PainelFinanceiroCommand**

Delete the `.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))` line in `data()` (remove now-unused `Permission`/`DefaultMemberPermissions` imports). Add the same four imports as Task 4 Step 1. Replace the `event.getGuild() == null` guard block in `execute(...)` with:

```java
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), cfg, Capability.FINANCEIRO)) {
            Replies.ephemeral(event, ctx, "Você não tem permissão **Financeiro** para usar este painel.");
            return;
        }
```
(Leave the rest of `execute` — the `sendMessageComponents(service.panel(...))` — unchanged.)

- [ ] **Step 2: FinanceService.onButton**

In `FinanceService.java` `onButton(...)`, replace:

```java
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            Replies.ephemeral(event, ctx, "Apenas a gerência (Manage Server) pode operar o painel financeiro.");
            return;
        }
```
with:

```java
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), cfg, ManagerPermissions.Capability.FINANCEIRO)) {
            Replies.ephemeral(event, ctx, "Apenas a gerência **Financeiro** pode operar este painel.");
            return;
        }
```
Add `import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;` (and `GuildConfig` is already imported). Remove the `net.dv8tion.jda.api.Permission` import if now unused.

- [ ] **Step 3: RelatorioCommand**

Delete its `.setDefaultPermissions(...)` line in `data()` (remove unused `Permission`/`DefaultMemberPermissions` imports). Add the four imports from Task 4 Step 1. Immediately after the existing `if (event.getGuild() == null) { Replies.ephemeral(...); return; }` guard in `execute(...)`, insert:

```java
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), cfg, Capability.FINANCEIRO)) {
            Replies.ephemeral(event, ctx, "Você não tem permissão **Financeiro** para gerar relatórios.");
            return;
        }
```

- [ ] **Step 4: Build + test**

Run: `./gradlew compileJava test`
Expected: BUILD SUCCESSFUL; tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/facs/commands/PainelFinanceiroCommand.java src/main/java/dev/davimf/basebot/modules/facs/economy/FinanceService.java src/main/java/dev/davimf/basebot/modules/facs/commands/RelatorioCommand.java
git commit -m "feat(facs): gate Financeiro on the financeiro capability

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: Gate Farm & Produção on the `farm` capability

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/facs/economy/FarmService.java` (`managerGate`)
- Modify: `src/main/java/dev/davimf/basebot/modules/facs/commands/ProduzirCommand.java` (`receita`)

**Interfaces:**
- Consumes: `ManagerPermissions.can(member, cfg, Capability.FARM)`.

- [ ] **Step 1: FarmService.managerGate**

Replace the `managerGate` method body with:

```java
    private boolean managerGate(ButtonInteractionEvent event) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), cfg, ManagerPermissions.Capability.FARM)) {
            Replies.ephemeral(event, ctx, "Apenas a gerência de **Farm** pode aprovar/recusar entregas.");
            return false;
        }
        return true;
    }
```
Add `import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;` (`GuildConfig` already imported). Remove `net.dv8tion.jda.api.Permission` import if now unused.

- [ ] **Step 2: ProduzirCommand.receita**

In `receita(SlashCommandInteractionEvent event, BotContext ctx)`, replace:

```java
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            Replies.ephemeral(event, ctx, "Apenas a gerência pode definir receitas.");
            return;
        }
```
with:

```java
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), cfg, ManagerPermissions.Capability.FARM)) {
            Replies.ephemeral(event, ctx, "Apenas a gerência de **Farm** pode definir receitas.");
            return;
        }
```
Add imports `dev.davimf.basebot.database.model.GuildConfig` and `dev.davimf.basebot.modules.facs.perms.ManagerPermissions`. Remove `net.dv8tion.jda.api.Permission` import if now unused. Leave `fazer` and `lista` untouched.

- [ ] **Step 3: Build + test**

Run: `./gradlew compileJava test`
Expected: BUILD SUCCESSFUL; tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/facs/economy/FarmService.java src/main/java/dev/davimf/basebot/modules/facs/commands/ProduzirCommand.java
git commit -m "feat(facs): gate Farm & receitas on the farm capability

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 7: Gate Recrutamento & Sets on the `recrutamento` capability

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/facs/commands/RecrutamentoCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/facs/recruit/RecruitComponentHandler.java` (`accept`, `reject`)
- Modify: `src/main/java/dev/davimf/basebot/modules/facs/sets/SetRequestComponentHandler.java` (`onButton`)

**Interfaces:**
- Consumes: `ManagerPermissions.can(member, cfg, Capability.RECRUTAMENTO)`.

- [ ] **Step 1: RecrutamentoCommand**

Delete the `.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))` line in `data()` (remove unused `Permission`/`DefaultMemberPermissions` imports). Add the four imports from Task 4 Step 1. After the existing `if (event.getGuild() == null) { Replies.ephemeral(...); return; }` guard in `execute(...)`, insert:

```java
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), cfg, Capability.RECRUTAMENTO)) {
            Replies.ephemeral(event, ctx, "Você não tem permissão de **Recrutamento** para publicar o painel.");
            return;
        }
```

- [ ] **Step 2: RecruitComponentHandler accept + reject**

Replace the gate at the top of `accept(...)`:

```java
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_ROLES)) {
            Replies.ephemeral(event, ctx, "Apenas a gerência pode aceitar solicitações.");
            return;
        }
```
with:

```java
        GuildConfig gcfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), gcfg, ManagerPermissions.Capability.RECRUTAMENTO)) {
            Replies.ephemeral(event, ctx, "Apenas a gerência de **Recrutamento** pode aceitar solicitações.");
            return;
        }
```
Do the same in `reject(...)`, replacing its `hasPermission(Permission.MANAGE_ROLES)` block with the same gate but message "Apenas a gerência de **Recrutamento** pode recusar solicitações." Add `import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;` (`GuildConfig` already imported). Keep `Permission` import only if still used elsewhere in the file; otherwise remove it.

- [ ] **Step 3: SetRequestComponentHandler — add capability gate, keep hierarchy guard**

In `onButton(...)`, immediately after the `int accent = EmbedColor.resolve(...)` line (before the `reject`/`approve` handling), insert:

```java
        if (!ManagerPermissions.can(event.getMember(),
                ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()),
                ManagerPermissions.Capability.RECRUTAMENTO)) {
            Replies.ephemeral(event, ctx, "Apenas a gerência de **Recrutamento** pode resolver Sets.");
            return;
        }
```
Add `import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;`. Leave the existing `Moderation.canManageRole(...)` check in the approve branch intact (the approver must still outrank the requested role).

- [ ] **Step 4: Build + test**

Run: `./gradlew compileJava test`
Expected: BUILD SUCCESSFUL; tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/facs/commands/RecrutamentoCommand.java src/main/java/dev/davimf/basebot/modules/facs/recruit/RecruitComponentHandler.java src/main/java/dev/davimf/basebot/modules/facs/sets/SetRequestComponentHandler.java
git commit -m "feat(facs): gate Recrutamento & Sets on the recrutamento capability

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 8: Gate Punições on the `punicoes` capability

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/facs/commands/PunirCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/facs/commands/PdCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/facs/commands/PunicoesCommand.java`

**Interfaces:**
- Consumes: `ManagerPermissions.can(member, cfg, Capability.PUNICOES)`.

- [ ] **Step 1: PunirCommand**

Delete the `.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))` line in `data()`. Add the four imports from Task 4 Step 1. After the existing `if (event.getGuild() == null || event.getMember() == null) { Replies.ephemeral(...); return; }` guard in `execute(...)`, insert:

```java
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), cfg, Capability.PUNICOES)) {
            Replies.ephemeral(event, ctx, "Você não tem permissão de **Punições**.");
            return;
        }
```
Keep the existing `Moderation.canModerate(...)` hierarchy guard. Keep `Permission` import only if still used; else remove.

- [ ] **Step 2: PdCommand**

Delete the `.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS))` line in `data()`. Add the four imports from Task 4 Step 1. After the `if (event.getGuild() == null || event.getMember() == null) {...}` guard in `execute(...)`, insert the same `PUNICOES` gate block as Step 1. Keep the existing `Moderation.canModerate(...)` guard. Keep `Permission` import only if still used; else remove.

- [ ] **Step 3: PunicoesCommand**

Delete the `.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))` line in `data()`. Add the four imports from Task 4 Step 1. After the `if (event.getGuild() == null) {...}` guard in `execute(...)`, insert:

```java
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), cfg, Capability.PUNICOES)) {
            Replies.ephemeral(event, ctx, "Você não tem permissão de **Punições** para ver o histórico.");
            return;
        }
```

- [ ] **Step 4: Build + run the full suite**

Run: `./gradlew compileJava test`
Expected: BUILD SUCCESSFUL; all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/facs/commands/PunirCommand.java src/main/java/dev/davimf/basebot/modules/facs/commands/PdCommand.java src/main/java/dev/davimf/basebot/modules/facs/commands/PunicoesCommand.java
git commit -m "feat(facs): gate Punições on the punicoes capability

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Final verification

- [ ] Run `./gradlew clean test --rerun-tasks` — BUILD SUCCESSFUL, all tests pass.
- [ ] Grep that no facs management gate still keys off raw perms:
  `grep -rn "MANAGE_SERVER\|MANAGE_ROLES\|MODERATE_MEMBERS\|KICK_MEMBERS" src/main/java/dev/davimf/basebot/modules/facs` — expect only the bot-self/`canModerate`/`canManageRole` hierarchy checks (Moderation), no `setDefaultPermissions`/`hasPermission` management gates.
- [ ] Manual smoke (optional, needs a test guild): open `/setup → Permissões`, pick Gerente de Farm, confirm `Farm` is ✅ by default and the others ✗; toggle `Financeiro` on; confirm a member with only that role can now open `/painel-financeiro`; add a free role and grant `Punições`; confirm a non-Administrator without any granted role is denied.
