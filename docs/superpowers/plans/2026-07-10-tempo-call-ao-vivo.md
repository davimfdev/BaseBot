# Tempo de call ao vivo + escopo aceitando o cargo membro — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `/tempocall` e `/topcall` passam a mostrar o tempo em call ao vivo (salvo + a janela ainda não creditada da sessão em curso), com precisão de segundos; e o escopo padrão passa a contar canais abertos ao **cargo membro**, não só a `@everyone`.

**Architecture:** O ticker credita a cada 60s, então a janela pendente de quem está em call é sempre menor que um minuto. Uma função pura (`VoicePending`) calcula essa janela, recortada no início da semana; outra (`VoiceRanking`) funde o salvo com o pendente, filtra zeros e ordena. Só a coleta do estado de voz (`VoiceLive`) toca a JDA. O escopo padrão passa a perguntar "está aberto aos membros?" em vez de "é público?".

**Tech Stack:** Java 22 (Gradle no JDK 22), JDA 6.4.2, SQLite, JUnit 5.

## Contexto que o implementador precisa saber

- O bot conta tempo em call por semana (segunda 00:00, `America/Sao_Paulo`). O SQLite é a fonte de verdade.
- Cada sessão aberta tem uma watermark `time_credited_until`. O ticker de 60s credita `[watermark, agora]` e avança a watermark, **mesmo quando nada é elegível**. Logo o "pendente" nunca passa de ~60s.
- `VoiceGate` é a trava de reconciliação, **por guild**. Enquanto fechada, nada pode ser creditado nem contado como pendente — a sessão ainda carrega a watermark de antes de um restart.
- `VoiceEligibility.timeEligible(snapshot)` = `!bot && !deafened && !afkChannel && !selfMuted && inScope`.

## Por que o escopo muda (não é enfeite)

O padrão hoje é "conta se `@everyone` tem `VIEW_CHANNEL` e `VOICE_CONNECT` efetivos". Mas o lockdown de verificação (`VerificationLockdown`) **nega** `@everyone` nos canais e **concede** ao cargo membro. Num servidor que usa lockdown, portanto, **nenhum canal conta tempo hoje** — o ranking fica vazio sem erro nenhum. O padrão passa a ser: conta se `@everyone` **ou** o cargo `membro` tem as duas permissões efetivas.

O cargo membro é lido de `cfg.role("membro")` — a mesma chave que `SecurityComponentHandler` usa.

## Global Constraints

- **Sem Mockito.** JUnit 5 puro. Classes que precisam de JDA viva (`VoiceLive`, comandos, handler) são verificadas por compilação. Não inventar teste com mock.
- **Toda mensagem do bot é um Container Components V2** (helper `Panels`). Nunca texto puro.
- **Emojis sempre pelo registry:** `Emojis.of(Emojis.CLOCK, "🕒")`. Nunca Unicode cru.
- Build: `./gradlew.bat compileJava`, `./gradlew.bat test`, `./gradlew.bat build -x shadowJar`. Nunca `./gradlew`.
- Baseline atual: **420 testes, 0 falhas**. A suíte tem de continuar verde.
- Trabalhar direto na `main`; não criar branches. `docs/` é gitignored (`git add -f` quando necessário).
- A árvore tem trabalho não commitado alheio (`PurgeLogSuppressor`, `BaseModule.java`). **Commitar só os arquivos de cada task**, nunca `git add -A` / `git add .`.

## File Structure

**Criar:**

| Arquivo | Responsabilidade |
|---|---|
| `modules/base/leveling/VoicePending.java` | Janela pendente, pura, recortada na semana |
| `modules/base/leveling/VoicePauseReason.java` | Por que o tempo não está subindo, puro |
| `modules/base/leveling/VoiceRanking.java` | Funde salvo + pendente, filtra e ordena, puro |
| `modules/base/leveling/VoiceLive.java` | Coleta o estado de voz da JDA e usa os três acima |

**Modificar:**

| Arquivo | Mudança |
|---|---|
| `leveling/VoiceFormat.java` | `duration(ms)` → `precise(ms)` com segundos |
| `leveling/VoiceScope.java` | `publicChannel` → `openByDefault`; adaptador consulta o cargo membro |
| `leveling/VoiceTimeRepository.java` | + `allOfWeek(guildId, weekStart)` |
| `leveling/TopCallView.java` | Usa `VoiceFormat.precise` |
| `leveling/VoiceTimeComponentHandler.java` | Ranking ao vivo; recebe `VoiceGate` |
| `commands/TopCallCommand.java` | Ranking ao vivo; recebe `VoiceGate` |
| `commands/TempoCallCommand.java` | Salvo / em andamento / total; recebe `VoiceGate` |
| `modules/base/BaseModule.java` | Passa `voiceGate` aos dois comandos e ao handler |

---

