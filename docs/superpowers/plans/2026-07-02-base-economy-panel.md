# Painel `/economia` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Um comando `/economia` (efêmero) que mostra saldo, status de cadeia/ficha, e todas as ações da economia com seu estado (pronta / cooldown / bloqueada por equipamento / preso) + o equipamento equipado — pra ninguém ficar perdido sobre o que fazer.

**Architecture:** Um resolver puro (`ActionStatus`) decide o estado de cada ação (testado); chaves de cooldown centralizadas em `EconomyCooldownKeys` (anti-drift); `EconomiaCommand` junta os dados (wallet, cadeia, 4 slots equipados lidos **uma vez**, 8 cooldowns) e `EconomiaPanelView` renderiza em Components V2 com emojis custom.

**Tech Stack:** Java 22, JDA 6.4.2 (Components V2), SQLite/HikariCP, JUnit 5 (sem Mockito).

## Global Constraints

- **Base ≠ facs.** Gated por `eco:enabled`. Comando **efêmero** (leitura pessoal).
- **Emojis custom:** todo emoji via `Emojis.of(name, fallbackUnicode)` (nunca unicode cru). Se o conceito não tiver constante em `Emojis`, chamar direto `Emojis.of("nome_do_emoji", "🔣")`.
- **`now = System.currentTimeMillis()` calculado uma vez** por execução, reusado em todos os `resolve(...)`.
- **Leitura de equipamento uma vez por slot:** `Map<Slot,Row>` (4 leituras), reaproveitado entre ações do mesmo slot (`WEAPON` serve `/crime`, `/roubar`, `/crimeorganizado`).
- **`equippedLabel` null-safe:** se `EquipmentCatalog.byKey(row.itemKey())` for null (não deveria — `InventoryRepository.equipped` já filtra linhas inconsistentes), não lançar exceção; label vira `"Item desconhecido"`.
- **`CooldownRepository` guarda ms.** `readyAt = lastTs + cooldownS*1000`.
- Consome do já-construído: `EconomyConfig`(`enabled`,`workCooldownSeconds`), `EconomyFormat`, `EconomyDefaults` (cooldowns), `WalletRepository.get`, `CooldownRepository.lastTs`, `InventoryRepository.equipped`+`Row`, `EquipmentCatalog`(`Slot`,`Equip.name()/maxUsos()`,`byKey`), `JailService`(`resolve`→`Status(kind,presoAte)`, `fichaSuja`), `Panels`/`Replies`/`EmbedColor`/`Emojis`/`SlashCommand`.
- **Spec:** `docs/superpowers/specs/2026-07-02-base-economy-panel-design.md`.

---

### Task 1: `EconomyCooldownKeys` + `ActionStatus` (resolver puro)

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/economy/EconomyCooldownKeys.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/economy/ActionStatus.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/economy/ActionStatusTest.java`

**Interfaces:**
- Produces:
  - `EconomyCooldownKeys` — constantes `CD_DAILY,CD_WORK,CD_MINE,CD_COOK,CD_DELIVERY,CD_CRIME,CD_ROB,CD_ORG` (Strings iguais às literais dos serviços).
  - `ActionStatus` — `enum Kind { READY, COOLDOWN, LOCKED, JAILED }`; `record ActionStatus(Kind kind, long readyAt)`; `static ActionStatus resolve(long now, long lastTs, long cooldownS, boolean unlocked, boolean jailed, boolean exemptWhenJailed)`.

- [ ] **Step 1: Write the failing test**

`ActionStatusTest.java`:
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.modules.base.economy.ActionStatus.Kind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActionStatusTest {

    @Test
    void jailedBeatsEverythingWhenNotExempt() {
        // preso, sem equipamento, em cooldown — mesmo assim JAILED (não isento)
        ActionStatus s = ActionStatus.resolve(1000, 0, 3600, false, true, false);
        assertEquals(Kind.JAILED, s.kind());
        assertEquals(0, s.readyAt());
    }

    @Test
    void exemptActionIgnoresJail() {
        // /daily é isento: mesmo preso, cai nas regras normais (aqui: READY)
        ActionStatus s = ActionStatus.resolve(10_000, 0, 1, true, true, true);
        assertEquals(Kind.READY, s.kind());
    }

    @Test
    void lockedBeatsCooldown() {
        // sem equipamento vence cooldown ativo
        ActionStatus s = ActionStatus.resolve(1000, 1000, 3600, false, false, false);
        assertEquals(Kind.LOCKED, s.kind());
        assertEquals(0, s.readyAt());
    }

    @Test
    void cooldownWhenActiveElseReady() {
        // ativo: now < lastTs + cd*1000
        ActionStatus active = ActionStatus.resolve(1000, 1000, 60, true, false, false);
        assertEquals(Kind.COOLDOWN, active.kind());
        assertEquals(1000 + 60_000, active.readyAt());
        // expirado
        ActionStatus ready = ActionStatus.resolve(1000 + 60_001, 1000, 60, true, false, false);
        assertEquals(Kind.READY, ready.kind());
        assertEquals(0, ready.readyAt());
    }

    @Test
    void readyWhenNeverUsed() {
        // "nunca usado" = lastTs 0; com now realista (epoch), 0 + cd*1000 já passou → READY.
        ActionStatus s = ActionStatus.resolve(10_000_000L, 0, 3600, true, false, false);
        assertEquals(Kind.READY, s.kind());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.economy.ActionStatusTest"`
