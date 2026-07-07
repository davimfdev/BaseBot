<!-- [OUTLINE START]
Markdown Document: Base Module — Voice Moderation + `.env` Support
[OUTLINE END] -->



# Base Module — Voice Moderation + `.env` Support

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `.env` file loading to the config system, then implement the Module 1 voice-moderation commands: `/disconnect`, `/voice-move`, `/mutecall`, `/unmutecall` (persistent server-mute) and the text-mute pair `/mute`, `/unmute`.

**Architecture:** A pure `DotEnv` parser (TDD) feeds `ConfigLoader` so secrets can live in a git-ignored `.env` (process env still wins). Persistent call-mutes are tracked in SQLite (`VoiceMuteRepository`, TDD) and re-applied on voice-join by a listener. Commands reuse the tested `Moderation` hierarchy guard and read the `mutado` role from `guild_config` (set via `/setup`).

**Tech Stack:** Java 22, JDA 6.4.2 (`guild.kickVoiceMember/moveVoiceMember/mute`, `Member.getVoiceState`, `GuildVoiceUpdateEvent`), SQLite via `SqliteManager`, JUnit 5.

## Global Constraints

- JDK 22; JDA `6.4.2`. Confirmed voice APIs: `guild.kickVoiceMember(user)` (disconnect), `guild.moveVoiceMember(user, audioChannel)`, `guild.mute(user, boolean)` (server mute), `member.getVoiceState().inAudioChannel()`, `GuildVoiceUpdateEvent.getChannelJoined()` (null when leaving). Permissions: `VOICE_MOVE_OTHERS`, `VOICE_MUTE_OTHERS`, `MODERATE_MEMBERS`. Channel option restricted via `new OptionData(CHANNEL,…).setChannelTypes(ChannelType.VOICE)`, read with `getAsChannel().asVoiceChannel()`.
- Process environment variables ALWAYS win over `.env` file values (12-factor). `.env` is git-ignored; never commit real secrets. `.env.example` documents required keys.
- All state Guild-specific; voice commands require their respective Discord permissions and validate moderator+bot hierarchy.
- Persistent voice mute survives reconnect: store in SQLite, re-apply on `GuildVoiceUpdateEvent` join.
- Portuguese UI copy. TDD: failing test first; frequent commits; DRY; YAGNI.

---

### Task 1: `.env` loading (`DotEnv` parser + `ConfigLoader` integration)

**Files:**
- Create: `src/main/java/dev/davimf/basebot/config/DotEnv.java`
- Modify: `src/main/java/dev/davimf/basebot/config/ConfigLoader.java` (consult `.env` via `DotEnv`)
- Create: `.env.example`
- Test: `src/test/java/dev/davimf/basebot/config/DotEnvTest.java`

**Interfaces:**
- Produces:
  - `DotEnv.parse(String content) -> Map<String,String>` (pure).
  - `DotEnv.load(Path file) -> DotEnv` (empty if file absent).
  - `dotEnv.get(String key) -> String` — process env wins, else `.env` value, else null.

- [ ] **Step 1: Write the failing test**

```java
package dev.davimf.basebot.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DotEnvTest {

    @Test
    void parsesKeyValueIgnoringCommentsAndBlanks() {
        Map<String, String> m = DotEnv.parse("""
                # comment
                BOT_TOKEN=abc123

                POSTGRES_USER=basebot
                """);
        assertEquals("abc123", m.get("BOT_TOKEN"));
        assertEquals("basebot", m.get("POSTGRES_USER"));
        assertEquals(2, m.size());
    }

    @Test
    void stripsQuotesAndExportPrefixAndKeepsInnerEquals() {
        Map<String, String> m = DotEnv.parse("""
                export TICKET_VIEW_BASE="https://davimf.dev/ticket"
                POSTGRES_URL=jdbc:postgresql://h/db?sslmode=require
                EMPTY=''
                """);
        assertEquals("https://davimf.dev/ticket", m.get("TICKET_VIEW_BASE"));
        assertEquals("jdbc:postgresql://h/db?sslmode=require", m.get("POSTGRES_URL"));
        assertEquals("", m.get("EMPTY"));
    }

    @Test
    void getReturnsFileValueWhenNotInProcessEnv(@TempDir Path dir) throws Exception {
        Path f = dir.resolve(".env");
        Files.writeString(f, "BASEBOT_UNLIKELY_KEY_42=hello\n");
        assertEquals("hello", DotEnv.load(f).get("BASEBOT_UNLIKELY_KEY_42"));
    }

    @Test
    void loadMissingFileYieldsEmptyLookup(@TempDir Path dir) {
        assertNull(DotEnv.load(dir.resolve("nope.env")).get("BASEBOT_UNLIKELY_KEY_42"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.config.DotEnvTest"`
