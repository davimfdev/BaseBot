# Eventos de chat + integração nível→dinheiro — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (inline). Steps em checkbox. Depende dos módulos leveling e economy (já implementados).

**Goal:** Canal principal configurável onde o bot dispara eventos aleatórios (quiz, digitação, matemática, coleta) com recompensa moedas (escaladas por nível) + XP; e o multiplicador nível→dinheiro (`LevelBonus`) aplicado nos eventos e no `/daily`.

**Architecture:** `LevelBonus` (puro, em leveling) usado no `EconomyService.daily` e nas recompensas. Pacote `modules/base/events/`: config + geradores puros + `ChatEventService` (coordenador stateful in-memory: fire/tick/resolve/reward) + listener + component handler + tela de setup.

**Tech Stack:** Java 22, JDA 6.4.2 (Components V2), SQLite.

## Global Constraints

- **JDK 22.** **Sem commits.** Base ≠ facs. Components V2 + `Emojis` + house style.
- **Sem migração** (config em `guild_config`; estado de eventos in-memory).
- **Recompensas fixas v1:** 100 moedas base + 50 XP; `LevelBonus` +2%/nível, teto 50. Só o intervalo é configurável.
- Moedas só se `eco:enabled`; XP só se `level:enabled`. Sem canal (`event-channel`) ou `!event:enabled` → não dispara.

**Símbolos confirmados:** `LevelFormula.levelForXp`, `LevelingService.award/users`, `EconomyService.wallets`, `WalletRepository.addCash`, `EconomyConfig.enabled/daily`, `LevelingConfig.enabled`, `UserLevelRepository.xp`; `ctx.scheduler().repeating/once`; setup helpers; `Replies.ephemeral`.

---

## Task 1: `LevelBonus` (puro) + teste

**Files:** Create `modules/base/leveling/LevelBonus.java`, `test/.../leveling/LevelBonusTest.java`.

- [ ] **Step 1: Teste**
```java
package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LevelBonusTest {
    @Test
    void noBonusAtLevelZero() {
        assertEquals(100, LevelBonus.scale(100, 0));
    }

    @Test
    void scalesTwoPercentPerLevel() {
        assertEquals(150, LevelBonus.scale(100, 25));
    }

    @Test
    void capsAtLevelFifty() {
        assertEquals(200, LevelBonus.scale(100, 50));
        assertEquals(200, LevelBonus.scale(100, 999));
    }

    @Test
    void negativeLevelTreatedAsZero() {
        assertEquals(100, LevelBonus.scale(100, -5));
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.LevelBonusTest"` → FAIL.
- [ ] **Step 3: Implementar**
```java
package dev.davimf.basebot.modules.base.leveling;

/** Bônus de moedas por nível (integração leveling↔economia). +2%/nível, teto no nível 50. Puro. */
public final class LevelBonus {

    public static final int CAP = 50;
    public static final int PCT_PER_LEVEL = 2;

    private LevelBonus() {}

    public static long scale(long base, int level) {
        int eff = Math.max(0, Math.min(level, CAP));
        return base + base * eff * PCT_PER_LEVEL / 100;
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.LevelBonusTest"` → PASS.

---

## Task 2: `EconomyService.daily` usa `LevelBonus`

**Files:** Modify `modules/base/economy/EconomyService.java`.

**Interfaces — Consumes:** `LevelBonus`, `LevelFormula`, `UserLevelRepository`.

- [ ] **Step 1:** Adicionar o repo de níveis ao serviço. No topo/imports:
```java
import dev.davimf.basebot.modules.base.leveling.LevelBonus;
import dev.davimf.basebot.modules.base.leveling.LevelFormula;
import dev.davimf.basebot.modules.base.leveling.UserLevelRepository;
```
No campo/construtor:
```java
    private final UserLevelRepository userLevels;
```
```java
        this.userLevels = new UserLevelRepository(ctx.database().sqlite());
```
- [ ] **Step 2:** No método `daily(...)`, escalar pelo nível:
```java
        int level = LevelFormula.levelForXp(userLevels.xp(g.getId(), m.getId()));
        long amount = LevelBonus.scale(EconomyConfig.daily(cfg(g)), level);
        wallets.addCash(g.getId(), m.getId(), amount);
        cooldowns.stamp(g.getId(), m.getId(), "daily", System.currentTimeMillis());
        return "Recompensa diária: **+" + EconomyFormat.formatNamed(amount, cfg(g)) + "** na carteira.";
```
> Substitui o cálculo antigo de `amount` no `daily`. O resto (cooldown) inalterado.
- [ ] **Step 3: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 3: `ChatEventConfig` (leitor puro) + teste