Expected: FAIL — `ActionStatus` não existe.

- [ ] **Step 3: Write the implementation**

`EconomyCooldownKeys.java`:
```java
package dev.davimf.basebot.modules.base.economy;

/** Chaves de cooldown (iguais às literais usadas pelos serviços — centralizadas anti-drift). */
public final class EconomyCooldownKeys {
    private EconomyCooldownKeys() {}
    public static final String CD_DAILY = "daily";
    public static final String CD_WORK = "work";
    public static final String CD_MINE = "minerar";
    public static final String CD_COOK = "cozinhar";
    public static final String CD_DELIVERY = "entregar";
    public static final String CD_CRIME = "crime";
    public static final String CD_ROB = "rob";
    public static final String CD_ORG = "orgcrime";
}
```

`ActionStatus.java`:
```java
package dev.davimf.basebot.modules.base.economy;

/** Estado puro de uma ação da economia. readyAt só é significativo em COOLDOWN (senão 0). */
public record ActionStatus(Kind kind, long readyAt) {

    public enum Kind { READY, COOLDOWN, LOCKED, JAILED }

    /** Precedência: preso (não isento) → bloqueado (sem equipamento) → cooldown → pronto. */
    public static ActionStatus resolve(long now, long lastTs, long cooldownS,
                                       boolean unlocked, boolean jailed, boolean exemptWhenJailed) {
        if (jailed && !exemptWhenJailed) {
            return new ActionStatus(Kind.JAILED, 0);
        }
        if (!unlocked) {
            return new ActionStatus(Kind.LOCKED, 0);
        }
        long readyAt = lastTs + cooldownS * 1000L;
        if (now < readyAt) {
            return new ActionStatus(Kind.COOLDOWN, readyAt);
        }
        return new ActionStatus(Kind.READY, 0);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.economy.ActionStatusTest"`
Expected: PASS.

- [ ] **Step 5: Commit** *(pular no modo no-commit)*

```bash
git add src/main/java/dev/davimf/basebot/modules/base/economy/EconomyCooldownKeys.java \
        src/main/java/dev/davimf/basebot/modules/base/economy/ActionStatus.java \
        src/test/java/dev/davimf/basebot/modules/base/economy/ActionStatusTest.java
git commit -m "feat(eco): ActionStatus (resolver puro) + EconomyCooldownKeys"
```

---

