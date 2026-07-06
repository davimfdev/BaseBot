# Segurança · Módulo 1 — AutoMod nativo · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** O bot configura/sincroniza as regras de AutoMod nativo da guilda (spam, mention-spam, links/convites, palavrões) e, quando o Discord bloqueia uma mensagem, registra um warn (que escalona via Infrações). Tudo ligável em `/setup → Segurança`.

**Architecture:** Native-first. O bot cria as regras via `Guild.createAutoModRule(...)`; o Discord faz a blocagem; o bot só reage ao `onAutoModExecution`. Mapeamento config→regras e contagem de violações são lógica **pura/testável**; a parte JDA (sync de regras + listener) é validada por build + smoke test manual. Reusa `ModerationService.warn(...)` (motor de warn/escalonamento já existente).

**Tech Stack:** Java 22, JDA 6.4.2 (AutoMod API), SQLite/Postgres (config em `guild_config`), JUnit 5, Gradle.

## Global Constraints
- **JDK 22** para compilar/rodar (`./gradlew build`); toolchain pinado, não rodar em JDK 26.
- **Base ≠ facs:** este módulo é da **Base** e NÃO pode depender de facs. Gates de permissão usam permissões do Discord.
- **Toda mensagem do bot é Components V2** via `ChannelLog`/`Panels`/`Replies`; **emojis custom** via `Emojis.of(name, fallback)` / `Emojis.button(name)` — nunca unicode cru em mensagem do bot; botão usa `.withEmoji(...)`, nunca emoji no label.
- **Config** vive em `guild_config` (`settings`/`toggles`), prefixo `sec:`, lida via `SecurityConfig` (parse puro). Padrão a espelhar: `modules/base/moderation/ModerationConfig.java`.
- **Punição** só via `ModerationService.warn(guild, member, "system", reason)` — não criar lógica de punição nova.
- Pacote raiz `dev.davimf.basebot`. Novo pacote do módulo: `modules.base.security`.
- **JDA incerto** (símbolos de AutoMod/intents): confirmar com `javap -cp <jar do JDA em ~/.gradle/caches>` antes de assumir — não adivinhar imports.

---

### Task 1: `SecurityConfig` (leitura/escrita da config de segurança)

Lógica pura espelhando `ModerationConfig`. Cobre as chaves do AutoMod + isenções compartilhadas (os outros módulos estendem depois).

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/security/SecurityConfig.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/security/SecurityConfigTest.java`

**Interfaces:**
- Produces:
  - `static boolean automod(GuildConfig cfg)` (default false), `automodWarn(cfg)` (default true).
  - `static int warnPer(cfg)` (default 1), `windowSeconds(cfg)` (default 0), `mentionLimit(cfg)` (default 5).
  - `static boolean blockInvites(cfg)` (default true).
  - `static java.util.List<String> keywords(cfg)` (CSV de `sec:automod-keywords`).
  - `static java.util.Set<String> exemptRoleIds(cfg)`, `exemptChannelIds(cfg)` (CSV).
  - `static boolean isExempt(GuildConfig cfg, java.util.Collection<String> memberRoleIds, String channelId)`.
  - Constantes de chave públicas (`KEY_AUTOMOD`, `KEY_AUTOMOD_WARN`, `KEY_WARN_PER`, `KEY_WINDOW_S`, `KEY_MENTION_LIMIT`, `KEY_BLOCK_INVITES`, `KEY_KEYWORDS`, `KEY_EXEMPT_ROLES`, `KEY_EXEMPT_CHANNELS`).

- [ ] **Step 1: Escrever o teste (falhando)**

```java
package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {

    private static GuildConfig cfg(Map<String, String> settings, Map<String, Boolean> toggles) {
        // GuildConfig é um record/model do projeto; usar o mesmo construtor que os outros testes de config.
        // Ver ModerationConfigTest para a forma exata de montar um GuildConfig em teste.
        return TestConfigs.of(settings, toggles);
    }

    @Test
    void defaults() {
        GuildConfig c = cfg(Map.of(), Map.of());
        assertFalse(SecurityConfig.automod(c));
        assertTrue(SecurityConfig.automodWarn(c));
        assertEquals(1, SecurityConfig.warnPer(c));
        assertEquals(5, SecurityConfig.mentionLimit(c));
        assertTrue(SecurityConfig.blockInvites(c));
        assertTrue(SecurityConfig.keywords(c).isEmpty());
    }

    @Test
    void parsesNumbersAndCsv() {
        GuildConfig c = cfg(Map.of(
                SecurityConfig.KEY_MENTION_LIMIT, "8",
                SecurityConfig.KEY_KEYWORDS, "scam, free nitro ,scam",
                SecurityConfig.KEY_EXEMPT_ROLES, "111,222"
        ), Map.of(SecurityConfig.KEY_AUTOMOD, true));
        assertTrue(SecurityConfig.automod(c));
        assertEquals(8, SecurityConfig.mentionLimit(c));
        assertEquals(List.of("scam", "free nitro"), SecurityConfig.keywords(c)); // trim + dedup
        assertEquals(Set.of("111", "222"), SecurityConfig.exemptRoleIds(c));
    }

    @Test
    void exemptByRoleOrChannel() {
        GuildConfig c = cfg(Map.of(
                SecurityConfig.KEY_EXEMPT_ROLES, "999",
                SecurityConfig.KEY_EXEMPT_CHANNELS, "777"
        ), Map.of());
        assertTrue(SecurityConfig.isExempt(c, List.of("999"), "100"));   // cargo isento
        assertTrue(SecurityConfig.isExempt(c, List.of("1"), "777"));     // canal isento
        assertFalse(SecurityConfig.isExempt(c, List.of("1"), "100"));    // nenhum
    }
}
```

> **Verificar:** a forma de montar um `GuildConfig` em teste. Abrir `ModerationConfigTest.java` e copiar exatamente como ele constrói o `GuildConfig` (settings + toggles). Se houver um helper, reusar; senão, montar inline e **remover o `TestConfigs.of(...)` placeholder acima**, substituindo pela construção real.

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.security.SecurityConfigTest"`
Expected: FAIL (classe ausente).

