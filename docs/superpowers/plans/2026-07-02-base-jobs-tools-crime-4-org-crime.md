# Empregos/Equipamentos/Crime — Plano 4: Crime organizado — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax. **Depende dos Planos 1–3.**

**Goal:** `/crimeorganizado` — um lobby público (5–10 pessoas armadas, 1x/dia por pessoa) que, ao iniciar, revalida os participantes e resolve **em lote por-participante** com chance = base + média dos bônus − 5%/ficha, divide um pote grande por peso da arma no sucesso, e no fracasso prende + destrói as armas + suja a ficha.

**Architecture:** Resolvers puros (`OrgCrime.chance`, `OrgCrime.split`) testados; um `OrgCrimeService` com lobby **in-memory** (1 por guild, padrão do giveaway/jokenpo, `synchronized`) que faz snapshot + resolução **por-participante segura** (age só sobre quem teve a arma efetivamente mutada — nunca "cancela tudo" depois de já consumir a arma de alguém); view V2 + component handler (namespace `org`).

**Tech Stack:** Java 22, JDA 6.4.2, SQLite/HikariCP, JUnit 5 (sem Mockito).

## Global Constraints

- **Base ≠ facs.** Gated por `eco:enabled`. **Líder e participante passam pela MESMA validação de entrada** (economia ligada, não preso, arma equipada, cooldown 24h livre) — `open`/`join` devolvem erro explícito (String; null = ok), nunca um `null` mudo.
- **Cooldown diário 24h por pessoa gravado só na resolução** (não na entrada/início), e **só pra quem foi de fato afetado**.
- **Resolução por-participante (regra de atomicidade v1 — ver seção abaixo):** consumir (sucesso)/destruir (falha) a arma de cada um; **agir só sobre quem teve a arma mutada** (`USED`/`USED_AND_BROKE` / `DESTROYED`); quem der `NOT_FOUND`/`NOT_OWNER`/`PERMANENT` (corrida com `/crime`) é **excluído** (não recebe/preso). Se ninguém foi mutado, cancela. **Nunca** dizer "ninguém foi afetado" depois de já ter consumido a arma de alguém.
- **`PERMANENT` é inválido pra arma no v1** (armas têm durabilidade positiva) → tratado como não-mutado.
- **Chance** = `clamp(base + médiaBônus − 5×fichasSujas, 5, 90)`. **Pote** = `~4.500 × nº de quem participou de fato`, dividido **por peso** = `arma.mult`. **Falha:** cadeia 6–12h aleatória por pessoa + ficha suja (se cumprir a pena).
- **Lobby expira em 5 min** (obrigatório): sai de `lobbies` + painel vira "expirado".
- **`CooldownRepository` guarda milissegundos** (verificado: `EconomyService` stampa `System.currentTimeMillis()` e compara `lastTs + s*1000L`) — o código deste plano segue o mesmo padrão.
- Consome dos Planos 1–3: `InventoryRepository`(`equipped`,`useOnce`,`destroy`,`UseResultType`,`DestroyResultType`,`Row`), `JailService`(`resolve`/`Kind`/`fichaSuja`/`jailFor`), `EquipmentCatalog`, `WalletRepository.addCash`, `CooldownRepository`, `EconomyConfig`/`EconomyFormat`/`EconomyDefaults`.
- **Spec:** §6 de `docs/superpowers/specs/2026-07-02-base-jobs-tools-crime-design.md`.

---

