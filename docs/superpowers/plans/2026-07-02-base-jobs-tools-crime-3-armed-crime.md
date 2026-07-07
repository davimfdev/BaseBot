# Empregos/Equipamentos/Crime — Plano 3: Crime armado — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax. **Depende dos Planos 1 e 2.**

**Goal:** `/crime` e `/roubar` passam a **exigir arma equipada**; chance e retorno escalam com a arma; ficha suja penaliza; toda falha destrói a arma. `/crime` falho: multa (sem cadeia). `/roubar`: cap por tier, carteira protegida em 500, cooldown por par 12h.

**Architecture:** Reescreve os resolvers puros `CrimeOutcome`/`RobOutcome` (agora com `bonus`/`fichaPenalty`/`mult`/`cap`/`floor`, tudo testado). Um `CrimeEconomyService` orquestra (guard → arma equipada → `useOnce`/`destroy` confirmado → paga/transfere → cooldowns). `CrimeCommand`/`RoubarCommand` passam a chamar o novo serviço; os métodos antigos `EconomyService.crime/rob` são removidos.

**Tech Stack:** Java 22, JDA 6.4.2, SQLite/HikariCP, JUnit 5 (sem Mockito).

## Global Constraints

- **Base ≠ facs.** Gated por `eco:enabled` antes de mutação. Guard de cadeia em ambos.
- **Ordem segura:** consumir/destruir a arma **antes** de pagar/transferir; consumo válido = **só `USED`/`USED_AND_BROKE`** (trate `NOT_FOUND`/`NOT_OWNER`/**`PERMANENT`** como inválido → aborta sem pagar nem cooldown; armas do v1 nunca são permanentes, mas não pague sem consumo de verdade). `destroy` válido = `DESTROYED`.
- **Toda falha destrói a arma** (crime, roubo). **Crime solo NÃO prende.**
- **Multa** = `min(rolled, cash)`, nunca negativa.
- **Roubo:** carteira do alvo protegida em **500** (`stealable = max(0, targetCash−500)`; ≤0 → recusa sem cooldown); `stolen = min(rolled×mult, robCap, stealable)`; **cooldown por par atacante→alvo 12h** (`eco_cooldowns` ação `rob:<targetId>`).
- Consome dos Planos 1–2: `InventoryRepository`(`equipped`,`useOnce`,`destroy`,`UseResultType`,`DestroyResultType`), `JailService`(`blockedIfJailed`,`fichaSuja`), `EquipmentCatalog`, `WalletRepository`(`get`,`addCash`,`tryDebitCash`,`transfer`), `CooldownRepository`, `EconomyConfig`, `EconomyFormat`, `EconomyDefaults` (`CRIME_*`,`ROB_*`,`FICHA_PENALTY_PCT`).
- **Spec:** §5 de `docs/superpowers/specs/2026-07-02-base-jobs-tools-crime-design.md`.

---

### Task 1: Reescrever `CrimeOutcome` e `RobOutcome` (puros, com arma+ficha)

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/economy/CrimeOutcome.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/economy/RobOutcome.java`
- Modify: `src/test/java/dev/davimf/basebot/modules/base/economy/CrimeOutcomeTest.java`
- Modify: `src/test/java/dev/davimf/basebot/modules/base/economy/RobOutcomeTest.java`

**Interfaces:**
- Produces:
  - `record CrimeOutcome(boolean success, long gain, long fine)`; `static CrimeOutcome resolve(int roll, int base, int bonus, int fichaPenalty, long winRolled, double mult, long fineRolled, long cash)`.
  - `record RobOutcome(boolean success, long stolen, long fine)`; `static RobOutcome resolve(int roll, int base, int bonus, int fichaPenalty, int stealPct, long targetCash, double mult, long robCap, long protectedFloor, long fineRolled, long attackerCash)`.
  - Ambos clampam a chance em `[5, 95]`.

- [ ] **Step 1: Rewrite the tests**

`CrimeOutcomeTest.java`:
```java
package dev.davimf.basebot.modules.base.economy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrimeOutcomeTest {
    @Test
    void successAppliesMultToWin() {
        // base 50 + bonus 40 = 90% → roll 10 sucesso; win 200 × 2.5 = 500
        CrimeOutcome o = CrimeOutcome.resolve(10, 50, 40, 0, 200, 2.5, 100, 9999);
        assertTrue(o.success());
        assertEquals(500, o.gain());
        assertEquals(0, o.fine());
    }

    @Test
    void fichaPenaltyLowersChance() {
        // base 50 + 0 − 15 = 35% → roll 40 falha
        CrimeOutcome o = CrimeOutcome.resolve(40, 50, 0, 15, 200, 1.0, 100, 9999);
        assertFalse(o.success());
        assertEquals(100, o.fine());
    }

    @Test
    void fineIsFlooredToCash() {
        // falha, multa rolada 250 mas só tem 30 → multa 30
        CrimeOutcome o = CrimeOutcome.resolve(99, 50, 0, 0, 200, 1.0, 250, 30);
        assertFalse(o.success());
        assertEquals(30, o.fine());
    }

    @Test
    void chanceClampsAt95() {
        // base 50 + bonus 999 → clamp 95; roll 96 falha
        assertFalse(CrimeOutcome.resolve(96, 50, 999, 0, 1, 1.0, 1, 9999).success());
        assertTrue(CrimeOutcome.resolve(94, 50, 999, 0, 1, 1.0, 1, 9999).success());
    }
}
```

`RobOutcomeTest.java`:
```java
package dev.davimf.basebot.modules.base.economy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RobOutcomeTest {
    @Test
    void stolenAppliesMultThenCaps() {
        // sucesso: 30% de 100000 = 30000 × 2.5 = 75000, capado no robCap 50000
        RobOutcome o = RobOutcome.resolve(10, 40, 40, 0, 30, 100_000, 2.5, 50_000, 500, 100, 9999);
        assertTrue(o.success());
        assertEquals(50_000, o.stolen());
    }

    @Test
    void stolenRespectsProtectedFloor() {
        // alvo tem 800, floor 500 → stealable 300; 30% de 800 = 240 ×1 = 240 <= 300 → 240
        RobOutcome o = RobOutcome.resolve(0, 40, 5, 0, 30, 800, 1.0, 5_000, 500, 50, 9999);
        assertTrue(o.success());
        assertEquals(240, o.stolen());
    }

    @Test
    void stolenNeverBreaksFloorEvenWithMult() {
        // alvo 600, floor 500 → stealable 100; 30% de 600=180×2.5=450 capado em stealable 100
        RobOutcome o = RobOutcome.resolve(0, 40, 40, 0, 30, 600, 2.5, 50_000, 500, 50, 9999);
        assertEquals(100, o.stolen());
    }

    @Test
    void failFineFlooredToAttackerCash() {
        RobOutcome o = RobOutcome.resolve(99, 40, 0, 0, 30, 100_000, 1.0, 5_000, 500, 200, 40);
        assertFalse(o.success());
        assertEquals(40, o.fine());
        assertEquals(0, o.stolen());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.economy.CrimeOutcomeTest" --tests "dev.davimf.basebot.modules.base.economy.RobOutcomeTest"`
Expected: FAIL — assinaturas antigas não batem (compilação).

- [ ] **Step 3: Rewrite the resolvers**

`CrimeOutcome.java`:
```java
package dev.davimf.basebot.modules.base.economy;

/** Resultado puro de /crime. Chance = clamp(base+bonus−fichaPenalty, 5, 95). */
public record CrimeOutcome(boolean success, long gain, long fine) {
    public static CrimeOutcome resolve(int roll, int base, int bonus, int fichaPenalty,
                                       long winRolled, double mult, long fineRolled, long cash) {
        int chance = Math.max(5, Math.min(95, base + bonus - fichaPenalty));
        if (roll < chance) {
            return new CrimeOutcome(true, Math.round(winRolled * mult), 0);
        }
        return new CrimeOutcome(false, 0, Math.min(fineRolled, Math.max(0, cash)));
    }
}
```

`RobOutcome.java`:
```java
package dev.davimf.basebot.modules.base.economy;

/** Resultado puro de /roubar. Chance = clamp(base+bonus−fichaPenalty, 5, 95); stolen capado por robCap e floor. */
public record RobOutcome(boolean success, long stolen, long fine) {
    public static RobOutcome resolve(int roll, int base, int bonus, int fichaPenalty, int stealPct,
                                     long targetCash, double mult, long robCap, long protectedFloor,
                                     long fineRolled, long attackerCash) {
        int chance = Math.max(5, Math.min(95, base + bonus - fichaPenalty));
        long stealable = Math.max(0, targetCash - protectedFloor);
        if (roll < chance) {
            long raw = Math.round(targetCash * stealPct / 100.0 * mult);
            long stolen = Math.max(0, Math.min(Math.min(raw, robCap), stealable));
            return new RobOutcome(true, stolen, 0);
        }
        return new RobOutcome(false, 0, Math.min(fineRolled, Math.max(0, attackerCash)));
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.economy.CrimeOutcomeTest" --tests "dev.davimf.basebot.modules.base.economy.RobOutcomeTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/economy/CrimeOutcome.java \
        src/main/java/dev/davimf/basebot/modules/base/economy/RobOutcome.java \
        src/test/java/dev/davimf/basebot/modules/base/economy/CrimeOutcomeTest.java \
        src/test/java/dev/davimf/basebot/modules/base/economy/RobOutcomeTest.java
git commit -m "feat(eco): CrimeOutcome/RobOutcome com arma+ficha+cap+floor (puros)"
```

---

### Task 2: `CrimeEconomyService` + `/crime`/`/roubar` armados + wiring

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/economy/CrimeEconomyService.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/commands/CrimeCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/commands/RoubarCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/economy/EconomyService.java` (remover `crime`/`rob`, agora substituídos)
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java`

**Interfaces:**
- Consumes: Task 1 resolvers; Planos 1–2 (`InventoryRepository`, `JailService`, `EquipmentCatalog`, `WalletRepository`, `CooldownRepository`); `EconomyConfig`/`EconomyFormat`/`EconomyDefaults`.
- Produces: `CrimeEconomyService(BotContext)` — `String runCrime(Guild, Member)`, `String runRobbery(Guild, Member attacker, Member target)`. `CrimeCommand`/`RoubarCommand` passam a receber `CrimeEconomyService` + `JailService`.

> Build-verified (resolvers testados na Task 1).

- [ ] **Step 1: Write `CrimeEconomyService`**

`CrimeEconomyService.java`:
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Equip;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.Row;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.UseResult;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.UseResultType;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.DestroyResultType;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.util.concurrent.ThreadLocalRandom;

/** /crime e /roubar armados: arma obrigatória, consumo/destruição antes de pagar, ficha penaliza. */
public final class CrimeEconomyService {

    private final BotContext ctx;
    private final InventoryRepository inv;
    private final WalletRepository wallets;
    private final CooldownRepository cooldowns;
    private final JailService jail;

    public CrimeEconomyService(BotContext ctx, JailService jail) {
        this.ctx = ctx;
        this.jail = jail;
        this.inv = new InventoryRepository(ctx.database().sqlite());
        this.wallets = new WalletRepository(ctx.database().sqlite());
        this.cooldowns = new CooldownRepository(ctx.database().sqlite());
    }

    private GuildConfig cfg(Guild g) { return ctx.database().guildConfig().findOrEmpty(g.getId()); }

    private String cd(Guild g, String u, String action, long s) {
        long readyAt = cooldowns.lastTs(g.getId(), u, action) + s * 1000L;
        return System.currentTimeMillis() < readyAt
                ? "Aguarde — disponível novamente <t:" + (readyAt / 1000) + ":R>." : null;
    }

    private static int rnd(int bound) { return ThreadLocalRandom.current().nextInt(bound); }
    private static long rnd(long min, long max) {
        return max <= min ? min : min + ThreadLocalRandom.current().nextLong(max - min + 1);
    }

    public String runCrime(Guild g, Member m) {
        GuildConfig cfg = cfg(g);
        String u = m.getId();
        String cdMsg = cd(g, u, "crime", EconomyDefaults.CRIME_COOLDOWN_S);
        if (cdMsg != null) {
            return cdMsg;
        }
        Row weapon = inv.equipped(g.getId(), u, Slot.WEAPON);
        if (weapon == null) {
            return "Equipe uma arma no `/inventario` pra cometer crimes.";
        }
        Equip w = EquipmentCatalog.byKey(weapon.itemKey());
        int penalty = jail.fichaSuja(g.getId(), u) ? EconomyDefaults.FICHA_PENALTY_PCT : 0;
        long cash = wallets.get(g.getId(), u).cash();
        CrimeOutcome o = CrimeOutcome.resolve(rnd(100), EconomyDefaults.CRIME_SUCCESS_PCT, w.chanceBonus(), penalty,
                rnd(EconomyDefaults.CRIME_WIN_MIN, EconomyDefaults.CRIME_WIN_MAX), w.mult(),
                rnd(EconomyDefaults.CRIME_FINE_MIN, EconomyDefaults.CRIME_FINE_MAX), cash);
        if (o.success()) {
            UseResult use = inv.useOnce(g.getId(), u, weapon.id());
            if (use.type() != UseResultType.USED && use.type() != UseResultType.USED_AND_BROKE) {
                return "Sua arma não está mais disponível — equipe de novo."; // NOT_FOUND/NOT_OWNER/PERMANENT
            }
            wallets.addCash(g.getId(), u, o.gain());
            cooldowns.stamp(g.getId(), u, "crime", System.currentTimeMillis());
            String broke = use.type() == UseResultType.USED_AND_BROKE ? "\n-# Sua " + w.name() + " quebrou." : "";
            return Emojis.of(Emojis.SKULL, "🔫") + " Crime bem-sucedido! **+" + EconomyFormat.formatNamed(o.gain(), cfg) + "**." + broke;
        }
        // Falha: destrói a arma ANTES da multa.
        if (inv.destroy(g.getId(), u, weapon.id()).type() != DestroyResultType.DESTROYED) {
            return "Sua arma não está mais disponível — equipe de novo.";
        }
        wallets.tryDebitCash(g.getId(), u, o.fine());
        cooldowns.stamp(g.getId(), u, "crime", System.currentTimeMillis());
        return Emojis.of(Emojis.KICK, "🚔") + " Você foi pego! Perdeu a **" + w.name() + "** e pagou **"
                + EconomyFormat.formatNamed(o.fine(), cfg) + "** de multa.";
    }

    public String runRobbery(Guild g, Member actor, Member target) {
        if (target.getUser().isBot() || target.getId().equals(actor.getId())) {
            return "Alvo inválido.";
        }
        GuildConfig cfg = cfg(g);
        String u = actor.getId();
        String cdMsg = cd(g, u, "rob", EconomyDefaults.ROB_COOLDOWN_S);
        if (cdMsg != null) {
            return cdMsg;
        }
        String pairCd = cd(g, u, "rob:" + target.getId(), EconomyDefaults.ROB_PAIR_COOLDOWN_S);
        if (pairCd != null) {
            return "Você roubou " + target.getEffectiveName() + " há pouco — espere um tempo.";
        }
        Row weapon = inv.equipped(g.getId(), u, Slot.WEAPON);
        if (weapon == null) {
            return "Equipe uma arma no `/inventario` pra roubar.";
        }
        long targetCash = wallets.get(g.getId(), target.getId()).cash();
        if (targetCash - EconomyDefaults.ROB_PROTECTED_FLOOR <= 0) {
            return target.getEffectiveName() + " não tem dinheiro suficiente pra roubar.";
        }
        Equip w = EquipmentCatalog.byKey(weapon.itemKey());
        int penalty = jail.fichaSuja(g.getId(), u) ? EconomyDefaults.FICHA_PENALTY_PCT : 0;
        int stealPct = (int) rnd(EconomyDefaults.ROB_STEAL_MIN_PCT, EconomyDefaults.ROB_STEAL_MAX_PCT);
        long actorCash = wallets.get(g.getId(), u).cash();
        RobOutcome o = RobOutcome.resolve(rnd(100), EconomyDefaults.ROB_SUCCESS_PCT, w.chanceBonus(), penalty,
                stealPct, targetCash, w.mult(), w.robCap(), EconomyDefaults.ROB_PROTECTED_FLOOR,
                rnd(EconomyDefaults.ROB_FINE_MIN, EconomyDefaults.ROB_FINE_MAX), actorCash);
        long now = System.currentTimeMillis();
        if (o.success()) {
            UseResult use = inv.useOnce(g.getId(), u, weapon.id());
            if (use.type() != UseResultType.USED && use.type() != UseResultType.USED_AND_BROKE) {
                return "Sua arma não está mais disponível — equipe de novo."; // NOT_FOUND/NOT_OWNER/PERMANENT
            }
            wallets.transfer(g.getId(), target.getId(), u, o.stolen());
            cooldowns.stamp(g.getId(), u, "rob", now);
            cooldowns.stamp(g.getId(), u, "rob:" + target.getId(), now);
            String broke = use.type() == UseResultType.USED_AND_BROKE ? "\n-# Sua " + w.name() + " quebrou." : "";
            return Emojis.of(Emojis.SKULL, "🕵️") + " Você roubou **" + EconomyFormat.formatNamed(o.stolen(), cfg)
                    + "** de " + target.getAsMention() + "!" + broke;
        }
        // Falha: destrói a arma ANTES da multa (paga ao alvo).
        if (inv.destroy(g.getId(), u, weapon.id()).type() != DestroyResultType.DESTROYED) {
            return "Sua arma não está mais disponível — equipe de novo.";
        }
        if (o.fine() > 0) {
            wallets.transfer(g.getId(), u, target.getId(), o.fine());
        }
        cooldowns.stamp(g.getId(), u, "rob", now);
        cooldowns.stamp(g.getId(), u, "rob:" + target.getId(), now);
        return Emojis.of(Emojis.KICK, "🚔") + " Roubo fracassado! Perdeu a **" + w.name() + "** e pagou **"
                + EconomyFormat.formatNamed(o.fine(), cfg) + "** a " + target.getAsMention() + ".";
    }
}
```
> Adicionar em `EconomyDefaults`: `public static final long ROB_PROTECTED_FLOOR = 500;` e `public static final long ROB_PAIR_COOLDOWN_S = 43_200;` (12h). Emojis: confirmar `SKULL`/`KICK` (existem no projeto).

- [ ] **Step 2: Update the commands + remove old EconomyService methods + wire**

Em `CrimeCommand`: trocar o campo `EconomyService eco` por `CrimeEconomyService crime` + `JailService jail`; após o check `eco:enabled`, aplicar guard e chamar o serviço:
```java
        if (jail.blockedIfJailed(event, ctx, event.getGuild().getId(), event.getMember().getId())) {
            return;
        }
        Replies.reply(event, ctx, crime.runCrime(event.getGuild(), event.getMember()));
```
Em `RoubarCommand`: idem, mantendo a option `usuario`; após o guard: `Replies.reply(event, ctx, crime.runRobbery(event.getGuild(), event.getMember(), target));`.

Em `EconomyService.java`: **remover** os métodos `crime(...)` e `rob(...)` (agora mortos — substituídos). Remover o array `WORK_MSGS`? Não — `work` ainda usa. Remover só `crime`/`rob` e imports que ficarem sem uso (`CrimeOutcome`/`RobOutcome`/`ThreadLocalRandom` se não usados por `work`; `work` usa `ThreadLocalRandom`, então mantenha).

Em `BaseModule.register(...)`: onde hoje registra `CrimeCommand`/`RoubarCommand` com `economy`, criar o serviço e passar:
```java
        dev.davimf.basebot.modules.base.economy.CrimeEconomyService crimeService =
                new dev.davimf.basebot.modules.base.economy.CrimeEconomyService(ctx, jail);
        registry.command(new dev.davimf.basebot.modules.base.commands.CrimeCommand(crimeService, jail));
        registry.command(new dev.davimf.basebot.modules.base.commands.RoubarCommand(crimeService, jail));
```
(Remover as linhas antigas `new CrimeCommand(economy)` / `new RoubarCommand(economy)`.)

- [ ] **Step 3: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL; `CrimeOutcomeTest`/`RobOutcomeTest` verdes.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/economy/CrimeEconomyService.java \
        src/main/java/dev/davimf/basebot/modules/base/economy/EconomyService.java \
        src/main/java/dev/davimf/basebot/modules/base/economy/EconomyDefaults.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/CrimeCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/RoubarCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(eco): /crime e /roubar armados (arma obrigatória, cap/floor/ficha, perda de arma)"
```

---

## Self-review (cobertura — Plano 3)
- Arma obrigatória + chance/retorno por tier + ficha −15% + toda falha destrói a arma; crime sem cadeia; roubo com cap/floor/par-cooldown; ordem consumir-antes-de-pagar; multa min(cash) (§5 + §3.5) → Tasks 1–2. Resolvers puros testados (cap, floor, ficha, clamp, fine-floor) → Task 1.