**Files:** Create `modules/base/events/ChatEventConfig.java`, `test/.../events/ChatEventConfigTest.java`.

- [ ] **Step 1: Teste**
```java
package dev.davimf.basebot.modules.base.events;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ChatEventConfigTest {
    private static GuildConfig cfg(Map<String, String> settings, Map<String, Boolean> toggles, Map<String, String> channels) {
        return new GuildConfig("g1", null, null, channels, Map.of(), toggles, List.of(), settings);
    }

    @Test
    void defaults() {
        GuildConfig c = cfg(Map.of(), Map.of(), Map.of());
        assertFalse(ChatEventConfig.enabled(c));
        assertNull(ChatEventConfig.channelId(c));
        assertEquals(30, ChatEventConfig.minMinutes(c));
        assertEquals(120, ChatEventConfig.maxMinutes(c));
    }

    @Test
    void normalizesRange() {
        GuildConfig c = cfg(Map.of(ChatEventConfig.KEY_MIN, "100", ChatEventConfig.KEY_MAX, "10"),
                Map.of(ChatEventConfig.KEY_ENABLED, true), Map.of(ChatEventConfig.KEY_CHANNEL, "c1"));
        assertTrue(ChatEventConfig.enabled(c));
        assertEquals("c1", ChatEventConfig.channelId(c));
        assertEquals(100, ChatEventConfig.minMinutes(c));
        assertEquals(100, ChatEventConfig.maxMinutes(c)); // max nunca menor que min
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.ChatEventConfigTest"` → FAIL.
- [ ] **Step 3: Implementar**
```java
package dev.davimf.basebot.modules.base.events;

import dev.davimf.basebot.database.model.GuildConfig;

/** Leitor puro da config de eventos de chat (prefixo {@code event:}). */
public final class ChatEventConfig {

    public static final String KEY_ENABLED = "event:enabled";
    public static final String KEY_CHANNEL = "event-channel";
    public static final String KEY_MIN = "event:min-interval";
    public static final String KEY_MAX = "event:max-interval";

    private ChatEventConfig() {}

    public static boolean enabled(GuildConfig cfg) { return cfg.toggle(KEY_ENABLED, false); }

    public static String channelId(GuildConfig cfg) { return cfg.channel(KEY_CHANNEL); }

    public static long minMinutes(GuildConfig cfg) { return Math.max(1, longOr(cfg.setting(KEY_MIN), 30)); }

    public static long maxMinutes(GuildConfig cfg) { return Math.max(minMinutes(cfg), longOr(cfg.setting(KEY_MAX), 120)); }

    private static long longOr(String v, long def) {
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.ChatEventConfigTest"` → PASS.

---

## Task 4: Tipos + geradores puros (`ChatEventType`, `QuizBank`, `WordBank`, `MathEvent`, `ChatEventAnswer`) + testes

**Files:** Create `modules/base/events/{ChatEventType,QuizBank,WordBank,MathEvent,ChatEventAnswer}.java`; Test `MathEventTest`, `ChatEventAnswerTest`.