### Task 1: `OrgCrime` (resolvers puros: chance + split)

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/economy/OrgCrime.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/economy/OrgCrimeTest.java`

**Interfaces:**
- Produces: `int OrgCrime.chance(int base, int avgBonus, int dirtyCount)` (clamp `[5, 90]`); `long[] OrgCrime.split(long pote, double[] weights)` (cada fatia = `floor(pote·w_i/Σw)`; o resto vai pra fatia de **maior peso** — no empate, o primeiro índice de peso máximo; soma exata = `pote`).

- [ ] **Step 1: Write the failing test**

`OrgCrimeTest.java`:
```java
package dev.davimf.basebot.modules.base.economy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrgCrimeTest {
    @Test
    void chanceCombinesBonusAndDirtyPenalty() {
        assertEquals(30 + 20 - 10, OrgCrime.chance(30, 20, 2)); // 40
    }

    @Test
    void chanceClampsTo90And5() {
        assertEquals(90, OrgCrime.chance(30, 100, 0));
        assertEquals(5, OrgCrime.chance(30, 0, 100));
    }

    @Test
    void splitIsProportionalAndSumsExactly() {
        long[] parts = OrgCrime.split(10_000, new double[]{2.5, 1.0, 1.0});
        assertEquals(10_000, parts[0] + parts[1] + parts[2]);
        assertTrue(parts[0] > parts[1]);           // fuzil leva mais
        assertEquals(parts[1], parts[2]);          // pesos iguais → fatias iguais
    }

    @Test
    void splitRemainderGoesToBiggestWeight() {
        // 100 / (1+1+1) = 33 cada, resto 1 → empate no maior peso → primeiro índice leva o resto
        long[] parts = OrgCrime.split(100, new double[]{1.0, 1.0, 1.0});
        assertEquals(34, parts[0]);
        assertEquals(33, parts[1]);
        assertEquals(33, parts[2]);
        assertEquals(100, parts[0] + parts[1] + parts[2]);
    }

    @Test
    void splitRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> OrgCrime.split(-1, new double[]{1.0}));
        assertThrows(IllegalArgumentException.class, () -> OrgCrime.split(100, new double[]{}));
        assertThrows(IllegalArgumentException.class, () -> OrgCrime.split(100, new double[]{1.0, -2.0}));
        assertThrows(IllegalArgumentException.class, () -> OrgCrime.split(100, new double[]{1.0, Double.NaN}));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.economy.OrgCrimeTest"`
Expected: FAIL — `OrgCrime` não existe.

- [ ] **Step 3: Write the implementation**

`OrgCrime.java`:
```java
package dev.davimf.basebot.modules.base.economy;

/** Resolvers puros do crime organizado: chance combinada e divisão do pote por peso. */
public final class OrgCrime {
    private OrgCrime() {}

    public static int chance(int base, int avgBonus, int dirtyCount) {
        return Math.max(5, Math.min(90, base + avgBonus - 5 * dirtyCount));
    }

    /** Divide {@code pote} por peso; resto (por arredondamento) vai pra fatia de maior peso. Soma exata = pote. */
    public static long[] split(long pote, double[] weights) {
        if (pote < 0 || weights.length == 0) {
            throw new IllegalArgumentException("pote/weights inválidos");
        }
        for (double weight : weights) {
            if (weight <= 0 || Double.isNaN(weight) || Double.isInfinite(weight)) {
                throw new IllegalArgumentException("peso inválido");
            }
        }
        long[] out = new long[weights.length];
        double sum = 0;
        for (double w : weights) {
            sum += w;
        }
        long distributed = 0;
        int maxIdx = 0;
        for (int i = 0; i < weights.length; i++) {
            out[i] = (long) Math.floor(pote * weights[i] / sum);
            distributed += out[i];
            if (weights[i] > weights[maxIdx]) {
                maxIdx = i;
            }
        }
        out[maxIdx] += pote - distributed; // resto pro maior peso (empate → primeiro índice)
        return out;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.economy.OrgCrimeTest"`
Expected: PASS.

- [ ] **Step 5: Commit** *(pular no modo no-commit)*

```bash
git add src/main/java/dev/davimf/basebot/modules/base/economy/OrgCrime.java \
        src/test/java/dev/davimf/basebot/modules/base/economy/OrgCrimeTest.java
git commit -m "feat(eco): OrgCrime — chance combinada + divisão do pote por peso (puros)"
```

---

## Regra de resolução do `OrgCrimeService` (atomicidade v1)

A resolução roda **síncrona** dentro de `start(guildId)` (`synchronized`), nesta ordem:

1. Pegar o lobby; se `started` já for `true` → recusar. Marcar `started=true`.
2. **Revalidar** cada participante (não preso, cooldown 24h livre) e **capturar** a arma equipada (`Row`) num mapa. Inválidos são descartados.
3. Se `< ORG_MIN` (5) armas válidas → **cancelar sem efeito** (remove o lobby, nenhum cooldown).
4. Calcular chance a partir do snapshot (soma dos bônus / nº, fichas sujas) e rolar (sucesso/falha).
5. **Mutar por-participante e agir só sobre quem foi mutado:**
   - Sucesso: `useOnce` de cada arma; **só** quem devolver `USED`/`USED_AND_BROKE` entra nos **winners** (com seu peso `mult`). `PERMANENT`/`NOT_FOUND`/`NOT_OWNER` → excluído.
   - Falha: `destroy` de cada arma; **só** quem devolver `DESTROYED` é preso + cooldown.
6. Se ninguém foi mutado → cancelar sem efeito.
7. **Sucesso:** pote = `ORG_POT_PER_PLAYER × nº de winners`, `OrgCrime.split` pelos pesos dos winners; creditar cada winner + gravar cooldown 24h dele.
8. **Falha:** cada preso recebe `jailFor(6–12h)` + cooldown 24h.
9. Remover o lobby.

> Nunca se retorna "ninguém foi afetado" **depois** de já ter consumido/destruído a arma de alguém: agir por-participante sobre o resultado real da mutação elimina o estado parcial sem precisar de transação cross-repo. **Follow-up ideal:** rodar tudo numa transação única (inventário+carteira+cooldown+cadeia na mesma `Connection`) com rollback — exige repos que aceitem `Connection`, fora do escopo do v1.

---

### Task 2: `OrgCrimeService` + `/crimeorganizado` + view + handler + wiring

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/economy/OrgCrimeService.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/economy/OrgCrimeView.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/economy/OrgCrimeComponentHandler.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/CrimeOrganizadoCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/economy/EconomyDefaults.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java`

**Interfaces:**
- Consumes: Task 1 `OrgCrime`; Planos 1–3; `Panels`/`Replies`/`EmbedColor`/`Emojis`/`ComponentId`/`ComponentHandler`/`SlashCommand`.
- Produces: `OrgCrimeService(BotContext, JailService)` — **params `String guildId`** (não `Guild`, pra testabilidade/desacoplamento): `Lobby lobby(String guildId)`, `String open(String guildId, String leaderId)` (erro/null), `String join(String guildId, String userId)` (erro/null), `String start(String guildId)` (mensagem pública), `void cancel(String guildId)`. `Lobby` expõe `leaderId()` e `participants()`. `OrgCrimeView` (`NS="org"`); `OrgCrimeComponentHandler` (namespace `org`, ações `join`/`start`); `CrimeOrganizadoCommand`.

> Build-verified (resolvers testados na Task 1; lobby/JDA sem teste unitário, padrão do projeto).

- [ ] **Step 1: Add constants**

Em `EconomyDefaults.java`:
```java
    public static final int ORG_BASE_CHANCE = 30;
    public static final long ORG_POT_PER_PLAYER = 4_500;
    public static final int ORG_MIN = 5, ORG_MAX = 10;
    public static final long ORG_DAILY_COOLDOWN_S = 86_400;   // 24h
    public static final long ORG_JAIL_MIN_S = 21_600;         // 6h
    public static final long ORG_JAIL_MAX_S = 43_200;         // 12h
