# Segurança · Módulo 2 — Anti-raid · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (inline) ou subagent-driven-development. Steps em checkbox.

**Goal:** Detectar surto de entradas (X entradas em Y s, reforçado por contas muito novas) e ativar um **lockdown** (sobe o nível de verificação da guilda) + alerta na modlog com botão "Desativar lockdown" que restaura o nível anterior.

**Architecture:** `onGuildMemberJoin` alimenta uma janela deslizante por guilda (pura/testável). Ao estourar o limiar, `AntiRaidService` sobe o nível de verificação (guardando o anterior em `guild_config`) e alerta. Um `SecurityComponentHandler` (namespace `sec`) trata o botão de desativar. Ligável em `/setup → Segurança`. Reusa `SecurityConfig`.

**Tech Stack:** Java 22, JDA 6.4.2, JUnit 5.

## Global Constraints
- JDK 22; Base ≠ facs; mensagens em Components V2 + emojis custom (`Emojis`); config em `guild_config` (`sec:`); sem commits salvo pedido; pacote `modules.base.security`.

---

### Task 1: Estender `SecurityConfig` com chaves de anti-raid (+ testes)

**Files:** Modify `SecurityConfig.java`; Modify `SecurityConfigTest.java`.

**Interfaces (Produces):** `antiraid(cfg)`:bool(false); `raidJoins(cfg)`:int(8,min2); `raidWindowSeconds(cfg)`:int(10,min1); `raidMinAgeDays(cfg)`:int(7,min0); `raidLockLevel(cfg)`:String(default "HIGH"); `raidPrevLevel(cfg)`:String|null. Constantes `KEY_ANTIRAID`, `KEY_RAID_JOINS`, `KEY_RAID_WINDOW_S`, `KEY_RAID_MIN_AGE`, `KEY_RAID_LOCK_LEVEL`, `KEY_RAID_PREV_LEVEL`.

- [ ] **Step 1:** Adicionar ao `SecurityConfigTest` um teste:
```java
    @Test
    void antiRaidDefaults() {
        GuildConfig c = cfg(Map.of(), Map.of());
        assertFalse(SecurityConfig.antiraid(c));
        assertEquals(8, SecurityConfig.raidJoins(c));
        assertEquals(10, SecurityConfig.raidWindowSeconds(c));
        assertEquals(7, SecurityConfig.raidMinAgeDays(c));
        assertEquals("HIGH", SecurityConfig.raidLockLevel(c));
        assertNull(SecurityConfig.raidPrevLevel(c));
    }
```
- [ ] **Step 2:** Rodar → FAIL.
- [ ] **Step 3:** Implementar no `SecurityConfig` (reusar os helpers `intOr`/toggle/setting):
```java
    public static final String KEY_ANTIRAID = "sec:antiraid";
    public static final String KEY_RAID_JOINS = "sec:antiraid-joins";
    public static final String KEY_RAID_WINDOW_S = "sec:antiraid-window-s";
    public static final String KEY_RAID_MIN_AGE = "sec:antiraid-min-age-days";
    public static final String KEY_RAID_LOCK_LEVEL = "sec:antiraid-lock-level";
    public static final String KEY_RAID_PREV_LEVEL = "sec:antiraid-prev-level";

    public static boolean antiraid(GuildConfig cfg) { return cfg.toggle(KEY_ANTIRAID, false); }
    public static int raidJoins(GuildConfig cfg) { return intOr(cfg.setting(KEY_RAID_JOINS), 8, 2); }
    public static int raidWindowSeconds(GuildConfig cfg) { return intOr(cfg.setting(KEY_RAID_WINDOW_S), 10, 1); }
    public static int raidMinAgeDays(GuildConfig cfg) { return intOr(cfg.setting(KEY_RAID_MIN_AGE), 7, 0); }
    public static String raidLockLevel(GuildConfig cfg) {
        String v = cfg.setting(KEY_RAID_LOCK_LEVEL);
        return v == null || v.isBlank() ? "HIGH" : v.trim().toUpperCase(java.util.Locale.ROOT);
    }
    public static String raidPrevLevel(GuildConfig cfg) {
        String v = cfg.setting(KEY_RAID_PREV_LEVEL);
        return v == null || v.isBlank() ? null : v.trim();
    }
```
- [ ] **Step 4:** Rodar → PASS. **Step 5:** (sem commit — working tree).