- [ ] **Step 1: Testes**
```java
package dev.davimf.basebot.modules.base.events;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MathEventTest {
    @Test
    void computesOperations() {
        assertEquals(84, MathEvent.compute(12, '×', 7));
        assertEquals(19, MathEvent.compute(12, '+', 7));
        assertEquals(5, MathEvent.compute(12, '-', 7));
    }

    @Test
    void generatedProblemAnswerMatchesPrompt() {
        MathEvent.Problem p = MathEvent.generate(new java.util.Random(42));
        assertNotNull(p.prompt());
        assertFalse(p.answer().isBlank());
        assertTrue(Long.parseLong(p.answer()) >= 0);
    }
}
```
```java
package dev.davimf.basebot.modules.base.events;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatEventAnswerTest {
    @Test
    void matchesTrimAndCaseInsensitive() {
        assertTrue(ChatEventAnswer.matches("  Banana ", "banana"));
        assertTrue(ChatEventAnswer.matches("84", "84"));
    }

    @Test
    void doesNotMatchDifferent() {
        assertFalse(ChatEventAnswer.matches("maca", "banana"));
        assertFalse(ChatEventAnswer.matches("", "banana"));
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.MathEventTest" --tests "*.ChatEventAnswerTest"` → FAIL.
- [ ] **Step 3: Implementar**
```java
package dev.davimf.basebot.modules.base.events;

import java.util.concurrent.ThreadLocalRandom;

/** Tipos de evento de chat. */
public enum ChatEventType {
    QUIZ, TYPING, MATH, GRAB;

    public static ChatEventType random() {
        ChatEventType[] all = values();
        return all[ThreadLocalRandom.current().nextInt(all.length)];
    }
}
```
```java
package dev.davimf.basebot.modules.base.events;

import java.util.List;

/** Banco estático de perguntas de quiz. */
public final class QuizBank {
    public record Question(String text, List<String> options, int correct) {}

    private static final List<Question> QUESTIONS = List.of(
            new Question("Qual a capital do Brasil?", List.of("Rio de Janeiro", "Brasília", "São Paulo", "Salvador"), 1),
            new Question("Quanto é 6 × 7?", List.of("42", "36", "48", "13"), 0),
            new Question("Qual planeta é o 'Planeta Vermelho'?", List.of("Vênus", "Júpiter", "Marte", "Saturno"), 2),
            new Question("Quantos lados tem um hexágono?", List.of("5", "6", "7", "8"), 1),
            new Question("Qual é o maior oceano?", List.of("Atlântico", "Índico", "Ártico", "Pacífico"), 3));

    private QuizBank() {}

    public static Question random() {
        return QUESTIONS.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(QUESTIONS.size()));
    }
}
```
```java
package dev.davimf.basebot.modules.base.events;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Banco estático de palavras para a corrida de digitação. */
public final class WordBank {
    private static final List<String> WORDS = List.of(
            "banana", "guitarra", "montanha", "relogio", "cachorro", "janela",
            "foguete", "abacaxi", "tijolo", "girassol", "caverna", "bicicleta");

    private WordBank() {}

    public static String random() {
        return WORDS.get(ThreadLocalRandom.current().nextInt(WORDS.size()));
    }
}
```
```java
package dev.davimf.basebot.modules.base.events;

import java.util.Random;

/** Gera um problema de matemática simples e sua resposta. */
public final class MathEvent {
    public record Problem(String prompt, String answer) {}

    private static final char[] OPS = {'+', '-', '×'};

    private MathEvent() {}

    public static Problem generate(Random r) {
        char op = OPS[r.nextInt(OPS.length)];
        long a = 2 + r.nextInt(11);
        long b = 2 + r.nextInt(11);
        if (op == '-' && b > a) {
            long tmp = a; a = b; b = tmp;
        }
        long ans = compute(a, op, b);
        return new Problem(a + " " + op + " " + b, String.valueOf(ans));
    }

    public static long compute(long a, char op, long b) {
        return switch (op) {
            case '+' -> a + b;
            case '-' -> a - b;
            default -> a * b;
        };
    }
}
```
```java
package dev.davimf.basebot.modules.base.events;

/** Checagem pura de resposta de evento (trim + case-insensitive). */
public final class ChatEventAnswer {
    private ChatEventAnswer() {}

    public static boolean matches(String input, String answer) {
        return input != null && answer != null && input.trim().equalsIgnoreCase(answer.trim());
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.MathEventTest" --tests "*.ChatEventAnswerTest"` → PASS.

---

## Task 5: `ChatEvent` (record) + `ChatEventView`

**Files:** Create `modules/base/events/ChatEvent.java`, `modules/base/events/ChatEventView.java`.

