# PD externo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (inline). Steps em checkbox.

**Goal:** `/pd` passa a registrar PD de quem já saiu do Discord via `id_jogo` + `nome_rp` (sem kick), mantendo o fluxo atual quando um `usuario` é informado.

**Architecture:** Modificação em `PdCommand.java`: `usuario` vira opcional, adiciona `id_jogo`/`nome_rp` opcionais, `motivo` obrigatório vem primeiro. Um resolvedor puro `mode(...)` decide MEMBER/EXTERNAL/INVALID; o registro (action_logs + FacsLog) é comum aos dois fluxos.

**Tech Stack:** Java 22, JDA 6.4.2.

## Global Constraints

- **JDK 22.** **Sem commits** (cada task termina em `./gradlew build`/test).
- Discord exige opções **obrigatórias antes das opcionais** → `motivo` primeiro.
- Permissão `ManagerPermissions.Capability.PUNICOES` inalterada; house style dos logs mantido.

---

## Task 1: Resolvedor de modo `PdCommand.mode(...)` + teste

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/facs/commands/PdCommand.java`
- Test: `src/test/java/dev/davimf/basebot/modules/facs/commands/PdModeTest.java`

**Interfaces — Produces:** `enum PdCommand.Mode { MEMBER, EXTERNAL, INVALID }` e `static Mode mode(boolean hasUsuario, String idJogo, String nomeRp)`.

- [ ] **Step 1: Teste**
```java
package dev.davimf.basebot.modules.facs.commands;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PdModeTest {

    @Test
    void memberWhenUserPresent() {
        assertEquals(PdCommand.Mode.MEMBER, PdCommand.mode(true, null, null));
        assertEquals(PdCommand.Mode.MEMBER, PdCommand.mode(true, "123", "Fulano"));
    }

    @Test
    void externalWhenGameIdAndRpNamePresent() {
        assertEquals(PdCommand.Mode.EXTERNAL, PdCommand.mode(false, "123", "Fulano"));
        assertEquals(PdCommand.Mode.EXTERNAL, PdCommand.mode(false, " 123 ", " Fulano "));
    }

    @Test
    void invalidWhenIncomplete() {
        assertEquals(PdCommand.Mode.INVALID, PdCommand.mode(false, null, null));
        assertEquals(PdCommand.Mode.INVALID, PdCommand.mode(false, "123", null));
        assertEquals(PdCommand.Mode.INVALID, PdCommand.mode(false, null, "Fulano"));
        assertEquals(PdCommand.Mode.INVALID, PdCommand.mode(false, "  ", "  "));
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.PdModeTest"` → FAIL (Mode/mode não existem).
- [ ] **Step 3: Implementar** — adicionar em `PdCommand`:
```java
    /** Qual fluxo o /pd deve seguir conforme as opções fornecidas. */
    public enum Mode { MEMBER, EXTERNAL, INVALID }

    static Mode mode(boolean hasUsuario, String idJogo, String nomeRp) {
        if (hasUsuario) {
            return Mode.MEMBER;
        }
        if (notBlank(idJogo) && notBlank(nomeRp)) {
            return Mode.EXTERNAL;
        }
        return Mode.INVALID;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.PdModeTest"` → PASS.

---

## Task 2: `/pd` opções + roteamento dos fluxos

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/facs/commands/PdCommand.java`

**Interfaces — Consumes:** `PdCommand.mode(...)` (Task 1).

- [ ] **Step 1:** `data()` — reordenar/adicionar opções (`motivo` obrigatório primeiro):
```java
    @Override
    public SlashCommandData data() {
        return Commands.slash("pd", "Aplica PD: remove o membro (se estiver no Discord) e registra nos logs.")
                .addOption(OptionType.STRING, "motivo", "Motivo do PD", true)
                .addOption(OptionType.USER, "usuario", "Membro a remover (se estiver no Discord)", false)
                .addOption(OptionType.STRING, "id_jogo", "ID de jogo (para quem já saiu do Discord)", false)
                .addOption(OptionType.STRING, "nome_rp", "Nome no RP (para quem já saiu do Discord)", false);
    }
```
- [ ] **Step 2:** `execute(...)` — substituir o corpo após a checagem de permissão. Mantém o fluxo membro atual e adiciona o externo:
```java
        String reason = event.getOption("motivo", OptionMapping::getAsString);
        boolean hasUsuario = event.getOption("usuario") != null;
        String idJogo = event.getOption("id_jogo", OptionMapping::getAsString);
        String nomeRp = event.getOption("nome_rp", OptionMapping::getAsString);
        String guildId = event.getGuild().getId();
        String actorId = event.getUser().getId();

        switch (mode(hasUsuario, idJogo, nomeRp)) {
            case MEMBER -> {
                Member target = event.getOption("usuario", OptionMapping::getAsMember);
                if (target == null) {
                    Replies.ephemeral(event, ctx,
                            "Esse usuário não está mais no servidor. Para registrar mesmo assim, use **id_jogo** + **nome_rp**.");
                    return;
                }
                Member self = event.getGuild().getSelfMember();
                if (!Moderation.canModerate(event.getMember(), target, self)) {
                    Replies.ephemeral(event, ctx,
                            "Hierarquia insuficiente: você ou o bot não estão acima desse membro.");
                    return;
                }
                String targetTag = target.getUser().getAsTag();
                String targetId = target.getId();
                event.getGuild().kick(target)
                        .reason(dev.davimf.basebot.util.ModReason.of(event.getUser(), "PD: " + reason)).queue(ok -> {
                    ctx.database().actionLogs().log(guildId, actorId, targetId, "PD", reason);
                    String memberLine = targetTag + " · `" + targetId + "`";
                    FacsLog.post(ctx, guildId, "log-pds", pdEntry(memberLine, actorId, reason));
                    FacsLog.post(ctx, guildId, "log-punicoes", pdEntry(memberLine, actorId, reason));
                    Replies.reply(event, ctx, Emojis.of(Emojis.SKULL, "💀") + " PD aplicado em " + targetTag + ".");
                }, err -> Replies.ephemeral(event, ctx, "Falha ao aplicar PD: " + err.getMessage()));
            }
            case EXTERNAL -> {
                String memberLine = nomeRp.trim() + " · id de jogo `" + idJogo.trim() + "` · (fora do Discord)";
                ctx.database().actionLogs().log(guildId, actorId, idJogo.trim(), "PD", reason);
                FacsLog.post(ctx, guildId, "log-pds", pdEntry(memberLine, actorId, reason));
                FacsLog.post(ctx, guildId, "log-punicoes", pdEntry(memberLine, actorId, reason));
                Replies.reply(event, ctx,
                        Emojis.of(Emojis.SKULL, "💀") + " PD registrado para **" + nomeRp.trim() + "** (fora do Discord).");
            }
            case INVALID -> Replies.ephemeral(event, ctx,
                    "Informe o **usuário** (membro no Discord) ou o **id_jogo** + **nome_rp** (para quem já saiu).");
        }
    }

    private static String pdEntry(String memberLine, String actorId, String reason) {
        return "## " + Emojis.of(Emojis.SKULL, "💀") + " PD aplicado\n---\n"
                + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + memberLine + "\n---\n"
                + Emojis.of(Emojis.SHIELD, "🛡️") + " **Responsável** · <@" + actorId + ">\n"
                + Emojis.of(Emojis.NOTE, "📝") + " **Motivo** · " + reason;
    }
```
> Remove o antigo bloco que resolvia `target`/`reason` no topo (agora dentro do switch). Imports já presentes: `Member`, `OptionMapping`, `Moderation`, `FacsLog`, `Emojis`, `Replies`. `event.getOption("usuario")` (sem mapper) retorna `OptionMapping` ou null.
- [ ] **Step 3: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 3: Build + smoke

- [ ] **Step 1: Run** `./gradlew build` → BUILD SUCCESSFUL (inclui `PdModeTest`).
- [ ] **Step 2: Smoke (servidor de teste):**
  - `/pd motivo:... usuario:@alguém` → kick + logs (como hoje).
  - `/pd motivo:... id_jogo:ABC123 nome_rp:"João Silva"` → sem kick, entra em `#log-pds` e `#log-punicoes` marcado como "(fora do Discord)".
  - `/pd motivo:...` (sem usuário nem id/nome) → erro orientando os campos.
  - `/pd motivo:... id_jogo:ABC123` (só id) → erro (falta nome_rp).

## Self-Review
- **Cobertura do spec:** um só /pd com campos opcionais (T2); id_jogo+nome_rp+motivo (T2); externo sem kick (T2); resolvedor puro testado (T1); erros INVALID / usuário-que-saiu (T2). ✓
- **Consistência:** `mode(...)`/`Mode` (T1) usados em T2; `pdEntry(...)` compartilhado pelos dois fluxos. ✓
- **Sem placeholders:** código real em cada passo. ✓