### Task 2: `EconomiaPanelView` + `EconomiaCommand` + wiring

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/economy/EconomiaPanelView.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/EconomiaCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java`

**Interfaces:**
- Consumes: Task 1 (`ActionStatus`, `EconomyCooldownKeys`) + tudo listado nos Global Constraints.
- Produces: `record EconomiaPanelView.ActionLine(String command, ActionStatus status, String unlockHint, String equippedLabel)`; `EconomiaPanelView.panel(int accent, Member m, WalletRepository.Wallet w, JailService.Status jail, boolean fichaSuja, List<ActionLine> acoes, GuildConfig cfg)` → `Container`. `EconomiaCommand` (name `"economia"`), recebe `JailService` no construtor (usa `ctx` pro resto).

> Build-verified (a lógica testável está em Task 1). Deliverable: `./gradlew build` SUCCESSFUL.

- [ ] **Step 1: Write `EconomiaPanelView`**

`EconomiaPanelView.java` — Container V2, house style, emojis via `Emojis` (constantes existentes: `MONEY`,`CASH`,`BANK`,`GEM`,`LOCK`,`WARN`,`CHECK_YES`,`HOURGLASS`,`KEY`,`BROOM`; se alguma faltar, usar `Emojis.of("nome","fallback")`):
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Member;

import java.util.List;

/** Painel de leitura do /economia. */
public final class EconomiaPanelView {

    public record ActionLine(String command, ActionStatus status, String unlockHint, String equippedLabel) {}

    private EconomiaPanelView() {}

    public static Container panel(int accent, Member m, WalletRepository.Wallet w, JailService.Status jail,
                                  boolean fichaSuja, List<ActionLine> acoes, GuildConfig cfg) {
        boolean preso = jail.kind() == JailService.Kind.PRESO;
        StringBuilder body = new StringBuilder();
        body.append("## ").append(Emojis.of(Emojis.MONEY, "🪙")).append(" Economia de ").append(m.getEffectiveName()).append('\n');
        long total = w.cash() + w.bank();
        body.append('\n').append(Emojis.of(Emojis.CASH, "💵")).append(" Carteira · ").append(EconomyFormat.format(w.cash(), cfg));
        body.append('\n').append(Emojis.of(Emojis.BANK, "🏦")).append(" Banco · ").append(EconomyFormat.format(w.bank(), cfg));
        body.append('\n').append(Emojis.of(Emojis.GEM, "💠")).append(" Total · ").append(EconomyFormat.formatNamed(total, cfg));

        if (preso) {
            body.append('\n').append('\n').append(Emojis.of(Emojis.LOCK, "🔒"))
                    .append(" **Preso** — sai <t:").append(jail.presoAte() / 1000).append(":R>. Pague `/fianca` pra sair já.");
        } else if (fichaSuja) {
            body.append('\n').append('\n').append(Emojis.of(Emojis.WARN, "⚠️"))
                    .append(" **Ficha suja** — −15% de chance em crimes. `/limparficha` limpa.");
        }

        StringBuilder acts = new StringBuilder();
        for (ActionLine a : acoes) {
            acts.append('\n').append(lineOf(a));
        }

        StringBuilder tips = new StringBuilder();
        if (preso) {
            tips.append('\n').append(Emojis.of(Emojis.KEY, "🔓")).append(" `/fianca` — sair da cadeia por ")
                    .append(EconomyFormat.format(EconomyDefaults.BAIL_BASE, cfg));
        } else if (fichaSuja) {
            tips.append('\n').append(Emojis.of(Emojis.BROOM, "🧼")).append(" `/limparficha` — limpar a ficha por ")
                    .append(EconomyFormat.format(EconomyDefaults.EXPUNGE, cfg));
        }

        String footer = "-# Roubo pode ter cooldown separado por alvo."
                + (preso ? " Preso? Só `/daily` e eventos rendem." : "");

        java.util.List<net.dv8tion.jda.api.components.container.ContainerChildComponent> kids = new java.util.ArrayList<>();
        kids.add(Panels.text(body.toString()));
        kids.add(Panels.divider());
        kids.add(Panels.text("### Ações" + acts));
        if (!tips.isEmpty()) {
            kids.add(Panels.divider());
            kids.add(Panels.text("### Ações úteis" + tips));
        }
        kids.add(Panels.divider());
        kids.add(Panels.text(footer));
        return Panels.container(accent, kids.toArray(new net.dv8tion.jda.api.components.container.ContainerChildComponent[0]));
    }

    private static String lineOf(ActionLine a) {
        String cmd = "`/" + a.command() + "`";
        String eq = a.equippedLabel() == null ? "" : " · " + a.equippedLabel();
        return switch (a.status().kind()) {
            case READY -> Emojis.of(Emojis.CHECK_YES, "✅") + " " + cmd + " — disponível agora" + eq;
            case COOLDOWN -> Emojis.of(Emojis.HOURGLASS, "⏳") + " " + cmd + " — <t:" + (a.status().readyAt() / 1000) + ":R>" + eq;
            case LOCKED -> Emojis.of(Emojis.LOCK, "🔒") + " " + cmd + " — " + a.unlockHint();
            case JAILED -> Emojis.of(Emojis.LOCK, "🔒") + " " + cmd + " — preso";
        };
    }
}
```
> Confirme os nomes de emoji em `Emojis.java`. `MONEY`/`CASH`/`BANK`/`GEM`/`LOCK`/`WARN`/`CHECK_YES`/`HOURGLASS`/`KEY`/`BROOM` existem no projeto. Se algum não existir, troque por `Emojis.of("nome_correto", "fallback")` (o 2º arg é o glyph unicode).