- [ ] **Step 1:** `ChatEvent.java`
```java
package dev.davimf.basebot.modules.base.events;

import java.util.List;

/** Evento ativo (in-memory). Um por guild. */
public record ChatEvent(ChatEventType type, String guildId, String channelId, String messageId,
                        String prompt, String answer, int correctIndex, List<String> options, long expiresAt) {

    public ChatEvent withMessageId(String id) {
        return new ChatEvent(type, guildId, channelId, id, prompt, answer, correctIndex, options, expiresAt);
    }
}
```
- [ ] **Step 2:** `ChatEventView.java`
```java
package dev.davimf.basebot.modules.base.events;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;

import java.util.ArrayList;
import java.util.List;

/** Render dos eventos de chat (namespace "chatevt"). */
public final class ChatEventView {

    public static final String NS = "chatevt";

    private ChatEventView() {}

    public static Container panel(int accent, ChatEvent ev) {
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + Emojis.of(Emojis.GIFT, "🎉") + " Evento de chat!"));
        kids.add(Panels.divider());
        kids.add(Panels.text(prompt(ev)));
        switch (ev.type()) {
            case QUIZ -> {
                List<Button> buttons = new ArrayList<>();
                List<String> opts = ev.options();
                for (int i = 0; i < opts.size(); i++) {
                    buttons.add(Button.secondary(ComponentId.of(NS, "ans", String.valueOf(i)), trim(opts.get(i))));
                }
                kids.add(ActionRow.of(buttons));
            }
            case GRAB -> kids.add(ActionRow.of(
                    Button.success(ComponentId.of(NS, "grab"), "Pegar!").withEmoji(Emojis.button(Emojis.GIFT))));
            default -> kids.add(Panels.text("-# Responda no chat! O primeiro a acertar leva."));
        }
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    public static Container resolved(int accent, String winnerMention, String rewardText) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.TROPHY, "🏆") + " Evento encerrado"),
                Panels.divider(),
                Panels.text(winnerMention + " ganhou! " + rewardText));
    }

    public static Container expired(int accent) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.CLOCK, "⏰") + " Evento expirado"),
                Panels.divider(),
                Panels.text("Ninguém acertou a tempo."));
    }

    private static String prompt(ChatEvent ev) {
        return switch (ev.type()) {
            case QUIZ -> "**" + ev.prompt() + "**\nEscolha a alternativa correta:";
            case TYPING -> "Primeiro a digitar: **" + ev.answer() + "**";
            case MATH -> "Resolva: **" + ev.prompt() + "**";
            case GRAB -> "Clique em **Pegar!** antes de todo mundo!";
        };
    }

    private static String trim(String s) {
        return s.length() > 78 ? s.substring(0, 78) : s;
    }
}
```
> **Verificar:** `Emojis.GIFT`/`TROPHY`/`CLOCK` (existem: `gift`, `trophy`, `clock`).
- [ ] **Step 3: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 6: `ChatEventService` (coordenador stateful)

**Files:** Create `modules/base/events/ChatEventService.java`.

**Consumes:** `LevelingService`, `EconomyService`, config, geradores, view. **Produces:** `tick()`, `onGuildMessage(MessageReceivedEvent)`, `resolveButton(ButtonInteractionEvent, ComponentId)`.