Expected: FAIL — `DotEnv` cannot be resolved.

- [ ] **Step 3: Implement `DotEnv` and wire it into `ConfigLoader`**

`DotEnv.java`:

```java
package dev.davimf.basebot.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal {@code .env} loader. Parses {@code KEY=VALUE} lines (ignoring blanks and
 * {@code #} comments, tolerating an {@code export} prefix and surrounding quotes) and
 * exposes a lookup where the real process environment always takes precedence over the
 * file — so production can rely purely on env vars while local dev uses {@code .env}.
 */
public final class DotEnv {

    private final Map<String, String> values;

    private DotEnv(Map<String, String> values) {
        this.values = values;
    }

    public static DotEnv load(Path file) {
        if (Files.exists(file)) {
            try {
                return new DotEnv(parse(Files.readString(file, StandardCharsets.UTF_8)));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read .env file: " + file, e);
            }
        }
        return new DotEnv(Map.of());
    }

    /** Process env wins; otherwise the parsed {@code .env} value; otherwise null. */
    public String get(String key) {
        String sys = System.getenv(key);
        if (sys != null && !sys.isBlank()) {
            return sys;
        }
        return values.get(key);
    }

    public static Map<String, String> parse(String content) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String raw : content.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).strip();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).strip();
            String value = stripQuotes(line.substring(eq + 1).strip());
            out.put(key, value);
        }
        return out;
    }

    private static String stripQuotes(String v) {
        if (v.length() >= 2
                && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }
}
```

In `ConfigLoader.java`, thread a `DotEnv` through the env helpers. Change `load(Path)` to create it, and change the private `env`/`envInt`/`envLong`/`parseEnv`/`require` helpers to take the `DotEnv` and call `env.get(key)` instead of `System.getenv(key)`. Concretely:

- At the top of `load(Path file)` add: `DotEnv env = DotEnv.load(Path.of(".env"));`
- Replace each `env("X", ...)` call with `env(env, "X", ...)`, each `envInt("X", d)` with `envInt(env, "X", d)`, each `envLong(...)` likewise, and `require("BOT_TOKEN", ...)` with `require(env, "BOT_TOKEN", ...)`.
- Update the helper signatures/bodies:

```java
    private static String env(DotEnv env, String key, String fallback) {
        String v = env.get(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private static int envInt(DotEnv env, String key, int fallback) {
        return parseEnv(env, key, fallback, Integer::parseInt);
    }

    private static long envLong(DotEnv env, String key, long fallback) {
        return parseEnv(env, key, fallback, Long::parseLong);
    }

    private static <T> T parseEnv(DotEnv env, String key, T fallback, Function<String, T> parser) {
        String v = env.get(key);
        if (v == null || v.isBlank()) return fallback;
        try {
            return parser.apply(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Env var " + key + " is not a valid number: " + v, e);
        }
    }

    private static String require(DotEnv env, String envKey, String yamlValue) {
        String v = env.get(envKey);
        if (v != null && !v.isBlank()) return v;
        if (yamlValue != null && !yamlValue.isBlank() && !yamlValue.startsWith("YOUR_")) return yamlValue;
        throw new IllegalStateException(
                "Missing required config: set env " + envKey + " (or .env) or the config.yml value.");
    }
```

`.env.example`:

