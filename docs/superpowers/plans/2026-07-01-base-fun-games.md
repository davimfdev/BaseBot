# Fun — Plano 2 (forca + quiz personalizado)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (inline). Steps em checkbox. Depende do Plano 1 (fun básico).

**Goal:** `/forca` (banco embutido PT, adivinha letras no chat) e `/quiz` personalizado (banco de perguntas por servidor, gerido no `/setup → Fun`, com fallback pro banco dos eventos). **Sem recompensa.**

**Architecture:** `HangmanState` puro (normaliza acentos) + `ForcaService` (in-memory por canal, listener de chat). Quiz: migração 032 + `QuizRepository` + `QuizPlayService` (in-memory por canal, botões) + gestão no setup.

**Tech Stack:** Java 22, JDA 6.4.2 (Components V2), SQLite.

## Global Constraints

- **JDK 22.** **Sem commits.** Base ≠ facs. Components V2 + `Emojis` + house style. **Fun = sem XP/moedas.**
- **Migração 032** anexada a `SqliteMigrator.MIGRATIONS`.
- Forca: normalização **NFD + `\p{M}` + lowercase** na letra E na palavra inteira; chute muta via `compute(...)`; edições do painel aceitam o risco de rate-limit no v1.
- Quiz: shuffle das alternativas no play (a correta não fica sempre no mesmo lugar); primeiro a acertar ganha só reconhecimento.

**Símbolos confirmados:** `Replies.ephemeral`; `Panels`; `ComponentId`; `EmbedColor.resolve`; `Emojis`; setup helpers (`moduleNav`, `addNav`, hub option, `edit`, `config`, `value`, `parseInt`); `events.QuizBank` (fallback); teste SQLite `new SqliteManager(new BotConfig.Sqlite(path))`.

---

## Task 1: `HangmanState` (puro) + teste

**Files:** Create `modules/base/fun/HangmanState.java`, `test/.../fun/HangmanStateTest.java`.

**Produces:** `record HangmanState(String word, java.util.Set<Character> tried, int wrongCount)` + `MAX_LIVES`, `guessLetter(char)`, `guessWord(String)`, `lives()`, `won()`, `lost()`, `masked()`, `start(String word)`.

