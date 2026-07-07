# Segurança · Módulo 4 — Anti-nuke · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (inline). Steps em checkbox.

**Goal:** Parar o "sangramento" quando um ator (admin comprometido) executa ações destrutivas em massa (deletar canais/cargos, banir, expulsar): contar ações por ator numa janela e, ao passar do limite, **remover os cargos do ator** + **alertar o dono** no modlog. Não desfaz o que já foi feito (fora de escopo).

**Architecture:** `AntiNukeListener` escuta `ChannelDeleteEvent`, `RoleDeleteEvent`, `GuildBanEvent`, `GuildMemberRemoveEvent` (kick). Para cada, resolve o **ator** no audit log com **retry curto** (`NukeAuditLookup`, pois o registro pode atrasar ms), conta por ator numa janela em memória (`ActorWindow`), e ao cruzar `sec:antinuke-max` em `sec:antinuke-window-s` chama `AntiNukeService.neutralize`. Dono e whitelist (`sec:antinuke-whitelist`) são isentos. Config em `/setup → Segurança`.

**Tech Stack:** Java 22, JDA 6.4.2.

## Global Constraints
- JDK 22; Base ≠ facs; Components V2 + emojis custom; config em `guild_config`; sem commits salvo pedido; pacote `modules.base.security`. Intent `GUILD_MODERATION` (ban/kick) já habilitado; precisa de **Ver Registro de Auditoria** + **Gerenciar Cargos** (degrada em silêncio quando faltar).

**Símbolos JDA confirmados (javap):** `ActionType.{CHANNEL_DELETE,ROLE_DELETE,BAN,KICK}`; `ChannelDeleteEvent extends GenericChannelEvent` → `getGuild()/getChannel()/isFromGuild()`; `RoleDeleteEvent` → `getGuild()/getRole()`; `GuildBanEvent.getUser()`; `GuildMemberRemoveEvent.getUser()`; `Guild.getOwnerIdLong()`, `removeRoleFromMember(UserSnowflake,Role)`, `retrieveMember(UserSnowflake)`. `TaskScheduler.once(Runnable,delay,unit)` existe.

---

### Task 1: `SecurityConfig` — chaves do anti-nuke + whitelist (pura) + teste

**Files:** Modify `SecurityConfig.java`, `SecurityConfigTest.java`.

- [ ] **Step 1:** Constantes (junto do bloco "Anti-raid"):
```java
    // Anti-nuke (Módulo 4)
    public static final String KEY_ANTINUKE = "sec:antinuke";
    public static final String KEY_ANTINUKE_MAX = "sec:antinuke-max";
    public static final String KEY_ANTINUKE_WINDOW_S = "sec:antinuke-window-s";
    public static final String KEY_ANTINUKE_WHITELIST = "sec:antinuke-whitelist";
```
- [ ] **Step 2:** Métodos (após o bloco anti-raid):
```java
    // --- anti-nuke -------------------------------------------------------------

    public static boolean antinuke(GuildConfig cfg) { return cfg.toggle(KEY_ANTINUKE, false); }
    public static int antinukeMax(GuildConfig cfg) { return intOr(cfg.setting(KEY_ANTINUKE_MAX), 5, 2); }
    public static int antinukeWindowSeconds(GuildConfig cfg) { return intOr(cfg.setting(KEY_ANTINUKE_WINDOW_S), 60, 5); }

    public static List<String> antinukeWhitelist(GuildConfig cfg) { return csv(cfg.setting(KEY_ANTINUKE_WHITELIST)); }

    /** True quando o ator está na whitelist por id (token {@code user:<id>}) ou por cargo ({@code role:<id>}). */
    public static boolean isNukeWhitelisted(GuildConfig cfg, String userId, Collection<String> roleIds) {
        List<String> wl = antinukeWhitelist(cfg);
        if (wl.contains("user:" + userId)) {
            return true;
        }
        for (String r : roleIds) {
            if (wl.contains("role:" + r)) {
                return true;
            }
        }
        return false;
    }
```
- [ ] **Step 3:** Teste em `SecurityConfigTest`:
```java
    @Test
    void antiNukeDefaultsAndWhitelist() {
        GuildConfig c = cfg(Map.of(), Map.of());
        assertFalse(SecurityConfig.antinuke(c));
        assertEquals(5, SecurityConfig.antinukeMax(c));
        assertEquals(60, SecurityConfig.antinukeWindowSeconds(c));

        GuildConfig wl = cfg(Map.of(SecurityConfig.KEY_ANTINUKE_WHITELIST, "user:42, role:99"), Map.of());
        assertTrue(SecurityConfig.isNukeWhitelisted(wl, "42", List.of()));
        assertTrue(SecurityConfig.isNukeWhitelisted(wl, "7", List.of("99")));
        assertFalse(SecurityConfig.isNukeWhitelisted(wl, "7", List.of("1")));
    }
```
- [ ] **Step 4:** `./gradlew test --tests "*.SecurityConfigTest"` → PASS.