- [ ] **Step 3: Implementar `SecurityConfig`**

```java
package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.database.model.GuildConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Lê/escreve a config de segurança a partir do GuildConfig (prefixo {@code sec:}). Puro e testado.
 *  Espelha o estilo de {@code ModerationConfig}. */
public final class SecurityConfig {

    public static final String KEY_AUTOMOD = "sec:automod";
    public static final String KEY_AUTOMOD_WARN = "sec:automod-warn";
    public static final String KEY_WARN_PER = "sec:automod-warn-per";
    public static final String KEY_WINDOW_S = "sec:automod-window-s";
    public static final String KEY_MENTION_LIMIT = "sec:automod-mention-limit";
    public static final String KEY_BLOCK_INVITES = "sec:automod-block-invites";
    public static final String KEY_KEYWORDS = "sec:automod-keywords";
    public static final String KEY_EXEMPT_ROLES = "sec:exempt-roles";
    public static final String KEY_EXEMPT_CHANNELS = "sec:exempt-channels";

    private SecurityConfig() {}

    public static boolean automod(GuildConfig cfg) { return cfg.toggle(KEY_AUTOMOD, false); }
    public static boolean automodWarn(GuildConfig cfg) { return cfg.toggle(KEY_AUTOMOD_WARN, true); }
    public static boolean blockInvites(GuildConfig cfg) { return cfg.toggle(KEY_BLOCK_INVITES, true); }

    public static int warnPer(GuildConfig cfg) { return intOr(cfg.setting(KEY_WARN_PER), 1, 1); }
    public static int windowSeconds(GuildConfig cfg) { return intOr(cfg.setting(KEY_WINDOW_S), 0, 0); }
    public static int mentionLimit(GuildConfig cfg) { return intOr(cfg.setting(KEY_MENTION_LIMIT), 5, 1); }

    public static List<String> keywords(GuildConfig cfg) { return csv(cfg.setting(KEY_KEYWORDS)); }
    public static Set<String> exemptRoleIds(GuildConfig cfg) { return new LinkedHashSet<>(csv(cfg.setting(KEY_EXEMPT_ROLES))); }
    public static Set<String> exemptChannelIds(GuildConfig cfg) { return new LinkedHashSet<>(csv(cfg.setting(KEY_EXEMPT_CHANNELS))); }

    public static boolean isExempt(GuildConfig cfg, Collection<String> memberRoleIds, String channelId) {
        Set<String> er = exemptRoleIds(cfg);
        if (channelId != null && exemptChannelIds(cfg).contains(channelId)) {
            return true;
        }
        for (String r : memberRoleIds) {
            if (er.contains(r)) {
                return true;
            }
        }
        return false;
    }

    private static int intOr(String raw, int def, int min) {
        if (raw == null || raw.isBlank()) return def;
        try { return Math.max(min, Integer.parseInt(raw.trim())); }
        catch (NumberFormatException e) { return def; }
    }

    private static List<String> csv(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        for (String s : Arrays.stream(raw.split(",")).map(String::trim).toList()) {
            if (!s.isEmpty() && out.stream().noneMatch(i -> i.equalsIgnoreCase(s))) out.add(s);
        }
        return out;
    }
}
```