- [ ] **Step 1: Teste**
```java
package dev.davimf.basebot.modules.base.fun;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HangmanStateTest {
    @Test
    void correctLetterRevealsNoLifeLost() {
        HangmanState s = HangmanState.start("Coração").guessLetter('C');
        assertTrue(s.masked().startsWith("C"));
        assertEquals(HangmanState.MAX_LIVES, s.lives());
    }

    @Test
    void accentInsensitive() {
        HangmanState s = HangmanState.start("Coração").guessLetter('c').guessLetter('a').guessLetter('o');
        // 'c' cobre C e ç; 'a' cobre a e ã; 'o' cobre o
        assertFalse(s.masked().contains("_") == false && !s.won()); // sanity
        assertTrue(s.masked().toLowerCase().replace(" ", "").contains("cora"));
    }

    @Test
    void wrongLetterLosesLife() {
        HangmanState s = HangmanState.start("gato").guessLetter('z');
        assertEquals(HangmanState.MAX_LIVES - 1, s.lives());
    }

    @Test
    void repeatedLetterNoExtraPenalty() {
        HangmanState s = HangmanState.start("gato").guessLetter('z').guessLetter('z');
        assertEquals(HangmanState.MAX_LIVES - 1, s.lives());
    }

    @Test
    void fullWordGuessWinsIgnoringAccentAndCase() {
        HangmanState s = HangmanState.start("Coração").guessWord("coracao");
        assertTrue(s.won());
    }

    @Test
    void wrongFullWordLosesLife() {
        HangmanState s = HangmanState.start("gato").guessWord("cão");
        assertEquals(HangmanState.MAX_LIVES - 1, s.lives());
        assertFalse(s.won());
    }

    @Test
    void lostAfterMaxWrong() {
        HangmanState s = HangmanState.start("gato");
        for (char c : "bcdfhjk".toCharArray()) {
            s = s.guessLetter(c);
        }
        assertTrue(s.lost());
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.HangmanStateTest"` → FAIL.
- [ ] **Step 3: Implementar**
```java
package dev.davimf.basebot.modules.base.fun;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;

/** Estado puro da forca. Comparação normaliza acentos e caixa; exibe a palavra original. */
public record HangmanState(String word, Set<Character> tried, int wrongCount) {

    public static final int MAX_LIVES = 6;

    public static HangmanState start(String word) {
        return new HangmanState(word, Set.of(), 0);
    }

    public int lives() {
        return MAX_LIVES - wrongCount;
    }

    public HangmanState guessLetter(char c) {
        char nc = normChar(c);
        if (nc == 0 || tried.contains(nc)) {
            return this;
        }
        Set<Character> next = new HashSet<>(tried);
        next.add(nc);
        boolean hit = norm(word).indexOf(nc) >= 0;
        return new HangmanState(word, next, hit ? wrongCount : wrongCount + 1);
    }

    public HangmanState guessWord(String guess) {
        if (norm(guess).equals(norm(word))) {
            Set<Character> next = new HashSet<>(tried);
            for (char ch : norm(word).toCharArray()) {
                if (Character.isLetter(ch)) {
                    next.add(ch);
                }
            }
            return new HangmanState(word, next, wrongCount);
        }
        return new HangmanState(word, tried, wrongCount + 1);
    }

    public boolean won() {
        for (char ch : norm(word).toCharArray()) {
            if (Character.isLetter(ch) && !tried.contains(ch)) {
                return false;
            }
        }
        return true;
    }

    public boolean lost() {
        return lives() <= 0;
    }

    /** Palavra mascarada: letras adivinhadas mostram o char original; o resto vira `_`. */
    public String masked() {
        StringBuilder sb = new StringBuilder();
        String nw = norm(word);
        for (int i = 0; i < word.length(); i++) {
            char original = word.charAt(i);
            char n = i < nw.length() ? nw.charAt(i) : normChar(original);
            if (!Character.isLetter(n)) {
                sb.append(original);
            } else if (tried.contains(n)) {
                sb.append(original);
            } else {
                sb.append('_');
            }
            sb.append(' ');
        }
        return sb.toString().strip();
    }

    private static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }

    private static char normChar(char c) {
        String n = norm(String.valueOf(c));
        return n.isEmpty() ? 0 : n.charAt(0);
    }
}
```
> **Nota:** `norm(word)` mantém o mesmo tamanho da palavra original para letras latinas (NFD só separa a marca, que é removida; `ç`→`c`, `ã`→`a` são 1 char cada). Assim o índice bate no `masked()`. Se houver caractere exótico que mude o tamanho, o `masked()` cai no `normChar(original)` como salvaguarda.
- [ ] **Step 4: Run** `./gradlew test --tests "*.HangmanStateTest"` → PASS.

---

## Task 2: `/forca` — banco + view + service + comando + listener

**Files:** Create `modules/base/fun/{HangmanBank,ForcaView,ForcaService,ForcaListener}.java`, `modules/base/commands/ForcaCommand.java`.