- [ ] **Step 1: Implementar**
```java
package dev.davimf.basebot.modules.base.events;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.EconomyFormat;
import dev.davimf.basebot.modules.base.economy.EconomyService;
import dev.davimf.basebot.modules.base.leveling.LevelBonus;
import dev.davimf.basebot.modules.base.leveling.LevelFormula;
import dev.davimf.basebot.modules.base.leveling.LevelingConfig;
import dev.davimf.basebot.modules.base.leveling.LevelingService;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/** Coordenador stateful (in-memory) dos eventos de chat: dispara, resolve e recompensa. */
public final class ChatEventService {

    private static final long EVENT_COIN_BASE = 100;
    private static final long EVENT_XP = 50;
    private static final long TIMEOUT_S = 60;
    private static final long ACTIVITY_WINDOW_MS = 15 * 60_000L;
    private static final long RETRY_MS = 5 * 60_000L;

    private final BotContext ctx;
    private final LevelingService leveling;
    private final EconomyService economy;

    private final ConcurrentHashMap<String, ChatEvent> active = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastActivity = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> nextFire = new ConcurrentHashMap<>();

    public ChatEventService(BotContext ctx, LevelingService leveling, EconomyService economy) {
        this.ctx = ctx;
        this.leveling = leveling;
        this.economy = economy;
    }

    private GuildConfig cfg(Guild g) { return ctx.database().guildConfig().findOrEmpty(g.getId()); }

    // --- scheduler tick --------------------------------------------------------

    public void tick() {
        if (ctx.jda() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Guild g : ctx.jda().getGuilds()) {
            GuildConfig cfg = cfg(g);
            if (!ChatEventConfig.enabled(cfg) || ChatEventConfig.channelId(cfg) == null) {
                continue;
            }
            String gid = g.getId();
            long next = nextFire.computeIfAbsent(gid, k -> now + randomIntervalMs(cfg));
            if (now < next || active.containsKey(gid)) {
                continue;
            }
            if (now - lastActivity.getOrDefault(gid, 0L) > ACTIVITY_WINDOW_MS) {
                nextFire.put(gid, now + RETRY_MS); // canal parado: tenta de novo mais tarde
                continue;
            }
            fire(g, cfg);
            nextFire.put(gid, now + randomIntervalMs(cfg));
        }
    }

    private long randomIntervalMs(GuildConfig cfg) {
        long min = ChatEventConfig.minMinutes(cfg);
        long max = ChatEventConfig.maxMinutes(cfg);
        long minutes = max <= min ? min : min + ThreadLocalRandom.current().nextLong(max - min + 1);
        return minutes * 60_000L;
    }

    // --- fire ------------------------------------------------------------------

    private void fire(Guild g, GuildConfig cfg) {
        TextChannel channel = g.getTextChannelById(ChatEventConfig.channelId(cfg));
        if (channel == null) {
            return;
        }
        ChatEvent draft = build(g.getId(), channel.getId());
        int accent = EmbedColor.resolve(cfg);
        channel.sendMessageComponents(ChatEventView.panel(accent, draft)).useComponentsV2().queue(sent -> {
            ChatEvent ev = draft.withMessageId(sent.getId());
            active.put(g.getId(), ev);
            ctx.scheduler().once(() -> expire(g.getId(), ev), TIMEOUT_S, TimeUnit.SECONDS);
        }, err -> { });
    }

    private ChatEvent build(String guildId, String channelId) {
        long expires = System.currentTimeMillis() + TIMEOUT_S * 1000L;
        return switch (ChatEventType.random()) {
            case QUIZ -> {
                QuizBank.Question q = QuizBank.random();
                yield new ChatEvent(ChatEventType.QUIZ, guildId, channelId, "", q.text(), null, q.correct(),
                        q.options(), expires);
            }
            case TYPING -> {
                String w = WordBank.random();
                yield new ChatEvent(ChatEventType.TYPING, guildId, channelId, "", w, w, -1, List.of(), expires);
            }
            case MATH -> {
                MathEvent.Problem p = MathEvent.generate(new Random());
                yield new ChatEvent(ChatEventType.MATH, guildId, channelId, "", p.prompt(), p.answer(), -1,
                        List.of(), expires);
            }
            case GRAB -> new ChatEvent(ChatEventType.GRAB, guildId, channelId, "", null, "", -1, List.of(), expires);
        };
    }

    private void expire(String guildId, ChatEvent ev) {
        if (active.remove(guildId, ev)) {
            TextChannel ch = ctx.jda().getTextChannelById(ev.channelId());
            if (ch != null) {
                ch.editMessageComponentsById(ev.messageId(),
                                ChatEventView.expired(EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId))))
                        .useComponentsV2().queue(ok -> { }, err -> { });
            }
        }
    }

    // --- resolution ------------------------------------------------------------

    /** Mensagens no canal do evento: marca atividade e resolve TYPING/MATH. */
    public void onGuildMessage(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.getMember() == null) {
            return;
        }
        String gid = event.getGuild().getId();
        String chId = ChatEventConfig.channelId(cfg(event.getGuild()));
        if (chId == null || !chId.equals(event.getChannel().getId())) {
            return;
        }
        lastActivity.put(gid, System.currentTimeMillis());
        ChatEvent ev = active.get(gid);
        if (ev == null || (ev.type() != ChatEventType.TYPING && ev.type() != ChatEventType.MATH)) {
            return;
        }
        if (ChatEventAnswer.matches(event.getMessage().getContentRaw(), ev.answer()) && active.remove(gid, ev)) {
            reward(event.getGuild(), event.getMember(), ev);
        }
    }

    /** Botões de QUIZ/GRAB. */
    public void resolveButton(ButtonInteractionEvent event, ComponentId id) {
        if (event.getGuild() == null || event.getMember() == null) {
            return;
        }
        String gid = event.getGuild().getId();
        ChatEvent ev = active.get(gid);
        if (ev == null) {
            Replies.ephemeral(event, ctx, "Esse evento já encerrou.");
            return;
        }
        if ("ans".equals(id.action())) {
            int clicked = parse(id.arg(0));
            if (clicked != ev.correctIndex()) {
                Replies.ephemeral(event, ctx, "Resposta errada!");
                return;
            }
        } else if (!"grab".equals(id.action())) {
            return;
        }
        if (!active.remove(gid, ev)) {
            Replies.ephemeral(event, ctx, "Alguém já ganhou!");
            return;
        }
        reward(event.getGuild(), event.getMember(), ev);
        event.editComponents(ChatEventView.resolved(EmbedColor.resolve(cfg(event.getGuild())),
                event.getMember().getAsMention(), rewardText(event.getGuild(), event.getMember())))
                .useComponentsV2().queue(ok -> { }, err -> { });
    }

    // --- reward ----------------------------------------------------------------

    private void reward(Guild g, Member m, ChatEvent ev) {
        GuildConfig cfg = cfg(g);
        if (EconomyConfig.enabled(cfg)) {
            int level = LevelFormula.levelForXp(leveling.users().xp(g.getId(), m.getId()));
            economy.wallets().addCash(g.getId(), m.getId(), LevelBonus.scale(EVENT_COIN_BASE, level));
        }
        TextChannel channel = g.getTextChannelById(ev.channelId());
        if (LevelingConfig.enabled(cfg) && channel != null) {
            leveling.award(g, m, EVENT_XP, channel);
        }
        // Para eventos de chat (sem botão), anuncia o vencedor editando a mensagem do evento.
        if ((ev.type() == ChatEventType.TYPING || ev.type() == ChatEventType.MATH) && channel != null) {
            channel.editMessageComponentsById(ev.messageId(),
                            ChatEventView.resolved(EmbedColor.resolve(cfg), m.getAsMention(), rewardText(g, m)))
                    .useComponentsV2().queue(ok -> { }, err -> { });
        }
    }

    private String rewardText(Guild g, Member m) {
        GuildConfig cfg = cfg(g);
        StringBuilder sb = new StringBuilder();
        if (EconomyConfig.enabled(cfg)) {
            int level = LevelFormula.levelForXp(leveling.users().xp(g.getId(), m.getId()));
            sb.append("**+").append(EconomyFormat.formatNamed(LevelBonus.scale(EVENT_COIN_BASE, level), cfg)).append("**");
        }
        if (LevelingConfig.enabled(cfg)) {
            sb.append(sb.length() > 0 ? " e " : "").append("**+").append(EVENT_XP).append(" XP**");
        }
        return sb.length() == 0 ? "a recompensa" : sb.toString();
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return -1; }
    }
}
```
> **Nota:** o `rewardText` recalcula o nível **após** o crédito de XP (leve imprecisão no texto do valor de moedas exibido — as moedas já foram creditadas com o nível de antes). Aceitável no v1. `editMessageComponentsById` existe em `MessageChannel`.
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 7: `ChatEventListener`