---

### Task 2: `JoinWindow` (janela deslizante de entradas por guilda) + teste

**Files:** Create `modules/base/security/JoinWindow.java`; Test `JoinWindowTest.java`.

**Interfaces (Produces):** `JoinWindow(long windowMs)`; `int record(String guildId, long nowMs)` — adiciona uma entrada, expira as antigas, retorna a contagem atual na janela.

- [ ] **Step 1:** Teste:
```java
package dev.davimf.basebot.modules.base.security;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class JoinWindowTest {
    @Test void countsWithinWindow() {
        JoinWindow w = new JoinWindow(10_000);
        assertEquals(1, w.record("g", 0));
        assertEquals(2, w.record("g", 1000));
        assertEquals(3, w.record("g", 2000));
    }
    @Test void expiresOld() {
        JoinWindow w = new JoinWindow(10_000);
        w.record("g", 0); w.record("g", 1000);
        assertEquals(1, w.record("g", 20_000)); // as 2 antigas saíram
    }
    @Test void perGuild() {
        JoinWindow w = new JoinWindow(10_000);
        w.record("a", 0);
        assertEquals(1, w.record("b", 0));
    }
}
```
- [ ] **Step 2:** FAIL. **Step 3:** Implementar:
```java
package dev.davimf.basebot.modules.base.security;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/** Conta entradas por guilda numa janela deslizante (memória). */
public final class JoinWindow {
    private final long windowMs;
    private final Map<String, Deque<Long>> joins = new ConcurrentHashMap<>();
    public JoinWindow(long windowMs) { this.windowMs = windowMs; }
    public synchronized int record(String guildId, long nowMs) {
        Deque<Long> q = joins.computeIfAbsent(guildId, k -> new ArrayDeque<>());
        while (!q.isEmpty() && nowMs - q.peekFirst() > windowMs) q.pollFirst();
        q.addLast(nowMs);
        return q.size();
    }
}
```
- [ ] **Step 4:** PASS (3 testes).

---

### Task 3: `AntiRaidService` (lockdown enter/exit)

**Files:** Create `modules/base/security/AntiRaidService.java`.

**Interfaces:** Consome `SecurityConfig`, `ChannelLog`, JDA guild manager. Produces:
- `static void lockdown(BotContext ctx, Guild guild)` — sobe o nível de verificação p/ `raidLockLevel`, guarda o anterior em `KEY_RAID_PREV_LEVEL`, alerta na modlog com botão `sec:raidunlock`. Idempotente (não re-tranca se já há prev-level salvo).
- `static void unlock(BotContext ctx, Guild guild)` — restaura o nível salvo, limpa `KEY_RAID_PREV_LEVEL`, alerta.