- [ ] **Step 1:** `HangmanBank.java`
```java
package dev.davimf.basebot.modules.base.fun;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Banco embutido de palavras da forca (PT). */
public final class HangmanBank {
    private static final List<String> WORDS = List.of(
            "computador", "girassol", "cachorro", "montanha", "biblioteca", "aventura",
            "coração", "fantasma", "relâmpago", "abóbora", "guitarra", "elefante",
            "chocolate", "borboleta", "cavaleiro", "tempestade");

    private HangmanBank() {}

    public static String random() {
        return WORDS.get(ThreadLocalRandom.current().nextInt(WORDS.size()));
    }
}
```
- [ ] **Step 2:** `ForcaView.java`
```java
package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.container.Container;

public final class ForcaView {
    private ForcaView() {}

    public static Container panel(int accent, HangmanState s) {
        String body = "## " + Emojis.of(Emojis.GAME, "🔤") + " Forca\n"
                + "`" + s.masked() + "`\n"
                + Emojis.of(Emojis.HEART, "❤️") + " Vidas: `" + s.lives() + "/" + HangmanState.MAX_LIVES + "`\n"
                + "-# Digite uma **letra** ou a **palavra inteira** no chat.";
        return Panels.container(accent, Panels.text(body));
    }

    public static Container ended(int accent, HangmanState s, boolean won) {
        String head = won ? Emojis.of(Emojis.CHECK_YES, "🎉") + " Acertaram!" : Emojis.of(Emojis.CHECK_NO, "💀") + " Fim de jogo!";
        return Panels.container(accent,
                Panels.text("## " + head),
                Panels.divider(),
                Panels.text("A palavra era: **" + s.word() + "**"));
    }
}
```
> **Verificar:** `Emojis.CHECK_NO` (existe: `checkno`). Se faltar, usar `SKULL`.
- [ ] **Step 3:** `ForcaService.java`
```java
package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.concurrent.ConcurrentHashMap;

/** Coordena jogos de forca (in-memory, um por canal). */
public final class ForcaService {

    private static final class Game {
        volatile String messageId;
        HangmanState state;
        Game(HangmanState state) { this.state = state; }
    }

    private final BotContext ctx;
    private final ConcurrentHashMap<String, Game> games = new ConcurrentHashMap<>();

    public ForcaService(BotContext ctx) { this.ctx = ctx; }

    public void start(SlashCommandInteractionEvent event) {
        if (!(event.getChannel() instanceof TextChannel channel)) {
            Replies.ephemeral(event, ctx, "Use num canal de texto.");
            return;
        }
        String chId = channel.getId();
        if (games.containsKey(chId)) {
            Replies.ephemeral(event, ctx, "Já tem uma forca rolando neste canal.");
            return;
        }
        Game game = new Game(HangmanState.start(HangmanBank.random()));
        games.put(chId, game);
        int accent = accent(event.getGuild() == null ? "0" : event.getGuild().getId());
        event.replyComponents(ForcaView.panel(accent, game.state)).useComponentsV2()
                .queue(hook -> hook.retrieveOriginal().queue(msg -> game.messageId = msg.getId(), err -> { }));
    }

    public void onMessage(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) {
            return;
        }
        String chId = event.getChannel().getId();
        Game game = games.get(chId);
        if (game == null) {
            return;
        }
        String content = event.getMessage().getContentRaw().trim();
        if (content.isEmpty() || !(event.getChannel() instanceof TextChannel channel)) {
            return;
        }
        boolean changed;
        synchronized (game) {
            HangmanState before = game.state;
            if (content.length() == 1 && Character.isLetter(content.charAt(0))) {
                game.state = game.state.guessLetter(content.charAt(0));
            } else if (content.chars().allMatch(Character::isLetter)) {
                game.state = game.state.guessWord(content);
            }
            changed = game.state != before;
            if (!changed) {
                return;
            }
        }
        int accent = accent(event.getGuild().getId());
        HangmanState s = game.state;
        if (s.won() || s.lost()) {
            games.remove(chId);
            if (game.messageId != null) {
                channel.editMessageComponentsById(game.messageId, ForcaView.ended(accent, s, s.won()))
                        .useComponentsV2().queue(ok -> { }, err -> { });
            }
        } else if (game.messageId != null) {
            channel.editMessageComponentsById(game.messageId, ForcaView.panel(accent, s))
                    .useComponentsV2().queue(ok -> { }, err -> { });
        }
    }

    private int accent(String guildId) {
        return EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
    }
}
```
- [ ] **Step 4:** `ForcaListener.java`
```java
package dev.davimf.basebot.modules.base.fun;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public final class ForcaListener extends ListenerAdapter {
    private final ForcaService service;
    public ForcaListener(ForcaService service) { this.service = service; }

    @Override public void onMessageReceived(MessageReceivedEvent event) {
        service.onMessage(event);
    }
}
```
- [ ] **Step 5:** `ForcaCommand.java`
```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.fun.ForcaService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /forca — inicia um jogo da forca no canal. */
public final class ForcaCommand implements SlashCommand {
    private final ForcaService service;
    public ForcaCommand(ForcaService service) { this.service = service; }

    @Override public String name() { return "forca"; }

    @Override public SlashCommandData data() {
        return Commands.slash("forca", "Inicia um jogo da forca no canal.");
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        service.start(event);
    }
}
```
- [ ] **Step 6: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 3: Migração 032 + `QuizQuestion` + `QuizRepository` + teste

**Files:** Create `resources/db/sqlite/032_quiz.sql`, `modules/base/fun/QuizQuestion.java`, `QuizRepository.java`; Modify `SqliteMigrator.java`; Test `QuizRepositoryTest`.

