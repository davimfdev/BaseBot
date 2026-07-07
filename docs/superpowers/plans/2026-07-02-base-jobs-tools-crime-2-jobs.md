# Empregos/Equipamentos/Crime — Plano 2: Empregos — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax. **Depende do Plano 1 (Fundação) já implementado.**

**Goal:** Os três empregos especializados `/minerar`, `/cozinhar`, `/entregar` — cada um exige a ferramenta equipada do seu slot, consome durabilidade, tem cooldown próprio, e a entrega auto-debita combustível.

**Architecture:** Um resolver puro (`JobOutcome`) para o valor do lucro; um serviço fino (`JobService`) que orquestra cooldown → equipado → `useOnce` (confirma) → paga (padrão dos métodos `String`-returning de `EconomyService`); comandos que aplicam o guard de cadeia e chamam o serviço.

**Tech Stack:** Java 22, JDA 6.4.2, SQLite/HikariCP, JUnit 5 (sem Mockito).

## Global Constraints

- **Base ≠ facs.** Gated por `eco:enabled` antes de qualquer mutação/cooldown.
- **Ordem:** `eco:enabled` → `blockedIfJailed` → cooldown ativo? → equipado? → `useOnce` (confirma) → paga → grava cooldown. Cooldown **só após tentativa válida**.
- **Combustível auto-debitado** na entrega; `cash < fuel` → recusa **sem** cooldown.
- **Ferramenta quebra na ação (usos→0):** a ação **conclui** e avisa.
- **`useOnce` = `NOT_FOUND`/`NOT_OWNER`** → aborta sem pagar nem gravar cooldown.
- Consome do Plano 1: `EquipmentCatalog`(+`Slot`,`Equip`), `InventoryRepository`(`equipped`,`useOnce`,`UseResult`/`UseResultType`,`Row`), `JailService.blockedIfJailed`, `WalletRepository.addCash`/`tryDebitCash`/`get`, `CooldownRepository`, `EconomyConfig`, `EconomyFormat`, `EconomyDefaults`.
- **Spec:** §4 de `docs/superpowers/specs/2026-07-02-base-jobs-tools-crime-design.md`.

---