---

### Task 2: `ActorWindow` (sliding window por ator) + teste

**Files:** Create `ActorWindow.java`, `ActorWindowTest.java`.

- [ ] **Step 1:** Implementar (espelha `JoinWindow`, chave = ator):
```java
package dev.davimf.basebot.modules.base.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Conta ações destrutivas por ator numa janela deslizante (em memória). Anti-nuke. */
public final class ActorWindow {

    private final long windowMs;
    private final Map<String, Deque<Long>> actions = new ConcurrentHashMap<>();

    public ActorWindow(long windowMs) {
        this.windowMs = windowMs;
    }

    /** Registra uma ação de {@code actorKey}, expira as antigas, retorna a contagem na janela. */
    public synchronized int record(String actorKey, long nowMs) {
        Deque<Long> q = actions.computeIfAbsent(actorKey, k -> new ArrayDeque<>());
        while (!q.isEmpty() && nowMs - q.peekFirst() > windowMs) {
            q.pollFirst();
        }
        q.addLast(nowMs);
        return q.size();
    }
}
```
- [ ] **Step 2:** Teste:
```java
package dev.davimf.basebot.modules.base.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorWindowTest {

    @Test
    void countsPerActorWithinWindow() {
        ActorWindow w = new ActorWindow(60_000);
        assertEquals(1, w.record("a", 0));
        assertEquals(2, w.record("a", 1000));
        assertEquals(1, w.record("b", 1000));
    }

    @Test
    void expiresOld() {
        ActorWindow w = new ActorWindow(60_000);
        w.record("a", 0);
        assertEquals(1, w.record("a", 61_000));
    }
}
```
- [ ] **Step 3:** `./gradlew test --tests "*.ActorWindowTest"` → PASS.

---

### Task 3: `NukeAuditLookup` (audit log com retry)

**Files:** Create `NukeAuditLookup.java`.

- [ ] **Step 1:** Implementar — resolve o **User** ator com 3 tentativas espaçadas (~500ms):
```java
package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.time.OffsetDateTime;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;

/** Igual ao {@code AuditLookup}, mas com retry curto: o registro de "quem fez" pode atrasar
 *  alguns ms no audit log. Resolve o ator (User) de uma ação e só então chama {@code onActor}.
 *  Requer VIEW_AUDIT_LOG; desiste em silêncio. */
public final class NukeAuditLookup {

    private NukeAuditLookup() {}

    public static void resolveActor(Guild guild, String targetId, ActionType type,
                                    BotContext ctx, Consumer<User> onActor) {
        attempt(guild, targetId, type, ctx, onActor, 3);
    }

    private static void attempt(Guild guild, String targetId, ActionType type,
                                BotContext ctx, Consumer<User> onActor, int remaining) {
        guild.retrieveAuditLogs().type(type).limit(6).queue(entries -> {
            AuditLogEntry hit = entries.stream()
                    .filter(e -> targetId == null || targetId.equals(e.getTargetId()))
                    .filter(e -> e.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(15)))
                    .findFirst().orElse(null);
            if (hit != null && hit.getUser() != null) {
                onActor.accept(hit.getUser());
            } else {
                retry(guild, targetId, type, ctx, onActor, remaining);
            }
        }, err -> retry(guild, targetId, type, ctx, onActor, remaining));
    }

    private static void retry(Guild guild, String targetId, ActionType type,
                              BotContext ctx, Consumer<User> onActor, int remaining) {
        if (remaining > 1) {
            ctx.scheduler().once(() -> attempt(guild, targetId, type, ctx, onActor, remaining - 1),
                    500, TimeUnit.MILLISECONDS);
        }
    }
}
```
- [ ] **Step 2:** Build → SUCCESS.