- [ ] **Step 2: Write `EconomiaCommand`**

`EconomiaCommand.java` — junta os dados (equipamento **uma vez por slot**, `now` **uma vez**) e responde efêmero:
```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.ActionStatus;
import dev.davimf.basebot.modules.base.economy.CooldownRepository;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.EconomyCooldownKeys;
import dev.davimf.basebot.modules.base.economy.EconomyDefaults;
import dev.davimf.basebot.modules.base.economy.EconomiaPanelView;
import dev.davimf.basebot.modules.base.economy.EconomiaPanelView.ActionLine;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Equip;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;
import dev.davimf.basebot.modules.base.economy.InventoryRepository;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.Row;
import dev.davimf.basebot.modules.base.economy.JailService;
import dev.davimf.basebot.modules.base.economy.WalletRepository;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** /economia — painel do que dá pra fazer + cooldowns + saldo + cadeia. */
public final class EconomiaCommand implements SlashCommand {

    private final JailService jail;

    public EconomiaCommand(JailService jail) { this.jail = jail; }

    @Override public String name() { return "economia"; }

    @Override public SlashCommandData data() {
        return Commands.slash("economia", "Mostra o que você pode fazer, seus cooldowns e saldo.");
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        String g = event.getGuild().getId();
        String u = event.getMember().getId();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(g);
        if (!EconomyConfig.enabled(cfg)) {
            Replies.ephemeral(event, ctx, "Economia desativada neste servidor.");
            return;
        }
        long now = System.currentTimeMillis();
        var wallets = new WalletRepository(ctx.database().sqlite());
        var cooldowns = new CooldownRepository(ctx.database().sqlite());
        var inv = new InventoryRepository(ctx.database().sqlite());

        WalletRepository.Wallet w = wallets.get(g, u);
        JailService.Status st = jail.resolve(g, u);            // pode aplicar release lazy + marcar ficha
        boolean preso = st.kind() == JailService.Kind.PRESO;
        boolean ficha = jail.fichaSuja(g, u);

        // Equipamento lido UMA vez por slot, reusado entre ações do mesmo slot.
        Map<Slot, Row> equipped = new EnumMap<>(Slot.class);
        for (Slot s : Slot.values()) {
            Row r = inv.equipped(g, u, s);
            if (r != null) {
                equipped.put(s, r);
            }
        }

        List<ActionLine> acoes = new ArrayList<>();
        acoes.add(line(g, u, now, cooldowns, preso, EconomyCooldownKeys.CD_DAILY, "daily",
                EconomyDefaults.DAILY_COOLDOWN_S, null, true, equipped, null));
        acoes.add(line(g, u, now, cooldowns, preso, EconomyCooldownKeys.CD_WORK, "trabalhar",
                EconomyConfig.workCooldownSeconds(cfg), null, false, equipped, null));
        acoes.add(line(g, u, now, cooldowns, preso, EconomyCooldownKeys.CD_MINE, "minerar",
                EconomyDefaults.MINE_COOLDOWN_S, Slot.MINING, false, equipped, "equipe uma picareta no `/mercado`"));
        acoes.add(line(g, u, now, cooldowns, preso, EconomyCooldownKeys.CD_COOK, "cozinhar",
                EconomyDefaults.COOK_COOLDOWN_S, Slot.COOKING, false, equipped, "equipe um utensílio no `/mercado`"));
        acoes.add(line(g, u, now, cooldowns, preso, EconomyCooldownKeys.CD_DELIVERY, "entregar",
                EconomyDefaults.DELIVERY_COOLDOWN_S, Slot.DELIVERY, false, equipped, "equipe uma moto no `/mercado`"));
        acoes.add(line(g, u, now, cooldowns, preso, EconomyCooldownKeys.CD_CRIME, "crime",
                EconomyDefaults.CRIME_COOLDOWN_S, Slot.WEAPON, false, equipped, "equipe uma arma no `/mercado`"));
        acoes.add(line(g, u, now, cooldowns, preso, EconomyCooldownKeys.CD_ROB, "roubar",
                EconomyDefaults.ROB_COOLDOWN_S, Slot.WEAPON, false, equipped, "equipe uma arma no `/mercado`"));
        acoes.add(line(g, u, now, cooldowns, preso, EconomyCooldownKeys.CD_ORG, "crimeorganizado",
                EconomyDefaults.ORG_DAILY_COOLDOWN_S, Slot.WEAPON, false, equipped, "equipe uma arma no `/mercado`"));

        event.replyComponents(EconomiaPanelView.panel(EmbedColor.resolve(cfg), event.getMember(), w, st, ficha, acoes, cfg))
                .useComponentsV2().setEphemeral(true).queue();
    }

    // preso já resolvido uma vez no execute() — passado aqui pra não repetir jail.resolve (nem leituras redundantes).
    private ActionLine line(String g, String u, long now, CooldownRepository cd, boolean preso, String key,
                            String command, long cooldownS, Slot slot, boolean exempt, Map<Slot, Row> equipped, String hint) {
        boolean unlocked = slot == null || equipped.containsKey(slot);
        ActionStatus status = ActionStatus.resolve(now, cd.lastTs(g, u, key), cooldownS, unlocked, preso, exempt);
        String eqLabel = null;
        if (slot != null && equipped.containsKey(slot)) {
            Row r = equipped.get(slot);
            Equip e = EquipmentCatalog.byKey(r.itemKey());
            eqLabel = (e == null ? "Item desconhecido" : e.name()) + " " + r.usosLeft() + "/"
                    + (e == null ? "?" : String.valueOf(e.maxUsos()));
        }
        return new ActionLine(command, status, hint, eqLabel);
    }
}
```
> **Nota:** `jail.resolve` é chamado **uma vez** no `execute` (aplica o release lazy da cadeia) e o `preso` resultante é passado pro `line(...)` — sem leituras redundantes de `user_crime_state`. Idem `now` e o mapa de equipamento: um por execução.

