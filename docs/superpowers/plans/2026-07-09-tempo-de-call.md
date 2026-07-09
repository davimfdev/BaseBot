# Tempo de call — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** XP de voz deixa de acumular com o microfone fechado, e todo tempo "ativo" em call passa a ser contado por semana, com ranking no bot e tabela no Neon para o site.

**Architecture:** O SQLite continua sendo a fonte de verdade. `voice_sessions` ganha uma segunda watermark (`time_credited_until`) irmã da `xp_credited_until` que já existe; o ticker de 60s e um listener de mudanças de estado de voz creditam janelas `[watermark, agora]` em baldes semanais (`voice_weekly_time`). Um flusher periódico sobe os baldes sujos para o Postgres com upsert idempotente de total absoluto. Não há job de reset semanal: a semana é uma coluna.

**Tech Stack:** Java 22 (Gradle pinado ao JDK 22), JDA 6.4.2, SQLite (JDBC + `SqliteMigrator` próprio), Postgres/Neon (HikariCP + `PostgresMigrator`), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-07-09-tempo-de-call-design.md`

## Global Constraints

- **Sem Mockito.** Testes são JUnit 5 puro. Interfaces da JDA, quando inevitáveis, via `java.lang.reflect.Proxy`. Repositórios testam contra SQLite real em `@TempDir`.
- **Toda mensagem do bot é um Container Components V2.** Usar o helper `Panels` / `Replies`. Nunca texto puro.
- **Emojis sempre pelo registry:** `Emojis.of(Emojis.X, "fallback")`. Nunca Unicode cru.
- **Fuso da semana:** `America/Sao_Paulo`, constante única em `VoiceWeek.ZONE`.
- **Retenção:** 90 dias, constante única em `VoiceRetention.DAYS`.
- **Toda migration SQLite precisa ser acrescentada a `SqliteMigrator.MIGRATIONS`.** A lista é explícita; esquecer faz a migration nunca rodar, em silêncio.
- **Migrations Postgres não rodam no boot.** São aplicadas pela ferramenta `ApplyPostgresSchema` (não há `psql` nesta máquina).
- **Statements SQLite terminam em `;` no fim da linha**, e nunca há comentário depois do `;` — requisitos do `SqliteMigrator.splitStatements`.
- **Branch:** trabalhar direto na `main`. Não criar branches.
- Build: `./gradlew.bat compileJava` e `./gradlew.bat test --tests "<classe>"`.

## Desvios do spec (decididos aqui, com justificativa)

1. **Chaves de config** usam a convenção existente do projeto (`level:ignored-channels`), não a do spec (`voicetime.include_channels`). Ficam: `voicetime:include-channels`, `voicetime:exclude-channels`, `voicetime:include-categories`, `voicetime:exclude-categories`.

2. **A contagem de tempo é independente de `level:enabled`.** O ticker hoje faz `continue` quando leveling está desligado. Se o tempo pendurasse nesse laço, desligar XP mataria o ranking de call — coisa que o spec não pede. O ticker passa a sempre processar sessões; o **XP** é que fica condicionado a `LevelingConfig.enabled(cfg)`.

3. **`VoiceScope` é dividido em núcleo puro + adaptador JDA.** `VoiceScope.counts(channelId, categoryId, publicChannel, cfg)` é testável sem `Proxy`; `VoiceScope.inScope(AudioChannel, GuildConfig)` só resolve os três argumentos e delega.

## Bug pré-existente encontrado (Task 9)

`PostgresMigrator.MIGRATIONS` lista até `/db/postgres/007_updated_by.sql`, mas existem os arquivos `008_dashboard_audit.sql` e `009_verification_questions.sql`, referenciados em lugar nenhum. **Eles nunca foram aplicados.** A Task 9 os acrescenta junto com o `010`. Se o schema do Neon já tiver essas tabelas (aplicadas à mão), o ledger `schema_migrations` não sabe disso e o `010` roda mesmo assim — as três são `IF NOT EXISTS`, então re-aplicar é seguro.

## File Structure

**Criar:**

| Arquivo | Responsabilidade |
|---|---|
| `util/ConfigIds.java` | Parser tolerante de CSV de IDs |
| `modules/base/leveling/VoiceWeek.java` | `weekStart`, `splitByWeek`, `Slice` |
| `modules/base/leveling/VoiceStateSnapshot.java` | Estado de voz imutável, flags de deafen separadas |
| `modules/base/leveling/VoiceScope.java` | Canal conta tempo? Precedência + padrão público |
| `modules/base/leveling/VoiceTimeConfig.java` | Chaves e leitura das 4 listas |
| `modules/base/leveling/VoiceTimeRepository.java` | Baldes semanais no SQLite |
| `modules/base/leveling/VoiceSnapshots.java` | Monta snapshot a partir da JDA |
| `modules/base/leveling/VoiceSettler.java` | Credita a janela pendente de uma sessão |
| `modules/base/leveling/VoiceStateListener.java` | Eventos de mute/deafen → settle |
| `modules/base/leveling/VoiceTimeFlusher.java` | SQLite sujo → Postgres |
| `modules/base/leveling/VoiceRetention.java` | Constante de 90 dias |
| `modules/base/leveling/VoiceRetentionSweeper.java` | Poda diária |
| `modules/base/leveling/VoiceFormat.java` | `12h 34m` |
| `modules/base/leveling/TopCallView.java` | Painel do ranking |
| `modules/base/leveling/VoiceTimeComponentHandler.java` | Paginação do `/topcall` |
| `modules/base/commands/TopCallCommand.java` | `/topcall` |
| `modules/base/commands/TempoCallCommand.java` | `/tempocall` |
| `resources/db/sqlite/039_voice_time.sql` | Watermark + baldes |
| `resources/db/postgres/010_voice_weekly_time.sql` | Tabela para o site |

**Modificar:**

| Arquivo | Mudança |
|---|---|
| `leveling/VoiceEligibility.java` | `isEligible` → `xpEligible` + `timeEligible` sobre snapshot |
| `leveling/VoiceSessionRepository.java` | `time_credited_until` em `Open`; `openSession(g,u)`; poda |
| `leveling/VoiceXpBatch.java` | Credita tempo via `splitByWeek`; nova `Credit` |
| `leveling/VoiceXpTicker.java` | Usa snapshot; credita tempo; XP condicionado a `enabled` |
| `leveling/VoiceSessionListener.java` | Settle antes de fechar, com o canal antigo |
| `leveling/VoiceReconciler.java` | Ancora `time_credited_until` |
| `leveling/LevelingConfig.java` | `ignoredChannels` delega a `ConfigIds.parse` |
| `database/sqlite/SqliteMigrator.java` | + `039` |
| `database/postgres/PostgresMigrator.java` | + `008`, `009`, `010` |
| `modules/base/BaseModule.java` | Registra listener, comandos, handler, flusher, sweeper |
| `modules/base/setup/SetupView.java` | `voiceTimeScreen` + botão na `levelingScreen` |
| `modules/base/setup/SetupComponentHandler.java` | 5 cases novos |

---

### Task 1: `VoiceWeek` — a semana e a divisão de janelas

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceWeek.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceWeekTest.java`

**Interfaces:**
- Consumes: nada.
- Produces: `VoiceWeek.ZONE` (`ZoneId`), `VoiceWeek.weekStart(long) -> long`, `VoiceWeek.nextWeekStart(long) -> long`, `VoiceWeek.splitByWeek(long from, long to) -> List<VoiceWeek.Slice>`, `record VoiceWeek.Slice(long weekStart, long from, long to)` com `durationMs()`.

- [ ] **Step 1: Write the failing test**

`src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceWeekTest.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VoiceWeekTest {

    private static long at(int year, int month, int day, int hour, int min, int sec) {
        return ZonedDateTime.of(year, month, day, hour, min, sec, 0, VoiceWeek.ZONE)
                .toInstant().toEpochMilli();
    }

    @Test
    void mondayMidnightBelongsToItsOwnWeek() {
        // 2026-07-06 é uma segunda-feira.
        long monday = at(2026, 7, 6, 0, 0, 0);
        assertEquals(monday, VoiceWeek.weekStart(monday));
    }

    @Test
    void sundayNightBelongsToThePreviousWeek() {
        long sunday = at(2026, 7, 5, 23, 59, 59);
        long previousMonday = at(2026, 6, 29, 0, 0, 0);
        assertEquals(previousMonday, VoiceWeek.weekStart(sunday));
    }

    @Test
    void midWeekResolvesToTheMondayBefore() {
        long thursday = at(2026, 7, 9, 15, 30, 0);
        assertEquals(at(2026, 7, 6, 0, 0, 0), VoiceWeek.weekStart(thursday));
    }

    @Test
    void windowInsideOneWeekYieldsOneSlice() {
        long from = at(2026, 7, 8, 10, 0, 0);
        long to = at(2026, 7, 8, 10, 1, 0);
        List<VoiceWeek.Slice> slices = VoiceWeek.splitByWeek(from, to);
        assertEquals(1, slices.size());
        assertEquals(at(2026, 7, 6, 0, 0, 0), slices.get(0).weekStart());
        assertEquals(60_000L, slices.get(0).durationMs());
    }

    @Test
    void windowCrossingMondayIsSplitAndPreservesTotalDuration() {
        long from = at(2026, 7, 5, 23, 59, 30);   // domingo
        long to = at(2026, 7, 6, 0, 0, 30);       // segunda
        List<VoiceWeek.Slice> slices = VoiceWeek.splitByWeek(from, to);

        assertEquals(2, slices.size());
        assertEquals(at(2026, 6, 29, 0, 0, 0), slices.get(0).weekStart());
        assertEquals(at(2026, 7, 6, 0, 0, 0), slices.get(1).weekStart());
        assertEquals(30_000L, slices.get(0).durationMs());
        assertEquals(30_000L, slices.get(1).durationMs());
        assertEquals(to - from, slices.stream().mapToLong(VoiceWeek.Slice::durationMs).sum());
    }

    @Test
    void emptyOrInvertedWindowYieldsNoSlices() {
        long t = at(2026, 7, 8, 10, 0, 0);
        assertTrue(VoiceWeek.splitByWeek(t, t).isEmpty());
        assertTrue(VoiceWeek.splitByWeek(t, t - 1000).isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceWeekTest"`