> **Verificar:** os nomes exatos de `GuildConfig.toggle(key, default)` e `GuildConfig.setting(key)` (já usados por `ModerationConfig`). Se diferirem, ajustar.

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.security.SecurityConfigTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/security/SecurityConfig.java src/test/java/dev/davimf/basebot/modules/base/security/SecurityConfigTest.java
git commit -m "feat(security): SecurityConfig (automod + exemptions, pure/tested)"
```

---

### Task 2: `ViolationWindow` (janela deslizante de violações por usuário)

Lógica pura: conta violações por usuário numa janela e diz quando atingiu o limite de warn.

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/security/ViolationWindow.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/security/ViolationWindowTest.java`

**Interfaces:**
- Produces:
  - `ViolationWindow(int warnPer, long windowMs)` — quando `windowMs <= 0`, modo imediato (cada `warnPer`-ésima violação dispara).
  - `boolean record(String userId, long nowMs)` — registra uma violação; retorna `true` quando deve aplicar um warn (atingiu `warnPer` dentro da janela). Reseta o contador do usuário ao disparar.

- [ ] **Step 1: Escrever o teste (falhando)**

```java
package dev.davimf.basebot.modules.base.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ViolationWindowTest {

    @Test
    void immediateWhenWarnPerIsOne() {
        ViolationWindow w = new ViolationWindow(1, 0);
        assertTrue(w.record("u1", 1000));   // 1 violação = warn
        assertTrue(w.record("u1", 1001));
    }

    @Test
    void accumulatesWithinWindow() {
        ViolationWindow w = new ViolationWindow(3, 10_000); // 3 em 10s
        assertFalse(w.record("u1", 0));
        assertFalse(w.record("u1", 1000));
        assertTrue(w.record("u1", 2000));   // 3ª dentro da janela -> warn
        assertFalse(w.record("u1", 3000));  // contador resetou
    }

    @Test
    void oldViolationsExpire() {
        ViolationWindow w = new ViolationWindow(3, 10_000);
        assertFalse(w.record("u1", 0));
        assertFalse(w.record("u1", 1000));
        assertFalse(w.record("u1", 20_000)); // as 2 primeiras já saíram da janela -> só 1 conta
    }

    @Test
    void perUserIsolation() {
        ViolationWindow w = new ViolationWindow(2, 10_000);
        assertFalse(w.record("a", 0));
        assertFalse(w.record("b", 0));
        assertTrue(w.record("a", 1000));     // a chegou a 2; b ainda em 1
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.security.ViolationWindowTest"`
Expected: FAIL.

- [ ] **Step 3: Implementar `ViolationWindow`**

```java
package dev.davimf.basebot.modules.base.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Conta violações por usuário numa janela deslizante; dispara quando atinge {@code warnPer}.
 *  Em memória (janela curta — perder no restart é aceitável). */
public final class ViolationWindow {

    private final int warnPer;
    private final long windowMs;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public ViolationWindow(int warnPer, long windowMs) {
        this.warnPer = Math.max(1, warnPer);
        this.windowMs = windowMs;
    }

    public synchronized boolean record(String userId, long nowMs) {
        Deque<Long> q = hits.computeIfAbsent(userId, k -> new ArrayDeque<>());
        if (windowMs > 0) {
            while (!q.isEmpty() && nowMs - q.peekFirst() > windowMs) {
                q.pollFirst();
            }
        }
        q.addLast(nowMs);
        if (q.size() >= warnPer) {
            q.clear();
            return true;
        }
        return false;
    }
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.security.ViolationWindowTest"`
Expected: PASS (4 testes).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/security/ViolationWindow.java src/test/java/dev/davimf/basebot/modules/base/security/ViolationWindowTest.java
git commit -m "feat(security): ViolationWindow (sliding per-user counter, tested)"
```

---

### Task 3: `AutoModManager` (sincroniza as regras nativas da guilda)

JDA. Cria/atualiza/remove as regras de AutoMod para casar com a config. Sem teste de unidade (JDA) — validado por build + smoke.

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/security/AutoModManager.java`

**Interfaces:**
- Consumes: `SecurityConfig`, JDA `Guild.createAutoModRule/retrieveAutoModRules/AutoModRule.delete()`.
- Produces: `static void sync(Guild guild, GuildConfig cfg)` — BLOQUEANTE (usa `.complete()`); chamar OFF-thread (ex.: `ctx.scheduler().executor()`).

- [ ] **Step 1: Implementar `AutoModManager`**

Prefixo de nome `BaseBot · ` identifica as regras geridas pelo bot. A cada sync: recolhe as regras existentes com esse prefixo, calcula as desejadas pela config, cria/atualiza/deleta.