### Task 1: `VoiceFormat.precise` — segundos

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceFormat.java` (reescrever)
- Modify: `src/main/java/dev/davimf/basebot/modules/base/leveling/TopCallView.java:24`
- Test: `src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceFormatTest.java` (reescrever)

**Interfaces:**
- Consumes: nada.
- Produces: `VoiceFormat.precise(long ms) -> String`. `duration(long)` **deixa de existir**.

- [ ] **Step 1: Write the failing test** (substitui o arquivo inteiro)

```java
package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoiceFormatTest {

    @Test
    void zeroAndNegativeReadAsZeroSeconds() {
        assertEquals("0s", VoiceFormat.precise(0));
        assertEquals("0s", VoiceFormat.precise(-5));
    }

    @Test
    void subSecondTruncatesToZero() {
        assertEquals("0s", VoiceFormat.precise(999));
    }

    @Test
    void underOneMinuteShowsOnlySeconds() {
        assertEquals("1s", VoiceFormat.precise(1_000));
        assertEquals("59s", VoiceFormat.precise(59_000));
    }

    @Test
    void underOneHourShowsMinutesAndZeroPaddedSeconds() {
        assertEquals("1m 00s", VoiceFormat.precise(60_000));
        assertEquals("47m 03s", VoiceFormat.precise((47 * 60 + 3) * 1_000L));
        assertEquals("59m 59s", VoiceFormat.precise(3_599_000));
    }

    @Test
    void oneHourOrMoreShowsAllThreeUnits() {
        assertEquals("1h 00m 00s", VoiceFormat.precise(3_600_000));
        assertEquals("12h 34m 07s", VoiceFormat.precise((12 * 3600 + 34 * 60 + 7) * 1_000L));
    }

    @Test
    void hoursDoNotWrapAtTwentyFour() {
        assertEquals("100h 00m 00s", VoiceFormat.precise(100 * 3_600_000L));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceFormatTest"`
Expected: FAIL — `precise` não existe.

- [ ] **Step 3: Rewrite `VoiceFormat`**

```java
package dev.davimf.basebot.modules.base.leveling;

/** Formata durações de call: {@code 12h 34m 07s}, {@code 47m 03s}, {@code 52s}, {@code 0s}. */
public final class VoiceFormat {

    private VoiceFormat() {}

    public static String precise(long ms) {
        if (ms <= 0) {
            return "0s";
        }
        long totalSeconds = ms / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return hours + "h " + String.format("%02dm %02ds", minutes, seconds);
        }
        if (minutes > 0) {
            return minutes + "m " + String.format("%02ds", seconds);
        }
        return seconds + "s";
    }
}
```

- [ ] **Step 4: Point `TopCallView` at it**

Em `TopCallView.java`, trocar `VoiceFormat.duration(e.ms())` por `VoiceFormat.precise(e.ms())`.

- [ ] **Step 5: Run the tests**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceFormatTest"` then `./gradlew.bat compileJava`
Expected: PASS, 6 testes; BUILD SUCCESSFUL. Se algo ainda chamar `VoiceFormat.duration`, o compilador aponta — o único chamador era `TopCallView` e `TempoCallCommand` (este é reescrito na Task 7; se ele quebrar agora, troque também a chamada para `precise`).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceFormat.java src/main/java/dev/davimf/basebot/modules/base/leveling/TopCallView.java src/main/java/dev/davimf/basebot/modules/base/commands/TempoCallCommand.java src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceFormatTest.java
git commit -m "feat(voice): VoiceFormat.precise mostra segundos"
```

---

### Task 2: `VoicePending` — a janela ainda não creditada

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoicePending.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/leveling/VoicePendingTest.java`

**Interfaces:**
- Consumes: nada.
- Produces: `VoicePending.pendingMs(long watermark, long now, long weekStart, boolean eligible) -> long`.

- [ ] **Step 1: Write the failing test**

```java
package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoicePendingTest {

    private static final long WEEK = VoiceWeek.weekStart(1_700_000_000_000L);

    @Test
    void ineligibleMemberHasNoPendingTime() {
        assertEquals(0, VoicePending.pendingMs(WEEK + 1_000, WEEK + 61_000, WEEK, false));
    }

    @Test
    void pendingIsTheGapSinceTheWatermark() {
        assertEquals(30_000, VoicePending.pendingMs(WEEK + 1_000, WEEK + 31_000, WEEK, true));
    }

    @Test
    void watermarkBeforeTheWeekStartIsClippedToTheWeekStart() {
        // Sessão aberta no domingo: só o tempo DESTA semana conta para esta semana.
        long lastWeek = WEEK - 600_000;
        assertEquals(45_000, VoicePending.pendingMs(lastWeek, WEEK + 45_000, WEEK, true));
    }

    @Test
    void watermarkAheadOfNowYieldsZeroNotNegative() {
        assertEquals(0, VoicePending.pendingMs(WEEK + 90_000, WEEK + 30_000, WEEK, true));
    }

    @Test
    void watermarkExactlyAtNowYieldsZero() {
        assertEquals(0, VoicePending.pendingMs(WEEK + 5_000, WEEK + 5_000, WEEK, true));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoicePendingTest"`
Expected: FAIL — `VoicePending` não existe.

- [ ] **Step 3: Write `VoicePending`**

```java
package dev.davimf.basebot.modules.base.leveling;

/**
 * A janela de tempo já vivida mas ainda não creditada: {@code [watermark, agora]}.
 *
 * <p>O ticker credita a cada 60s e avança a watermark de toda sessão aberta, então na prática isto
 * nunca passa de ~1 minuto. Existe para o {@code /tempocall} e o {@code /topcall} não ficarem
 * atrasados até um minuto.
 *
 * <p>A janela é recortada no início da semana: quem está em call desde domingo não deve ver o tempo
 * da semana passada somado a esta.
 */
public final class VoicePending {

    private VoicePending() {}

    /** {@code 0} quando o membro não está elegível (mutado, ensurdecido, AFK, fora do escopo). */
    public static long pendingMs(long watermark, long now, long weekStart, boolean eligible) {
        if (!eligible) {
            return 0;
        }
        long from = Math.max(watermark, weekStart);
        return Math.max(0, now - from);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoicePendingTest"`
Expected: PASS, 5 testes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/leveling/VoicePending.java src/test/java/dev/davimf/basebot/modules/base/leveling/VoicePendingTest.java
git commit -m "feat(voice): VoicePending — janela nao creditada, recortada na semana"
```

---

### Task 3: `VoicePauseReason` — por que o tempo não sobe

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoicePauseReason.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/leveling/VoicePauseReasonTest.java`

**Interfaces:**
- Consumes: `VoiceStateSnapshot` (já existe: `record VoiceStateSnapshot(boolean bot, long humanCount, boolean selfMuted, boolean selfDeafened, boolean guildDeafened, boolean afkChannel, boolean inScope)` com `deafened()`, `withSelfMuted`, `withSelfDeafened`, `withGuildDeafened`).
- Produces: `VoicePauseReason.of(VoiceStateSnapshot s) -> String` (`null` quando está contando).

- [ ] **Step 1: Write the failing test**

```java
package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VoicePauseReasonTest {

    /** Humano, sozinho basta para tempo, mic aberto, ouvindo, fora do AFK, canal no escopo. */
    private static VoiceStateSnapshot counting() {
        return new VoiceStateSnapshot(false, 1, false, false, false, false, true);
    }

    @Test
    void countingMemberHasNoReason() {
        assertNull(VoicePauseReason.of(counting()));
    }

    @Test
    void selfMuteIsReported() {
        assertEquals("microfone fechado", VoicePauseReason.of(counting().withSelfMuted(true)));
    }

    @Test
    void bothDeafenFlagsReportTheSameReason() {
        assertEquals("ensurdecido", VoicePauseReason.of(counting().withSelfDeafened(true)));
        assertEquals("ensurdecido", VoicePauseReason.of(counting().withGuildDeafened(true)));
    }

    @Test
    void afkChannelIsReported() {
        assertEquals("canal AFK", VoicePauseReason.of(
                new VoiceStateSnapshot(false, 1, false, false, false, true, true)));
    }

    @Test
    void outOfScopeChannelIsReported() {
        assertEquals("canal fora do escopo", VoicePauseReason.of(
                new VoiceStateSnapshot(false, 1, false, false, false, false, false)));
    }

    @Test
    void selfMuteWinsOverEveryOtherReason() {
        VoiceStateSnapshot everything = new VoiceStateSnapshot(false, 1, true, true, true, true, false);
        assertEquals("microfone fechado", VoicePauseReason.of(everything));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoicePauseReasonTest"`
Expected: FAIL — `VoicePauseReason` não existe.

- [ ] **Step 3: Write `VoicePauseReason`**

```java
package dev.davimf.basebot.modules.base.leveling;

/**
 * Traduz um estado de voz que NÃO está contando tempo no motivo, para o {@code /tempocall} explicar
 * em vez de mostrar um zero inexplicável.
 *
 * <p>Vários motivos podem valer ao mesmo tempo; a ordem abaixo é fixa e escolhe o mais acionável
 * pelo próprio membro primeiro.
 */
public final class VoicePauseReason {

    private VoicePauseReason() {}

    /** {@code null} quando o tempo está sendo contado. */
    public static String of(VoiceStateSnapshot s) {
        if (s.selfMuted()) {
            return "microfone fechado";
        }
        if (s.deafened()) {
            return "ensurdecido";
        }
        if (s.afkChannel()) {
            return "canal AFK";
        }
        if (!s.inScope()) {
            return "canal fora do escopo";
        }
        return null;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoicePauseReasonTest"`
Expected: PASS, 6 testes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/leveling/VoicePauseReason.java src/test/java/dev/davimf/basebot/modules/base/leveling/VoicePauseReasonTest.java
git commit -m "feat(voice): VoicePauseReason explica por que o tempo esta pausado"
```

---

### Task 4: escopo padrão aceita o cargo membro

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceScope.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceScopeTest.java` (acrescentar, não reescrever)

**Interfaces:**
- Consumes: `GuildConfig.role(String key) -> String` (nullable).
- Produces: `VoiceScope.counts(String channelId, String categoryId, boolean openByDefault, GuildConfig cfg) -> boolean` (só o nome do 3º parâmetro muda; posição e semântica idênticas). `VoiceScope.inScope(AudioChannel, GuildConfig)` inalterada em assinatura.

**Nota sobre testes.** O núcleo puro `counts(...)` **não muda de comportamento** — só o nome do 3º parâmetro. Os oito testes de `VoiceScopeTest` já o cobrem (precedência, exclusão vence inclusão, canal sem categoria, `parseIds` tolerante) e continuam válidos sem alteração. Não acrescente testes duplicando o que eles já afirmam.

A mudança real está no adaptador `inScope`, que consulta a JDA e por isso é compile-checked, como as demais classes JDA-dependentes do projeto.

- [ ] **Step 1: Rename the one test whose NAME now lies**

Em `VoiceScopeTest`, o teste `withoutListsFallsBackToPublicDefault` passa a se chamar
`withoutListsFallsBackToTheOpenByDefaultFlag`. O corpo não muda. É o único ponto do arquivo em que
a palavra "public" descreve algo que deixou de ser só sobre `@everyone`.

- [ ] **Step 2: Run it to confirm nothing broke**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceScopeTest"`
Expected: PASS, 8 testes.

- [ ] **Step 3: Rewrite `VoiceScope`**

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

/**
 * Decide se um canal de voz conta tempo. Precedência: canal &gt; categoria &gt; padrão.
 * Dentro de cada nível a exclusão é avaliada primeiro, então um ID presente nas duas listas do
 * mesmo nível não conta — configuração contraditória nunca abre acesso.
 *
 * <p>O padrão é "aberto aos membros": {@code @everyone} <b>ou</b> o cargo membro
 * ({@code guild_config.roles["membro"]}) com {@code VIEW_CHANNEL} e {@code VOICE_CONNECT}
 * <b>efetivos</b> (herança de categoria + overrides), não o override local.
 *
 * <p>O cargo membro entra porque o lockdown de verificação nega {@code @everyone} nos canais e
 * concede ao cargo membro. Sem isto, um servidor com lockdown não contaria tempo em canal nenhum.
 */
public final class VoiceScope {

    /** Mesma chave usada por {@code SecurityComponentHandler} para o cargo de membro verificado. */
    private static final String ROLE_MEMBER = "membro";

    private VoiceScope() {}

    /** Núcleo puro, sem JDA — testável sem mocks. {@code categoryId} pode ser null. */
    public static boolean counts(String channelId, String categoryId, boolean openByDefault, GuildConfig cfg) {
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
        return openByDefault;
    }

    /** Adaptador: resolve categoria e "aberto aos membros" a partir da JDA e delega ao núcleo. */
    public static boolean inScope(AudioChannel channel, GuildConfig cfg) {
        Category category = channel instanceof ICategorizableChannel c ? c.getParentCategory() : null;
        return counts(channel.getId(), category == null ? null : category.getId(),
                openToMembers(channel, cfg), cfg);
    }

    /**
     * {@code @everyone} ou o cargo membro pode ver E entrar no canal.
     *
     * <p>{@code hasPermission(GuildChannel, ...)} de {@code IPermissionHolder} já é a permissão
     * EFETIVA. Não usar {@code getPermissionOverride} (só override local) nem {@code PermissionUtil}
     * (API interna na JDA 6.4.2).
     */
    private static boolean openToMembers(AudioChannel channel, GuildConfig cfg) {
        Guild guild = channel.getGuild();
        if (guild.getPublicRole().hasPermission(channel, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT)) {
            return true;
        }
        String memberRoleId = cfg.role(ROLE_MEMBER);
        if (memberRoleId == null || memberRoleId.isBlank()) {
            return false;
        }
        Role member = guild.getRoleById(memberRoleId);
        return member != null
                && member.hasPermission(channel, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT);
    }
}
```

- [ ] **Step 4: Compile and run the whole leveling suite**

Run: `./gradlew.bat compileJava` then `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.*"`
Expected: BUILD SUCCESSFUL, PASS. Se `Role` ou `Guild` não resolverem, confirmar o pacote com
`javap -classpath "C:\Users\davi\.gradle\caches\modules-2\files-2.1\net.dv8tion\JDA\6.4.2\bac737719a47996d36e2b4d87a6b20e6c578899e\JDA-6.4.2.jar" net.dv8tion.jda.api.entities.Role`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceScope.java src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceScopeTest.java
git commit -m "fix(voice): escopo padrao conta canal aberto ao cargo membro, nao so a @everyone"
```

---

### Task 5: `VoiceRanking` — funde salvo + pendente, ordena

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceRanking.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceRankingTest.java`

**Interfaces:**
- Consumes: `record VoiceTimeRepository.Entry(String userId, long ms)` (já existe).
- Produces: `VoiceRanking.merge(List<VoiceTimeRepository.Entry> saved, Map<String, Long> pending) -> List<VoiceTimeRepository.Entry>` — totais, sem zeros, ordenados por `ms` decrescente e `userId` como desempate.

- [ ] **Step 1: Write the failing test**

```java
package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceRankingTest {

    private static VoiceTimeRepository.Entry e(String user, long ms) {
        return new VoiceTimeRepository.Entry(user, ms);
    }

    @Test
    void addsPendingOnTopOfSaved() {
        List<VoiceTimeRepository.Entry> out =
                VoiceRanking.merge(List.of(e("u1", 1_000)), Map.of("u1", 500L));
        assertEquals(List.of(e("u1", 1_500)), out);
    }

    @Test
    void memberWithOnlyPendingTimeAppears() {
        // Entrou na call há 30s: ainda não tem linha no banco, mas já deve aparecer.
        List<VoiceTimeRepository.Entry> out =
                VoiceRanking.merge(List.of(), Map.of("novato", 30_000L));
        assertEquals(List.of(e("novato", 30_000)), out);
    }

    @Test
    void pendingCanChangeTheOrder() {
        List<VoiceTimeRepository.Entry> out = VoiceRanking.merge(
                List.of(e("lider", 10_000), e("vice", 9_500)),
                Map.of("vice", 1_000L));
        assertEquals(List.of(e("vice", 10_500), e("lider", 10_000)), out);
    }

    @Test
    void zeroTotalsAreHidden() {
        List<VoiceTimeRepository.Entry> out =
                VoiceRanking.merge(List.of(e("fantasma", 0)), Map.of());
        assertTrue(out.isEmpty());
    }

    @Test
    void tiesBreakOnUserIdSoPaginationIsStable() {
        List<VoiceTimeRepository.Entry> out =
                VoiceRanking.merge(List.of(e("b", 100), e("a", 100)), Map.of());
        assertEquals(List.of(e("a", 100), e("b", 100)), out);
    }

    @Test
    void savedWithoutPendingIsUntouched() {
        List<VoiceTimeRepository.Entry> out =
                VoiceRanking.merge(List.of(e("u1", 7_000)), Map.of("outro", 400L));
        assertEquals(List.of(e("u1", 7_000), e("outro", 400)), out);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceRankingTest"`
Expected: FAIL — `VoiceRanking` não existe.

- [ ] **Step 3: Write `VoiceRanking`**

```java
package dev.davimf.basebot.modules.base.leveling;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Funde o tempo salvo no banco com o pendente de quem está em call agora, descarta totais zerados e
 * ordena. Puro, para o ranking ser testável sem JDA.
 *
 * <p>Ordenar aqui — e não no SQL — é o que permite o pendente mudar posições. Também torna a
 * contagem total e a página exibida coerentes: ambas saem da MESMA lista, e não de duas queries
 * independentes que podem discordar.
 */
public final class VoiceRanking {

    private VoiceRanking() {}

    public static List<VoiceTimeRepository.Entry> merge(List<VoiceTimeRepository.Entry> saved,
                                                        Map<String, Long> pending) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (VoiceTimeRepository.Entry e : saved) {
            totals.merge(e.userId(), e.ms(), Long::sum);
        }
        pending.forEach((userId, ms) -> totals.merge(userId, ms, Long::sum));

        List<VoiceTimeRepository.Entry> out = new ArrayList<>();
        totals.forEach((userId, ms) -> {
            if (ms > 0) {
                out.add(new VoiceTimeRepository.Entry(userId, ms));
            }
        });
        out.sort(Comparator.comparingLong(VoiceTimeRepository.Entry::ms).reversed()
                .thenComparing(VoiceTimeRepository.Entry::userId));
        return out;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceRankingTest"`
Expected: PASS, 6 testes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceRanking.java src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceRankingTest.java
git commit -m "feat(voice): VoiceRanking funde salvo e pendente e ordena"
```

---

### Task 6: `allOfWeek` + `VoiceLive`

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeRepository.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceLive.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeRepositoryTest.java` (acrescentar)

**Interfaces:**
- Consumes: `VoicePending.pendingMs` (T2), `VoicePauseReason.of` (T3), `VoiceRanking.merge` (T5), `VoiceSnapshots.of(Member, AudioChannel, GuildConfig, long humanBonus)`, `VoiceEligibility.timeEligible(VoiceStateSnapshot)`, `VoiceGate.isReconciled(String guildId)`, `VoiceSessionRepository.openSessions(String guildId)` e `openSession(String guildId, String userId)` (cujo `Open` tem `timeCreditedUntil()`).
- Produces:
  - `VoiceTimeRepository.allOfWeek(String guildId, long weekStart) -> List<Entry>` (todas as linhas da semana, **sem** filtrar `ms > 0`)
  - `record VoiceLive.Snapshot(long savedMs, long pendingMs, String pauseReason, boolean inCall)` com `totalMs()`
  - `VoiceLive.pendingByUser(Guild, GuildConfig, VoiceGate, VoiceSessionRepository, long now, long weekStart) -> Map<String, Long>`
  - `VoiceLive.forUser(Guild, String userId, GuildConfig, VoiceGate, VoiceSessionRepository, VoiceTimeRepository, long now, long weekStart) -> Snapshot`
  - `VoiceLive.ranking(Guild, GuildConfig, VoiceGate, VoiceSessionRepository, VoiceTimeRepository, long now, long weekStart) -> List<VoiceTimeRepository.Entry>`

- [ ] **Step 1: Write the failing test** (acrescentar ao final de `VoiceTimeRepositoryTest`)

```java
    @Test
    void allOfWeekReturnsEveryRowIncludingZeroes() {
        repo.addMs("g1", "u1", W1, 100);
        repo.addMs("g1", "zero", W1, 0);
        repo.addMs("g1", "outra-semana", W2, 50);
        repo.addMs("g2", "outra-guild", W1, 70);

        List<VoiceTimeRepository.Entry> rows = repo.allOfWeek("g1", W1);

        assertEquals(2, rows.size(), "linhas com ms=0 tambem voltam: quem tem pendente pode somar");
        assertTrue(rows.stream().anyMatch(e -> e.userId().equals("u1") && e.ms() == 100));
        assertTrue(rows.stream().anyMatch(e -> e.userId().equals("zero") && e.ms() == 0));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceTimeRepositoryTest"`
Expected: FAIL — `allOfWeek` não existe.

- [ ] **Step 3: Add `allOfWeek` to `VoiceTimeRepository`**

```java
    /**
     * Todas as linhas da semana, inclusive as com {@code ms = 0}. O ranking soma o tempo pendente
     * por cima, então filtrar aqui esconderia quem acabou de entrar na call.
     */
    public List<Entry> allOfWeek(String guildId, long weekStart) {
        String sql = "SELECT user_id, ms FROM voice_weekly_time WHERE guild_id=? AND week_start=?";
        List<Entry> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setLong(2, weekStart);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Entry(rs.getString("user_id"), rs.getLong("ms")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("allOfWeek " + guildId, e);
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat test --tests "dev.davimf.basebot.modules.base.leveling.VoiceTimeRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Write `VoiceLive`**

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Junta o tempo salvo no SQLite com a janela ainda não creditada de quem está em call agora.
 *
 * <p>É a única classe desta feature que toca a JDA: a aritmética vive em {@link VoicePending},
 * {@link VoicePauseReason} e {@link VoiceRanking}, todas puras e testadas.
 *
 * <p>Enquanto a {@link VoiceGate} da guild estiver fechada, o pendente é zero: as sessões ainda
 * carregam a watermark de antes do último restart, e somá-la lançaria o período offline inteiro.
 */
public final class VoiceLive {

    /** {@code pauseReason} é {@code null} quando o tempo está subindo. */
    public record Snapshot(long savedMs, long pendingMs, String pauseReason, boolean inCall) {
        public long totalMs() {
            return savedMs + pendingMs;
        }
    }

    private VoiceLive() {}

    /** Pendente de cada membro em call agora. Vazio se a guild ainda não foi reconciliada. */
    public static Map<String, Long> pendingByUser(Guild guild, GuildConfig cfg, VoiceGate gate,
                                                  VoiceSessionRepository sessions, long now, long weekStart) {
        Map<String, Long> out = new HashMap<>();
        if (!gate.isReconciled(guild.getId())) {
            return out;
        }
        for (VoiceSessionRepository.Open s : sessions.openSessions(guild.getId())) {
            Member member = guild.getMemberById(s.userId());
            AudioChannel channel = guild.getChannelById(AudioChannel.class, s.channelId());
            if (member == null || channel == null) {
                continue;
            }
            VoiceStateSnapshot snap = VoiceSnapshots.of(member, channel, cfg, 0);
            long pending = VoicePending.pendingMs(s.timeCreditedUntil(), now, weekStart,
                    VoiceEligibility.timeEligible(snap));
            if (pending > 0) {
                out.put(s.userId(), pending);
            }
        }
        return out;
    }

    /** Salvo + pendente + motivo da pausa de um único membro. */
    public static Snapshot forUser(Guild guild, String userId, GuildConfig cfg, VoiceGate gate,
                                   VoiceSessionRepository sessions, VoiceTimeRepository times,
                                   long now, long weekStart) {
        long saved = times.msOf(guild.getId(), userId, weekStart);
        VoiceSessionRepository.Open open = sessions.openSession(guild.getId(), userId);
        if (open == null) {
            return new Snapshot(saved, 0, null, false);
        }
        Member member = guild.getMemberById(userId);
        AudioChannel channel = guild.getChannelById(AudioChannel.class, open.channelId());
        if (member == null || channel == null) {
            return new Snapshot(saved, 0, null, false);
        }
        if (!gate.isReconciled(guild.getId())) {
            return new Snapshot(saved, 0, "sincronizando", true);
        }
        VoiceStateSnapshot snap = VoiceSnapshots.of(member, channel, cfg, 0);
        String reason = VoicePauseReason.of(snap);
        long pending = VoicePending.pendingMs(open.timeCreditedUntil(), now, weekStart,
                VoiceEligibility.timeEligible(snap));
        return new Snapshot(saved, pending, reason, true);
    }

    /** Ranking da semana, já com o pendente somado, sem zeros e ordenado. */
    public static List<VoiceTimeRepository.Entry> ranking(Guild guild, GuildConfig cfg, VoiceGate gate,
                                                          VoiceSessionRepository sessions,
                                                          VoiceTimeRepository times,
                                                          long now, long weekStart) {
        return VoiceRanking.merge(times.allOfWeek(guild.getId(), weekStart),
                pendingByUser(guild, cfg, gate, sessions, now, weekStart));
    }
}
```

- [ ] **Step 6: Compile**

Run: `./gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeRepository.java src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceLive.java src/test/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeRepositoryTest.java
git commit -m "feat(voice): VoiceLive junta o salvo com o pendente de quem esta em call"
```

---

### Task 7: comandos ao vivo

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/base/commands/TempoCallCommand.java` (reescrever)
- Modify: `src/main/java/dev/davimf/basebot/modules/base/commands/TopCallCommand.java` (reescrever)
- Modify: `src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeComponentHandler.java` (reescrever)
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java` (registro dos 3)

**Interfaces:**
- Consumes: `VoiceLive.forUser/ranking` (T6), `VoiceFormat.precise` (T1), `TopCallView.panel(int accent, List<VoiceTimeRepository.Entry> entries, int page, int total)` e `TopCallView.PAGE`/`NS` (já existem).
- Produces: `TopCallCommand(VoiceGate)`, `TempoCallCommand(VoiceGate)`, `VoiceTimeComponentHandler(VoiceGate)`.

- [ ] **Step 1: Rewrite `TempoCallCommand`**

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.leveling.VoiceFormat;
import dev.davimf.basebot.modules.base.leveling.VoiceGate;
import dev.davimf.basebot.modules.base.leveling.VoiceLive;
import dev.davimf.basebot.modules.base.leveling.VoiceSessionRepository;
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

/** /tempocall [membro] — tempo em call desta semana, incluindo a sessão em curso. */
public final class TempoCallCommand implements SlashCommand {

    private final VoiceGate gate;

    public TempoCallCommand(VoiceGate gate) { this.gate = gate; }

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
        long now = System.currentTimeMillis();
        long week = VoiceWeek.weekStart(now);
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);

        VoiceLive.Snapshot live = VoiceLive.forUser(event.getGuild(), target.getId(), cfg, gate,
                new VoiceSessionRepository(ctx.database().sqlite()),
                new VoiceTimeRepository(ctx.database().sqlite()), now, week);

        StringBuilder body = new StringBuilder("## " + Emojis.of(Emojis.CLOCK, "🕒") + " Tempo em call\n")
                .append("> ").append(target.getAsMention()).append("\n")
                .append("**Salvo** · `").append(VoiceFormat.precise(live.savedMs())).append("`\n");
        if (live.inCall()) {
            String andamento = live.pauseReason() != null
                    ? "pausado (" + live.pauseReason() + ")"
                    : VoiceFormat.precise(live.pendingMs());
            body.append("**Em andamento** · `").append(andamento).append("`\n")
                    .append("**Total** · `").append(VoiceFormat.precise(live.totalMs())).append("`\n");
        }
        body.append("-# Zera toda segunda 00:00.");

        event.replyComponents(Panels.container(EmbedColor.resolve(cfg), Panels.text(body.toString())))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
```

- [ ] **Step 2: Rewrite `TopCallCommand`**

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.leveling.TopCallView;
import dev.davimf.basebot.modules.base.leveling.VoiceGate;
import dev.davimf.basebot.modules.base.leveling.VoiceLive;
import dev.davimf.basebot.modules.base.leveling.VoiceSessionRepository;
import dev.davimf.basebot.modules.base.leveling.VoiceTimeRepository;
import dev.davimf.basebot.modules.base.leveling.VoiceWeek;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/** /topcall — ranking semanal de tempo em call, já com a sessão em curso somada. */
public final class TopCallCommand implements SlashCommand {

    private final VoiceGate gate;

    public TopCallCommand(VoiceGate gate) { this.gate = gate; }

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
        long now = System.currentTimeMillis();
        long week = VoiceWeek.weekStart(now);
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);

        List<VoiceTimeRepository.Entry> all = VoiceLive.ranking(event.getGuild(), cfg, gate,
                new VoiceSessionRepository(ctx.database().sqlite()),
                new VoiceTimeRepository(ctx.database().sqlite()), now, week);
        List<VoiceTimeRepository.Entry> page = all.subList(0, Math.min(TopCallView.PAGE, all.size()));

        event.replyComponents(TopCallView.panel(EmbedColor.resolve(cfg), page, 0, all.size()))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
```

- [ ] **Step 3: Rewrite `VoiceTimeComponentHandler`**

```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.util.List;

/** Paginação do /topcall (namespace "vtime"). Recalcula o ranking ao vivo a cada clique. */
public final class VoiceTimeComponentHandler implements ComponentHandler {

    private final VoiceGate gate;

    public VoiceTimeComponentHandler(VoiceGate gate) { this.gate = gate; }

    @Override
    public String namespace() { return TopCallView.NS; }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"top".equals(id.action()) || event.getGuild() == null) {
            return;
        }
        String guildId = event.getGuild().getId();
        long now = System.currentTimeMillis();
        long week = VoiceWeek.weekStart(now);
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);

        List<VoiceTimeRepository.Entry> all = VoiceLive.ranking(event.getGuild(), cfg, gate,
                new VoiceSessionRepository(ctx.database().sqlite()),
                new VoiceTimeRepository(ctx.database().sqlite()), now, week);

        int pages = Math.max(1, (all.size() + TopCallView.PAGE - 1) / TopCallView.PAGE);
        int page = Math.min(Math.max(0, parse(id.arg(0))), pages - 1);
        int from = Math.min(page * TopCallView.PAGE, all.size());
        int to = Math.min(from + TopCallView.PAGE, all.size());

        event.editComponents(TopCallView.panel(EmbedColor.resolve(cfg),
                        all.subList(from, to), page, all.size()))
                .useComponentsV2().queue();
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }
}
```

Note que o `page` agora é limitado por cima (`Math.min(..., pages - 1)`), fechando o buraco em que um id de botão forjado renderizava "Página 1000/1".

- [ ] **Step 4: Wire in `BaseModule`**

Trocar as três linhas de registro (perto de `registry.command(new ...TopCommand(leveling))`):

```java
        registry.command(new dev.davimf.basebot.modules.base.commands.TopCallCommand(voiceGate));
        registry.command(new dev.davimf.basebot.modules.base.commands.TempoCallCommand(voiceGate));
        registry.component(new dev.davimf.basebot.modules.base.leveling.VoiceTimeComponentHandler(voiceGate));
```

O campo `voiceGate` já existe e já é passado aos listeners e ao ticker. `BaseModule.java` tem trabalho não commitado alheio (`PurgeLogSuppressor`): **fazer staging só dos seus hunks** (monte um patch e use `git apply --cached`), conferindo com `git diff --cached` antes de commitar.

- [ ] **Step 5: Build**

Run: `./gradlew.bat compileJava` then `./gradlew.bat build -x shadowJar`
Expected: BUILD SUCCESSFUL, suíte inteira verde. (Baseline era 420; a Task 1 reescreve
`VoiceFormatTest` e as tasks 2, 3, 5 e 6 acrescentam testes, então o total sobe — o que não pode
haver é falha.)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/commands/TempoCallCommand.java src/main/java/dev/davimf/basebot/modules/base/commands/TopCallCommand.java src/main/java/dev/davimf/basebot/modules/base/leveling/VoiceTimeComponentHandler.java
# BaseModule: staging por hunk, conforme o Step 4
git commit -m "feat(voice): /tempocall e /topcall ao vivo, com segundos e motivo da pausa"
```

- [ ] **Step 7: Verificação manual (do usuário, não do implementador)**

Não executar. Apenas registrar no relatório que segue pendente:

1. Entrar num canal de voz e rodar `/tempocall`: deve mostrar **Salvo**, **Em andamento** (poucos segundos) e **Total**.
2. Fechar o microfone e rodar de novo: **Em andamento** deve dizer `pausado (microfone fechado)` e o **Total** parar de crescer.
3. Entrar num canal privado onde o cargo membro pode ver e entrar: o tempo deve contar.
4. `/topcall` deve listar quem entrou há menos de um minuto.

---

## Cobertura do design

| Requisito | Task |
|---|---|
| `/tempocall` mostra salvo, em andamento e total | 7 |
| Motivo da pausa (mic, deafen, AFK, fora do escopo) | 3, 7 |
| Precisão de segundos nos dois comandos | 1 |
| Pendente recortado na virada da semana | 2 |
| Pendente é zero enquanto a guild não reconciliou | 6 |
| `/topcall` ao vivo, com ordenação correta | 5, 6, 7 |
| Uma só computação alimenta total e página (conserta o Minor de `count`/`topPage`) | 5, 7 |
| Página limitada por cima (conserta o Minor do id forjado) | 7 |
| Escopo padrão aceita o cargo membro | 4 |