Expected: FAIL — compilação quebra, `VoiceWeek` não existe.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceWeek.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * A semana do ranking de call: segunda 00:00 em {@link #ZONE}, representada pelo epoch millis
 * desse instante. Nenhum job zera nada — a virada da semana apenas passa a escrever noutra linha.
 */
public final class VoiceWeek {

    /** Fuso fixo. O Brasil não tem horário de verão desde 2019, mas o cálculo é zoned mesmo assim. */
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private VoiceWeek() {}

    /** Uma fatia de uma janela, já atribuída a uma semana. */
    public record Slice(long weekStart, long from, long to) {
        public long durationMs() {
            return to - from;
        }
    }

    /** Segunda-feira 00:00 (em {@link #ZONE}) da semana que contém {@code epochMillis}. */
    public static long weekStart(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZONE)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(ZONE)
                .toInstant().toEpochMilli();
    }

    /** Segunda-feira 00:00 seguinte à semana que contém {@code epochMillis}. */
    public static long nextWeekStart(long epochMillis) {
        return Instant.ofEpochMilli(weekStart(epochMillis)).atZone(ZONE)
                .toLocalDate().plusWeeks(1)
                .atStartOfDay(ZONE)
                .toInstant().toEpochMilli();
    }

    /**
     * Divide {@code [from, to)} nas fronteiras de semana. A soma das durações das fatias é
     * exatamente {@code to - from}. Janela vazia ou invertida devolve lista vazia.
     *
     * <p>É a ÚNICA forma de creditar tempo — nenhum outro caminho deve reimplementar isto.
     */
    public static List<Slice> splitByWeek(long from, long to) {
        List<Slice> out = new ArrayList<>();
        long cursor = from;
        while (cursor < to) {
            long boundary = nextWeekStart(cursor);
            long end = Math.min(to, boundary);
            out.add(new Slice(weekStart(cursor), cursor, end));
            cursor = end;
        }
        return out;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceWeekTest"`
Expected: PASS, 6 testes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceWeek.java src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceWeekTest.java
git commit -m "feat(voice): VoiceWeek — semana de segunda 00:00 e divisao de janelas"
```

---

### Task 2: `ConfigIds` — parser tolerante, extraído do que já existe

`LevelingConfig.ignoredChannels` já implementa exatamente este parser. Extrair e fazer os dois usarem o mesmo código (DRY), em vez de duplicar.

**Files:**
- Create: `src/main/java/dev/davimf/basebot/util/ConfigIds.java`
- Test: `src/test/java/dev/davimf/basebot/util/ConfigIdsTest.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/leveling/LevelingConfig.java:31-39`

**Interfaces:**
- Consumes: nada.
- Produces: `ConfigIds.parse(String raw) -> Set<String>` (ordem preservada, sem duplicatas).

- [ ] **Step 1: Write the failing test**

`src/test/java/dev/davimf/basebot/util/ConfigIdsTest.java`:

```java
package dev.davimf.basebot.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConfigIdsTest {

    @Test
    void nullOrBlankYieldsEmpty() {
        assertTrue(ConfigIds.parse(null).isEmpty());
        assertTrue(ConfigIds.parse("").isEmpty());
        assertTrue(ConfigIds.parse("   ").isEmpty());
    }

    @Test
    void trimsAndDropsEmptySegments() {
        assertEquals(Set.of("123", "456"), ConfigIds.parse("123, 456"));
        assertTrue(ConfigIds.parse(",,,").isEmpty());
        assertEquals(Set.of("123"), ConfigIds.parse(" ,123, "));
    }

    @Test
    void dropsDuplicatesAndPreservesOrder() {
        assertEquals(List.of("9", "7"), List.copyOf(ConfigIds.parse("9,7,9")));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.util.ConfigIdsTest"`
Expected: FAIL — `ConfigIds` não existe.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/dev/davimf/basebot/util/ConfigIds.java`:

```java
package dev.davimf.basebot.util;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lê as listas de IDs separadas por vírgula guardadas em {@code GuildConfig.settings}.
 * Tolerante ao que o dashboard e os selects da JDA produzem: {@code null}, vazio, espaços,
 * vírgulas soltas e duplicatas.
 */
public final class ConfigIds {

    private ConfigIds() {}

    /** Ordem de inserção preservada, sem duplicatas, sem vazios. */
    public static Set<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.util.ConfigIdsTest"`
Expected: PASS.

- [ ] **Step 5: Point `LevelingConfig` at it**

Em `LevelingConfig.java`, trocar o corpo de `ignoredChannels` e remover os imports que ficarem sem uso (`java.util.Arrays`, `java.util.LinkedHashSet`, `java.util.stream.Collectors`):

```java
    public static Set<String> ignoredChannels(GuildConfig cfg) {
        return dev.davimf.basebot.util.ConfigIds.parse(cfg.setting(KEY_IGNORED));
    }
```

- [ ] **Step 6: Run the leveling config tests to verify no regression**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.LevelingConfigTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/davimf/basebot/util/ConfigIds.java src/test/java/dev/davimf/basebot/util/ConfigIdsTest.java src/main/java/dev/davimf/basebot/modules/base/leveling/LevelingConfig.java
git commit -m "refactor(config): extrai ConfigIds.parse e reusa em LevelingConfig"
```

---

### Task 3: Snapshot + elegibilidade — o bug do mute morre aqui

Esta task já corrige o problema original: quem está de microfone fechado para de ganhar XP.

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceStateSnapshot.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceEligibility.java` (reescrever)
- Modify: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceXpTicker.java:42-56`
- Test: `src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceEligibilityTest.java` (reescrever)

**Interfaces:**
- Consumes: nada.
- Produces:
  - `record VoiceStateSnapshot(boolean bot, long humanCount, boolean selfMuted, boolean selfDeafened, boolean guildDeafened, boolean afkChannel, boolean inScope)`, com `deafened()`, `withSelfMuted(boolean)`, `withSelfDeafened(boolean)`, `withGuildDeafened(boolean)`.
  - `VoiceEligibility.xpEligible(VoiceStateSnapshot) -> boolean`
  - `VoiceEligibility.timeEligible(VoiceStateSnapshot) -> boolean`
  - `VoiceEligibility.isEligible(...)` **deixa de existir**.

- [ ] **Step 1: Write the failing test** (substitui o arquivo inteiro)

`src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceEligibilityTest.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VoiceEligibilityTest {

    /** Humano, dois na call, mic aberto, ouvindo, fora do AFK, canal no escopo. */
    private static VoiceStateSnapshot active() {
        return new VoiceStateSnapshot(false, 2, false, false, false, false, true);
    }

    @Test
    void activeMemberEarnsBoth() {
        assertTrue(VoiceEligibility.xpEligible(active()));
        assertTrue(VoiceEligibility.timeEligible(active()));
    }

    @Test
    void selfMuteBlocksBoth() {
        VoiceStateSnapshot s = active().withSelfMuted(true);
        assertFalse(VoiceEligibility.xpEligible(s));
        assertFalse(VoiceEligibility.timeEligible(s));
    }

    /**
     * Server-mute não pausa nada, e a garantia disso é ESTRUTURAL: o snapshot não modela
     * {@code guildMuted}, então nenhum predicado consegue consultá-lo. Este teste falha se
     * alguém acrescentar o campo — forçando a revisitar a decisão de produto em vez de
     * silenciosamente passar a punir quem foi mutado por um moderador.
     */
    @Test
    void serverMuteIsDeliberatelyNotModeled() {
        boolean hasGuildMuted = java.util.Arrays.stream(VoiceStateSnapshot.class.getRecordComponents())
                .anyMatch(rc -> rc.getName().equals("guildMuted"));
        assertFalse(hasGuildMuted,
                "server-mute nao deve pausar XP nem tempo; se este campo passou a existir, "
                        + "revise VoiceEligibility e o spec antes de remover este teste");
    }

    @Test
    void selfDeafenBlocksBoth() {
        VoiceStateSnapshot s = active().withSelfDeafened(true);
        assertFalse(VoiceEligibility.xpEligible(s));
        assertFalse(VoiceEligibility.timeEligible(s));
    }

    @Test
    void guildDeafenBlocksBoth() {
        VoiceStateSnapshot s = active().withGuildDeafened(true);
        assertFalse(VoiceEligibility.xpEligible(s));
        assertFalse(VoiceEligibility.timeEligible(s));
    }

    @Test
    void deafenedIsTheDisjunctionOfBothFlags() {
        assertFalse(active().deafened());
        assertTrue(active().withSelfDeafened(true).deafened());
        assertTrue(active().withGuildDeafened(true).deafened());
        assertTrue(active().withSelfDeafened(true).withGuildDeafened(true).deafened());
    }

    @Test
    void afkChannelBlocksBoth() {
        VoiceStateSnapshot s = new VoiceStateSnapshot(false, 2, false, false, false, true, true);
        assertFalse(VoiceEligibility.xpEligible(s));
        assertFalse(VoiceEligibility.timeEligible(s));
    }

    @Test
    void aloneInChannelBlocksXpButNotTime() {
        VoiceStateSnapshot s = new VoiceStateSnapshot(false, 1, false, false, false, false, true);
        assertFalse(VoiceEligibility.xpEligible(s));
        assertTrue(VoiceEligibility.timeEligible(s));
    }

    @Test
    void channelOutOfScopeBlocksTimeButNotXp() {
        VoiceStateSnapshot s = new VoiceStateSnapshot(false, 2, false, false, false, false, false);
        assertTrue(VoiceEligibility.xpEligible(s));
        assertFalse(VoiceEligibility.timeEligible(s));
    }

    @Test
    void botsEarnNothing() {
        VoiceStateSnapshot s = new VoiceStateSnapshot(true, 5, false, false, false, false, true);
        assertFalse(VoiceEligibility.xpEligible(s));
        assertFalse(VoiceEligibility.timeEligible(s));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceEligibilityTest"`
Expected: FAIL — `VoiceStateSnapshot` não existe.

- [ ] **Step 3: Write `VoiceStateSnapshot`**

`src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceStateSnapshot.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

/**
 * Estado de voz de um membro durante uma janela de tempo. Imutável e sem dependência da JDA,
 * para que a elegibilidade seja testável sem mocks.
 *
 * <p>As flags de deafen são separadas de propósito. Reconstruir o estado anterior a um evento
 * invertendo o agregado {@code deafened} está errado: se alguém já estava server-deafened e dá
 * self-deafen, o agregado é {@code true} antes e depois.
 *
 * <p>{@code guildMuted} não existe aqui: server-mute não afeta nenhum dos dois predicados.
 */
public record VoiceStateSnapshot(
        boolean bot,
        long humanCount,
        boolean selfMuted,
        boolean selfDeafened,
        boolean guildDeafened,
        boolean afkChannel,
        boolean inScope) {

    public boolean deafened() {
        return selfDeafened || guildDeafened;
    }

    public VoiceStateSnapshot withSelfMuted(boolean value) {
        return new VoiceStateSnapshot(bot, humanCount, value, selfDeafened, guildDeafened, afkChannel, inScope);
    }

    public VoiceStateSnapshot withSelfDeafened(boolean value) {
        return new VoiceStateSnapshot(bot, humanCount, selfMuted, value, guildDeafened, afkChannel, inScope);
    }

    public VoiceStateSnapshot withGuildDeafened(boolean value) {
        return new VoiceStateSnapshot(bot, humanCount, selfMuted, selfDeafened, value, afkChannel, inScope);
    }
}
```

- [ ] **Step 4: Rewrite `VoiceEligibility`**

`src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceEligibility.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

/**
 * Regras puras de elegibilidade em call.
 *
 * <p>Self-mute pausa XP e tempo; server-mute não pausa nada (decisão de produto: quem foi mutado
 * por um moderador não perde progresso). Deafen — self ou de servidor — e o canal AFK pausam
 * ambos. O mínimo de dois humanos vale só para XP: tempo conta sozinho, desde que o canal esteja
 * no escopo configurado.
 */
public final class VoiceEligibility {

    private VoiceEligibility() {}

    public static boolean xpEligible(VoiceStateSnapshot s) {
        return !s.bot() && s.humanCount() >= 2 && !s.deafened() && !s.afkChannel() && !s.selfMuted();
    }

    public static boolean timeEligible(VoiceStateSnapshot s) {
        return !s.bot() && !s.deafened() && !s.afkChannel() && !s.selfMuted() && s.inScope();
    }
}
```

- [ ] **Step 5: Keep `VoiceXpTicker` compiling — e já corrigir o mute**

Em `VoiceXpTicker.tick()`, trocar o bloco que constrói `delta` (linhas 42-56 do arquivo atual) por:

```java
            for (VoiceSessionRepository.Open s : open) {
                Member member = guild.getMemberById(s.userId());
                AudioChannel channel = guild.getChannelById(AudioChannel.class, s.channelId());
                long delta = 0;
                if (member != null && channel != null) {
                    long humans = channel.getMembers().stream().filter(m -> !m.getUser().isBot()).count();
                    GuildVoiceState vs = member.getVoiceState();
                    boolean afk = afkId != null && afkId.equals(channel.getId());
                    VoiceStateSnapshot snap = new VoiceStateSnapshot(
                            member.getUser().isBot(), humans,
                            vs != null && vs.isSelfMuted(),
                            vs != null && vs.isSelfDeafened(),
                            vs != null && vs.isGuildDeafened(),
                            afk,
                            true); // escopo entra na Task 7; irrelevante para XP
                    if (VoiceEligibility.xpEligible(snap)) {
                        delta = (Math.max(0, now - s.xpCreditedUntil()) * XP_PER_MIN) / 60_000L;
                    }
                }
                credits.add(new VoiceXpBatch.Credit(s.id(), guild.getId(), s.userId(), delta, now));
            }
```

- [ ] **Step 6: Run the full leveling suite**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.*"`
Expected: PASS. Se algum teste ainda chamar `VoiceEligibility.isEligible`, ele foi substituído no Step 1 — nenhum outro lugar do código a usa (verificado por grep).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceStateSnapshot.java src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceEligibility.java src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceXpTicker.java src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceEligibilityTest.java
git commit -m "fix(voice): self-mute deixa de acumular XP; elegibilidade sobre snapshot"
```

---

### Task 4: `VoiceScope` — quais canais contam tempo

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeConfig.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceScope.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceScopeTest.java`

**Interfaces:**
- Consumes: `ConfigIds.parse` (Task 2).
- Produces:
  - `VoiceTimeConfig.KEY_INCLUDE_CHANNELS`, `KEY_EXCLUDE_CHANNELS`, `KEY_INCLUDE_CATEGORIES`, `KEY_EXCLUDE_CATEGORIES` (String)
  - `VoiceTimeConfig.includeChannels(GuildConfig) -> Set<String>` e as outras três
  - `VoiceScope.counts(String channelId, String categoryId, boolean publicChannel, GuildConfig cfg) -> boolean` (núcleo puro)
  - `VoiceScope.inScope(AudioChannel channel, GuildConfig cfg) -> boolean` (adaptador JDA)

- [ ] **Step 1: Write the failing test**

`src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceScopeTest.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VoiceScopeTest {

    private static GuildConfig cfg(Map<String, String> settings) {
        return new GuildConfig("g1", null, null, Map.of(), Map.of(), Map.of(), List.of(), settings);
    }

    private static final GuildConfig EMPTY = cfg(Map.of());

    @Test
    void withoutListsFallsBackToPublicDefault() {
        assertTrue(VoiceScope.counts("chan", "cat", true, EMPTY));
        assertFalse(VoiceScope.counts("chan", "cat", false, EMPTY));
    }

    @Test
    void channelWithoutCategoryFallsBackToPublicDefault() {
        assertTrue(VoiceScope.counts("chan", null, true, EMPTY));
        assertFalse(VoiceScope.counts("chan", null, false, EMPTY));
    }

    @Test
    void includedChannelBeatsExcludedCategory() {
        GuildConfig c = cfg(Map.of(
                VoiceTimeConfig.KEY_INCLUDE_CHANNELS, "chan",
                VoiceTimeConfig.KEY_EXCLUDE_CATEGORIES, "cat"));
        assertTrue(VoiceScope.counts("chan", "cat", false, c));
    }

    @Test
    void excludedChannelBeatsIncludedCategory() {
        GuildConfig c = cfg(Map.of(
                VoiceTimeConfig.KEY_EXCLUDE_CHANNELS, "chan",
                VoiceTimeConfig.KEY_INCLUDE_CATEGORIES, "cat"));
        assertFalse(VoiceScope.counts("chan", "cat", true, c));
    }

    @Test
    void exclusionWinsWhenTheSameIdIsInBothListsOfOneLevel() {
        GuildConfig channels = cfg(Map.of(
                VoiceTimeConfig.KEY_INCLUDE_CHANNELS, "chan",
                VoiceTimeConfig.KEY_EXCLUDE_CHANNELS, "chan"));
        assertFalse(VoiceScope.counts("chan", null, true, channels));

        GuildConfig cats = cfg(Map.of(
                VoiceTimeConfig.KEY_INCLUDE_CATEGORIES, "cat",
                VoiceTimeConfig.KEY_EXCLUDE_CATEGORIES, "cat"));
        assertFalse(VoiceScope.counts("chan", "cat", true, cats));
    }

    @Test
    void includedCategoryLetsAPrivateChannelCount() {
        GuildConfig c = cfg(Map.of(VoiceTimeConfig.KEY_INCLUDE_CATEGORIES, "cat"));
        assertTrue(VoiceScope.counts("chan", "cat", false, c));
    }

    @Test
    void excludedCategoryStopsAPublicChannel() {
        GuildConfig c = cfg(Map.of(VoiceTimeConfig.KEY_EXCLUDE_CATEGORIES, "cat"));
        assertFalse(VoiceScope.counts("chan", "cat", true, c));
    }

    @Test
    void listsTolerateSpacesAndEmptySegments() {
        GuildConfig c = cfg(Map.of(VoiceTimeConfig.KEY_EXCLUDE_CHANNELS, " , chan , "));
        assertFalse(VoiceScope.counts("chan", null, true, c));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceScopeTest"`
Expected: FAIL — `VoiceTimeConfig` / `VoiceScope` não existem.

- [ ] **Step 3: Write `VoiceTimeConfig`**

`src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeConfig.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.ConfigIds;

import java.util.Set;

/** Leitor puro do escopo de contagem de tempo em call (prefixo {@code voicetime:}). */
public final class VoiceTimeConfig {

    public static final String KEY_INCLUDE_CHANNELS = "voicetime:include-channels";
    public static final String KEY_EXCLUDE_CHANNELS = "voicetime:exclude-channels";
    public static final String KEY_INCLUDE_CATEGORIES = "voicetime:include-categories";
    public static final String KEY_EXCLUDE_CATEGORIES = "voicetime:exclude-categories";

    private VoiceTimeConfig() {}

    public static Set<String> includeChannels(GuildConfig cfg) {
        return ConfigIds.parse(cfg.setting(KEY_INCLUDE_CHANNELS));
    }

    public static Set<String> excludeChannels(GuildConfig cfg) {
        return ConfigIds.parse(cfg.setting(KEY_EXCLUDE_CHANNELS));
    }

    public static Set<String> includeCategories(GuildConfig cfg) {
        return ConfigIds.parse(cfg.setting(KEY_INCLUDE_CATEGORIES));
    }

    public static Set<String> excludeCategories(GuildConfig cfg) {
        return ConfigIds.parse(cfg.setting(KEY_EXCLUDE_CATEGORIES));
    }
}
```

- [ ] **Step 4: Write `VoiceScope`**

`src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceScope.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

/**
 * Decide se um canal de voz conta tempo. Precedência: canal &gt; categoria &gt; padrão.
 * Dentro de cada nível a exclusão é avaliada primeiro, então um ID presente nas duas listas do
 * mesmo nível não conta — configuração contraditória nunca abre acesso.
 *
 * <p>O padrão é "só canais públicos": {@code @everyone} com {@code VIEW_CHANNEL} e
 * {@code VOICE_CONNECT} <b>efetivos</b> (herança de categoria + overrides), não o override local.
 */
public final class VoiceScope {

    private VoiceScope() {}

    /** Núcleo puro, sem JDA — testável sem mocks. {@code categoryId} pode ser null. */
    public static boolean counts(String channelId, String categoryId, boolean publicChannel, GuildConfig cfg) {
        if (VoiceTimeConfig.excludeChannels(cfg).contains(channelId)) {
            return false;
        }
        if (VoiceTimeConfig.includeChannels(cfg).contains(channelId)) {
            return true;
        }
        if (categoryId != null) {
            if (VoiceTimeConfig.excludeCategories(cfg).contains(categoryId)) {
                return false;
            }
            if (VoiceTimeConfig.includeCategories(cfg).contains(categoryId)) {
                return true;
            }
        }
        return publicChannel;
    }

    /** Adaptador: resolve categoria e "público" a partir da JDA e delega ao núcleo. */
    public static boolean inScope(AudioChannel channel, GuildConfig cfg) {
        Category category = channel instanceof ICategorizableChannel c ? c.getParentCategory() : null;
        // hasPermission(GuildChannel, ...) em IPermissionHolder já é a permissão EFETIVA.
        // Não usar getPermissionOverride (só override local) nem PermissionUtil (API interna).
        boolean isPublic = channel.getGuild().getPublicRole()
                .hasPermission(channel, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT);
        return counts(channel.getId(), category == null ? null : category.getId(), isPublic, cfg);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceScopeTest"`
Expected: PASS, 8 testes.

- [ ] **Step 6: Verify the JDA adapter compiles**

Run: `./gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL. Se `ICategorizableChannel` não resolver, confirmar o pacote com:
`javap -classpath <jda.jar> net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel`

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeConfig.java src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceScope.java src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceScopeTest.java
git commit -m "feat(voice): VoiceScope — escopo de canais para contagem de tempo"
```

---

### Task 5: Migration 039 + repositórios

**Files:**
- Create: `src/main/resources/db/sqlite/039_voice_time.sql`
- Create: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeRepository.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceRetention.java`
- Modify: `src/main/java/dev/davimf/basebot/database/sqlite/SqliteMigrator.java:93`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceSessionRepository.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeRepositoryTest.java`

**Interfaces:**
- Consumes: `VoiceWeek.weekStart` (Task 1).
- Produces:
  - `VoiceRetention.DAYS` (int, 90), `VoiceRetention.cutoff(long now) -> long`
  - `VoiceSessionRepository.Open` ganha `long timeCreditedUntil()` (7º componente)
  - `VoiceSessionRepository.openSession(String guildId, String userId) -> Open` (null se não houver)
  - `VoiceSessionRepository.purgeClosedBefore(long cutoff) -> int`
  - `VoiceTimeRepository`: `addMs(g,u,weekStart,delta)`, `msOf(g,u,weekStart) -> long`, `topPage(g,weekStart,limit,offset) -> List<Entry>`, `count(g,weekStart) -> int`, `dirtyRows(limit) -> List<DirtyRow>`, `clearDirty(g,u,weekStart,ms) -> boolean`, `purgeWeeksBefore(cutoff) -> int`
  - `record VoiceTimeRepository.Entry(String userId, long ms)`
  - `record VoiceTimeRepository.DirtyRow(String guildId, String userId, long weekStart, long ms)`

- [ ] **Step 1: Write the migration**

`src/main/resources/db/sqlite/039_voice_time.sql`:

```sql
ALTER TABLE voice_sessions ADD COLUMN time_credited_until INTEGER NOT NULL DEFAULT 0;
UPDATE voice_sessions SET time_credited_until = xp_credited_until WHERE time_credited_until = 0;
CREATE TABLE IF NOT EXISTS voice_weekly_time (
    guild_id   TEXT    NOT NULL,
    user_id    TEXT    NOT NULL,
    week_start INTEGER NOT NULL,
    ms         INTEGER NOT NULL DEFAULT 0,
    dirty      INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (guild_id, user_id, week_start)
);
CREATE INDEX IF NOT EXISTS idx_voice_weekly_rank ON voice_weekly_time (guild_id, week_start, ms DESC);
CREATE INDEX IF NOT EXISTS idx_voice_weekly_dirty ON voice_weekly_time (dirty) WHERE dirty = 1;
```

- [ ] **Step 2: Register it in `SqliteMigrator`**

Em `SqliteMigrator.java`, após a linha `"/db/sqlite/038_verification.sql"` (linha 93), acrescentar a vírgula e a entrada:

```java
            "/db/sqlite/038_verification.sql",
            "/db/sqlite/039_voice_time.sql"
    );
```

- [ ] **Step 3: Write the failing test**

`src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeRepositoryTest.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VoiceTimeRepositoryTest {

    private static final long W1 = VoiceWeek.weekStart(1_700_000_000_000L);
    private static final long W2 = VoiceWeek.nextWeekStart(W1);

    private SqliteManager sqlite;
    private VoiceTimeRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new VoiceTimeRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void addMsAccumulatesPerWeekAndMarksDirty() {
        repo.addMs("g1", "u1", W1, 1000);
        repo.addMs("g1", "u1", W1, 500);
        repo.addMs("g1", "u1", W2, 300);

        assertEquals(1500, repo.msOf("g1", "u1", W1));
        assertEquals(300, repo.msOf("g1", "u1", W2));
        assertEquals(2, repo.dirtyRows(10).size());
    }

    @Test
    void missingRowReadsAsZero() {
        assertEquals(0, repo.msOf("g1", "nobody", W1));
    }

    @Test
    void topPageOrdersDescendingAndSkipsZero() {
        repo.addMs("g1", "u1", W1, 100);
        repo.addMs("g1", "u2", W1, 900);
        repo.addMs("g1", "u3", W1, 0);

        List<VoiceTimeRepository.Entry> top = repo.topPage("g1", W1, 10, 0);
        assertEquals(2, top.size());
        assertEquals("u2", top.get(0).userId());
        assertEquals(900, top.get(0).ms());
        assertEquals("u1", top.get(1).userId());
        assertEquals(2, repo.count("g1", W1));
    }

    @Test
    void clearDirtySucceedsOnlyWhenMsIsUnchanged() {
        repo.addMs("g1", "u1", W1, 1000);

        // O ticker incrementou depois que o flusher leu 1000: a linha continua suja.
        repo.addMs("g1", "u1", W1, 1000);
        assertFalse(repo.clearDirty("g1", "u1", W1, 1000));
        assertEquals(1, repo.dirtyRows(10).size());

        // Com o valor corrente, limpa.
        assertTrue(repo.clearDirty("g1", "u1", W1, 2000));
        assertTrue(repo.dirtyRows(10).isEmpty());
    }

    @Test
    void purgeWeeksBeforeSkipsDirtyRows() {
        repo.addMs("g1", "old-dirty", W1, 10);
        repo.addMs("g1", "old-clean", W1, 20);
        repo.clearDirty("g1", "old-clean", W1, 20);

        assertEquals(1, repo.purgeWeeksBefore(W2));

        assertEquals(10, repo.msOf("g1", "old-dirty", W1));
        assertEquals(0, repo.msOf("g1", "old-clean", W1));
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceTimeRepositoryTest"`
Expected: FAIL — `VoiceTimeRepository` não existe.

- [ ] **Step 5: Write `VoiceRetention`**

`src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceRetention.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import java.util.concurrent.TimeUnit;

/** Janela de retenção de dados de voz. Único lugar onde os 90 dias existem. */
public final class VoiceRetention {

    public static final int DAYS = 90;

    private VoiceRetention() {}

    /** Instante antes do qual os dados podem ser podados. */
    public static long cutoff(long now) {
        return now - TimeUnit.DAYS.toMillis(DAYS);
    }
}
```

- [ ] **Step 6: Write `VoiceTimeRepository`**

`src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeRepository.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Baldes semanais de tempo em call (migração 039). Fonte de verdade do ranking; o Postgres é
 * só uma cópia para o site, alimentada pelo {@link VoiceTimeFlusher}.
 */
public final class VoiceTimeRepository {

    public record Entry(String userId, long ms) {}

    public record DirtyRow(String guildId, String userId, long weekStart, long ms) {}

    private final SqliteManager sqlite;

    public VoiceTimeRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    /** Soma {@code deltaMs} ao balde da semana e marca a linha como pendente de sync. */
    public void addMs(String guildId, String userId, long weekStart, long deltaMs) {
        String sql = "INSERT INTO voice_weekly_time (guild_id, user_id, week_start, ms, dirty) "
                + "VALUES (?,?,?,?,1) "
                + "ON CONFLICT (guild_id, user_id, week_start) "
                + "DO UPDATE SET ms = ms + excluded.ms, dirty = 1";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setLong(3, weekStart);
            ps.setLong(4, deltaMs);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("addMs " + guildId + "/" + userId, e);
        }
    }

    public long msOf(String guildId, String userId, long weekStart) {
        String sql = "SELECT ms FROM voice_weekly_time WHERE guild_id=? AND user_id=? AND week_start=?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setLong(3, weekStart);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RepositoryException("msOf " + guildId + "/" + userId, e);
        }
    }

    /** Ranking da semana. Quem tem {@code ms = 0} não aparece. */
    public List<Entry> topPage(String guildId, long weekStart, int limit, int offset) {
        String sql = "SELECT user_id, ms FROM voice_weekly_time "
                + "WHERE guild_id=? AND week_start=? AND ms > 0 "
                + "ORDER BY ms DESC, user_id LIMIT ? OFFSET ?";
        List<Entry> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setLong(2, weekStart);
            ps.setInt(3, limit);
            ps.setInt(4, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Entry(rs.getString("user_id"), rs.getLong("ms")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("topPage " + guildId, e);
        }
    }

    public int count(String guildId, long weekStart) {
        String sql = "SELECT COUNT(*) FROM voice_weekly_time WHERE guild_id=? AND week_start=? AND ms > 0";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setLong(2, weekStart);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("count " + guildId, e);
        }
    }

    public List<DirtyRow> dirtyRows(int limit) {
        String sql = "SELECT guild_id, user_id, week_start, ms FROM voice_weekly_time "
                + "WHERE dirty = 1 LIMIT ?";
        List<DirtyRow> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new DirtyRow(rs.getString("guild_id"), rs.getString("user_id"),
                            rs.getLong("week_start"), rs.getLong("ms")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("dirtyRows", e);
        }
    }

    /**
     * Limpa {@code dirty} SÓ se {@code ms} ainda for o valor que subiu ao Postgres. Se o ticker
     * incrementou durante o flush, a linha continua suja e sobe na próxima rodada.
     * {@code ms} é monotonicamente crescente, então igualdade significa "não mudou".
     */
    public boolean clearDirty(String guildId, String userId, long weekStart, long flushedMs) {
        String sql = "UPDATE voice_weekly_time SET dirty = 0 "
                + "WHERE guild_id=? AND user_id=? AND week_start=? AND ms=?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setLong(3, weekStart);
            ps.setLong(4, flushedMs);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("clearDirty " + guildId + "/" + userId, e);
        }
    }

    /** Apaga baldes antigos JÁ sincronizados. Linha suja nunca é podada antes de subir. */
    public int purgeWeeksBefore(long cutoffWeekStart) {
        String sql = "DELETE FROM voice_weekly_time WHERE week_start < ? AND dirty = 0";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, cutoffWeekStart);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("purgeWeeksBefore", e);
        }
    }
}
```

- [ ] **Step 7: Extend `VoiceSessionRepository`**

Em `VoiceSessionRepository.java`:

a) O record `Open` ganha o campo:

```java
    public record Open(long id, String guildId, String userId, String channelId,
                       long joinTime, long xpCreditedUntil, long timeCreditedUntil) {}
```

b) `open(...)` grava as duas watermarks:

```java
    public void open(String guildId, String userId, String channelId, long now) {
        String sql = "INSERT INTO voice_sessions "
                + "(guild_id, user_id, channel_id, join_time, xp_credited_until, time_credited_until) "
                + "VALUES (?,?,?,?,?,?)";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setString(3, channelId);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.setLong(6, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("open voice session " + guildId + "/" + userId, e);
        }
    }
```

c) `query(...)` lê o campo novo:

```java
                    out.add(new Open(rs.getLong("id"), rs.getString("guild_id"), rs.getString("user_id"),
                            rs.getString("channel_id"), rs.getLong("join_time"),
                            rs.getLong("xp_credited_until"), rs.getLong("time_credited_until")));
```

d) Dois métodos novos no fim da classe:

```java
    /** A sessão aberta do usuário, ou {@code null} se ele não está em call. */
    public Open openSession(String guildId, String userId) {
        List<Open> found = query(
                "SELECT * FROM voice_sessions WHERE leave_time IS NULL AND guild_id=? AND user_id=?",
                guildId, userId);
        return found.isEmpty() ? null : found.get(0);
    }

    /** Apaga sessões FECHADAS antigas. Sessões abertas nunca são tocadas. */
    public int purgeClosedBefore(long cutoff) {
        String sql = "DELETE FROM voice_sessions WHERE leave_time IS NOT NULL AND leave_time < ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, cutoff);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("purgeClosedBefore", e);
        }
    }
```

e) `query` precisa aceitar dois parâmetros. Substituir o helper privado por:

```java
    private List<Open> query(String sql, String... args) {
        List<Open> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setString(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Open(rs.getLong("id"), rs.getString("guild_id"), rs.getString("user_id"),
                            rs.getString("channel_id"), rs.getLong("join_time"),
                            rs.getLong("xp_credited_until"), rs.getLong("time_credited_until")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("query voice sessions", e);
        }
    }
```

E ajustar os dois chamadores existentes: `openSessions()` passa a chamar `query("SELECT * FROM voice_sessions WHERE leave_time IS NULL")` (sem args) e `openSessions(guildId)` chama `query("SELECT * FROM voice_sessions WHERE leave_time IS NULL AND guild_id=?", guildId)`.

- [ ] **Step 8: Run the tests**

Run: `./gradlew.bat test`
Expected: **a suíte INTEIRA continua verde.** Esta task não altera `VoiceXpBatch.Credit`, e `new Open(...)` só é construído dentro do próprio `VoiceSessionRepository`, então acrescentar o 7º componente ao record não quebra nenhum chamador. Se algum teste falhar aqui, é um problema real — não descarte como esperado.

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/db/sqlite/039_voice_time.sql src/main/java/dev/davimf/basebot/database/sqlite/SqliteMigrator.java src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeRepository.java src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceRetention.java src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceSessionRepository.java src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeRepositoryTest.java
git commit -m "feat(voice): migration 039 — watermark de tempo e baldes semanais"
```

---

### Task 6: `VoiceXpBatch` credita tempo na mesma transação

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceXpBatch.java` (reescrever)
- Test: `src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceXpBatchTest.java` (reescrever)

**Interfaces:**
- Consumes: `VoiceWeek.splitByWeek` (Task 1), `VoiceSessionRepository.Open.timeCreditedUntil` (Task 5).
- Produces:
  - `record VoiceXpBatch.Credit(long sessionId, String guildId, String userId, long xpDelta, long timeFrom, long timeTo, long creditedUntil)` — `timeTo <= timeFrom` significa "sem crédito de tempo".
  - `VoiceXpBatch.apply(SqliteManager, List<Credit>) -> List<Result>` (`Result` inalterado).

- [ ] **Step 1: Write the failing test** (substitui o arquivo)

`src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceXpBatchTest.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VoiceXpBatchTest {

    private SqliteManager sqlite;
    private VoiceSessionRepository sessions;
    private UserLevelRepository users;
    private VoiceTimeRepository times;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        sessions = new VoiceSessionRepository(sqlite);
        users = new UserLevelRepository(sqlite);
        times = new VoiceTimeRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    private long openSessionId(String user, long at) {
        sessions.open("g1", user, "c1", at);
        return sessions.openSession("g1", user).id();
    }

    @Test
    void appliesXpAndTimeAndAdvancesBothWatermarks() {
        long id = openSessionId("u1", 1000L);
        long week = VoiceWeek.weekStart(1000L);

        VoiceXpBatch.apply(sqlite, List.of(
                new VoiceXpBatch.Credit(id, "g1", "u1", 10, 1000L, 61_000L, 61_000L)));

        assertEquals(10, users.xp("g1", "u1"));
        assertEquals(60_000L, times.msOf("g1", "u1", week));

        VoiceSessionRepository.Open s = sessions.openSession("g1", "u1");
        assertEquals(61_000L, s.xpCreditedUntil());
        assertEquals(61_000L, s.timeCreditedUntil());
    }

    @Test
    void ineligibleWindowAdvancesWatermarksWithoutCrediting() {
        long id = openSessionId("u1", 1000L);
        long week = VoiceWeek.weekStart(1000L);

        // Mutado a janela inteira: xpDelta 0, e timeTo == timeFrom (sem crédito).
        VoiceXpBatch.apply(sqlite, List.of(
                new VoiceXpBatch.Credit(id, "g1", "u1", 0, 1000L, 1000L, 61_000L)));

        assertEquals(0, users.xp("g1", "u1"));
        assertEquals(0, times.msOf("g1", "u1", week));

        VoiceSessionRepository.Open s = sessions.openSession("g1", "u1");
        assertEquals(61_000L, s.xpCreditedUntil());
        assertEquals(61_000L, s.timeCreditedUntil(),
                "a watermark precisa avancar, senao o tempo mutado seria creditado retroativamente");
    }

    @Test
    void timeWithoutXpIsPossible() {
        long id = openSessionId("u1", 1000L);
        long week = VoiceWeek.weekStart(1000L);

        // Sozinho no canal: tempo conta, XP nao.
        VoiceXpBatch.apply(sqlite, List.of(
                new VoiceXpBatch.Credit(id, "g1", "u1", 0, 1000L, 61_000L, 61_000L)));

        assertEquals(0, users.xp("g1", "u1"));
        assertEquals(60_000L, times.msOf("g1", "u1", week));
    }

    @Test
    void windowCrossingMondayIsWrittenToTwoWeeklyRows() {
        long sunday = ZonedDateTime.of(2026, 7, 5, 23, 59, 30, 0, VoiceWeek.ZONE)
                .toInstant().toEpochMilli();
        long monday = ZonedDateTime.of(2026, 7, 6, 0, 0, 30, 0, VoiceWeek.ZONE)
                .toInstant().toEpochMilli();

        long id = openSessionId("u1", sunday);
        VoiceXpBatch.apply(sqlite, List.of(
                new VoiceXpBatch.Credit(id, "g1", "u1", 0, sunday, monday, monday)));

        assertEquals(30_000L, times.msOf("g1", "u1", VoiceWeek.weekStart(sunday)));
        assertEquals(30_000L, times.msOf("g1", "u1", VoiceWeek.weekStart(monday)));
    }

    @Test
    void returnsOldAndNewXpOnlyForCreditedUsers() {
        long id1 = openSessionId("u1", 1000L);
        long id2 = openSessionId("u2", 1000L);

        List<VoiceXpBatch.Result> results = VoiceXpBatch.apply(sqlite, List.of(
                new VoiceXpBatch.Credit(id1, "g1", "u1", 10, 1000L, 1000L, 61_000L),
                new VoiceXpBatch.Credit(id2, "g1", "u2", 0, 1000L, 1000L, 61_000L)));

        assertEquals(1, results.size());
        assertEquals("u1", results.get(0).userId());
        assertEquals(0, results.get(0).oldXp());
        assertEquals(10, results.get(0).newXp());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceXpBatchTest"`
Expected: FAIL — a `Credit` antiga tem 5 componentes, não 7.

- [ ] **Step 3: Rewrite `VoiceXpBatch`**

`src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceXpBatch.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Aplica, numa ÚNICA transação, os créditos de um ciclo do ticker (ou de um settle disparado por
 * evento): avança as duas watermarks da sessão, soma o XP elegível em {@code user_levels} e o
 * tempo elegível nos baldes de {@code voice_weekly_time}.
 *
 * <p>Devolve os totais antigo/novo de quem recebeu XP para o chamador detectar level-up após o
 * commit — as chamadas ao Discord ficam fora da transação.
 *
 * <p><b>Invariantes:</b> as watermarks avançam para {@code creditedUntil} mesmo quando não houve
 * crédito (senão o período inelegível seria creditado retroativamente depois). E todo crédito de
 * tempo passa por {@link VoiceWeek#splitByWeek}, nunca por uma divisão ad-hoc.
 */
public final class VoiceXpBatch {

    private VoiceXpBatch() {}

    /**
     * @param xpDelta   XP a somar; 0 quando a janela não era elegível a XP
     * @param timeFrom  início da janela de tempo (a watermark anterior)
     * @param timeTo    fim da janela de tempo; {@code <= timeFrom} significa "sem crédito"
     * @param creditedUntil novo valor das duas watermarks
     */
    public record Credit(long sessionId, String guildId, String userId,
                         long xpDelta, long timeFrom, long timeTo, long creditedUntil) {}

    public record Result(String guildId, String userId, long oldXp, long newXp) {}

    public static List<Result> apply(SqliteManager sqlite, List<Credit> credits) {
        List<Result> results = new ArrayList<>();
        if (credits.isEmpty()) {
            return results;
        }
        try (Connection c = sqlite.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try (PreparedStatement advance = c.prepareStatement(
                        "UPDATE voice_sessions SET xp_credited_until=?, time_credited_until=? WHERE id=?");
                 PreparedStatement selXp = c.prepareStatement(
                        "SELECT xp FROM user_levels WHERE guild_id=? AND user_id=?");
                 PreparedStatement addXp = c.prepareStatement(
                        "INSERT INTO user_levels (guild_id, user_id, xp) VALUES (?,?,?) "
                        + "ON CONFLICT (guild_id, user_id) DO UPDATE SET xp = xp + excluded.xp");
                 PreparedStatement addTime = c.prepareStatement(
                        "INSERT INTO voice_weekly_time (guild_id, user_id, week_start, ms, dirty) "
                        + "VALUES (?,?,?,?,1) "
                        + "ON CONFLICT (guild_id, user_id, week_start) "
                        + "DO UPDATE SET ms = ms + excluded.ms, dirty = 1")) {

                for (Credit cr : credits) {
                    advance.setLong(1, cr.creditedUntil());
                    advance.setLong(2, cr.creditedUntil());
                    advance.setLong(3, cr.sessionId());
                    advance.executeUpdate();

                    if (cr.xpDelta() > 0) {
                        long old = 0;
                        selXp.setString(1, cr.guildId());
                        selXp.setString(2, cr.userId());
                        try (ResultSet rs = selXp.executeQuery()) {
                            if (rs.next()) {
                                old = rs.getLong(1);
                            }
                        }
                        addXp.setString(1, cr.guildId());
                        addXp.setString(2, cr.userId());
                        addXp.setLong(3, cr.xpDelta());
                        addXp.executeUpdate();
                        results.add(new Result(cr.guildId(), cr.userId(), old, old + cr.xpDelta()));
                    }

                    for (VoiceWeek.Slice slice : VoiceWeek.splitByWeek(cr.timeFrom(), cr.timeTo())) {
                        addTime.setString(1, cr.guildId());
                        addTime.setString(2, cr.userId());
                        addTime.setLong(3, slice.weekStart());
                        addTime.setLong(4, slice.durationMs());
                        addTime.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
            return results;
        } catch (SQLException e) {
            throw new RepositoryException("voice xp batch", e);
        }
    }
}
```

- [ ] **Step 4: Fix the one existing caller so it compiles**

Em `VoiceXpTicker.tick()`, a linha que monta a credit passa a ser (o tempo entra de verdade na Task 7; aqui só mantemos a compilação, sem creditar tempo):

```java
                credits.add(new VoiceXpBatch.Credit(s.id(), guild.getId(), s.userId(), delta,
                        s.timeCreditedUntil(), s.timeCreditedUntil(), now));
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceXpBatch.java src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceXpTicker.java src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceXpBatchTest.java
git commit -m "feat(voice): VoiceXpBatch credita tempo por semana na mesma transacao"
```

---

### Task 7: Ticker credita tempo de verdade

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceSnapshots.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceXpTicker.java` (reescrever)

**Interfaces:**
- Consumes: `VoiceStateSnapshot` (T3), `VoiceScope.inScope` (T4), `VoiceXpBatch.Credit` (T6).
- Produces: `VoiceSnapshots.of(Member member, AudioChannel channel, GuildConfig cfg, long humanBonus) -> VoiceStateSnapshot`.
  `humanBonus` soma à contagem de humanos do canal — usado no move, onde o membro já saiu do canal antigo.

- [ ] **Step 1: Write `VoiceSnapshots`**

`src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceSnapshots.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

/** Constrói um {@link VoiceStateSnapshot} a partir do estado vivo da JDA. */
public final class VoiceSnapshots {

    private VoiceSnapshots() {}

    /**
     * @param humanBonus soma à contagem de humanos do canal. Vale 1 ao fechar a janela de um move
     *                   ou leave, quando o membro já saiu do canal antigo e portanto não aparece
     *                   mais em {@code channel.getMembers()}.
     */
    public static VoiceStateSnapshot of(Member member, AudioChannel channel, GuildConfig cfg, long humanBonus) {
        Guild guild = channel.getGuild();
        long humans = channel.getMembers().stream().filter(m -> !m.getUser().isBot()).count() + humanBonus;
        GuildVoiceState vs = member.getVoiceState();
        String afkId = guild.getAfkChannel() == null ? null : guild.getAfkChannel().getId();
        return new VoiceStateSnapshot(
                member.getUser().isBot(),
                humans,
                vs != null && vs.isSelfMuted(),
                vs != null && vs.isSelfDeafened(),
                vs != null && vs.isGuildDeafened(),
                afkId != null && afkId.equals(channel.getId()),
                VoiceScope.inScope(channel, cfg));
    }
}
```

- [ ] **Step 2: Rewrite `VoiceXpTicker`**

`src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceXpTicker.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

import java.util.ArrayList;
import java.util.List;

/**
 * A cada 60s, credita XP e tempo em call das sessões abertas. Todas as escritas do ciclo vão numa
 * única transação ({@link VoiceXpBatch}).
 *
 * <p>A contagem de <b>tempo</b> não depende de {@code level:enabled} — desligar o XP não deve
 * zerar o ranking de call. Só o XP é condicionado ao toggle.
 */
public final class VoiceXpTicker {

    private static final long XP_PER_MIN = 10;

    private final BotContext ctx;
    private final LevelingService leveling;
    private final VoiceSessionRepository sessions;

    public VoiceXpTicker(BotContext ctx, LevelingService leveling) {
        this.ctx = ctx;
        this.leveling = leveling;
        this.sessions = new VoiceSessionRepository(ctx.database().sqlite());
    }

    public void tick() {
        if (ctx.jda() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Guild guild : ctx.jda().getGuilds()) {
            List<VoiceSessionRepository.Open> open = sessions.openSessions(guild.getId());
            if (open.isEmpty()) {
                continue;
            }
            GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
            boolean xpOn = LevelingConfig.enabled(cfg);

            List<VoiceXpBatch.Credit> credits = new ArrayList<>();
            for (VoiceSessionRepository.Open s : open) {
                Member member = guild.getMemberById(s.userId());
                AudioChannel channel = guild.getChannelById(AudioChannel.class, s.channelId());

                long xpDelta = 0;
                long timeTo = s.timeCreditedUntil(); // janela vazia = sem crédito
                if (member != null && channel != null) {
                    VoiceStateSnapshot snap = VoiceSnapshots.of(member, channel, cfg, 0);
                    if (xpOn && VoiceEligibility.xpEligible(snap)) {
                        xpDelta = (Math.max(0, now - s.xpCreditedUntil()) * XP_PER_MIN) / 60_000L;
                    }
                    if (VoiceEligibility.timeEligible(snap)) {
                        timeTo = now;
                    }
                }
                credits.add(new VoiceXpBatch.Credit(s.id(), guild.getId(), s.userId(),
                        xpDelta, s.timeCreditedUntil(), timeTo, now));
            }

            List<VoiceXpBatch.Result> results = VoiceXpBatch.apply(ctx.database().sqlite(), credits);
            // Efeitos de level-up FORA da transação (chamadas ao Discord).
            for (VoiceXpBatch.Result r : results) {
                Member member = guild.getMemberById(r.userId());
                if (member != null) {
                    leveling.applyVoiceLevelUp(guild, member, r.oldXp(), r.newXp());
                }
            }
        }
    }
}
```

- [ ] **Step 3: Compile and run the suite**

Run: `./gradlew.bat compileJava` then `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.*"`
Expected: BUILD SUCCESSFUL, PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceSnapshots.java src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceXpTicker.java
git commit -m "feat(voice): ticker credita tempo em call; XP condicionado ao toggle"
```

---

### Task 8: Parar a contagem no instante exato do mute

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceSettler.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceStateListener.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceSessionListener.java` (reescrever)
- Modify: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceReconciler.java:41-51`

**Interfaces:**
- Consumes: `VoiceSnapshots.of` (T7), `VoiceXpBatch` (T6), `VoiceSessionRepository.openSession` (T5).
- Produces: `VoiceSettler.settle(BotContext ctx, LevelingService leveling, Guild guild, Member member, VoiceStateSnapshot before, long now)`.

### Step 0 (obrigatório antes de tudo): guarda otimista contra crédito duplo

Até agora o `VoiceXpBatch` tinha **um único escritor**: o ticker, que o `scheduleAtFixedRate` garante não-reentrante. Esta task adiciona o **segundo**: o `VoiceSettler`, chamado das threads de evento da JDA, concorrente com o ticker.

O `advance` é hoje incondicional (`WHERE id=?`). Com dois escritores, esta intercalação credita a mesma janela duas vezes:

1. ticker lê a sessão, watermark `W`, monta `Credit(timeFrom=W, timeTo=agora)`;
2. a pessoa muta → listener lê a MESMA sessão, watermark ainda `W`, monta `Credit(timeFrom=W, timeTo=agora)`;
3. ambos aplicam. O tempo entre `W` e agora entra duas vezes no balde, e o XP também.

Torne o `advance` condicional ao watermark esperado, e credite **apenas** se ele casou. As duas watermarks são sempre iguais (`open` grava as duas com `now`, `advance` grava as duas com `creditedUntil`, o backfill da 039 copia uma na outra), então basta guardar por uma delas. `cr.timeFrom()` **é** o watermark que o chamador leu.

Em `VoiceXpBatch.apply`, trocar o statement e o corpo do laço:

```java
                 PreparedStatement advance = c.prepareStatement(
                        "UPDATE voice_sessions SET xp_credited_until=?, time_credited_until=? "
                        + "WHERE id=? AND time_credited_until=?");
```

```java
                for (Credit cr : credits) {
                    advance.setLong(1, cr.creditedUntil());
                    advance.setLong(2, cr.creditedUntil());
                    advance.setLong(3, cr.sessionId());
                    advance.setLong(4, cr.timeFrom());
                    if (advance.executeUpdate() == 0) {
                        // Outro escritor (ticker ou settler) já creditou esta janela. Pular,
                        // senão o tempo e o XP entrariam em dobro.
                        continue;
                    }
                    // ... resto do corpo permanece igual (XP e fatias de tempo)
                }
```

Acrescentar a `VoiceXpBatchTest`:

```java
    @Test
    void staleCreditIsSkippedSoConcurrentWritersCannotDoubleCount() {
        long id = openSessionId("u1", 1000L);
        long week = VoiceWeek.weekStart(1000L);

        // Primeiro escritor credita [1000, 61000) e move a watermark para 61000.
        VoiceXpBatch.apply(sqlite, List.of(
                new VoiceXpBatch.Credit(id, "g1", "u1", 10, 1000L, 61_000L, 61_000L)));

        // Segundo escritor tinha lido a watermark ANTIGA (1000) e tenta creditar a mesma janela.
        List<VoiceXpBatch.Result> stale = VoiceXpBatch.apply(sqlite, List.of(
                new VoiceXpBatch.Credit(id, "g1", "u1", 10, 1000L, 61_000L, 61_000L)));

        assertTrue(stale.isEmpty(), "credito obsoleto nao deve reportar level-up");
        assertEquals(10, users.xp("g1", "u1"), "XP nao pode ser creditado duas vezes");
        assertEquals(60_000L, times.msOf("g1", "u1", week), "tempo nao pode ser creditado duas vezes");
    }
```

Rodar `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceXpBatchTest"` e ver os 6 passarem. Commitar isto **separado**, antes do resto da task:

```bash
git add src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceXpBatch.java src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceXpBatchTest.java
git commit -m "fix(voice): guarda otimista no advance impede credito duplo entre ticker e settler"
```

- [ ] **Step 1: Write `VoiceSettler`**

`src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceSettler.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.util.List;

/**
 * Fecha a janela pendente de uma sessão de voz creditando-a com o estado que vigorou DURANTE a
 * janela, e avança as watermarks. É o que faz a contagem parar no instante exato do mute, em vez
 * de só no tick seguinte.
 */
public final class VoiceSettler {

    private static final long XP_PER_MIN = 10;

    private VoiceSettler() {}

    /** @param before estado de voz ANTERIOR ao evento que disparou o settle */
    public static void settle(BotContext ctx, LevelingService leveling, Guild guild, Member member,
                              VoiceStateSnapshot before, long now) {
        VoiceSessionRepository sessions = new VoiceSessionRepository(ctx.database().sqlite());
        VoiceSessionRepository.Open s = sessions.openSession(guild.getId(), member.getId());
        if (s == null) {
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());

        long xpDelta = 0;
        if (LevelingConfig.enabled(cfg) && VoiceEligibility.xpEligible(before)) {
            xpDelta = (Math.max(0, now - s.xpCreditedUntil()) * XP_PER_MIN) / 60_000L;
        }
        long timeTo = VoiceEligibility.timeEligible(before) ? now : s.timeCreditedUntil();

        List<VoiceXpBatch.Result> results = VoiceXpBatch.apply(ctx.database().sqlite(), List.of(
                new VoiceXpBatch.Credit(s.id(), guild.getId(), member.getId(),
                        xpDelta, s.timeCreditedUntil(), timeTo, now)));

        for (VoiceXpBatch.Result r : results) {
            leveling.applyVoiceLevelUp(guild, member, r.oldXp(), r.newXp());
        }
    }
}
```

- [ ] **Step 2: Write `VoiceStateListener`**

`src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceStateListener.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildDeafenEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildMuteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceSelfDeafenEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceSelfMuteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.function.UnaryOperator;

/**
 * Credita a janela pendente sempre que o estado de voz muda, usando o estado ANTERIOR ao evento.
 *
 * <p>Cada evento carrega o valor NOVO da flag que mudou; o valor anterior é a negação disso, e as
 * demais flags vêm do {@code GuildVoiceState} atual. Não invertemos o estado vivo nem o agregado
 * {@code isDeafened()} — ver {@link VoiceStateSnapshot}.
 *
 * <p>{@code GuildVoiceGuildMuteEvent} não muda elegibilidade (server-mute não pausa nada), mas
 * passa pelo mesmo caminho: o crédito é idêntico ao que seria com o estado atual, e manter o
 * padrão evita um buraco se a regra mudar.
 */
public final class VoiceStateListener extends ListenerAdapter {

    private final BotContext ctx;
    private final LevelingService leveling;

    public VoiceStateListener(BotContext ctx, LevelingService leveling) {
        this.ctx = ctx;
        this.leveling = leveling;
    }

    @Override
    public void onGuildVoiceSelfMute(GuildVoiceSelfMuteEvent event) {
        settle(event.getMember(), s -> s.withSelfMuted(!event.isSelfMuted()));
    }

    @Override
    public void onGuildVoiceSelfDeafen(GuildVoiceSelfDeafenEvent event) {
        settle(event.getMember(), s -> s.withSelfDeafened(!event.isSelfDeafened()));
    }

    @Override
    public void onGuildVoiceGuildDeafen(GuildVoiceGuildDeafenEvent event) {
        settle(event.getMember(), s -> s.withGuildDeafened(!event.isGuildDeafened()));
    }

    @Override
    public void onGuildVoiceGuildMute(GuildVoiceGuildMuteEvent event) {
        settle(event.getMember(), UnaryOperator.identity());
    }

    private void settle(Member member, UnaryOperator<VoiceStateSnapshot> toPrevious) {
        if (member.getUser().isBot() || member.getVoiceState() == null) {
            return;
        }
        AudioChannel channel = member.getVoiceState().getChannel();
        if (channel == null) {
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(member.getGuild().getId());
        VoiceStateSnapshot current = VoiceSnapshots.of(member, channel, cfg, 0);
        VoiceSettler.settle(ctx, leveling, member.getGuild(), member,
                toPrevious.apply(current), System.currentTimeMillis());
    }
}
```

- [ ] **Step 3: Rewrite `VoiceSessionListener` to settle with the OLD channel**

`src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceSessionListener.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Abre/fecha sessões de voz a partir do evento unificado (join/leave/move). Requer
 * GUILD_VOICE_STATES.
 *
 * <p>Ao sair ou trocar de canal, a janela pendente é creditada ANTES de fechar a sessão, avaliada
 * com o <b>canal antigo</b> — escopo, AFK e contagem de humanos são de lá. O membro já saiu do
 * canal antigo quando o evento chega, então somamos 1 à contagem de humanos para reconstruir a
 * população durante a janela.
 */
public final class VoiceSessionListener extends ListenerAdapter {

    private final BotContext ctx;
    private final LevelingService leveling;
    private final VoiceSessionRepository sessions;

    public VoiceSessionListener(BotContext ctx, LevelingService leveling) {
        this.ctx = ctx;
        this.leveling = leveling;
        this.sessions = new VoiceSessionRepository(ctx.database().sqlite());
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        Member member = event.getMember();
        if (member.getUser().isBot()) {
            return;
        }
        String guildId = event.getGuild().getId();
        String userId = member.getId();
        long now = System.currentTimeMillis();
        AudioChannel joined = event.getChannelJoined();
        AudioChannel left = event.getChannelLeft();

        if (left != null) {
            settleOnOldChannel(member, left, now);
            sessions.closeOpen(guildId, userId, now);
        }
        if (joined != null) {
            sessions.open(guildId, userId, joined.getId(), now);
        }
    }

    private void settleOnOldChannel(Member member, AudioChannel oldChannel, long now) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(member.getGuild().getId());
        // +1: o membro já não está mais em oldChannel.getMembers().
        VoiceStateSnapshot before = VoiceSnapshots.of(member, oldChannel, cfg, 1);
        VoiceSettler.settle(ctx, leveling, member.getGuild(), member, before, now);
    }
}
```

- [ ] **Step 4: Re-anchor watermarks in `VoiceReconciler` (corrige bug pré-existente)**

**A versão anterior deste plano dizia que nenhuma mudança era necessária. Estava errado.**

`VoiceReconciler.run` só toca a sessão quando ela está órfã ou quando o membro trocou de canal. Se o membro ficou **no mesmo canal** durante a queda do bot, a sessão sobrevive com a watermark congelada de antes. O primeiro tick após o restart calcula então `xpDelta = (now - xpCreditedUntil) * 10 / 60000` sobre a **queda inteira**: um dia offline vira ~240 XP de uma vez, e agora também 24h de tempo em call. O bug de XP já existia antes desta feature; o tempo o tornaria muito mais visível.

O spec exige: *"Bot offline: o tempo não é creditado. `VoiceReconciler` reancora as watermarks em `now`."*

Acrescentar a `VoiceSessionRepository`:

```java
    /** Reancora as duas watermarks da sessão aberta em {@code now}, sem creditar nada.
     *  Usado no boot: o período em que o bot esteve offline não deve ser creditado. */
    public void reanchor(String guildId, String userId, long now) {
        String sql = "UPDATE voice_sessions SET xp_credited_until=?, time_credited_until=? "
                + "WHERE guild_id=? AND user_id=? AND leave_time IS NULL";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, now);
            ps.setLong(2, now);
            ps.setString(3, guildId);
            ps.setString(4, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("reanchor " + guildId + "/" + userId, e);
        }
    }
```

Em `VoiceReconciler.run`, o ramo "mesma sessão, mesmo canal" passa a reancorar:

```java
            for (Map.Entry<String, String> e : current.entrySet()) {
                VoiceSessionRepository.Open open = dbOpen.get(e.getKey());
                if (open == null) {
                    repo.open(guildId, e.getKey(), e.getValue(), now);
                } else if (!open.channelId().equals(e.getValue())) {
                    repo.closeOpen(guildId, e.getKey(), now);
                    repo.open(guildId, e.getKey(), e.getValue(), now);
                } else {
                    // Mesma sessão, mesmo canal: o bot pode ter ficado horas fora. Reancora as
                    // watermarks para NÃO creditar o período offline no próximo tick.
                    repo.reanchor(guildId, e.getKey(), now);
                }
            }
```

E o Javadoc da classe:

```java
/** Acerta as sessões de voz no boot: fecha órfãs, abre/reabre conforme quem está em call agora,
 *  e reancora as watermarks de quem continuou no mesmo canal. O período em que o bot esteve
 *  offline NUNCA é creditado retroativamente. */
```

Teste novo em `src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceSessionRepositoryTest.java` (acrescentar ao arquivo existente; não reescrevê-lo):

```java
    @Test
    void reanchorMovesBothWatermarksAndCreditsNothing() {
        repo.open("g1", "u1", "c1", 1_000L);
        repo.reanchor("g1", "u1", 90_000_000L);

        VoiceSessionRepository.Open s = repo.openSession("g1", "u1");
        assertEquals(90_000_000L, s.xpCreditedUntil());
        assertEquals(90_000_000L, s.timeCreditedUntil());
        assertEquals(1_000L, s.joinTime(), "join_time nao muda: so as watermarks sao reancoradas");
    }

    @Test
    void reanchorIgnoresClosedSessions() {
        repo.open("g1", "u1", "c1", 1_000L);
        repo.closeOpen("g1", "u1", 2_000L);
        repo.reanchor("g1", "u1", 90_000_000L);

        assertEquals(2_000L, repo.sessionsOf("g1", "u1").get(0)[1], "sessao fechada nao e tocada");
    }
```

Rodar `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceSessionRepositoryTest"` e ver os testes existentes mais estes dois passarem.

- [ ] **Step 5: Update the `VoiceSessionListener` construction site**

`VoiceSessionListener` agora precisa de `leveling`. Em `BaseModule.java:298`:

```java
        registry.listener(new dev.davimf.basebot.modules.base.leveling.VoiceSessionListener(ctx, leveling));
        registry.listener(new dev.davimf.basebot.modules.base.leveling.VoiceStateListener(ctx, leveling));
```

- [ ] **Step 6: Compile and run the suite**

Run: `./gradlew.bat compileJava` then `./gradlew.bat test`
Expected: BUILD SUCCESSFUL, todos os testes passam.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceSettler.java src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceStateListener.java src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceSessionListener.java src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceReconciler.java src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(voice): contagem para no instante do mute; move credita com o canal antigo"
```

---

### Task 9: Postgres — tabela do site e o flusher

**Files:**
- Create: `src/main/resources/db/postgres/010_voice_weekly_time.sql`
- Create: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeFlusher.java`
- Modify: `src/main/java/dev/davimf/basebot/database/postgres/PostgresMigrator.java:44-52`
- Test: `src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeFlusherTest.java`

**Interfaces:**
- Consumes: `VoiceTimeRepository.dirtyRows` / `clearDirty` (T5).
- Produces: `VoiceTimeFlusher(BotContext)` com `flush()`; `VoiceTimeFlusher.BATCH` (int, 500).

- [ ] **Step 1: Write the Postgres migration**

`src/main/resources/db/postgres/010_voice_weekly_time.sql`:

```sql
-- Tempo semanal em call, espelhado do SQLite pelo VoiceTimeFlusher para o dashboard ler.
-- Idempotente (IF NOT EXISTS): seguro re-aplicar.

CREATE TABLE IF NOT EXISTS voice_weekly_time (
    guild_id   TEXT   NOT NULL,
    user_id    TEXT   NOT NULL,
    week_start BIGINT NOT NULL,
    ms         BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (guild_id, user_id, week_start)
);
CREATE INDEX IF NOT EXISTS idx_voice_weekly_time_rank
    ON voice_weekly_time (guild_id, week_start, ms DESC);
```

- [ ] **Step 2: Register the missing migrations**

`008_dashboard_audit.sql` e `009_verification_questions.sql` existem no disco mas **nunca foram registrados** — bug pré-existente. Substituir a lista em `PostgresMigrator.java`:

```java
    public static final List<String> MIGRATIONS = List.of(
            "/db/postgres/001_guild_config.sql",
            "/db/postgres/002_guild_settings.sql",
            "/db/postgres/003_ticket_categories.sql",
            "/db/postgres/004_config_tables.sql",
            "/db/postgres/005_snapshots.sql",
            "/db/postgres/006_dashboard_access.sql",
            "/db/postgres/007_updated_by.sql",
            "/db/postgres/008_dashboard_audit.sql",
            "/db/postgres/009_verification_questions.sql",
            "/db/postgres/010_voice_weekly_time.sql"
    );
```

Antes de commitar, abra `008_dashboard_audit.sql` e `009_verification_questions.sql` e confirme que ambos usam `IF NOT EXISTS` em todo DDL. Se algum não usar, **pare e reporte** — re-aplicar num Neon que já tenha as tabelas quebraria o boot da ferramenta.

- [ ] **Step 3: Write the failing test**

O flusher precisa de Postgres, que não existe no CI. Testamos a **lógica de reconciliação** — a parte que erra — contra o SQLite, injetando um "upstream" falso.

`src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeFlusherTest.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VoiceTimeFlusherTest {

    private static final long WEEK = VoiceWeek.weekStart(1_700_000_000_000L);

    private SqliteManager sqlite;
    private VoiceTimeRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new VoiceTimeRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    /** Simula o Postgres: guarda o último ms visto por chave (upsert de total absoluto). */
    private static final class FakeUpstream implements VoiceTimeFlusher.Upstream {
        final List<Long> received = new ArrayList<>();
        long lastMs = -1;

        @Override
        public void upsert(String guildId, String userId, long weekStart, long ms) {
            received.add(ms);
            lastMs = ms; // absoluto, não soma: reenviar não duplica
        }
    }

    @Test
    void flushSendsAbsoluteTotalAndClearsDirty() {
        repo.addMs("g1", "u1", WEEK, 1000);
        FakeUpstream up = new FakeUpstream();

        VoiceTimeFlusher.flushOnce(repo, up);

        assertEquals(List.of(1000L), up.received);
        assertTrue(repo.dirtyRows(10).isEmpty());
    }

    @Test
    void repeatedFlushIsIdempotent() {
        repo.addMs("g1", "u1", WEEK, 1000);
        FakeUpstream up = new FakeUpstream();

        VoiceTimeFlusher.flushOnce(repo, up);
        repo.addMs("g1", "u1", WEEK, 0); // volta a sujar sem mudar o valor
        VoiceTimeFlusher.flushOnce(repo, up);

        assertEquals(1000L, up.lastMs, "upsert absoluto: reenviar nao duplica");
    }

    @Test
    void rowIncrementedDuringFlushStaysDirty() {
        repo.addMs("g1", "u1", WEEK, 1000);

        // Upstream que incrementa a linha no meio do upsert, como o ticker faria.
        VoiceTimeFlusher.Upstream racy = (g, u, w, ms) -> repo.addMs(g, u, w, 500);

        VoiceTimeFlusher.flushOnce(repo, racy);

        assertEquals(1500, repo.msOf("g1", "u1", WEEK));
        assertEquals(1, repo.dirtyRows(10).size(),
                "os 500 novos ainda nao subiram: a linha precisa continuar suja");
    }

    @Test
    void upstreamFailureLeavesRowDirty() {
        repo.addMs("g1", "u1", WEEK, 1000);
        VoiceTimeFlusher.Upstream broken = (g, u, w, ms) -> {
            throw new IllegalStateException("neon offline");
        };

        VoiceTimeFlusher.flushOnce(repo, broken);

        assertEquals(1, repo.dirtyRows(10).size());
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceTimeFlusherTest"`
Expected: FAIL — `VoiceTimeFlusher` não existe.

- [ ] **Step 5: Write `VoiceTimeFlusher`**

`src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeFlusher.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.postgres.PostgresPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Sobe os baldes semanais sujos do SQLite para o Postgres, de onde o site lê o ranking.
 *
 * <p>O upsert manda o <b>total absoluto</b> da semana, nunca um delta, então reenviar a mesma
 * linha após um timeout parcial não duplica tempo. E {@code dirty} só é limpo se {@code ms} não
 * mudou durante o round-trip — senão o incremento do ticker se perderia.
 *
 * <p>Neon fora do ar não afeta a contagem: a linha continua suja e sobe na próxima rodada.
 */
public final class VoiceTimeFlusher {

    private static final Logger log = LoggerFactory.getLogger(VoiceTimeFlusher.class);

    /** Quantas linhas sujas por rodada. */
    public static final int BATCH = 500;

    /** Destino do flush. Extraído para testar a reconciliação sem um Postgres de verdade. */
    public interface Upstream {
        void upsert(String guildId, String userId, long weekStart, long ms) throws Exception;
    }

    private final VoiceTimeRepository repo;
    private final Upstream upstream;

    public VoiceTimeFlusher(BotContext ctx) {
        this.repo = new VoiceTimeRepository(ctx.database().sqlite());
        this.upstream = postgresUpstream(ctx.database().postgres());
    }

    public void flush() {
        flushOnce(repo, upstream);
    }

    /** Uma rodada. Cada linha é independente: uma falha não impede as outras. */
    public static void flushOnce(VoiceTimeRepository repo, Upstream upstream) {
        for (VoiceTimeRepository.DirtyRow row : repo.dirtyRows(BATCH)) {
            try {
                upstream.upsert(row.guildId(), row.userId(), row.weekStart(), row.ms());
                repo.clearDirty(row.guildId(), row.userId(), row.weekStart(), row.ms());
            } catch (Exception e) {
                log.warn("Falha ao sincronizar voice_weekly_time {}/{} semana {}: {}",
                        row.guildId(), row.userId(), row.weekStart(), e.toString());
            }
        }
    }

    private static Upstream postgresUpstream(PostgresPool pool) {
        String sql = "INSERT INTO voice_weekly_time (guild_id, user_id, week_start, ms, updated_at) "
                + "VALUES (?,?,?,?, now()) "
                + "ON CONFLICT (guild_id, user_id, week_start) "
                + "DO UPDATE SET ms = EXCLUDED.ms, updated_at = now()";
        return (guildId, userId, weekStart, ms) -> {
            try (Connection c = pool.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, guildId);
                ps.setString(2, userId);
                ps.setLong(3, weekStart);
                ps.setLong(4, ms);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new SQLException("upsert voice_weekly_time", e);
            }
        };
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceTimeFlusherTest"`
Expected: PASS, 4 testes.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/db/postgres/010_voice_weekly_time.sql src/main/java/dev/davimf/basebot/database/postgres/PostgresMigrator.java src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeFlusher.java src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeFlusherTest.java
git commit -m "feat(voice): tabela no Neon + flusher idempotente; registra migrations 008/009 esquecidas"
```

- [ ] **Step 8: Apply the Postgres schema (manual, fora do build)**

Rodar a ferramenta `ApplyPostgresSchema` conforme já documentado no projeto. Confirmar que `010_voice_weekly_time.sql` aparece na lista de aplicadas.

---

### Task 10: Poda de 90 dias

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceRetentionSweeper.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceRetentionSweeperTest.java`

**Interfaces:**
- Consumes: `VoiceRetention.cutoff` (T5), `VoiceSessionRepository.purgeClosedBefore` (T5), `VoiceTimeRepository.purgeWeeksBefore` (T5).
- Produces: `VoiceRetentionSweeper(BotContext)` com `sweep()`; `VoiceRetentionSweeper.sweepLocal(VoiceSessionRepository, VoiceTimeRepository, long now)`.

- [ ] **Step 1: Write the failing test**

`src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceRetentionSweeperTest.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class VoiceRetentionSweeperTest {

    private SqliteManager sqlite;
    private VoiceSessionRepository sessions;
    private VoiceTimeRepository times;

    private static final long NOW = 1_800_000_000_000L;
    private static final long DAY = TimeUnit.DAYS.toMillis(1);

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        sessions = new VoiceSessionRepository(sqlite);
        times = new VoiceTimeRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void deletesSessionsClosedOver90DaysAgo() {
        sessions.open("g1", "old", "c1", NOW - 92 * DAY);
        sessions.closeOpen("g1", "old", NOW - 91 * DAY);
        sessions.open("g1", "recent", "c1", NOW - 90 * DAY);
        sessions.closeOpen("g1", "recent", NOW - 89 * DAY);

        VoiceRetentionSweeper.sweepLocal(sessions, times, NOW);

        assertEquals(0, sessions.sessionsOf("g1", "old").size());
        assertEquals(1, sessions.sessionsOf("g1", "recent").size());
    }

    @Test
    void neverDeletesAnOpenSessionNoMatterHowOld() {
        sessions.open("g1", "marathon", "c1", NOW - 200 * DAY);

        VoiceRetentionSweeper.sweepLocal(sessions, times, NOW);

        assertNotNull(sessions.openSession("g1", "marathon"));
    }

    @Test
    void deletesOldSyncedWeeksButKeepsDirtyOnes() {
        long oldWeek = VoiceWeek.weekStart(NOW - 120 * DAY);
        long recentWeek = VoiceWeek.weekStart(NOW - 10 * DAY);

        times.addMs("g1", "synced", oldWeek, 100);
        times.clearDirty("g1", "synced", oldWeek, 100);
        times.addMs("g1", "pending", oldWeek, 200);   // continua dirty
        times.addMs("g1", "recent", recentWeek, 300);
        times.clearDirty("g1", "recent", recentWeek, 300);

        VoiceRetentionSweeper.sweepLocal(sessions, times, NOW);

        assertEquals(0, times.msOf("g1", "synced", oldWeek));
        assertEquals(200, times.msOf("g1", "pending", oldWeek),
                "linha nao sincronizada nunca e podada antes de subir");
        assertEquals(300, times.msOf("g1", "recent", recentWeek));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceRetentionSweeperTest"`
Expected: FAIL — `VoiceRetentionSweeper` não existe.

- [ ] **Step 3: Write `VoiceRetentionSweeper`**

`src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceRetentionSweeper.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.postgres.PostgresPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Poda diária dos dados de voz com mais de {@link VoiceRetention#DAYS} dias.
 *
 * <p>Duas guardas: sessões <b>abertas</b> nunca são apagadas, por mais longas que sejam; e baldes
 * semanais ainda <b>sujos</b> nunca são apagados antes de chegarem ao Postgres. Por isso o sweeper
 * é agendado depois do flusher.
 */
public final class VoiceRetentionSweeper {

    private static final Logger log = LoggerFactory.getLogger(VoiceRetentionSweeper.class);

    private final VoiceSessionRepository sessions;
    private final VoiceTimeRepository times;
    private final PostgresPool postgres;

    public VoiceRetentionSweeper(BotContext ctx) {
        this.sessions = new VoiceSessionRepository(ctx.database().sqlite());
        this.times = new VoiceTimeRepository(ctx.database().sqlite());
        this.postgres = ctx.database().postgres();
    }

    public void sweep() {
        long now = System.currentTimeMillis();
        sweepLocal(sessions, times, now);
        sweepPostgres(postgres, VoiceRetention.cutoff(now));
    }

    /** A parte que roda no SQLite. Separada para ser testável. */
    public static void sweepLocal(VoiceSessionRepository sessions, VoiceTimeRepository times, long now) {
        long cutoff = VoiceRetention.cutoff(now);
        int closed = sessions.purgeClosedBefore(cutoff);
        int weeks = times.purgeWeeksBefore(VoiceWeek.weekStart(cutoff));
        if (closed > 0 || weeks > 0) {
            log.info("Retenção de voz: {} sessões e {} baldes semanais podados (>{}d)",
                    closed, weeks, VoiceRetention.DAYS);
        }
    }

    private static void sweepPostgres(PostgresPool pool, long cutoff) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM voice_weekly_time WHERE week_start < ?")) {
            ps.setLong(1, VoiceWeek.weekStart(cutoff));
            ps.executeUpdate();
        } catch (Exception e) {
            // Neon fora do ar: tenta de novo amanhã. Nunca bloqueia a poda local.
            log.warn("Falha ao podar voice_weekly_time no Postgres: {}", e.toString());
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceRetentionSweeperTest"`
Expected: PASS, 3 testes.

- [ ] **Step 5: Check the retention side-effect on existing totals**

`VoiceSessionRepository.totalVoiceMs` e `sessionsOf` passam a refletir só os últimos 90 dias. Rodar:

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceSessionTotalsTest"`
Expected: PASS (o teste usa timestamps recentes). Se falhar, é sinal de que algo depende de "tempo total desde sempre" — **pare e reporte**, não ajuste o teste.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceRetentionSweeper.java src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceRetentionSweeperTest.java
git commit -m "feat(voice): poda diaria de 90 dias, preservando sessoes abertas e linhas sujas"
```

---

### Task 11: `/topcall` e `/tempocall`

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceFormat.java`
- Create: `src/main/java/dev/davimf/basebot/core/component/RankingPanel.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/leveling/TopCallView.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeComponentHandler.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/TopCallCommand.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/TempoCallCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/leveling/TopView.java` (passa a usar `RankingPanel`)
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java` (registro + agendamento)
- Test: `src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceFormatTest.java`
- Test: `src/test/java/dev/davimf/basebot/core/component/RankingPanelTest.java`

**Interfaces:**
- Consumes: `VoiceTimeRepository.topPage` / `msOf` / `count` (T5), `VoiceWeek.weekStart` (T1), `VoiceTimeFlusher` (T9), `VoiceRetentionSweeper` (T10).
- Produces:
  - `VoiceFormat.duration(long ms) -> String`
  - `RankingPanel.pageCount(int total, int pageSize) -> int`
  - `RankingPanel.of(int accent, String heading, String emptyLine, List<String> lines, String namespace, int page, int total, int pageSize, String footerSuffix) -> Container`
  - `TopCallView.NS` (`"vtime"`), `TopCallView.PAGE` (10), `TopCallView.panel(int accent, List<VoiceTimeRepository.Entry> entries, int page, int total) -> Container`

- [ ] **Step 1: Write the failing test**

`src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceFormatTest.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoiceFormatTest {

    @Test
    void zeroReadsAsZeroMinutes() {
        assertEquals("0m", VoiceFormat.duration(0));
    }

    @Test
    void underOneMinuteIsCalledOut() {
        assertEquals("menos de 1m", VoiceFormat.duration(59_000));
    }

    @Test
    void underOneHourShowsOnlyMinutes() {
        assertEquals("34m", VoiceFormat.duration(34 * 60_000L));
    }

    @Test
    void oneHourOrMoreShowsHoursAndMinutes() {
        assertEquals("1h 00m", VoiceFormat.duration(60 * 60_000L));
        assertEquals("12h 34m", VoiceFormat.duration((12 * 60 + 34) * 60_000L));
    }

    @Test
    void hoursDoNotWrapAtTwentyFour() {
        assertEquals("100h 00m", VoiceFormat.duration(100 * 60 * 60_000L));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceFormatTest"`
Expected: FAIL — `VoiceFormat` não existe.

- [ ] **Step 3: Write `VoiceFormat`**

`src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceFormat.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

/** Formata durações de call para exibição: {@code 12h 34m}, {@code 34m}, {@code menos de 1m}. */
public final class VoiceFormat {

    private VoiceFormat() {}

    public static String duration(long ms) {
        if (ms <= 0) {
            return "0m";
        }
        long totalMinutes = ms / 60_000L;
        if (totalMinutes == 0) {
            return "menos de 1m";
        }
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours == 0 ? minutes + "m" : hours + "h " + String.format("%02dm", minutes);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceFormatTest"`
Expected: PASS, 5 testes.

- [ ] **Step 5a: Write `RankingPanel` (paginador comum) com teste**

`/top` e `/topcall` compartilham a moldura: cabeçalho, linhas numeradas, divisor, rodapé `Página x/y` e os botões ◀ ▶. Só o conteúdo das linhas difere. Extraia a moldura em vez de duplicá-la.

`src/test/java/dev/davimf/basebot/core/component/RankingPanelTest.java`:

```java
package dev.davimf.basebot.core.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankingPanelTest {

    @Test
    void emptyRankingStillHasOnePage() {
        assertEquals(1, RankingPanel.pageCount(0, 10));
    }

    @Test
    void exactMultipleDoesNotAddATrailingEmptyPage() {
        assertEquals(1, RankingPanel.pageCount(10, 10));
        assertEquals(2, RankingPanel.pageCount(20, 10));
    }

    @Test
    void remainderRoundsUp() {
        assertEquals(2, RankingPanel.pageCount(11, 10));
        assertEquals(3, RankingPanel.pageCount(21, 10));
    }
}
```

Run: `./gradlew.bat test --tests "dev.davimf.basebot.core.component.RankingPanelTest"` → FAIL (`RankingPanel` não existe).

`src/main/java/dev/davimf/basebot/core/component/RankingPanel.java`:

```java
package dev.davimf.basebot.core.component;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Moldura compartilhada dos leaderboards paginados (/top, /topcall): cabeçalho, linhas já
 * formatadas pelo chamador, divisor, rodapé e os botões ◀ ▶.
 *
 * <p>Os botões usam a ação {@code "top"} no namespace do chamador, então cada ranking mantém o
 * seu próprio {@code ComponentHandler}.
 */
public final class RankingPanel {

    private RankingPanel() {}

    /** Ao menos 1, mesmo com ranking vazio. */
    public static int pageCount(int total, int pageSize) {
        return Math.max(1, (total + pageSize - 1) / pageSize);
    }

    /**
     * @param heading      título markdown, ex. {@code "## 🏆 Ranking de nível"}
     * @param emptyLine    exibido quando {@code lines} está vazia
     * @param lines        linhas já numeradas e formatadas
     * @param namespace    namespace do {@code ComponentHandler} que pagina este painel
     * @param footerSuffix acrescentado ao rodapé, ou {@code null}
     */
    public static Container of(int accent, String heading, String emptyLine, List<String> lines,
                               String namespace, int page, int total, int pageSize, String footerSuffix) {
        int pages = pageCount(total, pageSize);
        StringBuilder sb = new StringBuilder(heading);
        if (lines.isEmpty()) {
            sb.append("\n").append(emptyLine);
        } else {
            lines.forEach(line -> sb.append("\n").append(line));
        }

        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text(sb.toString()));
        kids.add(Panels.divider());
        kids.add(Panels.text("-# Página " + (page + 1) + "/" + pages
                + (footerSuffix == null ? "" : " · " + footerSuffix)));
        if (pages > 1) {
            kids.add(ActionRow.of(
                    Button.secondary(ComponentId.of(namespace, "top", String.valueOf(page - 1)), "◀")
                            .withDisabled(page <= 0),
                    Button.secondary(ComponentId.of(namespace, "top", String.valueOf(page + 1)), "▶")
                            .withDisabled(page >= pages - 1)));
        }
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }
}
```

Run: `./gradlew.bat test --tests "dev.davimf.basebot.core.component.RankingPanelTest"` → PASS.

- [ ] **Step 5b: Refactor `TopView` onto `RankingPanel`**

O `/top` já funciona; a refatoração é comportamentalmente neutra (o rodapé passa `null` como sufixo, gerando exatamente o texto atual). `TopView.java` inteiro:

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.component.RankingPanel;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.container.Container;

import java.util.ArrayList;
import java.util.List;

/** Leaderboard de nível, paginado (10/página). */
public final class TopView {

    public static final String NS = "lvl";
    public static final int PAGE = 10;

    private TopView() {}

    public static Container panel(int accent, List<UserLevelRepository.Entry> entries, int page, int total) {
        List<String> lines = new ArrayList<>();
        int base = page * PAGE;
        for (int i = 0; i < entries.size(); i++) {
            UserLevelRepository.Entry e = entries.get(i);
            lines.add("`" + (base + i + 1) + ".` <@" + e.userId() + "> · nível `"
                    + LevelFormula.levelForXp(e.xp()) + "` · `" + e.xp() + " XP`");
        }
        return RankingPanel.of(accent, "## " + Emojis.of(Emojis.TROPHY, "🏆") + " Ranking de nível",
                "-# Ninguém pontuou ainda.", lines, NS, page, total, PAGE, null);
    }
}
```

Run: `./gradlew.bat compileJava` → BUILD SUCCESSFUL. O `LevelingComponentHandler` não muda.

- [ ] **Step 5c: Write `TopCallView` on top of it**

`src/main/java/dev/davimf/basebot/modules/base/leveling/TopCallView.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.component.RankingPanel;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.container.Container;

import java.util.ArrayList;
import java.util.List;

/** Ranking semanal de tempo em call, paginado (10/página). */
public final class TopCallView {

    public static final String NS = "vtime";
    public static final int PAGE = 10;

    private TopCallView() {}

    public static Container panel(int accent, List<VoiceTimeRepository.Entry> entries, int page, int total) {
        List<String> lines = new ArrayList<>();
        int base = page * PAGE;
        for (int i = 0; i < entries.size(); i++) {
            VoiceTimeRepository.Entry e = entries.get(i);
            lines.add("`" + (base + i + 1) + ".` <@" + e.userId() + "> — `"
                    + VoiceFormat.duration(e.ms()) + "`");
        }
        return RankingPanel.of(accent, "## " + Emojis.of(Emojis.TROPHY, "🏆") + " Ranking de call — esta semana",
                "-# Ninguém entrou em call esta semana.", lines, NS, page, total, PAGE,
                "zera toda segunda 00:00");
    }
}
```

- [ ] **Step 6: Write the paging handler** (espelha `LevelingComponentHandler`)

`src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeComponentHandler.java`:

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/** Paginação do /topcall (namespace "vtime"). */
public final class VoiceTimeComponentHandler implements ComponentHandler {

    @Override
    public String namespace() { return TopCallView.NS; }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"top".equals(id.action()) || event.getGuild() == null) {
            return;
        }
        int page = Math.max(0, parse(id.arg(0)));
        String guildId = event.getGuild().getId();
        long week = VoiceWeek.weekStart(System.currentTimeMillis());
        VoiceTimeRepository repo = new VoiceTimeRepository(ctx.database().sqlite());
        int total = repo.count(guildId, week);
        var entries = repo.topPage(guildId, week, TopCallView.PAGE, page * TopCallView.PAGE);
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
        event.editComponents(TopCallView.panel(accent, entries, page, total)).useComponentsV2().queue();
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }
}
```

- [ ] **Step 7: Write `/topcall`**

`src/main/java/dev/davimf/basebot/modules/base/commands/TopCallCommand.java`:

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.leveling.TopCallView;
import dev.davimf.basebot.modules.base.leveling.VoiceTimeRepository;
import dev.davimf.basebot.modules.base.leveling.VoiceWeek;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /topcall — ranking semanal de tempo em call (paginado). */
public final class TopCallCommand implements SlashCommand {

    @Override public String name() { return "topcall"; }

    @Override
    public SlashCommandData data() {
        return Commands.slash("topcall", "Ranking de tempo em call desta semana.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        String guildId = event.getGuild().getId();
        long week = VoiceWeek.weekStart(System.currentTimeMillis());
        VoiceTimeRepository repo = new VoiceTimeRepository(ctx.database().sqlite());
        int total = repo.count(guildId, week);
        var entries = repo.topPage(guildId, week, TopCallView.PAGE, 0);
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
        event.replyComponents(TopCallView.panel(accent, entries, 0, total))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
```

- [ ] **Step 8: Write `/tempocall`**

`src/main/java/dev/davimf/basebot/modules/base/commands/TempoCallCommand.java`:

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.modules.base.leveling.VoiceFormat;
import dev.davimf.basebot.modules.base.leveling.VoiceTimeRepository;
import dev.davimf.basebot.modules.base.leveling.VoiceWeek;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /tempocall [membro] — tempo em call desta semana. */
public final class TempoCallCommand implements SlashCommand {

    @Override public String name() { return "tempocall"; }

    @Override
    public SlashCommandData data() {
        return Commands.slash("tempocall", "Tempo em call nesta semana.")
                .addOptions(new OptionData(OptionType.USER, "membro", "Membro (opcional)", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        OptionMapping option = event.getOption("membro");
        User target = option == null ? event.getUser() : option.getAsUser();
        String guildId = event.getGuild().getId();
        long week = VoiceWeek.weekStart(System.currentTimeMillis());
        long ms = new VoiceTimeRepository(ctx.database().sqlite()).msOf(guildId, target.getId(), week);
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));

        String body = "## " + Emojis.of(Emojis.CLOCK, "🕒") + " Tempo em call\n"
                + "> " + target.getAsMention() + "\n"
                + "**Esta semana** · `" + VoiceFormat.duration(ms) + "`\n"
                + "-# Zera toda segunda 00:00.";
        event.replyComponents(Panels.container(accent, Panels.text(body)))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
```

- [ ] **Step 9: Wire everything into `BaseModule`**

a) No bloco de registro, junto de `TopCommand` (linha ~294):

```java
        registry.command(new dev.davimf.basebot.modules.base.commands.TopCallCommand());
        registry.command(new dev.davimf.basebot.modules.base.commands.TempoCallCommand());
        registry.component(new dev.davimf.basebot.modules.base.leveling.VoiceTimeComponentHandler());
```

b) Em `onReady`, **fora** do `if (leveling != null)` — a contagem de tempo não depende do leveling. Acrescentar depois do bloco de leveling:

```java
        // Tempo em call: sobe os baldes sujos pro Neon a cada 5 min; poda de 90 dias 1x/dia.
        // O sweeper roda DEPOIS do flusher (initialDelay maior) para que uma linha suja
        // recém-criada tenha chance de subir antes de ser considerada para poda.
        dev.davimf.basebot.modules.base.leveling.VoiceTimeFlusher voiceFlusher =
                new dev.davimf.basebot.modules.base.leveling.VoiceTimeFlusher(ctx);
        ctx.scheduler().repeating(voiceFlusher::flush, 90, 300, TimeUnit.SECONDS);
        dev.davimf.basebot.modules.base.leveling.VoiceRetentionSweeper voiceSweeper =
                new dev.davimf.basebot.modules.base.leveling.VoiceRetentionSweeper(ctx);
        ctx.scheduler().repeating(voiceSweeper::sweep, 600, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
```

Nota: o `VoiceReconciler` roda aos 5s e o flusher aos 90s. A reconciliação termina bem antes do primeiro flush.

- [ ] **Step 10: Compile and run the full suite**

Run: `./gradlew.bat compileJava` then `./gradlew.bat test`
Expected: BUILD SUCCESSFUL, todos os testes passam.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceFormat.java src/main/java/dev/davimf/basebot/core/component/RankingPanel.java src/main/java/dev/davimf/basebot/modules/base/leveling/TopView.java src/main/java/dev/davimf/basebot/modules/base/leveling/TopCallView.java src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeComponentHandler.java src/main/java/dev/davimf/basebot/modules/base/commands/TopCallCommand.java src/main/java/dev/davimf/basebot/modules/base/commands/TempoCallCommand.java src/main/java/dev/davimf/basebot/modules/base/BaseModule.java src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceFormatTest.java src/test/java/dev/davimf/basebot/core/component/RankingPanelTest.java
git commit -m "feat(voice): /topcall e /tempocall sobre RankingPanel comum; agenda flusher e sweeper"
```

---

### Task 12: Tela de escopo no `/setup`

Uma tela dedicada, alcançada por botão a partir da tela de Nível. Quatro `EntitySelectMenu` numa `levelingScreen` já cheia estourariam o limite de componentes do container — por isso a tela separada.

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupView.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java`

**Interfaces:**
- Consumes: `VoiceTimeConfig.*` (T4), `ConfigIds.parse` (T2).
- Produces: `SetupView.voiceTimeScreen(GuildConfig cfg) -> Container`.

- [ ] **Step 1: Add `voiceTimeScreen` to `SetupView`**

Logo após `levelingScreen`, acrescentar:

```java
    // --- Tempo de call ---------------------------------------------------------

    public static Container voiceTimeScreen(GuildConfig cfg) {
        int accent = EmbedColor.resolve(cfg);
        java.util.Set<String> incCh = VoiceTimeConfig.includeChannels(cfg);
        java.util.Set<String> excCh = VoiceTimeConfig.excludeChannels(cfg);
        java.util.Set<String> incCat = VoiceTimeConfig.includeCategories(cfg);
        java.util.Set<String> excCat = VoiceTimeConfig.excludeCategories(cfg);

        String overview = Emojis.of(Emojis.CLOCK, "🕒") + " **Padrão** · só canais públicos contam tempo\n"
                + "-# Público = `@everyone` pode ver e entrar no canal.\n"
                + Emojis.of(Emojis.HASH, "#") + " **Canais** · `" + incCh.size() + "` incluídos · `"
                + excCh.size() + "` excluídos\n"
                + Emojis.of(Emojis.FOLDER, "📁") + " **Categorias** · `" + incCat.size() + "` incluídas · `"
                + excCat.size() + "` excluídas\n"
                + "-# Precedência: canal > categoria > padrão. Exclusão vence inclusão no mesmo nível.";

        List<ContainerChildComponent> kids = new java.util.ArrayList<>();
        kids.add(Panels.text("## " + Emojis.of(Emojis.CLOCK, "🕒") + " Tempo de call"));
        kids.add(Panels.divider());
        kids.add(Panels.text(overview));
        kids.add(Panels.divider());
        kids.add(ActionRow.of(voiceScopeSelect("vtincch", ChannelType.VOICE,
                "Canais que SEMPRE contam…", incCh)));
        kids.add(ActionRow.of(voiceScopeSelect("vtexcch", ChannelType.VOICE,
                "Canais que NUNCA contam…", excCh)));
        kids.add(ActionRow.of(voiceScopeSelect("vtinccat", ChannelType.CATEGORY,
                "Categorias que SEMPRE contam…", incCat)));
        kids.add(ActionRow.of(voiceScopeSelect("vtexccat", ChannelType.CATEGORY,
                "Categorias que NUNCA contam…", excCat)));
        kids.add(moduleNav("nivel"));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    private static EntitySelectMenu voiceScopeSelect(String action, ChannelType type,
                                                     String placeholder, java.util.Set<String> selected) {
        EntitySelectMenu.Builder b = EntitySelectMenu.create(ComponentId.of(NS, action), SelectTarget.CHANNEL)
                .setChannelTypes(type)
                .setPlaceholder(placeholder)
                .setRequiredRange(0, 25);
        if (!selected.isEmpty()) {
            b.setDefaultValues(selected.stream().map(DefaultValue::channel).toList());
        }
        return b.build();
    }
```

Imports a acrescentar no topo de `SetupView.java` (se ainda não existirem): `dev.davimf.basebot.modules.base.leveling.VoiceTimeConfig`.

Se `moduleNav("nivel")` não for a chave correta da tela de Nível, confirme lendo as outras chamadas a `moduleNav(...)` no arquivo e use a mesma string usada por `levelingScreen`.

- [ ] **Step 2: Add the entry button on `levelingScreen`**

Em `levelingScreen`, antes de `kids.add(moduleNav(...))`, acrescentar:

```java
        kids.add(ActionRow.of(Button.secondary(ComponentId.of(NS, "vtscope"),
                "Tempo de call — escopo de canais")));
```

- [ ] **Step 3: Handle the button in `SetupComponentHandler`**

No `switch` de botões (onde ficam os outros `case` de navegação), acrescentar:

```java
            case "vtscope" -> edit(event, SetupView.voiceTimeScreen(config(ctx, event.getGuild().getId())));
```

- [ ] **Step 4: Handle the four selects in `SetupComponentHandler`**

No `switch` de `EntitySelectInteractionEvent` — o mesmo bloco onde vive `case "nivelignored"` (linha ~435) — acrescentar:

```java
            case "vtincch" -> saveVoiceScope(event, ctx, VoiceTimeConfig.KEY_INCLUDE_CHANNELS);
            case "vtexcch" -> saveVoiceScope(event, ctx, VoiceTimeConfig.KEY_EXCLUDE_CHANNELS);
            case "vtinccat" -> saveVoiceScope(event, ctx, VoiceTimeConfig.KEY_INCLUDE_CATEGORIES);
            case "vtexccat" -> saveVoiceScope(event, ctx, VoiceTimeConfig.KEY_EXCLUDE_CATEGORIES);
```

E o helper, junto dos outros métodos privados:

```java
    private void saveVoiceScope(EntitySelectInteractionEvent event, BotContext ctx, String key) {
        String guildId = event.getGuild().getId();
        String csv = event.getMentions().getChannels().stream()
                .map(net.dv8tion.jda.api.entities.channel.middleman.GuildChannel::getId)
                .collect(java.util.stream.Collectors.joining(","));
        GuildConfig updated = GuildConfigEdits.withSetting(config(ctx, guildId), key, csv);
        ctx.database().guildConfig().save(updated);
        edit(event, SetupView.voiceTimeScreen(updated));
    }
```

Import a acrescentar: `dev.davimf.basebot.modules.base.leveling.VoiceTimeConfig`.

- [ ] **Step 5: Compile**

Run: `./gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL. Se `EntitySelectInteractionEvent` não for o tipo do handler, use o mesmo tipo declarado pelo método que trata `"nivelignored"`.

- [ ] **Step 6: Run the full suite**

Run: `./gradlew.bat test`
Expected: todos os testes passam.

- [ ] **Step 7: Manual verification**

Subir o bot num servidor de teste e confirmar, nesta ordem:

1. `/setup` → Nível → "Tempo de call — escopo de canais" abre a tela.
2. Selecionar um canal em "NUNCA contam" e reabrir a tela: a seleção persiste.
3. Entrar num canal público, esperar ~2 min, rodar `/tempocall`: mostra ~2m.
4. Mutar o próprio microfone, esperar 2 min, rodar `/tempocall`: **o tempo não subiu**.
5. Desmutar, esperar 1 min: o tempo volta a subir, sem crédito retroativo dos 2 min mutados.
6. `/topcall` lista o usuário.
7. Entrar num canal excluído no escopo: `/tempocall` não sobe.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/setup/SetupView.java src/main/java/dev/davimf/basebot/modules/base/setup/SetupComponentHandler.java
git commit -m "feat(setup): tela de escopo de canais para tempo de call"
```

---

## Cobertura do spec

| Requisito do spec | Task |
|---|---|
| Self-mute pausa XP e tempo | 3 |
| Server-mute não pausa nada | 3 |
| Deafen (self/guild) e AFK pausam ambos | 3 |
| ≥2 humanos só para XP | 3 |
| Escopo de canais, precedência canal > categoria > padrão | 4 |
| Público = permissão efetiva de `@everyone` | 4 |
| `parseIds` tolerante | 2 |
| Semana = segunda 00:00 America/Sao_Paulo | 1 |
| `splitByWeek` como única divisão | 1, 6 |
| Watermarks independentes, avançam sem crédito | 6 |
| Valor anterior vem do evento | 8 |
| Move avalia com o canal antigo | 8 |
| Boot não credita retroativo | 8 |
| Upsert absoluto idempotente | 9 |
| `dirty` limpo só se `ms` não mudou | 5, 9 |
| Neon fora do ar não afeta a contagem | 9 |
| Retenção de 90 dias, guardas de aberta/suja | 10 |
| `/topcall` com `ms > 0` | 5, 11 |
| `/tempocall` | 11 |
| Formato `12h 34m` | 11 |
| Tela de `/setup` | 12 |
| Tabela no Neon para o site | 9 |