---

### Task 4: `AntiNukeService.neutralize` (remove cargos + alerta o dono)

**Files:** Create `AntiNukeService.java`.

- [ ] **Step 1:** Implementar:
```java
package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.moderation.ModerationService;
import dev.davimf.basebot.util.ChannelLog;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.List;

/** Resposta do anti-nuke: remove os cargos do ator (os que o bot consegue tocar) e
 *  alerta o dono no modlog. Não desfaz canais/cargos/bans já apagados. */
public final class AntiNukeService {

    private AntiNukeService() {}

    public static void neutralize(BotContext ctx, Guild guild, Member actor, int count) {
        List<Role> removable = actor.getRoles().stream()
                .filter(r -> !r.isManaged() && guild.getSelfMember().canInteract(r))
                .toList();
        for (Role r : removable) {
            guild.removeRoleFromMember(actor, r).reason("Anti-nuke: ações destrutivas em massa").queue(ok -> {}, err -> {});
        }
        boolean impotent = !guild.getSelfMember().canInteract(actor);

        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        String body = "## " + Emojis.of(Emojis.SHIELD, "🛡️") + " Anti-nuke acionado\n"
                + "<@" + guild.getOwnerIdLong() + ">\n---\n"
                + "**Ator** · " + actor.getAsMention() + " (`" + actor.getId() + "`)\n"
                + "**Ações destrutivas** · `" + count + "` em janela curta\n"
                + (impotent
                    ? "-# " + Emojis.of(Emojis.WARN, "⚠️") + " O ator está **acima do meu cargo** — não consegui remover os cargos. Intervenha manualmente."
                    : "-# Cargos removidos. Canais/cargos/bans já feitos **não** são desfeitos — verifique os danos.");

        TextChannel modlog = modlog(ctx, guild);
        if (modlog != null) {
            modlog.sendMessageComponents(Panels.container(EmbedColor.resolve(cfg), Panels.text(body)))
                    .useComponentsV2()
                    .setAllowedMentions(List.of(Message.MentionType.USER))
                    .queue(ok -> {}, err -> {});
        } else {
            ChannelLog.post(ctx, guild.getId(), ModerationService.MODLOG_KEY, body);
        }
    }

    private static TextChannel modlog(BotContext ctx, Guild guild) {
        String id = ctx.database().guildConfig().findOrEmpty(guild.getId()).channel(ModerationService.MODLOG_KEY);
        return id == null ? null : guild.getTextChannelById(id);
    }
}
```
> **Verificar:** `Message.MentionType.USER` e `setAllowedMentions(Collection)` em `sendMessageComponents(...)`. `Role.isManaged()`.
- [ ] **Step 2:** Build → SUCCESS.

---

### Task 5: `AntiNukeListener` (eventos destrutivos → contar → neutralizar)

**Files:** Create `AntiNukeListener.java`.