```
# Copy to .env (git-ignored) and fill in. Process env vars override these.

# Discord
BOT_TOKEN=your-bot-token
# DEV_GUILD_ID=123456789012345678

# PostgreSQL — guild config source of truth (Supabase today, Neon later)
POSTGRES_URL=jdbc:postgresql://localhost:5432/basebot?sslmode=disable
POSTGRES_USER=basebot
POSTGRES_PASSWORD=change-me

# SQLite — local fast state
SQLITE_PATH=data/basebot.db

# Ticket transcript ingest -> davimf.dev
TICKET_INGEST_URL=https://davimf.dev/api/ticket-store
TICKET_INGEST_SECRET=change-me
TICKET_VIEW_BASE=https://davimf.dev/ticket
```

(`.env` is already in `.gitignore`.)

- [ ] **Step 4: Run tests + compile**

Run: `./gradlew test --tests "dev.davimf.basebot.config.DotEnvTest"`
Expected: PASS (4 tests).

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/config/DotEnv.java \
        src/main/java/dev/davimf/basebot/config/ConfigLoader.java \
        .env.example \
        src/test/java/dev/davimf/basebot/config/DotEnvTest.java
git commit -m "feat(config): load secrets from .env (process env still wins)"
```

---

### Task 2: `VoiceMuteRepository` (persistent call-mutes in SQLite)

**Files:**
- Create: `src/main/resources/db/sqlite/003_voice_mutes.sql`
- Create: `src/main/java/dev/davimf/basebot/modules/base/voice/VoiceMuteRepository.java`
- Modify: `src/main/java/dev/davimf/basebot/database/sqlite/SqliteMigrator.java` (append migration)
- Modify: `src/main/java/dev/davimf/basebot/database/DatabaseManager.java` (construct + expose `voiceMutes()`)
- Test: `src/test/java/dev/davimf/basebot/modules/base/voice/VoiceMuteRepositoryTest.java`

**Interfaces:**
- Produces: `VoiceMuteRepository(SqliteManager)` with `add(guildId,userId)`, `remove(guildId,userId)`, `isMuted(guildId,userId) -> boolean`; `DatabaseManager.voiceMutes() -> VoiceMuteRepository`.

- [ ] **Step 1: Migration + failing test**

`src/main/resources/db/sqlite/003_voice_mutes.sql`:

```sql
-- Persistent voice (call) mutes: re-applied on voice join. Guild-scoped, maps IDs.
CREATE TABLE IF NOT EXISTS voice_mutes (
    guild_id TEXT NOT NULL,
    user_id  TEXT NOT NULL,
    PRIMARY KEY (guild_id, user_id)
);
```

`src/test/java/dev/davimf/basebot/modules/base/voice/VoiceMuteRepositoryTest.java`:

```java
package dev.davimf.basebot.modules.base.voice;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceMuteRepositoryTest {

    private SqliteManager sqlite;
    private VoiceMuteRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new VoiceMuteRepository(sqlite);
    }

    @AfterEach
    void tearDown() {
        sqlite.close();
    }

    @Test
    void addThenIsMutedTrue() {
        repo.add("g1", "u1");
        assertTrue(repo.isMuted("g1", "u1"));
    }

    @Test
    void removeClearsIt() {
        repo.add("g1", "u1");
        repo.remove("g1", "u1");
        assertFalse(repo.isMuted("g1", "u1"));
    }

    @Test
    void isGuildScoped() {
        repo.add("g1", "u1");
        assertFalse(repo.isMuted("g2", "u1"));
    }

    @Test
    void addIsIdempotent() {
        repo.add("g1", "u1");
        repo.add("g1", "u1");
        assertTrue(repo.isMuted("g1", "u1"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.voice.VoiceMuteRepositoryTest"`
Expected: FAIL — `VoiceMuteRepository` cannot be resolved.

- [ ] **Step 3: Implement repository + register migration + expose from DatabaseManager**

`VoiceMuteRepository.java`:

```java
package dev.davimf.basebot.modules.base.voice;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Tracks persistent voice (call) mutes so they survive reconnects (BOTSPECS Module 1). */
public final class VoiceMuteRepository {

    private final SqliteManager sqlite;

    public VoiceMuteRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public void add(String guildId, String userId) {
        String sql = "INSERT INTO voice_mutes (guild_id, user_id) VALUES (?, ?) "
                + "ON CONFLICT (guild_id, user_id) DO NOTHING";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("add voice mute " + guildId + "/" + userId, e);
        }
    }

    public void remove(String guildId, String userId) {
        String sql = "DELETE FROM voice_mutes WHERE guild_id = ? AND user_id = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("remove voice mute " + guildId + "/" + userId, e);
        }
    }

    public boolean isMuted(String guildId, String userId) {
        String sql = "SELECT 1 FROM voice_mutes WHERE guild_id = ? AND user_id = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RepositoryException("check voice mute " + guildId + "/" + userId, e);
        }
    }
}
```

In `SqliteMigrator.java`, append to `MIGRATIONS`:

```java
            "/db/sqlite/002_pix_keys.sql",
            "/db/sqlite/003_voice_mutes.sql"
```

In `DatabaseManager.java`: import and add a field + accessor, constructed alongside the others.
- Add field: `private final VoiceMuteRepository voiceMutes;`
- In the constructor (after `this.actionLogs = ...`): `this.voiceMutes = new VoiceMuteRepository(sqlite);`
- Add accessor:

```java
    public VoiceMuteRepository voiceMutes() {
        return voiceMutes;
    }
```

- Import: `import dev.davimf.basebot.modules.base.voice.VoiceMuteRepository;`

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.base.voice.VoiceMuteRepositoryTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/sqlite/003_voice_mutes.sql \
        src/main/java/dev/davimf/basebot/modules/base/voice/VoiceMuteRepository.java \
        src/main/java/dev/davimf/basebot/database/sqlite/SqliteMigrator.java \
        src/main/java/dev/davimf/basebot/database/DatabaseManager.java \
        src/test/java/dev/davimf/basebot/modules/base/voice/VoiceMuteRepositoryTest.java
git commit -m "feat(base): add persistent voice-mute SQLite store"
```

---

### Task 3: `/disconnect` and `/voice-move`

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/DisconnectCommand.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/VoiceMoveCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java` (register)
- Test: compile (hierarchy is covered by `RoleHierarchy`/`Moderation` tests).

**Interfaces:** consumes `Moderation.canModerate`; JDA `guild.kickVoiceMember` / `moveVoiceMember`.

- [ ] **Step 1: Implement both commands**

`DisconnectCommand.java`:

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /disconnect — kicks a member from their voice channel (BOTSPECS Module 1). */
public final class DisconnectCommand implements SlashCommand {

    @Override
    public String name() {
        return "disconnect";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("disconnect", "Desconecta um membro do canal de voz.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.VOICE_MOVE_OTHERS))
                .addOption(OptionType.USER, "usuario", "Membro a desconectar", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        if (target == null) {
            event.reply("Membro inválido.").setEphemeral(true).queue();
            return;
        }
        if (!Moderation.canModerate(event.getMember(), target, event.getGuild().getSelfMember())) {
            event.reply("Hierarquia insuficiente.").setEphemeral(true).queue();
            return;
        }
        GuildVoiceState vs = target.getVoiceState();
        if (vs == null || !vs.inAudioChannel()) {
            event.reply("Esse membro não está em um canal de voz.").setEphemeral(true).queue();
            return;
        }
        event.getGuild().kickVoiceMember(target).queue(
                ok -> {
                    ctx.database().actionLogs().log(event.getGuild().getId(),
                            event.getUser().getId(), target.getId(), "VOICE_DISCONNECT", null);
                    event.reply(target.getUser().getAsTag() + " foi desconectado.").queue();
                },
                err -> event.reply("Falha: " + err.getMessage()).setEphemeral(true).queue());
    }
}
```

`VoiceMoveCommand.java`:

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /voice-move — moves a member to another voice channel (BOTSPECS Module 1). */
public final class VoiceMoveCommand implements SlashCommand {

    @Override
    public String name() {
        return "voice-move";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("voice-move", "Move um membro para outro canal de voz.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.VOICE_MOVE_OTHERS))
                .addOption(OptionType.USER, "usuario", "Membro a mover", true)
                .addOptions(new OptionData(OptionType.CHANNEL, "canal", "Canal de voz destino", true)
                        .setChannelTypes(ChannelType.VOICE));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        OptionMapping canalOpt = event.getOption("canal");
        if (target == null || canalOpt == null) {
            event.reply("Membro ou canal inválido.").setEphemeral(true).queue();
            return;
        }
        VoiceChannel channel = canalOpt.getAsChannel().asVoiceChannel();
        if (!Moderation.canModerate(event.getMember(), target, event.getGuild().getSelfMember())) {
            event.reply("Hierarquia insuficiente.").setEphemeral(true).queue();
            return;
        }
        GuildVoiceState vs = target.getVoiceState();
        if (vs == null || !vs.inAudioChannel()) {
            event.reply("Esse membro não está em um canal de voz.").setEphemeral(true).queue();
            return;
        }
        event.getGuild().moveVoiceMember(target, channel).queue(
                ok -> {
                    ctx.database().actionLogs().log(event.getGuild().getId(),
                            event.getUser().getId(), target.getId(), "VOICE_MOVE", channel.getId());
                    event.reply(target.getUser().getAsTag() + " movido para " + channel.getName()).queue();
                },
                err -> event.reply("Falha: " + err.getMessage()).setEphemeral(true).queue());
    }
}
```

In `BaseModule.java`, add imports + register (a new "Voice moderation" block):

```java
import dev.davimf.basebot.modules.base.commands.DisconnectCommand;
import dev.davimf.basebot.modules.base.commands.VoiceMoveCommand;
```

```java
        // Voice moderation (BOTSPECS Module 1).
        registry.command(new DisconnectCommand());
        registry.command(new VoiceMoveCommand());
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/commands/DisconnectCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/VoiceMoveCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(base): add /disconnect and /voice-move"
```

---

### Task 4: `/mutecall`, `/unmutecall` + persistence listener

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/MuteCallCommand.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/UnmuteCallCommand.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/voice/VoiceMutePersistenceListener.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java` (register both + the listener)
- Test: compile + `VoiceMuteRepository` (Task 2).

**Interfaces:** consumes `Moderation.canModerate`, `ctx.database().voiceMutes()`, `guild.mute(user, bool)`, `GuildVoiceUpdateEvent`.

- [ ] **Step 1: Implement the commands + listener**

`MuteCallCommand.java`:

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /mutecall — persistent server voice mute; re-applied on reconnect (BOTSPECS Module 1). */
public final class MuteCallCommand implements SlashCommand {

    @Override
    public String name() {
        return "mutecall";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("mutecall", "Silencia um membro na call (persistente).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.VOICE_MUTE_OTHERS))
                .addOption(OptionType.USER, "usuario", "Membro a silenciar", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        if (target == null) {
            event.reply("Membro inválido.").setEphemeral(true).queue();
            return;
        }
        if (!Moderation.canModerate(event.getMember(), target, event.getGuild().getSelfMember())) {
            event.reply("Hierarquia insuficiente.").setEphemeral(true).queue();
            return;
        }
        ctx.database().voiceMutes().add(event.getGuild().getId(), target.getId());
        ctx.database().actionLogs().log(event.getGuild().getId(),
                event.getUser().getId(), target.getId(), "VOICE_MUTE", null);

        GuildVoiceState vs = target.getVoiceState();
        if (vs != null && vs.inAudioChannel()) {
            event.getGuild().mute(target, true).reason("Mute de call").queue();
        }
        event.reply(target.getUser().getAsTag() + " silenciado na call (persistente).").queue();
    }
}
```

`UnmuteCallCommand.java`:

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /unmutecall — clears a persistent call mute (BOTSPECS Module 1). */
public final class UnmuteCallCommand implements SlashCommand {

    @Override
    public String name() {
        return "unmutecall";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("unmutecall", "Remove o silêncio de call de um membro.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.VOICE_MUTE_OTHERS))
                .addOption(OptionType.USER, "usuario", "Membro", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        if (target == null) {
            event.reply("Membro inválido.").setEphemeral(true).queue();
            return;
        }
        if (!Moderation.canModerate(event.getMember(), target, event.getGuild().getSelfMember())) {
            event.reply("Hierarquia insuficiente.").setEphemeral(true).queue();
            return;
        }
        ctx.database().voiceMutes().remove(event.getGuild().getId(), target.getId());
        ctx.database().actionLogs().log(event.getGuild().getId(),
                event.getUser().getId(), target.getId(), "VOICE_UNMUTE", null);

        GuildVoiceState vs = target.getVoiceState();
        if (vs != null && vs.inAudioChannel()) {
            event.getGuild().mute(target, false).reason("Unmute de call").queue();
        }
        event.reply(target.getUser().getAsTag() + " liberado na call.").queue();
    }
}
```

`VoiceMutePersistenceListener.java`:

```java
package dev.davimf.basebot.modules.base.voice;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Re-applies persistent call mutes (BOTSPECS Module 1): when a flagged member joins a
 * voice channel, server-mute them again so the mute survives reconnects.
 */
public final class VoiceMutePersistenceListener extends ListenerAdapter {

    private final BotContext ctx;

    public VoiceMutePersistenceListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        if (event.getChannelJoined() == null) {
            return; // only act when joining/moving into a channel
        }
        Member member = event.getMember();
        String guildId = event.getGuild().getId();
        if (ctx.database().voiceMutes().isMuted(guildId, member.getId())) {
            event.getGuild().mute(member, true).reason("Mute de call persistente").queue(ok -> {}, err -> {});
        }
    }
}
```

In `BaseModule.java`, add imports + register:

```java
import dev.davimf.basebot.modules.base.commands.MuteCallCommand;
import dev.davimf.basebot.modules.base.commands.UnmuteCallCommand;
import dev.davimf.basebot.modules.base.voice.VoiceMutePersistenceListener;
```

```java
        registry.command(new MuteCallCommand());
        registry.command(new UnmuteCallCommand());
        registry.listener(new VoiceMutePersistenceListener(ctx));
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/commands/MuteCallCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/UnmuteCallCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/voice/VoiceMutePersistenceListener.java \
        src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(base): add /mutecall /unmutecall with persistent re-apply"
```

---

### Task 5: `/mute`, `/unmute` (text mute via configured `mutado` role)

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/MuteCommand.java`
- Create: `src/main/java/dev/davimf/basebot/modules/base/commands/UnmuteCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/base/BaseModule.java` (register)
- Test: compile + full build.

**Interfaces:** consumes `Moderation.canModerate`, `GuildConfig.role("mutado")` (set via `/setup` Cargos), `guild.addRoleToMember`/`removeRoleFromMember`.

- [ ] **Step 1: Implement both commands**

`MuteCommand.java`:

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /mute — text mute by applying the configured "mutado" role (BOTSPECS Module 1). */
public final class MuteCommand implements SlashCommand {

    @Override
    public String name() {
        return "mute";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("mute", "Silencia um membro no texto (cargo de mutado).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER, "usuario", "Membro a silenciar", true)
                .addOption(OptionType.STRING, "motivo", "Motivo", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        if (target == null) {
            event.reply("Membro inválido.").setEphemeral(true).queue();
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        String roleId = cfg.role("mutado");
        Role role = roleId == null ? null : event.getGuild().getRoleById(roleId);
        if (role == null) {
            event.reply("Cargo de mutado não configurado. Use /setup → Cargos.")
                    .setEphemeral(true).queue();
            return;
        }
        Member self = event.getGuild().getSelfMember();
        if (!Moderation.canModerate(event.getMember(), target, self) || !self.canInteract(role)) {
            event.reply("Hierarquia insuficiente para aplicar o cargo de mutado.")
                    .setEphemeral(true).queue();
            return;
        }
        String reason = event.getOption("motivo", "Sem motivo informado.", OptionMapping::getAsString);
        event.getGuild().addRoleToMember(target, role).reason(reason).queue(
                ok -> {
                    ctx.database().actionLogs().log(event.getGuild().getId(),
                            event.getUser().getId(), target.getId(), "MUTE", reason);
                    event.reply(target.getUser().getAsTag() + " foi silenciado.").queue();
                },
                err -> event.reply("Falha: " + err.getMessage()).setEphemeral(true).queue());
    }
}
```

`UnmuteCommand.java`:

```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /unmute — removes the configured "mutado" role (BOTSPECS Module 1). */
public final class UnmuteCommand implements SlashCommand {

    @Override
    public String name() {
        return "unmute";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("unmute", "Remove o silêncio de texto de um membro.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER, "usuario", "Membro", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        if (target == null) {
            event.reply("Membro inválido.").setEphemeral(true).queue();
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        String roleId = cfg.role("mutado");
        Role role = roleId == null ? null : event.getGuild().getRoleById(roleId);
        if (role == null) {
            event.reply("Cargo de mutado não configurado. Use /setup → Cargos.")
                    .setEphemeral(true).queue();
            return;
        }
        Member self = event.getGuild().getSelfMember();
        if (!Moderation.canModerate(event.getMember(), target, self) || !self.canInteract(role)) {
            event.reply("Hierarquia insuficiente.").setEphemeral(true).queue();
            return;
        }
        event.getGuild().removeRoleFromMember(target, role).queue(
                ok -> {
                    ctx.database().actionLogs().log(event.getGuild().getId(),
                            event.getUser().getId(), target.getId(), "UNMUTE", null);
                    event.reply(target.getUser().getAsTag() + " foi dessilenciado.").queue();
                },
                err -> event.reply("Falha: " + err.getMessage()).setEphemeral(true).queue());
    }
}
```

In `BaseModule.java`, add imports + register:

```java
import dev.davimf.basebot.modules.base.commands.MuteCommand;
import dev.davimf.basebot.modules.base.commands.UnmuteCommand;
```

```java
        registry.command(new MuteCommand());
        registry.command(new UnmuteCommand());
```

- [ ] **Step 2: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — compiles, all unit tests pass, `basebot.jar` produced.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/base/commands/MuteCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/commands/UnmuteCommand.java \
        src/main/java/dev/davimf/basebot/modules/base/BaseModule.java
git commit -m "feat(base): add /mute /unmute via configured mutado role"
```

---

## Self-Review

**Spec coverage:**
- `.env` usage for required config/secrets → Task 1 (`DotEnv` + `ConfigLoader` + `.env.example`). ✓
- `/disconnect`, `/voice-move` (voice moderation) → Task 3. ✓
- `/mutecall`, `/unmutecall` (persistent voice moderation) → Task 4 (+ Task 2 store + re-apply listener). ✓
- `/mute`, `/unmute` (text moderation) → Task 5, using the `mutado` role configured via `/setup`. ✓
- Each command validates moderator+bot hierarchy via the tested `Moderation` guard and writes an `action_logs` row.
- **Deferred** (remaining Module 1): `/lock`·`/unlock`, `/bot-name`·`/bot-icon`·`/bot-nick`, `/listacargo`, `/embed`·`/editembed`, `/addemoji`, `/formulario`.

**Placeholder scan:** No stubs; every command/listener is fully implemented. `.env.example` uses obvious placeholder secret values by design.

**Type consistency:** `DotEnv.parse/load/get`; `VoiceMuteRepository.add/remove/isMuted` + `DatabaseManager.voiceMutes()`; `Moderation.canModerate(Member,Member,Member)`; `GuildConfig.role("mutado")`; JDA `guild.mute(user,bool)` / `kickVoiceMember` / `moveVoiceMember` — referenced identically across defining and consuming tasks. ✓
