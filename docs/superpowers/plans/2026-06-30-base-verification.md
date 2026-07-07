# Segurança · Módulo 3 — Verificação · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (inline). Steps em checkbox.

**Goal:** Gate de entrada: ao entrar, o membro recebe o cargo `nao-verificado`; um painel com botão **Verificar** concede o cargo `membro` e remove o `nao-verificado`. Casa com o lockdown do anti-raid (nível de verificação alto + gate manual).

**Architecture:** `VerificationListener` (onGuildMemberJoin) atribui `nao-verificado` se a verificação estiver ligada. O painel (`VerificationView`) é publicado num canal por um botão do `/setup → Segurança`. O botão **Verificar** é tratado pelo `SecurityComponentHandler` (namespace `sec`, já existente) — concede `membro`, remove `nao-verificado`. Reusa os slots de cargo do `/setup → Cargos`.

**Tech Stack:** Java 22, JDA 6.4.2.

## Global Constraints
- JDK 22; Base ≠ facs; Components V2 + emojis custom; config em `guild_config`; sem commits salvo pedido; pacote `modules.base.security`.

---

### Task 1: `SecurityConfig.verify` + slot de cargo `nao-verificado`

**Files:** Modify `SecurityConfig.java` (+ test), `modules/base/setup/SetupRoleKeys.java`.

- [ ] **Step 1:** `SecurityConfig`: `KEY_VERIFY = "sec:verify"`; `static boolean verify(GuildConfig cfg){return cfg.toggle(KEY_VERIFY,false);}`. Teste: `assertFalse(SecurityConfig.verify(cfg(Map.of(),Map.of())))`.
- [ ] **Step 2:** `SetupRoleKeys.build()`: adicionar `Map.entry("nao-verificado", "Não-verificado")` à lista base (logo após `mutado`). Assim aparece em `/setup → Cargos` e no setup rápido de cargos.
- [ ] **Step 3:** `./gradlew test --tests "*.SecurityConfigTest"` → PASS.

---

### Task 2: `VerificationView` (painel)

**Files:** Create `modules/base/security/VerificationView.java`.

**Interfaces:** `static Container panel(int accent)` — heading + descrição + ActionRow com `Button.success(ComponentId.of("sec","verify"), "Verificar")`.

- [ ] **Step 1:** Implementar:
```java
package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;

public final class VerificationView {
    private VerificationView() {}

    public static Container panel(int accent) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.CHECK_YES, "✅") + " Verificação"),
                Panels.divider(),
                Panels.text("> Clique em **Verificar** para liberar seu acesso ao servidor."),
                ActionRow.of(Button.success(ComponentId.of(AntiRaidService.NS, "verify"), "Verificar")
                        .withEmoji(Emojis.button(Emojis.CHECK_YES))));
    }
}
```
- [ ] **Step 2:** Build → SUCCESS.

---

### Task 3: `VerificationListener` (atribui `nao-verificado` no join)

**Files:** Create `modules/base/security/VerificationListener.java`.

- [ ] **Step 1:** Implementar:
```java
package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Ao entrar, atribui o cargo "nao-verificado" (gate). O membro vira "membro" ao verificar. */
public final class VerificationListener extends ListenerAdapter {

    private final BotContext ctx;

    public VerificationListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!SecurityConfig.verify(cfg)) {
            return;
        }
        String roleId = cfg.role("nao-verificado");
        Role role = roleId == null ? null : event.getGuild().getRoleById(roleId);
        if (role != null && event.getGuild().getSelfMember().canInteract(role)) {
            event.getGuild().addRoleToMember(event.getMember(), role)
                    .reason("Verificação: aguardando").queue(ok -> {}, err -> {});
        }
    }
}
```
> **Verificar:** `cfg.role(key)` existe (usado em outros lugares). `getSelfMember().canInteract(role)`.
- [ ] **Step 2:** Build → SUCCESS.

---

### Task 4: Botão "Verificar" no `SecurityComponentHandler`

**Files:** Modify `SecurityComponentHandler.java`.