- [ ] **Step 1: Migração** `032_quiz.sql`
```sql
CREATE TABLE IF NOT EXISTS quiz_questions (
    id       TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL,
    question TEXT NOT NULL,
    correct  TEXT NOT NULL,
    wrong1   TEXT NOT NULL,
    wrong2   TEXT NOT NULL,
    wrong3   TEXT NOT NULL
);
```
- [ ] **Step 2:** Anexar a `SqliteMigrator.MIGRATIONS` (após `031_social.sql`): `"/db/sqlite/032_quiz.sql"`.
- [ ] **Step 3:** `QuizQuestion.java`
```java
package dev.davimf.basebot.modules.base.fun;

/** Uma pergunta de quiz personalizada (migração 032). */
public record QuizQuestion(String id, String guildId, String question,
                           String correct, String wrong1, String wrong2, String wrong3) {}
```
- [ ] **Step 4: Teste** `QuizRepositoryTest.java`
```java
package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class QuizRepositoryTest {
    private SqliteManager sqlite;
    private QuizRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new QuizRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void addListRemove() {
        String id = repo.add("g1", "2+2?", "4", "3", "5", "22");
        assertEquals(1, repo.list("g1").size());
        assertEquals("4", repo.random("g1").orElseThrow().correct());
        repo.remove(id);
        assertTrue(repo.list("g1").isEmpty());
        assertTrue(repo.random("g1").isEmpty());
    }
}
```
- [ ] **Step 5: Run** `./gradlew test --tests "*.QuizRepositoryTest"` → FAIL.
- [ ] **Step 6: Implementar** `QuizRepository.java`
```java
package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQLite store das perguntas de quiz personalizadas (migração 032). */
public final class QuizRepository {

    private final SqliteManager sqlite;

    public QuizRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    public String add(String guildId, String question, String correct, String w1, String w2, String w3) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String sql = "INSERT INTO quiz_questions (id, guild_id, question, correct, wrong1, wrong2, wrong3) "
                + "VALUES (?,?,?,?,?,?,?)";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, guildId);
            ps.setString(3, question);
            ps.setString(4, correct);
            ps.setString(5, w1);
            ps.setString(6, w2);
            ps.setString(7, w3);
            ps.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new RepositoryException("quiz add " + guildId, e);
        }
    }

    public void remove(String id) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM quiz_questions WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("quiz remove " + id, e);
        }
    }

    public List<QuizQuestion> list(String guildId) {
        List<QuizQuestion> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM quiz_questions WHERE guild_id=? ORDER BY id")) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("quiz list " + guildId, e);
        }
    }

    public Optional<QuizQuestion> random(String guildId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM quiz_questions WHERE guild_id=? ORDER BY RANDOM() LIMIT 1")) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("quiz random " + guildId, e);
        }
    }

    private static QuizQuestion map(ResultSet rs) throws SQLException {
        return new QuizQuestion(rs.getString("id"), rs.getString("guild_id"), rs.getString("question"),
                rs.getString("correct"), rs.getString("wrong1"), rs.getString("wrong2"), rs.getString("wrong3"));
    }
}
```
- [ ] **Step 7: Run** `./gradlew test --tests "*.QuizRepositoryTest"` → PASS.

---

## Task 4: `/quiz` — play service + view + comando + handler

**Files:** Create `modules/base/fun/{QuizPlayView,QuizPlayService,QuizComponentHandler}.java`, `modules/base/commands/QuizCommand.java`.

**Consumes:** `QuizRepository`, `events.QuizBank` (fallback). **Produces:** namespace `funquiz`; quiz ativo in-memory por canal.