### Task 1: `JobOutcome` (resolver puro do lucro)

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/economy/JobOutcome.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/economy/JobOutcomeTest.java`

**Interfaces:**
- Produces: `long JobOutcome.reward(long min, long max, long roll)` — valor em `[min, max]` determinístico pelo `roll` (roll injetável → testável).

- [ ] **Step 1: Write the failing test**

`JobOutcomeTest.java`:
```java
package dev.davimf.basebot.modules.base.economy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JobOutcomeTest {
    @Test
    void rewardStaysInRange() {
        for (long roll = 0; roll < 1000; roll++) {
            long r = JobOutcome.reward(60, 120, roll);
            assertTrue(r >= 60 && r <= 120, "fora do range: " + r);
        }
    }

    @Test
    void rewardIsDeterministicByRoll() {
        assertEquals(JobOutcome.reward(60, 120, 7), JobOutcome.reward(60, 120, 7));
        assertEquals(60, JobOutcome.reward(60, 120, 0));
        assertEquals(120, JobOutcome.reward(60, 120, 60)); // span=61 → roll 60 = max
    }

    @Test
    void rewardHandlesEqualMinMax() {
        assertEquals(100, JobOutcome.reward(100, 100, 999));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.economy.JobOutcomeTest"`
Expected: FAIL — `JobOutcome` não existe.

- [ ] **Step 3: Write the implementation**

`JobOutcome.java`:
```java
package dev.davimf.basebot.modules.base.economy;

/** Resolver puro do lucro de um emprego dado um roll (determinístico → testável). */
public final class JobOutcome {
    private JobOutcome() {}

    /** Valor em [min, max]; {@code roll} pode ser qualquer inteiro não-negativo. */
    public static long reward(long min, long max, long roll) {
        if (max <= min) {
            return min;
        }
        long span = max - min + 1;
        return min + Math.floorMod(roll, span);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.economy.JobOutcomeTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/economy/JobOutcome.java \
        src/test/java/dev/davimf/basebot/modules/base/economy/JobOutcomeTest.java
git commit -m "feat(eco): JobOutcome — resolver puro do lucro dos empregos"
```

---

### Task 2: `JobService` + comandos `/minerar` `/cozinhar` `/entregar` + wiring

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/economy/JobService.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/MinerarCommand.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/CozinharCommand.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/EntregarCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/economy/EconomyDefaults.java` (cooldowns dos empregos)

**Interfaces:**
- Consumes: Task 1 `JobOutcome`; Plano 1 (`InventoryRepository`, `JailService`, `EquipmentCatalog`); `WalletRepository`, `CooldownRepository`, `EconomyConfig`, `EconomyFormat`, `EconomyDefaults`.
- Produces: `JobService(BotContext)` — `String runMining(Guild,Member)`, `String runCooking(Guild,Member)`, `String runDelivery(Guild,Member)` (mensagens formatadas, padrão de `EconomyService`). Comandos `MinerarCommand`/`CozinharCommand`/`EntregarCommand` (recebem `JobService` + `JailService`).

> Build-verified (o resolver puro está testado; o serviço orquestra JDA/DB — sem teste unitário, padrão do projeto).

- [ ] **Step 1: Add cooldown constants**

Em `EconomyDefaults.java`:
```java
    public static final long MINE_COOLDOWN_S = 1_800;   // 30min
    public static final long COOK_COOLDOWN_S = 1_800;   // 30min
    public static final long DELIVERY_COOLDOWN_S = 900; // 15min
```

- [ ] **Step 2: Write `JobService`**

`JobService.java`:
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Equip;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.Row;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.UseResult;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.UseResultType;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.util.concurrent.ThreadLocalRandom;

/** Empregos: cooldown → equipado → useOnce (confirma) → paga. Devolve mensagem formatada. */
public final class JobService {

    private final BotContext ctx;
    private final InventoryRepository inv;
    private final WalletRepository wallets;
    private final CooldownRepository cooldowns;

    public JobService(BotContext ctx) {
        this.ctx = ctx;
        this.inv = new InventoryRepository(ctx.database().sqlite());
        this.wallets = new WalletRepository(ctx.database().sqlite());
        this.cooldowns = new CooldownRepository(ctx.database().sqlite());
    }

    private GuildConfig cfg(Guild g) { return ctx.database().guildConfig().findOrEmpty(g.getId()); }

    private String onCooldown(Guild g, String u, String action, long cooldownS) {
        long readyAt = cooldowns.lastTs(g.getId(), u, action) + cooldownS * 1000L;
        return System.currentTimeMillis() < readyAt
                ? "Aguarde — disponível novamente <t:" + (readyAt / 1000) + ":R>." : null;
    }

    public String runMining(Guild g, Member m) { return runTool(g, m, Slot.MINING, "minerar",
            EconomyDefaults.MINE_COOLDOWN_S, "picareta", Emojis.of(Emojis.PICKAXE, "⛏️")); }

    public String runCooking(Guild g, Member m) { return runTool(g, m, Slot.COOKING, "cozinhar",
            EconomyDefaults.COOK_COOLDOWN_S, "utensílio de cozinha", Emojis.of(Emojis.COOK, "🍳")); }

    private String runTool(Guild g, Member m, Slot slot, String action, long cd, String noun, String emoji) {
        GuildConfig cfg = cfg(g);
        String u = m.getId();
        String cdMsg = onCooldown(g, u, action, cd);
        if (cdMsg != null) {
            return cdMsg;
        }
        Row row = inv.equipped(g.getId(), u, slot);
        if (row == null) {
            return "Equipe " + (slot == Slot.MINING ? "uma " : "um ") + noun + " no `/inventario` primeiro.";
        }
        Equip e = EquipmentCatalog.byKey(row.itemKey());
        UseResult use = inv.useOnce(g.getId(), u, row.id());
        if (use.type() == UseResultType.NOT_FOUND || use.type() == UseResultType.NOT_OWNER) {
            return "Seu " + noun + " não está mais disponível — equipe de novo.";
        }
        long amount = JobOutcome.reward(e.payoutMin(), e.payoutMax(), ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
        wallets.addCash(g.getId(), u, amount);
        cooldowns.stamp(g.getId(), u, action, System.currentTimeMillis());
        String broke = use.type() == UseResultType.USED_AND_BROKE ? "\n-# Seu " + noun + " quebrou." : "";
        return emoji + " " + e.name() + " rendeu **+" + EconomyFormat.formatNamed(amount, cfg) + "**." + broke;
    }

    public String runDelivery(Guild g, Member m) {
        GuildConfig cfg = cfg(g);
        String u = m.getId();
        String cdMsg = onCooldown(g, u, "entregar", EconomyDefaults.DELIVERY_COOLDOWN_S);
        if (cdMsg != null) {
            return cdMsg;
        }
        Row row = inv.equipped(g.getId(), u, Slot.DELIVERY);
        if (row == null) {
            return "Equipe uma moto no `/inventario` primeiro.";
        }
        Equip e = EquipmentCatalog.byKey(row.itemKey());
        if (wallets.get(g.getId(), u).cash() < e.fuel()) {
            return "Sem dinheiro pro combustível (precisa de " + EconomyFormat.formatNamed(e.fuel(), cfg) + ").";
        }
        UseResult use = inv.useOnce(g.getId(), u, row.id());
        if (use.type() == UseResultType.NOT_FOUND || use.type() == UseResultType.NOT_OWNER) {
            return "Sua moto não está mais disponível — equipe de novo.";
        }
        wallets.tryDebitCash(g.getId(), u, e.fuel()); // combustível pago (checado acima)
        long gross = JobOutcome.reward(e.payoutMin(), e.payoutMax(), ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
        wallets.addCash(g.getId(), u, gross);
        cooldowns.stamp(g.getId(), u, "entregar", System.currentTimeMillis());
        String broke = use.type() == UseResultType.USED_AND_BROKE ? "\n-# Sua moto quebrou." : "";
        return Emojis.of(Emojis.MOTO, "🏍️") + " Entrega feita: **+" + EconomyFormat.formatNamed(gross, cfg)
                + "** (−" + EconomyFormat.formatNamed(e.fuel(), cfg) + " de combustível)." + broke;
    }
}
```
> **Emojis:** confirme `PICKAXE`/`COOK`/`MOTO` em `Emojis.java`; se não existirem, use um existente adequado (ex.: `SWORDS`/`GEAR`/`STREAM`) — o 2º arg de `Emojis.of` é o fallback unicode e mantém o build verde.

- [ ] **Step 3: Write the commands + wire**

Cada comando espelha `CrimeCommand` (efêmero na borda, `Replies.reply` no resultado) + guard de cadeia. Ex. `MinerarCommand`:
```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.JailService;
import dev.davimf.basebot.modules.base.economy.JobService;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /minerar — emprego que exige picareta equipada. */
public final class MinerarCommand implements SlashCommand {
    private final JobService jobs;
    private final JailService jail;
    public MinerarCommand(JobService jobs, JailService jail) { this.jobs = jobs; this.jail = jail; }

    @Override public String name() { return "minerar"; }
    @Override public SlashCommandData data() { return Commands.slash("minerar", "Minera com a picareta equipada."); }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!EconomyConfig.enabled(cfg)) {
            Replies.ephemeral(event, ctx, "Economia desativada neste servidor.");
            return;
        }
        if (jail.blockedIfJailed(event, ctx, event.getGuild().getId(), event.getMember().getId())) {
            return;
        }
        Replies.reply(event, ctx, jobs.runMining(event.getGuild(), event.getMember()));
    }
}
```
`CozinharCommand` (name "cozinhar", `jobs.runCooking`) e `EntregarCommand` (name "entregar", `jobs.runDelivery`) são idênticos trocando nome/descrição/método.

Em `BaseModule.register(...)`, após o bloco da fundação (Plano 1), guardar campo e registrar:
```java
        // Empregos (Base) — exigem ferramenta equipada.
        dev.davimf.basebot.modules.base.economy.JobService jobs =
                new dev.davimf.basebot.modules.base.economy.JobService(ctx);
        registry.command(new dev.davimf.basebot.modules.base.commands.MinerarCommand(jobs, jail));
        registry.command(new dev.davimf.basebot.modules.base.commands.CozinharCommand(jobs, jail));
        registry.command(new dev.davimf.basebot.modules.base.commands.EntregarCommand(jobs, jail));
```
(`jail` é o campo `JailService` criado no Plano 1.)

- [ ] **Step 4: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL; `JobOutcomeTest` verde.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/economy/JobService.java \
        src/main/java/dev/davimf/basebot/modules/base/economy/EconomyDefaults.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/MinerarCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/CozinharCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/EntregarCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(eco): empregos /minerar /cozinhar /entregar (ferramenta + combustível)"
```

---

## Self-review (cobertura — Plano 2)
- Empregos com ferramenta equipada, cooldown próprio, durabilidade, combustível auto-debitado, quebra-conclui (§4) → Tasks 1–2. Ordem/cooldown-após-tentativa-válida e `useOnce` confirmado (§3.5) → JobService. Resolver puro testado → Task 1.
