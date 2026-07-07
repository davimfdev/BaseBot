# Lockdown de canais na verificação — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ao ligar a verificação num servidor, esconder de `@everyone` os canais não-log que estavam abertos e concedê-los ao cargo `membro`; reverter (stateless, por assinatura) ao desligar.

**Architecture:** Um serviço `VerificationLockdown` com helpers puros (testáveis) para decidir exclusão/estado/assinatura, mais dois métodos de efeito colateral (`apply`/`revert`) que varrem `guild.getChannels()` e editam overrides de permissão via JDA. O disparo vem do `/setup → Segurança → Verificação`: automático no toggle `sectoggle:verify` e manual em dois botões novos. As edições de permissão rodam numa thread do executor (padrão do `QuickLogSetup`), nunca na thread do gateway.

**Tech Stack:** Java 22, JDA 5 (Components V2), JUnit 5 (sem Mockito). Postgres/Neon (guild_config) — **sem** migração nova nesta feature.

## Global Constraints

- Modo **NO-COMMIT**: a árvore está suja com trabalho anterior. Implementar + testar **sem** rodar git. O controlador cuida de baseline/ledger. Subagentes **não** rodam git.
- Toda mensagem do bot é um **container Components V2** (`Panels` + `.useComponentsV2()` no envio).
- Cargo membros = `cfg.role("membro")`; canal verificação = `SecurityConfig.CHANNEL_VERIFY` (`"verificacao"`); canal anti-spam = `SecurityConfig.CHANNEL_ANTISPAM` (`"anti-spam"`).
- Canais de log = ids em `guild_config.channels` sob as chaves de `SetupLogTypes.ALL`.
- `SetupView.NS` = `"setup"`. Ações de setup usam `ComponentId.of(SetupView.NS, action, args...)`.
- Ordem do construtor: `new GuildConfig(guildId, logChannelId, ticketLogChannelId, channels, roles, toggles, staffRoleIds, settings)`.
- Idioma de permissão: `IPermissionContainer.upsertPermissionOverride(role).deny/grant/clear(Permission...).complete()`; `IPermissionContainer.getPermissionOverride(role)` → `PermissionOverride` (ou `null`); `PermissionOverride.getDenied()/getAllowed()` → `EnumSet<Permission>`. `@everyone` = `guild.getPublicRole()`.

---