- [ ] **Step 1:** Implementar:
```java
package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Anti-nuke: conta ações destrutivas por ator (resolvido no audit log com retry) e remove
 *  os cargos do ator ao passar do limite. Janela em memória; estado perdido no restart (ok). */
public final class AntiNukeListener extends ListenerAdapter {

    private final BotContext ctx;
    private volatile ActorWindow window = new ActorWindow(60_000);
    private volatile long windowStamp = Long.MIN_VALUE;
    private final Set<String> neutralized = ConcurrentHashMap.newKeySet();

    public AntiNukeListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event) {
        if (event.isFromGuild()) {
            onDestructive(event.getGuild(), event.getChannel().getId(), ActionType.CHANNEL_DELETE);
        }
    }

    @Override
    public void onRoleDelete(RoleDeleteEvent event) {
        onDestructive(event.getGuild(), event.getRole().getId(), ActionType.ROLE_DELETE);
    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        onDestructive(event.getGuild(), event.getUser().getId(), ActionType.BAN);
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        // Só conta se o audit log confirmar um KICK desse alvo (saídas voluntárias não batem).
        onDestructive(event.getGuild(), event.getUser().getId(), ActionType.KICK);
    }

    private void onDestructive(Guild guild, String targetId, ActionType type) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        if (!SecurityConfig.antinuke(cfg)) {
            return;
        }
        if (!guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOG)) {
            return; // degrada em silêncio
        }
        ensureWindow(cfg);
        NukeAuditLookup.resolveActor(guild, targetId, type, ctx, user -> {
            if (user.isBot() || user.getIdLong() == guild.getJDA().getSelfUser().getIdLong()
                    || user.getIdLong() == guild.getOwnerIdLong()) {
                return;
            }
            String key = guild.getId() + ":" + user.getId();
            guild.retrieveMember(user).queue(member -> {
                List<String> roleIds = member.getRoles().stream().map(ISnowflake::getId).toList();
                if (SecurityConfig.isNukeWhitelisted(cfg, user.getId(), roleIds)) {
                    return;
                }
                int count = window.record(key, System.currentTimeMillis());
                if (count >= SecurityConfig.antinukeMax(cfg) && neutralized.add(key)) {
                    AntiNukeService.neutralize(ctx, guild, member, count);
                }
            }, err -> { });
        });
    }

    private void ensureWindow(GuildConfig cfg) {
        long stamp = SecurityConfig.antinukeWindowSeconds(cfg);
        if (stamp != windowStamp) {
            window = new ActorWindow(stamp * 1000L);
            windowStamp = stamp;
        }
    }
}
```
> **Nota:** `neutralized` evita alertar/remover em duplicado pro mesmo ator (sem auto-limpar — após neutralizar, os cargos já foram). `ISnowflake.getId()` para os ids de cargo.
- [ ] **Step 2:** Build → SUCCESS.

---

### Task 6: `/setup → Segurança` — toggle + editar anti-nuke + nota de hierarquia

**Files:** Modify `SetupView.securityScreen` (+ novo `antinukeModal`), `SetupComponentHandler`.

- [ ] **Step 1:** `securityScreen`: `boolean nuke = SecurityConfig.antinuke(cfg);` e no overview adicionar:
```java
                + "\n" + Emojis.of(Emojis.SHIELD, "🛡️") + " **Anti-nuke** · " + (nuke ? "ligado" : "desligado")
                + " · `" + dev.davimf.basebot.modules.base.security.SecurityConfig.antinukeMax(cfg) + "/"
                + dev.davimf.basebot.modules.base.security.SecurityConfig.antinukeWindowSeconds(cfg) + "s`"
```
Trocar o rodapé `Panels.text("-# Anti-nuke chega no próximo módulo.")` por uma nota de hierarquia:
```java
                Panels.text("-# " + Emojis.of(Emojis.WARN, "⚠️") + " O anti-nuke só neutraliza atores **abaixo** do meu cargo. Mantenha meu cargo no topo."),
```
E adicionar uma ActionRow (antes da row de Voltar):
```java
                ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "sectoggle", "antinuke"), "Anti-nuke: " + (nuke ? "on" : "off"))
                                .withEmoji(Emojis.button(Emojis.SHIELD)),
                        Button.primary(ComponentId.of(NS, "nukeedit"), "Editar anti-nuke").withEmoji(Emojis.button(Emojis.EDIT))),