- [ ] **Step 1:** Trocar o corpo do `onButton` para rotear por ação:
```java
    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null) {
            return;
        }
        switch (id.action()) {
            case "raidunlock" -> raidUnlock(event, ctx);
            case "verify" -> verify(event, ctx);
            default -> { /* not ours */ }
        }
    }

    private void raidUnlock(ButtonInteractionEvent event, BotContext ctx) {
        if (event.getMember() == null || !event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            Replies.ephemeral(event, ctx, "Apenas quem tem **Gerenciar Servidor** pode desativar o lockdown.");
            return;
        }
        AntiRaidService.unlock(ctx, event.getGuild());
        Replies.reply(event, ctx, "Lockdown desativado.");
    }

    private void verify(ButtonInteractionEvent event, BotContext ctx) {
        var guild = event.getGuild();
        var cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        if (!SecurityConfig.verify(cfg)) {
            Replies.ephemeral(event, ctx, "A verificação não está ativa.");
            return;
        }
        String memberRoleId = cfg.role("membro");
        net.dv8tion.jda.api.entities.Role memberRole = memberRoleId == null ? null : guild.getRoleById(memberRoleId);
        if (memberRole == null) {
            Replies.ephemeral(event, ctx, "Cargo de **membro** não configurado (/setup → Cargos).");
            return;
        }
        if (!guild.getSelfMember().canInteract(memberRole)) {
            Replies.ephemeral(event, ctx, "Não consigo atribuir o cargo de membro (acima do meu cargo).");
            return;
        }
        net.dv8tion.jda.api.entities.Member m = event.getMember();
        guild.addRoleToMember(m, memberRole).reason("Verificação concluída").queue(ok -> {}, err -> {});
        String unvId = cfg.role("nao-verificado");
        net.dv8tion.jda.api.entities.Role unv = unvId == null ? null : guild.getRoleById(unvId);
        if (unv != null && m.getRoles().contains(unv) && guild.getSelfMember().canInteract(unv)) {
            guild.removeRoleFromMember(m, unv).reason("Verificação concluída").queue(ok -> {}, err -> {});
        }
        Replies.ephemeral(event, ctx, "" + Emojis.of(Emojis.CHECK_YES, "✅") + " Verificado! Bem-vindo.");
    }
```
Adicionar imports: `Emojis`. (Permission/Role/Member usados via FQN acima.) Remover o corpo antigo do `onButton`.
- [ ] **Step 2:** Build → SUCCESS.

---

### Task 5: `/setup → Segurança` — toggle de verificação + publicar painel

**Files:** Modify `SetupView.securityScreen`, `SetupComponentHandler`.

- [ ] **Step 1:** `securityScreen`: `boolean verify = SecurityConfig.verify(cfg);` no overview (`✅ **Verificação** · ligado/desligado`), e uma ActionRow:
```java
                ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "sectoggle", "verify"), "Verificação: " + (verify ? "on" : "off")).withEmoji(Emojis.button(Emojis.CHECK_YES)),
                        Button.primary(ComponentId.of(NS, "verifypanel"), "Publicar painel").withEmoji(Emojis.button(Emojis.SEND)))
```
(colocar antes da row de Voltar; cuidado com o limite de 5 action rows — mover o "Editar AutoMod" pra junto do Voltar já existe; total ≤5.)
- [ ] **Step 2:** `SetupComponentHandler`:
  - `sectoggle` key switch: `case "verify" -> SecurityConfig.KEY_VERIFY;` (default false — já coberto pelo `def = warn||invites`).
  - botão: `case "verifypanel" -> publishVerify(event, ctx);` com helper:
```java
    private void publishVerify(ButtonInteractionEvent event, BotContext ctx) {
        if (!(event.getChannel() instanceof net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel ch)) {
            Replies.ephemeral(event, ctx, "Use num canal de texto.");
            return;
        }
        int accent = EmbedColor.resolve(config(ctx, event.getGuild().getId()));
        ch.sendMessageComponents(dev.davimf.basebot.modules.base.security.VerificationView.panel(accent))
                .useComponentsV2().queue(ok -> Replies.ephemeral(event, ctx, "Painel publicado."),
                        err -> Replies.ephemeral(event, ctx, "Falha: " + err.getMessage()));
    }
```
> **Verificar:** o tipo de canal pra `sendMessageComponents` (GuildMessageChannel/TextChannel) e import de `EmbedColor` (já usado no handler).
- [ ] **Step 3:** Build completo → SUCCESS.

---

### Task 6: Wiring + smoke

**Files:** Modify `BaseModule.java`.

- [ ] **Step 1:** `registry.listener(new dev.davimf.basebot.modules.base.security.VerificationListener(ctx));`
- [ ] **Step 2:** `./gradlew build` → BUILD SUCCESSFUL.
- [ ] **Step 3: Smoke:** `/setup → Cargos` mapear "Não-verificado" (e "Membro"); `/setup → Segurança` ligar Verificação; **Publicar painel** num canal; entrar com um alt → recebe "nao-verificado"; clicar **Verificar** → ganha "membro", perde "nao-verificado".

## Self-Review
- Atribuir nao-verificado no join → Task 3. Painel + botão Verificar → Tasks 2,4. Toggle+publicar → Task 5. Slot de cargo → Task 1. Wiring → Task 6. ✓