```

- [ ] **Step 2: Write `OrgCrimeService`**

`OrgCrimeService.java`:
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Equip;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.Row;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.UseResultType;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.DestroyResultType;
import dev.davimf.basebot.util.Emojis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Crime organizado: lobby in-memory (1 por guild) + resolução em lote por-participante. */
public final class OrgCrimeService {

    public static final class Lobby {
        final String leaderId;
        final Set<String> participants = new LinkedHashSet<>();
        boolean started;
        Lobby(String leaderId) { this.leaderId = leaderId; participants.add(leaderId); }
        public String leaderId() { return leaderId; }
        /** Cópia imutável em ordem estável (o Set interno não vaza pra view/handler). */
        public List<String> participants() { return List.copyOf(participants); }
        public boolean started() { return started; }
    }

    /** Snapshot da arma válida de um participante, capturado na revalidação (evita byKey repetido). */
    private record WeaponSnapshot(Row row, Equip equip) {}

    private final BotContext ctx;
    private final JailService jail;
    private final InventoryRepository inv;
    private final WalletRepository wallets;
    private final CooldownRepository cooldowns;
    private final Map<String, Lobby> lobbies = new ConcurrentHashMap<>();

    public OrgCrimeService(BotContext ctx, JailService jail) {
        this.ctx = ctx;
        this.jail = jail;
        this.inv = new InventoryRepository(ctx.database().sqlite());
        this.wallets = new WalletRepository(ctx.database().sqlite());
        this.cooldowns = new CooldownRepository(ctx.database().sqlite());
    }

    public Lobby lobby(String guildId) { return lobbies.get(guildId); }
    public synchronized void cancel(String guildId) { lobbies.remove(guildId); }

    private boolean dailyCooldownActive(String g, String u) {
        return System.currentTimeMillis() < cooldowns.lastTs(g, u, "orgcrime")
                + EconomyDefaults.ORG_DAILY_COOLDOWN_S * 1000L;
    }

    /** Erro de entrada comum a líder e participante (null = pode entrar). */
    private String entryError(String guildId, String userId) {
        if (!EconomyConfig.enabled(ctx.database().guildConfig().findOrEmpty(guildId))) {
            return "A economia está desligada.";
        }
        if (jail.resolve(guildId, userId).kind() == JailService.Kind.PRESO) {
            return "Você está preso.";
        }
        if (inv.equipped(guildId, userId, Slot.WEAPON) == null) {
            return "Você precisa de uma arma equipada.";
        }
        if (dailyCooldownActive(guildId, userId)) {
            return "Você já participou de um crime organizado hoje.";
        }
        return null;
    }

    /** Abre o lobby; o líder passa pelas MESMAS validações de entrada. Devolve erro (null = aberto). */
    public synchronized String open(String guildId, String leaderId) {
        if (lobbies.containsKey(guildId)) {
            return "Já existe um crime organizado sendo montado aqui.";
        }
        String err = entryError(guildId, leaderId);
        if (err != null) {
            return err;
        }
        lobbies.put(guildId, new Lobby(leaderId));
        return null;
    }

    /** Tenta entrar (null = entrou). */
    public synchronized String join(String guildId, String userId) {
        Lobby l = lobbies.get(guildId);
        if (l == null || l.started) {
            return "Não há lobby aberto.";
        }
        if (l.participants.contains(userId)) {
            return "Você já está no grupo.";
        }
        if (l.participants.size() >= EconomyDefaults.ORG_MAX) {
            return "O grupo está cheio.";
        }
        String err = entryError(guildId, userId);
        if (err != null) {
            return err;
        }
        l.participants.add(userId);
        return null;
    }

    private static boolean validUse(UseResultType t) {
        return t == UseResultType.USED || t == UseResultType.USED_AND_BROKE; // PERMANENT/NOT_* = inválido
    }

    /** Resolve o crime (só o líder deve acionar — checado no handler). Devolve a mensagem pública. */
    public synchronized String start(String guildId) {
        Lobby l = lobbies.get(guildId);
        if (l == null) {
            return "Não há lobby.";
        }
        if (l.started) {
            return "Esse crime já foi iniciado.";
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        if (!EconomyConfig.enabled(cfg)) {           // defesa: nunca resolver com economia off
            return "A economia está desligada.";
        }
        l.started = true;
        long now = System.currentTimeMillis();

        // 1) Revalida + captura a arma equipada VÁLIDA (Row + Equip do catálogo) de cada participante.
        Map<String, WeaponSnapshot> weapons = new LinkedHashMap<>();
        for (String uid : l.participants) {
            if (jail.resolve(guildId, uid).kind() == JailService.Kind.PRESO) {
                continue;
            }
            if (dailyCooldownActive(guildId, uid)) {
                continue;
            }
            Row w = inv.equipped(guildId, uid, Slot.WEAPON);
            if (w == null) {
                continue;
            }
            Equip e = EquipmentCatalog.byKey(w.itemKey());
            if (e == null || e.slot() != Slot.WEAPON) { // dado antigo/inconsistente
                continue;
            }
            weapons.put(uid, new WeaponSnapshot(w, e));
        }
        if (weapons.size() < EconomyDefaults.ORG_MIN) {
            lobbies.remove(guildId);
            return "Crime cancelado — participantes válidos insuficientes (mín. " + EconomyDefaults.ORG_MIN + ").";
        }

        // 2) Chance a partir do snapshot.
        List<String> ids = new ArrayList<>(weapons.keySet());
        int sumBonus = 0, dirty = 0;
        for (String uid : ids) {
            sumBonus += weapons.get(uid).equip().chanceBonus();
            if (jail.fichaSuja(guildId, uid)) {
                dirty++;
            }
        }
        int chance = OrgCrime.chance(EconomyDefaults.ORG_BASE_CHANCE, sumBonus / ids.size(), dirty);
        boolean success = ThreadLocalRandom.current().nextInt(100) < chance;
        lobbies.remove(guildId);

        // 3) Muta por-participante; age só sobre quem teve a arma efetivamente mutada.
        if (success) {
            List<String> winners = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            for (String uid : ids) {
                if (validUse(inv.useOnce(guildId, uid, weapons.get(uid).row().id()).type())) {
                    winners.add(uid);
                    weights.add(weapons.get(uid).equip().mult());
                }
            }
            if (winners.isEmpty()) {
                return "Crime cancelado — ninguém tinha a arma na hora.";
            }
            long pote = EconomyDefaults.ORG_POT_PER_PLAYER * winners.size();
            double[] w = new double[weights.size()];
            for (int i = 0; i < w.length; i++) {
                w[i] = weights.get(i);
            }
            long[] cuts = OrgCrime.split(pote, w);
            StringBuilder sb = new StringBuilder(Emojis.of(Emojis.MONEY, "💰")
                    + " **Crime bem-sucedido!** Pote de " + EconomyFormat.format(pote, cfg) + " dividido:\n");
            for (int i = 0; i < winners.size(); i++) {
                wallets.addCash(guildId, winners.get(i), cuts[i]);
                cooldowns.stamp(guildId, winners.get(i), "orgcrime", now);
                sb.append("<@").append(winners.get(i)).append("> +")
                        .append(EconomyFormat.format(cuts[i], cfg)).append("\n");
            }
            return sb.toString();
        }
        // Falha: destrói e prende só quem tinha a arma.
        int jailed = 0;
        for (String uid : ids) {
            if (inv.destroy(guildId, uid, weapons.get(uid).row().id()).type() == DestroyResultType.DESTROYED) {
                long jailMs = ThreadLocalRandom.current().nextLong(
                        EconomyDefaults.ORG_JAIL_MIN_S, EconomyDefaults.ORG_JAIL_MAX_S + 1) * 1000L;
                jail.jailFor(guildId, uid, jailMs);
                cooldowns.stamp(guildId, uid, "orgcrime", now);
                jailed++;
            }
        }
        if (jailed == 0) {
            return "Crime cancelado — ninguém tinha a arma na hora.";
        }
        return Emojis.of(Emojis.KICK, "🚔") + " **A polícia chegou!** Os participantes perderam a arma e "
                + "foram presos. Paguem `/fianca` ou cumpram a pena.";
    }
}
```
> Emojis `MONEY`/`KICK` existem no projeto.