- [ ] **Step 1:** Implementar:
```java
package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.moderation.ModerationService;
import dev.davimf.basebot.modules.base.setup.GuildConfigEdits;
import dev.davimf.basebot.util.ChannelLog;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;

public final class AntiRaidService {

    public static final String NS = "sec";

    private AntiRaidService() {}

    public static synchronized void lockdown(BotContext ctx, Guild guild) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        if (SecurityConfig.raidPrevLevel(cfg) != null) {
            return; // já em lockdown
        }
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_SERVER)) {
            ChannelLog.post(ctx, guild.getId(), ModerationService.MODLOG_KEY,
                    "## " + Emojis.of(Emojis.WARN, "⚠️") + " Possível raid detectado\n---\n"
                            + "-# Não consegui subir a verificação (falta **Gerenciar Servidor**).");
            return;
        }
        Guild.VerificationLevel prev = guild.getVerificationLevel();
        Guild.VerificationLevel target = parseLevel(SecurityConfig.raidLockLevel(cfg));
        ctx.database().guildConfig().save(GuildConfigEdits.withSetting(cfg,
                SecurityConfig.KEY_RAID_PREV_LEVEL, prev.name()));
        guild.getManager().setVerificationLevel(target).queue(ok -> {}, err -> {});

        int accent = EmbedColor.resolve(cfg);
        guild.getJDA(); // noop
        net.dv8tion.jda.api.entities.channel.concrete.TextChannel modlog = modlog(ctx, guild);
        String body = "## " + Emojis.of(Emojis.SHIELD, "🛡️") + " Lockdown ativado (anti-raid)\n---\n"
                + "**Verificação** · `" + prev.name() + "` → `" + target.name() + "`\n"
                + "-# Surto de entradas detectado. Clique para desativar quando passar.";
        if (modlog != null) {
            modlog.sendMessageComponents(Panels.container(accent, Panels.text(body),
                            ActionRow.of(Button.danger(ComponentId.of(NS, "raidunlock"), "Desativar lockdown")
                                    .withEmoji(Emojis.button(Emojis.UNLOCK)))))
                    .useComponentsV2().setAllowedMentions(java.util.List.of()).queue(ok -> {}, err -> {});
        }
    }

    public static synchronized void unlock(BotContext ctx, Guild guild) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        String prev = SecurityConfig.raidPrevLevel(cfg);
        if (prev == null) {
            return;
        }
        guild.getManager().setVerificationLevel(parseLevel(prev)).queue(ok -> {}, err -> {});
        ctx.database().guildConfig().save(GuildConfigEdits.withSetting(cfg, SecurityConfig.KEY_RAID_PREV_LEVEL, ""));
        ChannelLog.post(ctx, guild.getId(), ModerationService.MODLOG_KEY,
                "## " + Emojis.of(Emojis.CHECK_YES, "✅") + " Lockdown desativado\n---\n"
                        + "**Verificação restaurada** · `" + prev + "`");
    }

    private static Guild.VerificationLevel parseLevel(String name) {
        try {
            return Guild.VerificationLevel.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Guild.VerificationLevel.HIGH;
        }
    }

    private static net.dv8tion.jda.api.entities.channel.concrete.TextChannel modlog(BotContext ctx, Guild guild) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        String id = cfg.channel(ModerationService.MODLOG_KEY);
        return id == null ? null : guild.getTextChannelById(id);
    }
}
```
> **Verificar:** `GuildConfigEdits.withSetting` aceita valor vazio para "limpar" (usado p/ apagar prev-level). `cfg.channel(key)` existe (usado por ChannelLog). Remover a linha `guild.getJDA(); // noop`.

- [ ] **Step 2:** Build → BUILD SUCCESSFUL.

---

### Task 4: `AntiRaidListener` (detecção no join)

**Files:** Create `modules/base/security/AntiRaidListener.java`.

- [ ] **Step 1:** Implementar:
```java
package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.time.OffsetDateTime;

/** Detecta surto de entradas e dispara o lockdown (anti-raid). */
public final class AntiRaidListener extends ListenerAdapter {

    private final BotContext ctx;
    private final JoinWindow window;
    private volatile long windowStamp = Long.MIN_VALUE;
    private JoinWindow current;

    public AntiRaidListener(BotContext ctx) {
        this.ctx = ctx;
        this.window = new JoinWindow(10_000);
        this.current = window;
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!SecurityConfig.antiraid(cfg)) {
            return;
        }
        ensureWindow(cfg);
        long now = System.currentTimeMillis();
        int count = current.record(event.getGuild().getId(), now);
        boolean youngAccount = event.getUser().getTimeCreated()
                .isAfter(OffsetDateTime.now().minusDays(SecurityConfig.raidMinAgeDays(cfg)));
        // Surto: atingiu o limiar de entradas na janela. Contas novas reforçam (limiar -2 quando jovem).
        int threshold = SecurityConfig.raidJoins(cfg) - (youngAccount ? 2 : 0);
        if (count >= Math.max(2, threshold)) {
            AntiRaidService.lockdown(ctx, event.getGuild());
        }
    }

    private void ensureWindow(GuildConfig cfg) {
        long stamp = SecurityConfig.raidWindowSeconds(cfg);
        if (stamp != windowStamp) {
            current = new JoinWindow(SecurityConfig.raidWindowSeconds(cfg) * 1000L);
            windowStamp = stamp;
        }
    }
}
```
> **Verificar:** simplificar o campo `window` não usado (deixar só `current`). `getUser().getTimeCreated()` retorna OffsetDateTime.

- [ ] **Step 2:** Build → SUCCESS.