```java
package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.automod.AutoModResponse;
import net.dv8tion.jda.api.entities.automod.AutoModRule;
import net.dv8tion.jda.api.entities.automod.build.AutoModRuleData;
import net.dv8tion.jda.api.entities.automod.build.TriggerConfig;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Sincroniza as regras de AutoMod nativo da guilda com a {@link SecurityConfig}. As regras
 *  geridas pelo bot levam o prefixo {@link #PREFIX} para não mexer nas regras manuais do dono. */
public final class AutoModManager {

    public static final String PREFIX = "BaseBot · ";

    private AutoModManager() {}

    /** BLOQUEANTE — chamar fora da thread do JDA. */
    public static void sync(Guild guild, GuildConfig cfg) {
        if (!guild.getSelfMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            return; // sem permissão para gerir AutoMod — degrada em silêncio
        }
        List<Role> exemptRoles = SecurityConfig.exemptRoleIds(cfg).stream()
                .map(guild::getRoleById).filter(java.util.Objects::nonNull).collect(Collectors.toList());
        List<GuildChannel> exemptCh = SecurityConfig.exemptChannelIds(cfg).stream()
                .map(guild::getGuildChannelById).filter(java.util.Objects::nonNull).collect(Collectors.toList());

        // Regras desejadas (nome -> data). null/ausente = não criar.
        List<AutoModRuleData> desired = new ArrayList<>();
        if (SecurityConfig.automod(cfg)) {
            desired.add(decorate(AutoModRuleData.onMessage(PREFIX + "Spam", TriggerConfig.antiSpam()), exemptRoles, exemptCh));
            desired.add(decorate(AutoModRuleData.onMessage(PREFIX + "Mention Spam",
                    TriggerConfig.mentionSpam(SecurityConfig.mentionLimit(cfg))), exemptRoles, exemptCh));
            desired.add(decorate(AutoModRuleData.onMessage(PREFIX + "Palavroes",
                    TriggerConfig.presetKeywordFilter(
                            AutoModRule.KeywordPreset.PROFANITY,
                            AutoModRule.KeywordPreset.SEXUAL_CONTENT,
                            AutoModRule.KeywordPreset.SLURS)), exemptRoles, exemptCh));
            List<String> patterns = new ArrayList<>();
            if (SecurityConfig.blockInvites(cfg)) {
                patterns.add("discord.gg/*");
                patterns.add("discord.com/invite/*");
                patterns.add("discordapp.com/invite/*");
            }
            List<String> kw = SecurityConfig.keywords(cfg);
            if (!patterns.isEmpty() || !kw.isEmpty()) {
                TriggerConfig trig = kw.isEmpty()
                        ? TriggerConfig.patternFilter(patterns)
                        : TriggerConfig.keywordFilter(kw); // keywords custom; padrões de convite via patternFilter
                desired.add(decorate(AutoModRuleData.onMessage(PREFIX + "Links/Keywords", trig), exemptRoles, exemptCh));
            }
        }

        // Reconciliar: deletar as nossas que não são mais desejadas; criar/recriar as desejadas.
        List<AutoModRule> existing;
        try {
            existing = guild.retrieveAutoModRules().complete().stream()
                    .filter(r -> r.getName().startsWith(PREFIX)).collect(Collectors.toList());
        } catch (RuntimeException e) {
            return;
        }
        java.util.Set<String> desiredNames = desired.stream()
                .map(d -> nameOf(d)).collect(Collectors.toSet());
        for (AutoModRule r : existing) {
            if (!desiredNames.contains(r.getName())) {
                r.delete().queue(ok -> {}, err -> {});
            }
        }
        java.util.Set<String> existingNames = existing.stream().map(AutoModRule::getName).collect(Collectors.toSet());
        for (AutoModRuleData d : desired) {
            if (!existingNames.contains(nameOf(d))) {
                guild.createAutoModRule(d).queue(ok -> {}, err -> {});
            }
            // Atualização de regra existente (mudança de limiar/keywords) fica como melhoria futura:
            // deletar+recriar quando a config muda é suficiente para a v1 (ver Task 6 / smoke).
        }
    }

    private static AutoModRuleData decorate(AutoModRuleData data, List<Role> roles, List<GuildChannel> chans) {
        data.setEnabled(true).putResponses(AutoModResponse.blockMessage());
        if (!roles.isEmpty()) data.setExemptRoles(roles);
        if (!chans.isEmpty()) data.setExemptChannels(chans);
        return data;
    }

    /** O nome com que a regra será criada (para reconciliar por nome). */
    private static String nameOf(AutoModRuleData d) {
        // AutoModRuleData não expõe getName() público em todas as versões; carregar o nome à parte
        // se necessário. Ver "Verificar" abaixo.
        return AutoModRuleNames.of(d);
    }
}
```