- [ ] **Step 3: View + handler + command + wiring (com expiração obrigatória de 5 min)**

`OrgCrimeView` (`NS="org"`): `panel(accent, lobby, cfg)` — Container V2, título `## 💰 Crime Organizado`, contagem `lobby.participants().size()/10`, menções (`<@id>`), e `ActionRow.of(Button.success(ComponentId.of(NS,"join"),"Entrar"), Button.primary(ComponentId.of(NS,"start"),"Iniciar"))`. `result(accent, msg)` e `expired(accent)` = painéis simples (sem botões).

`OrgCrimeComponentHandler` (namespace `org`, `onButton`) — **checa `EconomyConfig.enabled` primeiro** (botão público pode ser clicado após desligar a economia → `Replies.ephemeral` "economia desligada"):
```java
switch (id.action()) {
    case "join" -> {
        String err = svc.join(guildId, userId);
        if (err != null) { Replies.ephemeral(event, ctx, err); return; }
        var lobby = svc.lobby(guildId);                 // pode ter expirado entre o join e o edit
        if (lobby == null) { Replies.ephemeral(event, ctx, "O lobby expirou."); return; }
        event.editComponents(OrgCrimeView.panel(accent, lobby, cfg)).useComponentsV2().queue();
    }
    case "start" -> {
        var l = svc.lobby(guildId);
        if (l == null) { Replies.ephemeral(event, ctx, "Não há lobby."); }
        else if (!l.leaderId().equals(userId)) { Replies.ephemeral(event, ctx, "Só quem abriu pode iniciar."); }
        else {
            String res = svc.start(guildId);
            event.editComponents(OrgCrimeView.result(accent, res)).useComponentsV2().queue();
        }
    }
}
```