- [ ] **Step 1:** `QuizPlayView.java`
```java
package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;

import java.util.ArrayList;
import java.util.List;

public final class QuizPlayView {
    public static final String NS = "funquiz";

    private QuizPlayView() {}

    public static Container panel(int accent, String question, List<String> options) {
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            buttons.add(Button.secondary(ComponentId.of(NS, "ans", String.valueOf(i)), trim(options.get(i))));
        }
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.INFO, "❓") + " Quiz\n" + question),
                Panels.divider(),
                ActionRow.of(buttons));
    }

    public static Container answered(int accent, String question, String winnerMention, String correct) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.CHECK_YES, "🎉") + " Quiz respondido"),
                Panels.divider(),
                Panels.text(question + "\n\n" + winnerMention + " acertou! Resposta: **" + correct + "**"));
    }

    private static String trim(String s) {
        return s.length() > 78 ? s.substring(0, 78) : s;
    }
}
```
- [ ] **Step 2:** `QuizPlayService.java`
```java
package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.modules.base.events.QuizBank;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Quiz personalizado em jogo (in-memory, um por canal). Fallback pro banco dos eventos. */
public final class QuizPlayService {

    private record Active(String messageId, String question, List<String> options, int correct) {}

    private final BotContext ctx;
    private final QuizRepository repo;
    private final ConcurrentHashMap<String, Active> active = new ConcurrentHashMap<>();

    public QuizPlayService(BotContext ctx) {
        this.ctx = ctx;
        this.repo = new QuizRepository(ctx.database().sqlite());
    }

    public void start(SlashCommandInteractionEvent event) {
        if (!(event.getChannel() instanceof TextChannel channel)) {
            Replies.ephemeral(event, ctx, "Use num canal de texto.");
            return;
        }
        String chId = channel.getId();
        if (active.containsKey(chId)) {
            Replies.ephemeral(event, ctx, "Já tem um quiz rolando neste canal.");
            return;
        }
        String guildId = event.getGuild() == null ? "0" : event.getGuild().getId();
        Prepared p = prepare(guildId);
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
        event.replyComponents(QuizPlayView.panel(accent, p.question, p.options)).useComponentsV2()
                .queue(hook -> hook.retrieveOriginal().queue(msg -> {
                    active.put(chId, new Active(msg.getId(), p.question, p.options, p.correct));
                    ctx.scheduler().once(() -> expire(chId, msg.getId()), 60, TimeUnit.SECONDS);
                }, err -> { }));
    }

    public void onAnswer(ButtonInteractionEvent event, int clicked) {
        String chId = event.getChannel().getId();
        Active a = active.get(chId);
        if (a == null) {
            Replies.ephemeral(event, ctx, "Esse quiz já encerrou.");
            return;
        }
        if (clicked != a.correct()) {
            Replies.ephemeral(event, ctx, "Resposta errada!");
            return;
        }
        if (!active.remove(chId, a)) {
            Replies.ephemeral(event, ctx, "Alguém já acertou!");
            return;
        }
        int accent = EmbedColor.resolve(ctx.database().guildConfig()
                .findOrEmpty(event.getGuild() == null ? "0" : event.getGuild().getId()));
        event.editComponents(QuizPlayView.answered(accent, a.question(),
                        event.getUser().getAsMention(), a.options().get(a.correct())))
                .useComponentsV2().queue(ok -> { }, err -> { });
    }

    private void expire(String chId, String messageId) {
        Active a = active.get(chId);
        if (a != null && a.messageId().equals(messageId) && active.remove(chId, a)) {
            TextChannel ch = ctx.jda() == null ? null : ctx.jda().getTextChannelById(chId);
            if (ch != null) {
                ch.editMessageComponentsById(messageId, QuizPlayView.answered(
                                EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty("0")),
                                a.question(), "*Ninguém*", a.options().get(a.correct())))
                        .useComponentsV2().queue(ok -> { }, err -> { });
            }
        }
    }

    private record Prepared(String question, List<String> options, int correct) {}

    private Prepared prepare(String guildId) {
        Optional<QuizQuestion> custom = repo.random(guildId);
        if (custom.isPresent()) {
            QuizQuestion q = custom.get();
            List<String> opts = new ArrayList<>(List.of(q.correct(), q.wrong1(), q.wrong2(), q.wrong3()));
            Collections.shuffle(opts);
            return new Prepared(q.question(), opts, opts.indexOf(q.correct()));
        }
        QuizBank.Question q = QuizBank.random();
        return new Prepared(q.text(), q.options(), q.correct());
    }
}
```
> **Verificar:** `events.QuizBank.Question` tem `text()/options()/correct()` (confere com o Plano de eventos). `Collections.shuffle` + `indexOf` acham a nova posição da correta.
- [ ] **Step 3:** `QuizCommand.java`
```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.fun.QuizPlayService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /quiz — inicia um quiz (perguntas do servidor, ou banco embutido). */
public final class QuizCommand implements SlashCommand {
    private final QuizPlayService service;
    public QuizCommand(QuizPlayService service) { this.service = service; }

    @Override public String name() { return "quiz"; }

    @Override public SlashCommandData data() {
        return Commands.slash("quiz", "Inicia um quiz no canal.");
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        service.start(event);
    }
}
```
- [ ] **Step 4:** `QuizComponentHandler.java`
```java
package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

public final class QuizComponentHandler implements ComponentHandler {
    private final QuizPlayService service;
    public QuizComponentHandler(QuizPlayService service) { this.service = service; }

    @Override public String namespace() { return QuizPlayView.NS; }

    @Override public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"ans".equals(id.action())) {
            return;
        }
        try {
            service.onAnswer(event, Integer.parseInt(id.arg(0)));
        } catch (NumberFormatException ignored) {
            // índice inválido
        }
    }
}
```
- [ ] **Step 5: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 5: `/setup → Fun` (gestão do quiz)