> **Verificar (importante):**
> - `AutoModRuleData` tem getter de nome? Se **não** tiver `getName()`, **não** use `nameOf(d)` lendo do data: em vez disso, modele as regras desejadas como uma lista de `record Desired(String name, AutoModRuleData data)` e reconcilie pelo `name` do record (remova a classe `AutoModRuleNames` placeholder). Confirme via `javap net.dv8tion.jda.api.entities.automod.build.AutoModRuleData`.
> - `AutoModRule.KeywordPreset` — confirmar os nomes exatos dos presets (`PROFANITY`, `SEXUAL_CONTENT`, `SLURS`) via `javap 'net.dv8tion.jda.api.entities.automod.AutoModRule$KeywordPreset'`.
> - **Limites do Discord (gotcha #3 do spec):** `keywordFilter`/`patternFilter` têm limite de quantidade/complexidade. **Capar** a lista de keywords (ex.: máx ~30 e cada uma curta) e validar antes; dividir em mais de uma regra se passar do limite. Se `createAutoModRule` retornar erro, logar e seguir.

- [ ] **Step 2: Refatorar para o record `Desired` (resolve o getName)**

Substituir `List<AutoModRuleData> desired` por `List<Desired>` com `record Desired(String name, AutoModRuleData data)`, usando o mesmo `name` no `onMessage(name, ...)`. Reconciliar `existing` (por `getName()`) vs `desired` (por `name`). Isso elimina o placeholder `AutoModRuleNames`.

- [ ] **Step 3: Build**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/security/AutoModManager.java
git commit -m "feat(security): AutoModManager syncs native AutoMod rules from config"
```

---

### Task 4: `AutoModExecutionListener` (bloqueio → warn)

JDA. Reage ao bloqueio nativo: checa isenção, conta na janela, aplica warn + modlog.

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/security/AutoModExecutionListener.java`

**Interfaces:**
- Consumes: `SecurityConfig`, `ViolationWindow`, `ModerationService.warn(...)`, `ChannelLog`, `BotContext`.

- [ ] **Step 1: Criar o listener**

```java
package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.moderation.ModerationService;
import dev.davimf.basebot.util.ChannelLog;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.automod.AutoModExecutionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;
import java.util.stream.Collectors;

/** Quando o AutoMod nativo bloqueia uma mensagem: conta a violação e, ao atingir o limite,
 *  aplica um warn (que escalona via Infrações). */
public final class AutoModExecutionListener extends ListenerAdapter {

    private final BotContext ctx;
    private final ModerationService moderation;
    private volatile ViolationWindow window = new ViolationWindow(1, 0);
    private volatile long windowConfigStamp = -1;

    public AutoModExecutionListener(BotContext ctx, ModerationService moderation) {
        this.ctx = ctx;
        this.moderation = moderation;
    }

    @Override
    public void onAutoModExecution(AutoModExecutionEvent event) {
        Guild guild = event.getGuild();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        if (!SecurityConfig.automod(cfg) || !SecurityConfig.automodWarn(cfg)) {
            return;
        }
        String userId = event.getUserId();
        String channelId = event.getChannel() == null ? null : event.getChannel().getId();

        guild.retrieveMemberById(userId).queue(member -> {
            List<String> roleIds = member.getRoles().stream()
                    .map(net.dv8tion.jda.api.entities.Role::getId).collect(Collectors.toList());
            if (SecurityConfig.isExempt(cfg, roleIds, channelId)) {
                return;
            }
            ensureWindow(cfg);
            boolean warnNow = window.record(userId, System.currentTimeMillis());
            String matched = event.getMatchedKeyword() != null ? event.getMatchedKeyword()
                    : (event.getMatchedContent() != null ? event.getMatchedContent() : "—");
            ChannelLog.post(ctx, guild.getId(), ModerationService.MODLOG_KEY,
                    "## " + Emojis.of(Emojis.SHIELD, "🛡️") + " AutoMod bloqueou\n---\n"
                            + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + member.getAsMention()
                            + "\n**Tipo** · `" + event.getTriggerType() + "`"
                            + "\n**Casou** · `" + trim(matched) + "`"
                            + (warnNow ? "\n---\n" + Emojis.of(Emojis.WARN, "⚠️") + " **Warn aplicado** (limite atingido)" : ""));
            if (warnNow) {
                moderation.warn(guild, member, "system", "AutoMod: " + event.getTriggerType());
            }
        }, err -> { });
    }

    private void ensureWindow(GuildConfig cfg) {
        long stamp = (long) SecurityConfig.warnPer(cfg) * 1_000_000L + SecurityConfig.windowSeconds(cfg);
        if (stamp != windowConfigStamp) {
            window = new ViolationWindow(SecurityConfig.warnPer(cfg), SecurityConfig.windowSeconds(cfg) * 1000L);
            windowConfigStamp = stamp;
        }
    }

    private static String trim(String s) {
        return s.length() > 100 ? s.substring(0, 100) + "…" : s;
    }
}
```

> **Verificar:** o intent necessário para receber `AutoModExecutionEvent` (provável `GatewayIntent.AUTO_MODERATION_EXECUTION`). Rodar `javap net.dv8tion.jda.api.GatewayIntent | grep -i moder`. Se existir, habilitar na Task 5. Se o evento não chegar sem um intent, o smoke test (Task 6) vai pegar.

- [ ] **Step 2: Build + Commit**

Run: `./gradlew compileJava` → BUILD SUCCESSFUL.
```bash
git add src/main/java/dev/davimf/basebot/modules/base/security/AutoModExecutionListener.java
git commit -m "feat(security): AutoModExecutionListener (block -> warn via Infractions)"
```

---

### Task 5: `/setup → Segurança` (tela + nav + handlers de AutoMod)

UI no setup. Espelha `SetupView.moderation` + os toggles/modal. Validado por build + smoke.

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupView.java` (nova `securityScreen` + `securityModal` + entrada no `moduleNav` e no select do `hub`)
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java` (nav "seguranca", toggles `sectoggle`, modal `securityform`)

**Interfaces:**
- Consumes: `SecurityConfig`, `GuildConfigEdits.withToggle/withSetting`.

- [ ] **Step 1: Tela + modal em `SetupView`**

Adicionar (perto de `moderation(...)`):
```java
    public static Container securityScreen(GuildConfig cfg) {
        int accent = EmbedColor.resolve(cfg);
        boolean on = dev.davimf.basebot.modules.base.security.SecurityConfig.automod(cfg);
        boolean warn = dev.davimf.basebot.modules.base.security.SecurityConfig.automodWarn(cfg);
        boolean inv = dev.davimf.basebot.modules.base.security.SecurityConfig.blockInvites(cfg);
        String overview = Emojis.of(Emojis.SHIELD, "🛡️") + " **AutoMod** · " + (on ? "ligado" : "desligado") + "\n"
                + Emojis.of(Emojis.WARN, "⚠️") + " **Warn na violação** · " + (warn ? "sim" : "não")
                + " · `" + dev.davimf.basebot.modules.base.security.SecurityConfig.warnPer(cfg) + "/violação`\n"
                + Emojis.of(Emojis.LINK, "🔗") + " **Bloquear convites** · " + (inv ? "sim" : "não") + "\n"
                + Emojis.of(Emojis.MEMBERS, "👥") + " **Limite de menções** · `" + dev.davimf.basebot.modules.base.security.SecurityConfig.mentionLimit(cfg) + "`";
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.SHIELD, "🛡️") + " Segurança"),
                Panels.divider(),
                Panels.text(overview),
                Panels.text("-# Anti-raid, verificação e anti-nuke chegam nos próximos módulos."),
                Panels.divider(),
                ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "sectoggle", "automod"), "AutoMod: " + (on ? "on" : "off")).withEmoji(Emojis.button(Emojis.SHIELD)),
                        Button.secondary(ComponentId.of(NS, "sectoggle", "warn"), "Warn: " + (warn ? "on" : "off")).withEmoji(Emojis.button(Emojis.WARN)),
                        Button.secondary(ComponentId.of(NS, "sectoggle", "invites"), "Convites: " + (inv ? "block" : "off")).withEmoji(Emojis.button(Emojis.LINK))),
                ActionRow.of(
                        Button.primary(ComponentId.of(NS, "secedit"), "Editar limites/keywords").withEmoji(Emojis.button(Emojis.EDIT)),
                        Button.secondary(ComponentId.of(NS, "nav", "hub"), "◀ Voltar")),
                moduleNav("seguranca"));
    }

    public static Modal securityModal(GuildConfig cfg) {
        var sc = dev.davimf.basebot.modules.base.security.SecurityConfig.class; // só para referência mental
        TextInput mention = TextInput.create("mention", TextInputStyle.SHORT)
                .setPlaceholder("Limite de menções por mensagem (ex.: 5)").setRequired(false).setMaxLength(3)
                .setValue(String.valueOf(dev.davimf.basebot.modules.base.security.SecurityConfig.mentionLimit(cfg))).build();
        TextInput warnPer = TextInput.create("warnper", TextInputStyle.SHORT)
                .setPlaceholder("Violações por warn (ex.: 1)").setRequired(false).setMaxLength(3)
                .setValue(String.valueOf(dev.davimf.basebot.modules.base.security.SecurityConfig.warnPer(cfg))).build();
        TextInput kw = TextInput.create("keywords", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Palavras/padrões bloqueados, separados por vírgula").setRequired(false).setMaxLength(500)
                .build();
        return Modal.create(ComponentId.of(NS, "securityform"), "AutoMod — limites e keywords")
                .addComponents(Label.of("Limite de menções", mention), Label.of("Violações por warn", warnPer),
                        Label.of("Keywords (CSV)", kw))
                .build();
    }
```

> **Verificar:** remover a linha placeholder `var sc = ...SecurityConfig.class;` (é só ilustrativa). `setValue` rejeita string vazia — `mentionLimit`/`warnPer` nunca são vazios (têm default numérico), então ok; o campo `keywords` não usa `setValue` (evita o bug que já corrigimos em `moderationRulesModal`).

- [ ] **Step 2: Entradas de navegação**

Em `SetupView.hub(...)` adicionar no `StringSelectMenu` (após "Moderação"):
```java
                .addOption("Segurança", "seguranca", "AutoMod, anti-raid, verificação e anti-nuke")
```
Em `SetupView.moduleNav(...)` adicionar (após "Moderação"):
```java
        addNav(menu, current, "Segurança", "seguranca");
```

- [ ] **Step 3: Handlers em `SetupComponentHandler`**

No `onButton` (`switch (id.action())`), adicionar:
```java
            case "secedit" -> event.replyModal(SetupView.securityModal(config(ctx, guildId))).queue();
            case "sectoggle" -> {
                GuildConfig cfg = config(ctx, guildId);
                String key = switch (id.arg(0)) {
                    case "automod" -> dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_AUTOMOD;
                    case "warn" -> dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_AUTOMOD_WARN;
                    default -> dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_BLOCK_INVITES;
                };
                boolean def = !"warn".equals(id.arg(0)) ? "warn".equals(id.arg(0)) : true; // automod/invites default false/true
                GuildConfig updated = GuildConfigEdits.withToggle(cfg, key, !cfg.toggle(key, "warn".equals(id.arg(0)) || "invites".equals(id.arg(0))));
                ctx.database().guildConfig().save(updated);
                ctx.scheduler().executor().execute(() ->
                        dev.davimf.basebot.modules.base.security.AutoModManager.sync(event.getGuild(), updated));
                edit(event, SetupView.securityScreen(updated));
            }
```
> **Verificar/simplificar:** a expressão do default acima está confusa de propósito para você revisar — substitua por algo claro: cada toggle tem seu default (`automod`=false, `warn`=true, `invites`=true). Calcule `boolean current = cfg.toggle(key, defaultFor(arg)); save(withToggle(cfg, key, !current));` com um helper `defaultFor`. Depois de qualquer toggle do AutoMod, chamar `AutoModManager.sync(...)` off-thread.

No `nav` switch (botão): `case "seguranca" -> SetupView.securityScreen(config(ctx, guildId));`
No `section` select (string select): `case "seguranca" -> SetupView.securityScreen(cfg);`

No `onModal`, adicionar antes do fim:
```java
        if ("securityform".equals(id.action())) {
            saveSecurity(event, ctx);
            return;
        }
```
E o helper:
```java
    private void saveSecurity(ModalInteractionEvent event, BotContext ctx) {
        String guildId = event.getGuild().getId();
        GuildConfig cfg = config(ctx, guildId);
        GuildConfig u = cfg;
        var SC = dev.davimf.basebot.modules.base.security.SecurityConfig.class;
        String mention = value(event, "mention");
        String warnper = value(event, "warnper");
        String kw = value(event, "keywords");
        if (mention != null && mention.matches("\\d+")) {
            u = GuildConfigEdits.withSetting(u, dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_MENTION_LIMIT, mention.trim());
        }
        if (warnper != null && warnper.matches("\\d+")) {
            u = GuildConfigEdits.withSetting(u, dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_WARN_PER, warnper.trim());
        }
        u = GuildConfigEdits.withSetting(u, dev.davimf.basebot.modules.base.security.SecurityConfig.KEY_KEYWORDS,
                kw == null ? "" : kw.trim());
        ctx.database().guildConfig().save(u);
        GuildConfig finalU = u;
        ctx.scheduler().executor().execute(() ->
                dev.davimf.basebot.modules.base.security.AutoModManager.sync(event.getGuild(), finalU));
        edit(event, SetupView.securityScreen(u));
    }
```
> **Verificar:** remover a linha placeholder `var SC = ...class;`. Confirmar que `edit(event, screen)` (helper que faz `editComponents`) aceita o `ModalInteractionEvent` (já usado pelos outros saves do setup). Importar `dev.davimf.basebot.modules.base.security.SecurityConfig`/`AutoModManager` no topo do arquivo (ou usar FQN como acima).

- [ ] **Step 4: Build completo**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (compila + testes das Tasks 1–2 passam).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/setup/SetupView.java src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java
git commit -m "feat(security): /setup → Segurança screen (AutoMod toggles + limits)"
```

---

### Task 6: Intents + wiring no `BaseModule` + smoke test

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/BotApplication.java` (intents de AutoMod)
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java` (registrar listener + sync no `onReady`)

- [ ] **Step 1: Habilitar os intents de AutoMod**

Confirmar os nomes via `javap net.dv8tion.jda.api.GatewayIntent | grep -i moder` e adicionar ao bloco `.enableIntents(...)` em `BotApplication` (provavelmente `AUTO_MODERATION_CONFIGURATION` e `AUTO_MODERATION_EXECUTION`).

```java
                        GatewayIntent.AUTO_MODERATION_CONFIGURATION,
                        GatewayIntent.AUTO_MODERATION_EXECUTION,
```
> **Verificar:** se esses nomes não existirem nessa versão do JDA, ajustar para os corretos (o `javap` mostra). Se não houver intent dedicado, os eventos podem vir por padrão — o smoke (Step 4) confirma.

- [ ] **Step 2: Registrar o listener + sync no `BaseModule`**

`BaseModule` já tem `moderation` (ModerationService) no `register(...)` e os listeners de log. Adicionar:
```java
        registry.listener(new dev.davimf.basebot.modules.base.security.AutoModExecutionListener(ctx, moderation));
```
> **Verificar:** o nome da variável do `ModerationService` em `BaseModule` (provável `moderation`). Se for instanciado depois do bloco de listeners, mover a criação para antes.

No `onReady(BotContext ctx)` de `BaseModule`, sincronizar as regras de todas as guildas:
```java
        ctx.scheduler().executor().execute(() -> {
            if (ctx.jda() == null) return;
            for (net.dv8tion.jda.api.entities.Guild g : ctx.jda().getGuilds()) {
                dev.davimf.basebot.modules.base.security.AutoModManager.sync(g,
                        ctx.database().guildConfig().findOrEmpty(g.getId()));
            }
        });
```

- [ ] **Step 3: Build completo**

Run: `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/davimf/basebot/BotApplication.java src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(security): enable AutoMod intents + wire sync/listener in BaseModule"
```

- [ ] **Step 5: Smoke test manual** (sem harness de unidade para JDA)

Pré-requisitos: bot com **Gerenciar Servidor**; AutoMod intents habilitados no Dev Portal se necessário.
- [ ] `/setup → Segurança` → ligar AutoMod. Conferir no **Server Settings → AutoMod** do Discord que surgiram as regras `BaseBot · Spam/Mention Spam/Palavroes/Links`.
- [ ] Mandar uma mensagem com um convite (`discord.gg/...`) num canal não-isento → Discord bloqueia → aparece o log `🛡️ AutoMod bloqueou` no `log-moderacao`, e (com `warn-per=1`) um **warn** é registrado (`/infrações`), escalando conforme `/setup → Moderação`.
- [ ] Mandar como um cargo **isento** → não bloqueia/não conta.
- [ ] Desligar AutoMod no setup → as regras `BaseBot · *` somem do AutoMod do servidor.
- [ ] Editar limite de menções/keywords no modal → regra recriada com o novo valor.

---

## Self-Review (cobertura do spec — Módulo 1)
- AutoMod nativo (spam, mention-spam, links/convites, palavrões preset+custom) → Task 3 (`AutoModManager`). ✓
- Violação → warn (configurável, default 1/violação) → Task 2 (`ViolationWindow`) + Task 4 (listener). ✓
- Isenções (cargos/canais) → Task 1 (`SecurityConfig.isExempt`) aplicadas no manager (exemptRoles/Channels) e no listener. ✓
- `/setup → Segurança` + integração com Infrações/modlog → Tasks 5 + 4. ✓
- Intents + wiring → Task 6. ✓
- **Gotcha #3 (limites do AutoMod):** tratado na Task 3 (capar/validar keywords, dividir regra). ✓

**Limitações conhecidas (v1):** mudança de config recria a regra (deleta+cria) em vez de `manager().update(...)` — suficiente; otimizar depois. Nomes exatos dos intents/presets/`AutoModRuleData.getName` a confirmar via `javap` durante a execução (marcado nas tasks).