```
- [ ] **Step 2:** `SetupView.antinukeModal` (novo, espelha `antiraidModal`):
```java
    public static Modal antinukeModal(GuildConfig cfg) {
        TextInput max = TextInput.create("max", TextInputStyle.SHORT)
                .setPlaceholder("Ações destrutivas p/ disparar (ex.: 5)").setRequired(false).setMaxLength(3)
                .setValue(String.valueOf(dev.davimf.basebot.modules.base.security.SecurityConfig.antinukeMax(cfg))).build();
        TextInput window = TextInput.create("window", TextInputStyle.SHORT)
                .setPlaceholder("Janela em segundos (ex.: 60)").setRequired(false).setMaxLength(4)
                .setValue(String.valueOf(dev.davimf.basebot.modules.base.security.SecurityConfig.antinukeWindowSeconds(cfg))).build();
        TextInput.Builder wlB = TextInput.create("whitelist", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Isentos: user:<id>, role:<id> (separados por vírgula)").setRequired(false).setMaxLength(500);
        String wl = String.join(", ", dev.davimf.basebot.modules.base.security.SecurityConfig.antinukeWhitelist(cfg));
        if (!wl.isBlank()) {
            wlB.setValue(wl);
        }
        return Modal.create(ComponentId.of(NS, "antinukeform"), "Anti-nuke — limiares")
                .addComponents(Label.of("Ações p/ disparar", max), Label.of("Janela (s)", window),
                        Label.of("Whitelist (user:/role:)", wlB.build()))
                .build();
    }
```
- [ ] **Step 3:** `SetupComponentHandler.onButton`:
  - botão: adicionar `case "nukeedit" -> event.replyModal(SetupView.antinukeModal(config(ctx, guildId))).queue();`
  - `sectoggle` key switch: `case "antinuke" -> dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_ANTINUKE;` (default false — já coberto por `def`).
- [ ] **Step 4:** `SetupComponentHandler.onModal`: após o bloco `antiraidform`:
```java
        if ("antinukeform".equals(id.action())) {
            saveAntinuke(event, ctx);
            return;
        }
```
e o helper:
```java
    private void saveAntinuke(ModalInteractionEvent event, BotContext ctx) {
        String guildId = event.getGuild().getId();
        GuildConfig u = config(ctx, guildId);
        String max = value(event, "max");
        String window = value(event, "window");
        String whitelist = value(event, "whitelist");
        if (max != null && max.matches("\\d+")) {
            u = GuildConfigEdits.withSetting(u, dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_ANTINUKE_MAX, max.trim());
        }
        if (window != null && window.matches("\\d+")) {
            u = GuildConfigEdits.withSetting(u, dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_ANTINUKE_WINDOW_S, window.trim());
        }
        u = GuildConfigEdits.withSetting(u, dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_ANTINUKE_WHITELIST,
                whitelist == null ? "" : whitelist.trim());
        ctx.database().guildConfig().save(u);
        edit(event, SetupView.securityScreen(u));
    }
```
- [ ] **Step 5:** Build → SUCCESS. (Conferir nº de ActionRows ≤ limite no container de Segurança.)

---

### Task 7: Wiring + smoke

**Files:** Modify `BaseModule.java`.

- [ ] **Step 1:** Após o `VerificationListener`:
```java
        // Segurança · Módulo 4: anti-nuke (audit log + neutralização).
        registry.listener(new dev.davimf.basebot.modules.base.security.AntiNukeListener(ctx));
```
- [ ] **Step 2:** `./gradlew build` → BUILD SUCCESSFUL (com testes).
- [ ] **Step 3: Smoke:** num servidor de teste, garantir que o cargo do bot está no topo + tem **Ver Registro de Auditoria**/**Gerenciar Cargos**; `/setup → Segurança` ligar Anti-nuke (max 3, janela 60s p/ testar); com uma conta de staff abaixo do bot, deletar 3 canais rápido → cargos da staff removidos + alerta com ping do dono no modlog. Repetir com um ator acima do bot → alerta de "impotente".

## Self-Review
- Detectar destrutivos + resolver ator c/ retry → Tasks 3,5. Contar por ator → Task 2. Neutralizar + alertar dono → Task 4. Whitelist/dono isentos → Tasks 1,5. Config + nota de hierarquia → Task 6. Wiring → Task 7. ✓
- Tipos: `antinukeMax/antinukeWindowSeconds/isNukeWhitelisted` (Task 1) usados em 5/6; `ActorWindow.record` (Task 2) em 5; `NukeAuditLookup.resolveActor` (Task 3) em 5; `AntiNukeService.neutralize` (Task 4) em 5. Consistentes. ✓