### Task 1: `VerificationLockdown` (helpers puros + apply/revert)

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/security/VerificationLockdown.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/security/VerificationLockdownTest.java`

**Interfaces:**
- Consumes: `SecurityConfig.CHANNEL_VERIFY`, `SecurityConfig.CHANNEL_ANTISPAM`, `SetupLogTypes.ALL` (`LogType.key()`), `GuildConfig.channel/role`, `BotContext.database().guildConfig().findOrEmpty(String)`.
- Produces:
  - `record VerificationLockdown.Summary(int changed, int skipped, boolean memberRoleMissing)`
  - `static Set<String> excludedChannelIds(GuildConfig cfg)`
  - `static boolean isOpenForEveryone(Collection<Permission> everyoneDenied)`
  - `static boolean matchesLockdownSignature(Collection<Permission> everyoneDenied, Collection<Permission> memberAllowed)`
  - `static Summary apply(BotContext ctx, Guild guild)`
  - `static Summary revert(BotContext ctx, Guild guild)`

- [ ] **Step 1: Escrever o teste que falha (helpers puros)**

Crie `VerificationLockdownTest.java`:

```java
package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.Permission;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationLockdownTest {

    private static GuildConfig cfg(Map<String, String> channels, Map<String, String> roles) {
        return new GuildConfig("g1", null, null, channels, roles, Map.of(), List.of(), Map.of());
    }

    @Test
    void excludedIdsHaveLogsVerifyAntispamNoNullsNoDupes() {
        Map<String, String> channels = Map.of(
                "log-comandos", "100",
                "log-mensagens", "101",
                SecurityConfig.CHANNEL_VERIFY, "200",
                SecurityConfig.CHANNEL_ANTISPAM, "300");
        Set<String> ids = VerificationLockdown.excludedChannelIds(cfg(channels, Map.of()));
        assertTrue(ids.containsAll(Set.of("100", "101", "200", "300")));
        // canal comum (não log/verif/antispam) não entra
        assertFalse(ids.contains("999"));
    }

    @Test
    void excludedIdsIgnoreUnsetKeys() {
        // nenhum canal configurado -> conjunto vazio (sem NPE, sem nulls)
        Set<String> ids = VerificationLockdown.excludedChannelIds(cfg(Map.of(), Map.of()));
        assertTrue(ids.isEmpty());
    }

    @Test
    void openWhenViewNotDenied() {
        assertTrue(VerificationLockdown.isOpenForEveryone(null));
        assertTrue(VerificationLockdown.isOpenForEveryone(Set.of()));
        assertTrue(VerificationLockdown.isOpenForEveryone(Set.of(Permission.MESSAGE_SEND)));
        assertFalse(VerificationLockdown.isOpenForEveryone(Set.of(Permission.VIEW_CHANNEL)));
    }

    @Test
    void signatureNeedsBothDenyAndAllow() {
        assertTrue(VerificationLockdown.matchesLockdownSignature(
                Set.of(Permission.VIEW_CHANNEL), Set.of(Permission.VIEW_CHANNEL)));
        assertFalse(VerificationLockdown.matchesLockdownSignature(
                Set.of(Permission.VIEW_CHANNEL), Set.of()));               // membro não permite
        assertFalse(VerificationLockdown.matchesLockdownSignature(
                Set.of(), Set.of(Permission.VIEW_CHANNEL)));               // everyone não nega
        assertFalse(VerificationLockdown.matchesLockdownSignature(null, null));
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha (compilação)**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.security.VerificationLockdownTest"`
Expected: FALHA de compilação (classe `VerificationLockdown` não existe).

- [ ] **Step 3: Implementar `VerificationLockdown` (helpers + apply/revert)**

Crie `VerificationLockdown.java`:

```java
package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.setup.SetupLogTypes;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Esconde/reabre canais quando a verificação liga/desliga. Ao ligar, canais não-log abertos
 *  ficam invisíveis para @everyone e visíveis para o cargo membro. Ao desligar, reverte apenas
 *  os canais com a assinatura de lockdown (@everyone nega VIEW + membro permite VIEW). A decisão
 *  de exclusão/estado/assinatura é pura e testável; as edições de permissão são efeito JDA. */
public final class VerificationLockdown {

    /** Resultado de um sweep. {@code memberRoleMissing} = cargo membro não configurado (nada feito). */
    public record Summary(int changed, int skipped, boolean memberRoleMissing) {}

    private VerificationLockdown() {}

    // ---- Helpers puros (testáveis) ----

    /** Ids que nunca são escondidos nem revertidos: canais de log + verificação + anti-spam. */
    public static Set<String> excludedChannelIds(GuildConfig cfg) {
        Set<String> ids = new LinkedHashSet<>();
        for (SetupLogTypes.LogType t : SetupLogTypes.ALL) {
            String id = cfg.channel(t.key());
            if (id != null) {
                ids.add(id);
            }
        }
        addIfPresent(ids, cfg.channel(SecurityConfig.CHANNEL_VERIFY));
        addIfPresent(ids, cfg.channel(SecurityConfig.CHANNEL_ANTISPAM));
        return ids;
    }

    private static void addIfPresent(Set<String> ids, String id) {
        if (id != null) {
            ids.add(id);
        }
    }

    /** True quando @everyone NÃO tem VIEW_CHANNEL negado (canal "aberto"). null = vazio. */
    public static boolean isOpenForEveryone(Collection<Permission> everyoneDenied) {
        return everyoneDenied == null || !everyoneDenied.contains(Permission.VIEW_CHANNEL);
    }

    /** True só quando @everyone nega VIEW_CHANNEL E o cargo membro permite VIEW_CHANNEL. null = vazio. */
    public static boolean matchesLockdownSignature(Collection<Permission> everyoneDenied,
                                                   Collection<Permission> memberAllowed) {
        return everyoneDenied != null && everyoneDenied.contains(Permission.VIEW_CHANNEL)
                && memberAllowed != null && memberAllowed.contains(Permission.VIEW_CHANNEL);
    }

    // ---- Efeitos JDA (não unit-testados; rodar no executor, nunca na thread do gateway) ----

    /** Esconde os canais não-log abertos: nega VIEW p/ @everyone, concede p/ membro. */
    public static Summary apply(BotContext ctx, Guild guild) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        Role membro = memberRole(cfg, guild);
        if (membro == null) {
            return new Summary(0, 0, true);
        }
        Role everyone = guild.getPublicRole();
        Set<String> excluded = excludedChannelIds(cfg);
        int changed = 0;
        int skipped = 0;
        for (GuildChannel ch : guild.getChannels()) {
            if (!(ch instanceof IPermissionContainer pc) || excluded.contains(ch.getId())) {
                continue;
            }
            PermissionOverride everyOv = pc.getPermissionOverride(everyone);
            if (!isOpenForEveryone(everyOv == null ? null : everyOv.getDenied())) {
                continue; // já fechado
            }
            if (!guild.getSelfMember().hasPermission(ch, Permission.MANAGE_PERMISSIONS)) {
                skipped++;
                continue;
            }
            try {
                pc.upsertPermissionOverride(everyone).deny(Permission.VIEW_CHANNEL).complete();
                pc.upsertPermissionOverride(membro).grant(Permission.VIEW_CHANNEL).complete();
                changed++;
            } catch (RuntimeException e) {
                skipped++;
            }
        }
        return new Summary(changed, skipped, false);
    }

    /** Reabre apenas os canais com a assinatura de lockdown; excluídos nunca são revertidos. */
    public static Summary revert(BotContext ctx, Guild guild) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        Role membro = memberRole(cfg, guild);
        if (membro == null) {
            return new Summary(0, 0, true);
        }
        Role everyone = guild.getPublicRole();
        Set<String> excluded = excludedChannelIds(cfg);
        int changed = 0;
        int skipped = 0;
        for (GuildChannel ch : guild.getChannels()) {
            if (!(ch instanceof IPermissionContainer pc) || excluded.contains(ch.getId())) {
                continue;
            }
            PermissionOverride everyOv = pc.getPermissionOverride(everyone);
            PermissionOverride membroOv = pc.getPermissionOverride(membro);
            boolean matches = matchesLockdownSignature(
                    everyOv == null ? null : everyOv.getDenied(),
                    membroOv == null ? null : membroOv.getAllowed());
            if (!matches) {
                continue;
            }
            if (!guild.getSelfMember().hasPermission(ch, Permission.MANAGE_PERMISSIONS)) {
                skipped++;
                continue;
            }
            try {
                pc.upsertPermissionOverride(everyone).clear(Permission.VIEW_CHANNEL).complete();
                pc.upsertPermissionOverride(membro).clear(Permission.VIEW_CHANNEL).complete();
                changed++;
            } catch (RuntimeException e) {
                skipped++;
            }
        }
        return new Summary(changed, skipped, false);
    }

    private static Role memberRole(GuildConfig cfg, Guild guild) {
        String id = cfg.role("membro");
        return id == null ? null : guild.getRoleById(id);
    }
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.security.VerificationLockdownTest"`
Expected: PASS (4 testes). Saída limpa.

- [ ] **Step 5: Compilar o módulo inteiro**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. Se algum import JDA divergir (ex.: tipo aceito por `hasPermission(GuildChannel, Permission...)`), ajuste conforme o erro do compilador — a intenção é: pular canal sem `MANAGE_PERMISSIONS`.

- [ ] **Step 6: (NO-COMMIT) Não commitar**

Não rode git. Reporte os arquivos alterados.

---

### Task 2: Botões de lockdown + banner na `verificationScreen`

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupView.java` (método `verificationScreen`)

**Interfaces:**
- Consumes: `SecurityConfig.verify(GuildConfig)`, `GuildConfig.role(String)`, `Emojis.LOCK`, `Emojis.UNLOCK`, `Emojis.WARN`.
- Produces: botões `ComponentId.of(NS, "verifylock")` e `ComponentId.of(NS, "verifyunlock")` na sub-tela; banner de aviso quando `membro` ausente.

- [ ] **Step 1: Adicionar o banner de aviso (membro ausente)**

No método `verificationScreen`, logo **depois** da linha que adiciona o texto de status
(`kids.add(Panels.text(Emojis.of(Emojis.CHECK_YES, "✅") + " **Ativa** · ...));`) e **antes** do
`kids.add(Panels.divider());` seguinte, insira:

```java
        if (on && cfg.role("membro") == null) {
            kids.add(Panels.text("-# " + Emojis.of(Emojis.WARN, "⚠️")
                    + " Cargo **membro** não configurado — canais não serão escondidos."));
        }
```

(`on` já é `SecurityConfig.verify(cfg)`, calculado no início do método.)

- [ ] **Step 2: Adicionar a linha com os botões de lockdown**

Ainda em `verificationScreen`, **antes** da linha final que adiciona a `ActionRow` de "Publicar
painel"/"Voltar" (`kids.add(ActionRow.of(Button.primary(ComponentId.of(NS, "verifypanel") ...`),
insira:

```java
        kids.add(ActionRow.of(
                Button.secondary(ComponentId.of(NS, "verifylock"), "Esconder canais")
                        .withEmoji(Emojis.button(Emojis.LOCK)),
                Button.secondary(ComponentId.of(NS, "verifyunlock"), "Reabrir canais")
                        .withEmoji(Emojis.button(Emojis.UNLOCK))));
```

- [ ] **Step 3: Compilar**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. (`Emojis.LOCK`/`Emojis.UNLOCK` já são usados em `LockCommand`/`UnlockCommand`.)

- [ ] **Step 4: (NO-COMMIT) Não commitar**

Reporte o arquivo alterado.

---

### Task 3: Disparo automático + botões manuais no `SetupComponentHandler`

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java`

**Interfaces:**
- Consumes: `VerificationLockdown.apply/revert` + `Summary`, `SecurityConfig.verify(GuildConfig)`, `GuildConfig.role`, `ctx.scheduler().executor()`, `ctx.database().actionLogs().log(guildId, userId, targetId, action, detail)`, `SetupView.verificationScreen`, `Panels`, `EmbedColor` (ambos já importados no arquivo).
- Produces: rotas `verifylock`/`verifyunlock`; disparo automático no `sectoggle:verify`.

- [ ] **Step 1: Rotear os botões manuais no `onButton`**

No `switch (id.action())` de `onButton`, ao lado do case `secnav`, adicione:

```java
            case "verifylock" -> runVerificationLockdown(event, ctx, guildId, true);
            case "verifyunlock" -> runVerificationLockdown(event, ctx, guildId, false);
```

- [ ] **Step 2: Disparo automático no toggle `sectoggle:verify`**

No case `sectoggle`, o tail atual re-renderiza a sub-tela para `verify`/`verifyuser`:

```java
                if ("verify".equals(arg) || "verifyuser".equals(arg)) {
                    edit(event, SetupView.verificationScreen(updated,
                            ctx.database().verificationQuestions().listByGuild(guildId)));
                } else if ("antispam".equals(arg)) {
```

Troque o bloco `if ("verify".equals(arg) || "verifyuser".equals(arg)) { ... }` por:

```java
                if ("verify".equals(arg) || "verifyuser".equals(arg)) {
                    if ("verify".equals(arg)) {
                        scheduleVerificationLockdown(ctx, event, updated);
                    }
                    edit(event, SetupView.verificationScreen(updated,
                            ctx.database().verificationQuestions().listByGuild(guildId)));
                } else if ("antispam".equals(arg)) {
```

(As linhas `else if ("antispam"...)` / `else { securityScreen }` permanecem exatamente iguais.)

- [ ] **Step 3: Adicionar os métodos auxiliares**

Perto de `publishVerify` (ou de `runVerificationLockdown` na mesma região de helpers), adicione:

```java
    /** Agenda o sweep de lockdown após um toggle verify (on→apply, off→revert). Se o cargo membro
     *  não estiver configurado ao ligar, não faz nada — o banner da sub-tela comunica o motivo. */
    private void scheduleVerificationLockdown(BotContext ctx, ButtonInteractionEvent event, GuildConfig updated) {
        boolean nowOn = dev.davimf.basebot.modules.base.security.SecurityConfig.verify(updated);
        boolean hasMembro = updated.role("membro") != null;
        if (nowOn && !hasMembro) {
            return;
        }
        net.dv8tion.jda.api.entities.Guild guild = event.getGuild();
        String userId = event.getUser().getId();
        ctx.scheduler().executor().execute(() -> {
            dev.davimf.basebot.modules.base.security.VerificationLockdown.Summary s = nowOn
                    ? dev.davimf.basebot.modules.base.security.VerificationLockdown.apply(ctx, guild)
                    : dev.davimf.basebot.modules.base.security.VerificationLockdown.revert(ctx, guild);
            if (!s.memberRoleMissing()) {
                ctx.database().actionLogs().log(guild.getId(), userId, null,
                        nowOn ? "VERIFY_LOCKDOWN" : "VERIFY_UNLOCK",
                        s.changed() + " alterados / " + s.skipped() + " pulados");
            }
        });
    }

    /** Botão manual: esconde (hide=true) ou reabre (hide=false) e devolve um resumo efêmero. */
    private void runVerificationLockdown(ButtonInteractionEvent event, BotContext ctx, String guildId, boolean hide) {
        event.deferEdit().queue();
        net.dv8tion.jda.api.entities.Guild guild = event.getGuild();
        String userId = event.getUser().getId();
        ctx.scheduler().executor().execute(() -> {
            dev.davimf.basebot.modules.base.security.VerificationLockdown.Summary s = hide
                    ? dev.davimf.basebot.modules.base.security.VerificationLockdown.apply(ctx, guild)
                    : dev.davimf.basebot.modules.base.security.VerificationLockdown.revert(ctx, guild);
            GuildConfig refreshed = ctx.database().guildConfig().findOrEmpty(guildId);
            event.getHook().editOriginalComponents(SetupView.verificationScreen(refreshed,
                            ctx.database().verificationQuestions().listByGuild(guildId)))
                    .useComponentsV2().queue(ok -> {}, err -> {});
            String msg = s.memberRoleMissing()
                    ? "Configure o cargo **membro** primeiro — nada foi alterado."
                    : (hide ? "Escondidos" : "Reabertos") + " `" + s.changed() + "` canais · `"
                            + s.skipped() + "` pulados.";
            if (!s.memberRoleMissing()) {
                ctx.database().actionLogs().log(guildId, userId, null,
                        hide ? "VERIFY_LOCKDOWN" : "VERIFY_UNLOCK",
                        s.changed() + " alterados / " + s.skipped() + " pulados");
            }
            event.getHook().sendMessageComponents(
                            Panels.container(EmbedColor.resolve(refreshed), Panels.text(msg)))
                    .useComponentsV2().setEphemeral(true).queue(ok -> {}, err -> {});
        });
    }
```

- [ ] **Step 4: Compilar + rodar toda a suíte**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL; todos os testes passam (inclui os 4 novos de `VerificationLockdownTest`).

Se `event.getHook().sendMessageComponents(...).setEphemeral(true)` não compilar na versão de JDA do
projeto (follow-up efêmero indisponível após `deferEdit`), substitua o envio do resumo por uma linha
no topo do painel: prefixe o texto de `verificationScreen` — ou seja, remova o `sendMessageComponents`
e, no `editOriginalComponents`, use uma variante do painel com o `msg` no topo. Documente a escolha no
report.

- [ ] **Step 5: (NO-COMMIT) Não commitar**

Reporte os arquivos alterados e o resultado do build.

---

## Notas de verificação manual (pós-implementação, feitas pelo usuário no Discord)

- Ligar verificação com cargo `membro` configurado → canais não-log abertos somem para `@everyone`;
  `membro` continua vendo; canal de verificação/anti-spam/logs intactos.
- Desligar → só os canais com a assinatura voltam a aparecer; canais mexidos à mão ficam como estão.
- Botões "Esconder canais"/"Reabrir canais" reexecutam o sweep e devolvem resumo efêmero.
- Ligar sem cargo `membro` → banner de aviso na sub-tela; nenhum canal alterado.
- **Pré-requisito:** o bot precisa de Administrador (ou cargo com View acima) para não perder acesso
  aos canais que esconder, e de `MANAGE_PERMISSIONS` para editar overrides.