- [ ] **Step 3: Wire into BaseModule**

Em `BaseModule.register(...)`, no bloco da economia (perto de onde `jail`/`equipment` são registrados), adicionar:
```java
        registry.command(new dev.davimf.basebot.modules.base.commands.EconomiaCommand(jail));
```
(usa o campo `jail` já criado na fundação da economia.)

- [ ] **Step 4: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL; `ActionStatusTest` verde + suíte inteira.

- [ ] **Step 5: Commit** *(pular no modo no-commit)*

```bash
git add src/main/java/dev/davimf/basebot/modules/base/economy/EconomiaPanelView.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/EconomiaCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(eco): /economia — painel de ações + cooldowns + saldo + cadeia"
```

---

## Self-review (cobertura do spec)
- Resolver puro com precedência + `readyAt` padronizado (§Resolver) → Task 1. `EconomyCooldownKeys` anti-drift (§Chaves) → Task 1.
- Painel efêmero: saldo + status criminal + ações com estado + equipamento + ações úteis (`/fianca`,`/limparficha`) + rodapé do roubo (§View) → Task 2. Emojis via `Emojis` (§Objetivo/Global). `now` uma vez + equipamento uma vez por slot + `equippedLabel` null-safe (§Command + considerações do usuário) → Task 2 (`execute`/`line`).
- Gated por `eco:enabled`; sem migração/config nova. `jail.resolve` efeito-lazy documentado (§Observações de UX) → refletido na nota do Step 2.