---

### Task 5: `SecurityComponentHandler` (botão desativar lockdown)

**Files:** Create `modules/base/security/SecurityComponentHandler.java`.

- [ ] **Step 1:** Implementar:
```java
package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/** Botões de segurança postados fora do /setup (ex.: alerta de lockdown). Namespace "sec". */
public final class SecurityComponentHandler implements ComponentHandler {

    private final BotContext ctx;

    public SecurityComponentHandler(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String namespace() {
        return AntiRaidService.NS; // "sec"
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"raidunlock".equals(id.action()) || event.getGuild() == null) {
            return;
        }
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            Replies.ephemeral(event, ctx, "Apenas quem tem **Gerenciar Servidor** pode desativar o lockdown.");
            return;
        }
        AntiRaidService.unlock(ctx, event.getGuild());
        Replies.reply(event, ctx, "Lockdown desativado.");
    }
}
```
> **Verificar:** o import `Moderation` não é usado — remover. Confirmar a assinatura de `ComponentHandler.onButton` (mesma do `FarmComponentHandler`).

- [ ] **Step 2:** Build → SUCCESS.

---

### Task 6: `/setup → Segurança` — controles de anti-raid

**Files:** Modify `SetupView.securityScreen` + add `antiraidModal`; Modify `SetupComponentHandler`.

- [ ] **Step 1:** Em `securityScreen`, acrescentar ao overview e uma 2ª linha de botões:
```java
        boolean raid = dev.davimf.basebot.modules.base.security.SecurityConfig.antiraid(cfg);
        // ...adicionar ao overview:
        //  + "\n" + Emojis.of(Emojis.MEMBERS,"👥") + " **Anti-raid** · " + (raid?"ligado":"desligado")
        //         + " · `" + raidJoins + "/" + raidWindowSeconds + "s` · lock `" + raidLockLevel + "`"
        // ...e uma ActionRow:
        //  Button.secondary(ComponentId.of(NS,"sectoggle","antiraid"), "Anti-raid: " + (raid?"on":"off")).withEmoji(Emojis.button(Emojis.SHIELD)),
        //  Button.primary(ComponentId.of(NS,"raidedit"), "Editar anti-raid").withEmoji(Emojis.button(Emojis.EDIT))
```
E `antiraidModal(cfg)` com inputs `joins`, `window`, `minage`, `locklevel` (prefill com `setValue` dos valores atuais — nunca vazios).

- [ ] **Step 2:** No `SetupComponentHandler`:
  - no `sectoggle` switch, adicionar `case "antiraid" -> SecurityConfig.KEY_ANTIRAID;` (default false).
  - novo botão `case "raidedit" -> event.replyModal(SetupView.antiraidModal(config(ctx,guildId))).queue();`
  - no `onModal`, `if ("antiraidform".equals(id.action())) { saveAntiraid(event, ctx); return; }` + helper `saveAntiraid` (grava joins/window/minage se numéricos, locklevel se em {NONE,LOW,MEDIUM,HIGH,VERY_HIGH}; `edit(event, securityScreen)`).

- [ ] **Step 3:** Build completo → SUCCESS.

---

### Task 7: Wiring no `BaseModule` + smoke

**Files:** Modify `BaseModule.java`.

- [ ] **Step 1:** Registrar:
```java
        registry.listener(new dev.davimf.basebot.modules.base.security.AntiRaidListener(ctx));
        registry.component(new dev.davimf.basebot.modules.base.security.SecurityComponentHandler(ctx));
```
- [ ] **Step 2:** `./gradlew build` → BUILD SUCCESSFUL.
- [ ] **Step 3: Smoke** (manual): `/setup → Segurança` ligar Anti-raid; simular várias entradas rápidas (ou baixar o limiar p/ 2 e entrar com um alt) → verificar nível de verificação subir + alerta na modlog com botão; clicar "Desativar lockdown" → nível restaurado.

## Self-Review
- Detecção (janela + idade) → Tasks 2,4. Lockdown/unlock + alerta+botão → Tasks 3,5. Config+UI → Tasks 1,6. Wiring → Task 7. ✓
- Gotcha #4 (VERY_HIGH/telefone) → nível configurável, default HIGH (Task 1/6). ✓