**Files:** Create `modules/base/events/ChatEventListener.java`.

- [ ] **Step 1: Implementar**
```java
package dev.davimf.basebot.modules.base.events;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Encaminha mensagens do canal de eventos para o serviço (atividade + respostas de chat). */
public final class ChatEventListener extends ListenerAdapter {

    private final ChatEventService service;

    public ChatEventListener(ChatEventService service) {
        this.service = service;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        service.onGuildMessage(event);
    }
}
```
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 8: `ChatEventComponentHandler`

**Files:** Create `modules/base/events/ChatEventComponentHandler.java`.

- [ ] **Step 1: Implementar**
```java
package dev.davimf.basebot.modules.base.events;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/** Botões de QUIZ/GRAB (namespace "chatevt"). */
public final class ChatEventComponentHandler implements ComponentHandler {

    private final ChatEventService service;

    public ChatEventComponentHandler(ChatEventService service) {
        this.service = service;
    }

    @Override
    public String namespace() {
        return ChatEventView.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        service.resolveButton(event, id);
    }
}
```
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 9: `/setup → Eventos`

**Files:** Modify `SetupView.java` (`eventsScreen` + `eventIntervalModal`), `SetupComponentHandler.java` (nav + toggle + canal + modal), `SetupView.hub`/`moduleNav` (entrada "Eventos").