`CrimeOrganizadoCommand` — `/crimeorganizado`: checa guild + `event.getMember()`; chama `String err = svc.open(guildId, userId)`; se `err != null` → `Replies.ephemeral(event, ctx, err)`; senão posta o painel **público** e **agenda a expiração de 5 min dentro do callback do reply** (usando o `hook` recebido, não `event.getHook()` que pode não estar pronto):
```java
        event.replyComponents(OrgCrimeView.panel(accent, svc.lobby(guildId), cfg))
                .useComponentsV2()
                .queue(hook -> ctx.scheduler().once(() -> {
                    var l = svc.lobby(guildId);
                    if (l != null && !l.started()) {
                        svc.cancel(guildId);
                        hook.editOriginalComponents(OrgCrimeView.expired(accent)).useComponentsV2().queue(null, e -> {});
                    }
                }, 5, java.util.concurrent.TimeUnit.MINUTES));
```
(O `open` já validou eco/preso/arma/cooldown do líder, então o comando não precisa repetir essas checagens.)

Em `BaseModule.register(...)`, após o bloco de crime armado (Plano 3):
```java
        dev.davimf.basebot.modules.base.economy.OrgCrimeService orgCrime =
                new dev.davimf.basebot.modules.base.economy.OrgCrimeService(ctx, jail);
        registry.command(new dev.davimf.basebot.modules.base.commands.CrimeOrganizadoCommand(orgCrime));
        registry.component(new dev.davimf.basebot.modules.base.economy.OrgCrimeComponentHandler(orgCrime));
```

- [ ] **Step 4: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL; `OrgCrimeTest` verde + todos os testes anteriores.

- [ ] **Step 5: Commit** *(pular no modo no-commit)*

```bash
git add src/main/java/dev/davimf/basebot/modules/base/economy/OrgCrimeService.java \
        src/main/java/dev/davimf/basebot/modules/base/economy/OrgCrimeView.java \
        src/main/java/dev/davimf/basebot/modules/base/economy/OrgCrimeComponentHandler.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/CrimeOrganizadoCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/economy/EconomyDefaults.java \
        src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(eco): /crimeorganizado — lobby + resolução por-participante + cadeia/ficha"
```

---

## Self-review (cobertura — Plano 4)
- Lobby 5–10 armados, 1x/dia, revalidação no início, resolução por-participante **segura contra corrida de equipamento** (sem cancelamento falso depois de mutar arma; a atomicidade total cross-repo é follow-up), item_key validado contra o catálogo, chance combinada, split por peso (com guards), cadeia 6–12h + ficha na falha, cooldown só pra afetados; líder valida igual ao join (erro explícito); `start` também checa eco; `started` marcado; `PERMANENT` inválido; `participants()` imutável; expiração obrigatória de 5 min (agendada no callback do reply); join revalida o lobby + checa eco (§6 + §3.5) → Tasks 1–2. Resolvers puros (chance clamp + split soma-exata) testados → Task 1.
- **Follow-up documentado:** transação única cross-repo (rollback) e resultado em `record` (em vez de String) — não-bloqueantes pro v1.

## Nota de execução (todos os 4 planos)
Ordem obrigatória **1 → 2 → 3 → 4**. Smoke manual completo ao fim (economia ligada; comprar/equipar; cada emprego; crime armado sucesso/falha perde arma; roubo com cap/floor/par-cooldown; montar org de 5+ e resolver; fiança/ficha; abrir lobby em cooldown → recusa; lobby abandonado expira em 5 min).