**Files:** Modify `SetupView.java` (`funScreen` + `quizAddModal`), `SetupComponentHandler.java` (nav + botões + select-remove + modal), `SetupView.hub`/`moduleNav`.

- [ ] **Step 1:** `SetupView` — importar `QuizRepository`/`QuizQuestion`; `funScreen(GuildConfig cfg, List<QuizQuestion> quizzes)`: título + contagem + lista (com um `StringSelectMenu` `quizdel` p/ remover, se houver) + botões `quizadd` (modal) e `nav hub` Voltar + `moduleNav("fun")`. `quizAddModal()`: 5 campos SHORT/PARAGRAPH — `pergunta`, `correta`, `errada1`, `errada2`, `errada3`; id do modal `quizaddform`.
- [ ] **Step 2:** `SetupView.moduleNav` — `addNav(menu, current, "Fun", "fun");`. `SetupView.hub` — `.addOption("Fun", "fun", "Quiz personalizado do servidor")`.
- [ ] **Step 3:** `SetupComponentHandler` — helper `quizRepo(ctx)`; nav button + `onStringSelect "section"`: `case "fun" -> SetupView.funScreen(config(ctx, guildId), quizRepo(ctx).list(guildId));`; `onButton`: `quizadd` → `replyModal(SetupView.quizAddModal())`; `onStringSelect`: `quizdel` → `quizRepo(ctx).remove(value)` + re-render fun; `onModal`: `quizaddform` → `quizRepo(ctx).add(guildId, value("pergunta"), value("correta"), value("errada1"), value("errada2"), value("errada3"))` + re-render (validar campos não-vazios; senão aviso efêmero).
> Seguir os moldes de `nivelreward`/`nivelrewarddel` já existentes (botão abre modal; select remove; modal salva).
- [ ] **Step 4: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 6: Registro no `BaseModule` + build + smoke

**Files:** Modify `modules/base/BaseModule.java`.

- [ ] **Step 1:** No `register(...)`, após o Fun básico:
```java
        // Forca (Base).
        dev.davimf.basebot.modules.base.fun.ForcaService forca =
                new dev.davimf.basebot.modules.base.fun.ForcaService(ctx);
        registry.command(new dev.davimf.basebot.modules.base.commands.ForcaCommand(forca));
        registry.listener(new dev.davimf.basebot.modules.base.fun.ForcaListener(forca));
        // Quiz personalizado (Base).
        dev.davimf.basebot.modules.base.fun.QuizPlayService quiz =
                new dev.davimf.basebot.modules.base.fun.QuizPlayService(ctx);
        registry.command(new dev.davimf.basebot.modules.base.commands.QuizCommand(quiz));
        registry.component(new dev.davimf.basebot.modules.base.fun.QuizComponentHandler(quiz));
```
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL (todos os testes: `HangmanStateTest`, `QuizRepositoryTest`).
- [ ] **Step 3: Smoke (servidor de teste):**
  - `/forca` → painel; digitar letras (acentos: "coração" acerta com `c`/`a`/`o`); errar 6 → perde; acertar a palavra inteira → ganha.
  - `/setup → Fun`: adicionar uma pergunta (pergunta + correta + 3 erradas); `/quiz` → mostra com alternativas embaralhadas; primeiro a clicar certo → "acertou"; sem perguntas custom → usa o banco embutido; remover pela lista.

## Self-Review
- **Cobertura do spec (Plano 2):** forca puro c/ normalização (T1); banco+service+listener+comando (T2); migração 032 + repo (T3); quiz play c/ shuffle + fallback (T4); gestão no setup (T5); registro (T6). ✓
- **Normalização** (letra E palavra inteira) em `HangmanState.norm`; **quiz shuffle** em `QuizPlayService.prepare`; **revela uma vez** via `active.remove(chId, a)`. ✓
- **Consistência:** `HangmanState.start/guessLetter/guessWord/masked/won/lost`, `QuizRepository.add/list/remove/random`, `QuizPlayView.NS`, `events.QuizBank.Question`. ✓
- **Pontos a confirmar no build (inline):** `Emojis.CHECK_NO/INFO`; `events.QuizBank` acessível; modais de 5 campos; helpers de setup.