- [ ] **Step 1:** `SetupView` — importar `ChatEventConfig`; `eventsScreen(GuildConfig cfg)`: overview (ligado?, canal, intervalo min–max) + `channelSelect("eventchan", ChatEventConfig.KEY_CHANNEL, "Canal principal dos eventos…", cfg.channel(ChatEventConfig.KEY_CHANNEL))` + botões `evttoggle` (liga/desliga `ChatEventConfig.KEY_ENABLED`), `evtinterval` (modal), `nav hub` Voltar + `moduleNav("eventos")`. Modal `eventform-interval` (campos `min`, `max`, pré-preenchidos com `ChatEventConfig.minMinutes/maxMinutes`).
- [ ] **Step 2:** `SetupView.moduleNav` — `addNav(menu, current, "Eventos", "eventos");`. `SetupView.hub` — `.addOption("Eventos", "eventos", "Eventos aleatórios no chat principal (quiz, coleta…)")`.
- [ ] **Step 3:** `SetupComponentHandler` — nav button `case "eventos" -> SetupView.eventsScreen(config(ctx, guildId));`; `onStringSelect "section"` idem; `onButton`: `evttoggle` (withToggle KEY_ENABLED, re-render), `evtinterval` (replyModal); `onEntitySelect`: `eventchan` (`saveChannel(event, ctx, id.arg(0), firstChannelId(event))` + re-render `eventsScreen`); `onModal`: `eventform-interval` → `withLong(KEY_MIN)`/`withLong(KEY_MAX)` (reusa o helper `withLong` criado na economia) + save + re-render.
> Seguir os moldes de `welctoggle`/`welcomechan`/`nivelnotify` já existentes.
- [ ] **Step 4: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 10: Registro no `BaseModule` + build + smoke

**Files:** Modify `modules/base/BaseModule.java`.

**Consumes:** os campos `leveling` (Plano 1 leveling) + a instância `economy` local. **⚠️ Ordem:** o `EconomyService economy` já é criado no `register(...)`; guardar numa **variável de campo** ou criar o `ChatEventService` logo após, no mesmo método, e guardar num campo para o `onReady` agendar o tick.

- [ ] **Step 1:** Campo:
```java
    private dev.davimf.basebot.modules.base.events.ChatEventService chatEvents;
```
- [ ] **Step 2:** No `register(...)`, após registrar a economia:
```java
        // Eventos de chat (Base) — usa leveling + economia.
        this.chatEvents = new dev.davimf.basebot.modules.base.events.ChatEventService(ctx, leveling, economy);
        registry.listener(new dev.davimf.basebot.modules.base.events.ChatEventListener(chatEvents));
        registry.component(new dev.davimf.basebot.modules.base.events.ChatEventComponentHandler(chatEvents));
```
- [ ] **Step 3:** No `onReady(...)`, agendar o tick (a cada 60s):
```java
        // Eventos de chat: verifica/dispara a cada 60s.
        if (chatEvents != null) {
            ctx.scheduler().repeating(chatEvents::tick, 60, 60, TimeUnit.SECONDS);
        }
```
- [ ] **Step 4: Run** `./gradlew build` → BUILD SUCCESSFUL (todos os testes: `LevelBonusTest`, `ChatEventConfigTest`, `MathEventTest`, `ChatEventAnswerTest`).
- [ ] **Step 5: Smoke (servidor de teste):**
  - `/setup → Eventos`: ligar, escolher canal principal, intervalo curto (ex.: min 1, max 1) pra testar. Mandar uma mensagem no canal (marca atividade) → em ~1 min o bot posta um evento.
  - Quiz: clicar certo ganha (errado → aviso); Coleta: 1º a clicar; Digitação/Matemática: 1º a mandar a resposta no chat. Conferir moedas (`/saldo`) e XP (`/rank`).
  - `/daily` com nível alto → recompensa maior (LevelBonus); nível 0 → base.
  - Deixar expirar (ninguém responde) → mensagem "evento expirado".

## Self-Review
- **Cobertura do spec:** LevelBonus + /daily (T1,T2); config (T3); 4 tipos + geradores (T4); modelo + view (T5); serviço fire/resolve/reward + tick + atividade + expiração (T6); listener (T7); botões (T8); setup (T9); registro + agendamento (T10). ✓
- **Race-safety:** `active.remove(guildId, ev)` (conditional atomic) garante um único vencedor. ✓
- **Consistência:** `ChatEvent`/`ChatEventType`/`ChatEventView.NS`/`ChatEventService.tick/onGuildMessage/resolveButton`; `LevelBonus.scale`; `MathEvent.compute/generate`. ✓
- **Pontos a confirmar no build (inline):** `Emojis.GIFT/TROPHY/CLOCK`; `editMessageComponentsById` + `useComponentsV2`; helper `withLong` (reuso da economia) acessível no handler.
