# Leveling — Plano 2 (Voz: sessões persistidas, reconciliação e XP por tempo em call)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (inline). Steps em checkbox. Depende do Plano 1 (núcleo) já implementado.

**Goal:** XP por tempo em call — `voice_sessions` persistidas (fonte de verdade do tempo, reusável nos sorteios), reconciliação no boot, e um ticker de 60s que credita XP do delta **elegível** numa **única transação SQL** por ciclo.

**Architecture:** `voice_sessions` (migração 028, índice parcial) + `VoiceSessionRepository`. Elegibilidade pura (`VoiceEligibility`). O ticker (`VoiceXpTicker`) lê as sessões abertas, calcula deltas/elegibilidade ao vivo, e persiste **tudo do ciclo numa transação** via `VoiceXpBatch`; os efeitos no Discord (cargos/notificação de level-up) rodam **depois do commit** por `LevelingService.applyVoiceLevelUp(...)`. `VoiceSessionListener` (evento unificado `GuildVoiceUpdateEvent`) abre/fecha sessões; `VoiceReconciler` acerta o estado no boot.

**Tech Stack:** Java 22, JDA 6.4.2, SQLite.

## Global Constraints

- **JDK 22.** **Sem commits** (cada task termina em `./gradlew build`/test).
- **Migração 028** anexada a `SqliteMigrator.MIGRATIONS`. Índice **parcial** `WHERE leave_time IS NULL`.
- **Taxas fixas:** ~10 XP/min elegível. Intent `GUILD_VOICE_STATES` (já habilitado — `VoiceLoggingListener` usa).
- **⚠️ Transação única por ciclo do ticker:** todas as escritas de XP + avanço de `xp_credited_until` de um ciclo rodam num único `BEGIN…COMMIT` (evita rajada de I/O e `SQLITE_BUSY/database is locked`). Detecção de level-up e chamadas de API do Discord ficam **fora** da transação.
- **Contar humanos, não membros:** elegibilidade filtra `member.getUser().isBot()`.
- **Reconciliação nunca credita período offline.** Sem crédito final no fechamento da sessão (o delta ≤60s ao sair é descartado — simplificação deliberada que mantém o crédito num único lugar/transação: o ticker).

**Símbolos a confirmar no build:**
- `GuildVoiceUpdateEvent`: `getMember()`, `getChannelJoined()`, `getChannelLeft()` (retornam `AudioChannelUnion`/`null`). Import `net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent`.
- `guild.getAfkChannel()` (nullable); `channel.getMembers()` → `List<Member>`; `member.getVoiceState()` → `GuildVoiceState` com `isDeafened()` (self+server), `getChannel()`.
- `SqliteManager.getConnection()` — cada chamada devolve uma conexão utilizável para transação (try-with-resources; `setAutoCommit(false)` seguro). **Confirmar** que não é uma conexão única compartilhada antes de assumir a transação; se for pool/por-chamada, ok.

---

## Task 1: Migração 028 + `VoiceSessionRepository` + teste

**Files:**
- Create: `src/main/resources/db/sqlite/028_voice_sessions.sql`, `modules/base/leveling/VoiceSessionRepository.java`
- Modify: `database/sqlite/SqliteMigrator.java`
- Test: `test/.../leveling/VoiceSessionRepositoryTest.java`

**Interfaces — Produces:** `record Open(long id, String guildId, String userId, String channelId, long joinTime, long xpCreditedUntil)`; `void open(g,u,ch,now)`, `void closeOpen(g,u,now)`, `List<Open> openSessions()`, `List<Open> openSessions(String guildId)`.

- [ ] **Step 1: Migração** `028_voice_sessions.sql`
```sql
CREATE TABLE IF NOT EXISTS voice_sessions (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id          TEXT NOT NULL,
    user_id           TEXT NOT NULL,
    channel_id        TEXT NOT NULL,
    join_time         INTEGER NOT NULL,
    leave_time        INTEGER,
    xp_credited_until INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_voice_sessions_open ON voice_sessions (guild_id, user_id) WHERE leave_time IS NULL;
```
- [ ] **Step 2:** Anexar a `SqliteMigrator.MIGRATIONS` (após `027_leveling.sql`):
```java
            "/db/sqlite/027_leveling.sql",
            "/db/sqlite/028_voice_sessions.sql"
```
- [ ] **Step 3: Teste** `VoiceSessionRepositoryTest.java`
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

class VoiceSessionRepositoryTest {
    private SqliteManager sqlite;
    private VoiceSessionRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new VoiceSessionRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void openThenListReturnsOpenSession() {
        repo.open("g1", "u1", "c1", 1000L);
        List<VoiceSessionRepository.Open> open = repo.openSessions("g1");
        assertEquals(1, open.size());
        assertEquals("c1", open.get(0).channelId());
        assertEquals(1000L, open.get(0).joinTime());
        assertEquals(1000L, open.get(0).xpCreditedUntil());
    }

    @Test
    void closeOpenRemovesFromOpenList() {
        repo.open("g1", "u1", "c1", 1000L);
        repo.closeOpen("g1", "u1", 2000L);
        assertTrue(repo.openSessions("g1").isEmpty());
    }

    @Test
    void openSessionsAllGuilds() {
        repo.open("g1", "u1", "c1", 1000L);
        repo.open("g2", "u2", "c2", 1000L);
        assertEquals(2, repo.openSessions().size());
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.VoiceSessionRepositoryTest"` → FAIL.
- [ ] **Step 5: Implementar** `VoiceSessionRepository.java`
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

/** SQLite store para sessões de voz (migração 028). Fonte de verdade do tempo em call. */
public final class VoiceSessionRepository {

    public record Open(long id, String guildId, String userId, String channelId,
                       long joinTime, long xpCreditedUntil) {}

    private final SqliteManager sqlite;

    public VoiceSessionRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    public void open(String guildId, String userId, String channelId, long now) {
        String sql = "INSERT INTO voice_sessions (guild_id, user_id, channel_id, join_time, xp_credited_until) "
                + "VALUES (?,?,?,?,?)";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setString(3, channelId);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("open voice session " + guildId + "/" + userId, e);
        }
    }

    public void closeOpen(String guildId, String userId, long now) {
        String sql = "UPDATE voice_sessions SET leave_time=? WHERE guild_id=? AND user_id=? AND leave_time IS NULL";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, now);
            ps.setString(2, guildId);
            ps.setString(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("close voice session " + guildId + "/" + userId, e);
        }
    }

    public List<Open> openSessions() {
        return query("SELECT * FROM voice_sessions WHERE leave_time IS NULL", null);
    }

    public List<Open> openSessions(String guildId) {
        return query("SELECT * FROM voice_sessions WHERE leave_time IS NULL AND guild_id=?", guildId);
    }

    private List<Open> query(String sql, String guildId) {
        List<Open> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (guildId != null) {
                ps.setString(1, guildId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Open(rs.getLong("id"), rs.getString("guild_id"), rs.getString("user_id"),
                            rs.getString("channel_id"), rs.getLong("join_time"), rs.getLong("xp_credited_until")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("query voice sessions", e);
        }
    }
}
```
- [ ] **Step 6: Run** `./gradlew test --tests "*.VoiceSessionRepositoryTest"` → PASS.

---

## Task 2: `VoiceEligibility` (puro) + teste

**Files:**
- Create: `modules/base/leveling/VoiceEligibility.java`
- Test: `test/.../leveling/VoiceEligibilityTest.java`

**Interfaces — Produces:** `static boolean isEligible(boolean isBot, long humanCount, boolean deafened, boolean afkChannel)`.

- [ ] **Step 1: Teste**
```java
package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VoiceEligibilityTest {
    @Test
    void eligibleWithTwoHumansNotDeafNotAfk() {
        assertTrue(VoiceEligibility.isEligible(false, 2, false, false));
    }

    @Test
    void notEligibleAlone() {
        assertFalse(VoiceEligibility.isEligible(false, 1, false, false));
    }

    @Test
    void notEligibleWhenBotDeafOrAfk() {
        assertFalse(VoiceEligibility.isEligible(true, 5, false, false));
        assertFalse(VoiceEligibility.isEligible(false, 5, true, false));
        assertFalse(VoiceEligibility.isEligible(false, 5, false, true));
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.VoiceEligibilityTest"` → FAIL.
- [ ] **Step 3: Implementar**
```java
package dev.davimf.basebot.modules.base.leveling;

/** Regra pura de elegibilidade de XP por voz. Conta HUMANOS (não membros — filtrar bots antes). */
public final class VoiceEligibility {

    private VoiceEligibility() {}

    public static boolean isEligible(boolean isBot, long humanCount, boolean deafened, boolean afkChannel) {
        return !isBot && humanCount >= 2 && !deafened && !afkChannel;
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.VoiceEligibilityTest"` → PASS.

---

## Task 3: `VoiceXpBatch` (crédito transacional) + teste

**Files:**
- Create: `modules/base/leveling/VoiceXpBatch.java`
- Test: `test/.../leveling/VoiceXpBatchTest.java`

**Interfaces — Produces:** `record Credit(long sessionId, String guildId, String userId, long xpDelta, long creditedUntil)`; `record Result(String guildId, String userId, long oldXp, long newXp)`; `static List<Result> apply(SqliteManager sqlite, List<Credit> credits)` — **uma transação**: avança `xp_credited_until` de cada sessão e, quando `xpDelta>0`, soma o XP em `user_levels`; devolve os totais antigo/novo (para o ticker detectar level-up após o commit).

- [ ] **Step 1: Teste**
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

class VoiceXpBatchTest {
    private SqliteManager sqlite;
    private VoiceSessionRepository sessions;
    private UserLevelRepository users;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        sessions = new VoiceSessionRepository(sqlite);
        users = new UserLevelRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void appliesXpAndAdvancesCreditedUntilInOneTransaction() {
        sessions.open("g1", "u1", "c1", 1000L);
        sessions.open("g1", "u2", "c1", 1000L);
        long id1 = sessions.openSessions("g1").get(0).id();
        long id2 = sessions.openSessions("g1").get(1).id();

        List<VoiceXpBatch.Result> results = VoiceXpBatch.apply(sqlite, List.of(
                new VoiceXpBatch.Credit(id1, "g1", "u1", 10, 61000L),
                new VoiceXpBatch.Credit(id2, "g1", "u2", 0, 61000L)));

        assertEquals(10, users.xp("g1", "u1"));
        assertEquals(0, users.xp("g1", "u2"));
        assertEquals(61000L, sessions.openSessions("g1").stream()
                .filter(o -> o.userId().equals("u1")).findFirst().orElseThrow().xpCreditedUntil());
        // só quem recebeu XP (>0) volta como Result
        assertEquals(1, results.size());
        assertEquals(0, results.get(0).oldXp());
        assertEquals(10, results.get(0).newXp());
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.VoiceXpBatchTest"` → FAIL.
- [ ] **Step 3: Implementar** `VoiceXpBatch.java`
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
 * Aplica, numa ÚNICA transação SQL, os créditos de XP de voz de um ciclo do ticker:
 * avança {@code xp_credited_until} de cada sessão e soma o XP elegível em {@code user_levels}.
 * Devolve os totais antigo/novo de quem recebeu XP para o ticker detectar level-up após o commit
 * (as chamadas ao Discord ficam fora da transação).
 */
public final class VoiceXpBatch {

    private VoiceXpBatch() {}

    public record Credit(long sessionId, String guildId, String userId, long xpDelta, long creditedUntil) {}

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
                        "UPDATE voice_sessions SET xp_credited_until=? WHERE id=?");
                 PreparedStatement selXp = c.prepareStatement(
                        "SELECT xp FROM user_levels WHERE guild_id=? AND user_id=?");
                 PreparedStatement addXp = c.prepareStatement(
                        "INSERT INTO user_levels (guild_id, user_id, xp) VALUES (?,?,?) "
                        + "ON CONFLICT (guild_id, user_id) DO UPDATE SET xp = xp + excluded.xp")) {
                for (Credit cr : credits) {
                    advance.setLong(1, cr.creditedUntil());
                    advance.setLong(2, cr.sessionId());
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
- [ ] **Step 4: Run** `./gradlew test --tests "*.VoiceXpBatchTest"` → PASS.

---

## Task 4: `LevelingService.applyVoiceLevelUp(...)`

**Files:**
- Modify: `modules/base/leveling/LevelingService.java`

**Interfaces — Produces:** `void applyVoiceLevelUp(Guild guild, Member member, long oldXp, long newXp)` — detecta level-up entre dois totais já persistidos e aplica cargos + notificação (contexto de voz → `current=null`, cai na DM no modo "current"). Reusa o `onLevelUp(...)` existente.

- [ ] **Step 1: Implementar** — adicionar o método público (o XP já foi somado pela transação do batch; aqui só os efeitos):
```java
    /** Efeitos de level-up quando o XP já foi persistido (voz): cargos + notificação, sem re-somar XP. */
    public void applyVoiceLevelUp(net.dv8tion.jda.api.entities.Guild guild,
                                  net.dv8tion.jda.api.entities.Member member, long oldXp, long newXp) {
        int antes = LevelFormula.levelForXp(oldXp);
        int agora = LevelFormula.levelForXp(newXp);
        if (agora > antes) {
            onLevelUp(guild, member, antes, agora, null);
        }
    }
```
> `onLevelUp(Guild, Member, int, int, MessageChannel)` já existe (privado). `current=null` já faz o modo "current" cair na DM (implementado no Plano 1).
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 5: `VoiceSessionListener` (evento unificado)

**Files:**
- Create: `modules/base/leveling/VoiceSessionListener.java`

**Interfaces — Consumes:** `VoiceSessionRepository`. Intent `GUILD_VOICE_STATES`.

- [ ] **Step 1: Implementar**
```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Abre/fecha sessões de voz a partir do evento unificado (join/leave/move). Requer GUILD_VOICE_STATES. */
public final class VoiceSessionListener extends ListenerAdapter {

    private final VoiceSessionRepository sessions;

    public VoiceSessionListener(BotContext ctx) {
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

        if (joined != null && left == null) {
            sessions.open(guildId, userId, joined.getId(), now);
        } else if (left != null && joined == null) {
            sessions.closeOpen(guildId, userId, now);
        } else if (left != null && joined != null) {
            sessions.closeOpen(guildId, userId, now);
            sessions.open(guildId, userId, joined.getId(), now);
        }
    }
}
```
> **Verificar:** `event.getChannelJoined()/getChannelLeft()` retornam `AudioChannelUnion` (subtipo de `AudioChannel`) — atribuível a `AudioChannel`. Se o import de `AudioChannel` divergir, usar `net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel` ou o tipo `AudioChannelUnion` diretamente.
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 6: `VoiceReconciler` (boot)

**Files:**
- Create: `modules/base/leveling/VoiceReconciler.java`

**Interfaces — Consumes:** `VoiceSessionRepository`, JDA. **Produces:** `static void run(BotContext ctx)` — chamado no `onReady` com delay.

- [ ] **Step 1: Implementar**
```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Acerta as sessões de voz no boot: fecha órfãs, abre/reabre conforme quem está em call agora. */
public final class VoiceReconciler {

    private VoiceReconciler() {}

    public static void run(BotContext ctx) {
        if (ctx.jda() == null) {
            return;
        }
        VoiceSessionRepository repo = new VoiceSessionRepository(ctx.database().sqlite());
        long now = System.currentTimeMillis();
        for (Guild guild : ctx.jda().getGuilds()) {
            String guildId = guild.getId();
            // userId -> canal da sessão aberta que o banco conhece
            Map<String, VoiceSessionRepository.Open> dbOpen = new HashMap<>();
            for (VoiceSessionRepository.Open o : repo.openSessions(guildId)) {
                dbOpen.put(o.userId(), o);
            }
            // quem está em call agora (canal atual por usuário)
            Map<String, String> current = new HashMap<>();
            guild.getVoiceChannels().forEach(vc -> {
                for (Member m : vc.getMembers()) {
                    if (!m.getUser().isBot()) {
                        current.put(m.getId(), vc.getId());
                    }
                }
            });
            // abre/reabre para quem está em call
            for (Map.Entry<String, String> e : current.entrySet()) {
                VoiceSessionRepository.Open open = dbOpen.get(e.getKey());
                if (open == null) {
                    repo.open(guildId, e.getKey(), e.getValue(), now);
                } else if (!open.channelId().equals(e.getValue())) {
                    repo.closeOpen(guildId, e.getKey(), now);
                    repo.open(guildId, e.getKey(), e.getValue(), now);
                }
            }
            // fecha órfãs: banco achava aberto mas não está mais em call
            for (String userId : dbOpen.keySet()) {
                if (!current.containsKey(userId)) {
                    repo.closeOpen(guildId, userId, now);
                }
            }
        }
    }
}
```
> Não credita XP do período offline (só acerta presença). Chamado com delay no `onReady` (Task 8) pro cache de voz popular.
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 7: `VoiceXpTicker` (60s, transação por ciclo)

**Files:**
- Create: `modules/base/leveling/VoiceXpTicker.java`

**Interfaces — Consumes:** `VoiceSessionRepository`, `VoiceEligibility`, `VoiceXpBatch`, `LevelingService`, `LevelingConfig`. **Produces:** `void tick()` (agendado a cada 60s no `onReady`).

- [ ] **Step 1: Implementar**
```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

import java.util.ArrayList;
import java.util.List;

/** Credita XP por voz a cada 60s. Todas as escritas do ciclo vão numa única transação (VoiceXpBatch). */
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
            if (!LevelingConfig.enabled(ctx.database().guildConfig().findOrEmpty(guild.getId()))) {
                continue;
            }
            List<VoiceSessionRepository.Open> open = sessions.openSessions(guild.getId());
            if (open.isEmpty()) {
                continue;
            }
            List<VoiceXpBatch.Credit> credits = new ArrayList<>();
            List<Member> members = new ArrayList<>();
            List<VoiceXpBatch.Credit> creditForMember = new ArrayList<>();
            for (VoiceSessionRepository.Open s : open) {
                Member member = guild.getMemberById(s.userId());
                VoiceChannel channel = guild.getVoiceChannelById(s.channelId());
                long delta = 0;
                if (member != null && channel != null) {
                    long humans = channel.getMembers().stream().filter(m -> !m.getUser().isBot()).count();
                    GuildVoiceState vs = member.getVoiceState();
                    boolean deaf = vs != null && vs.isDeafened();
                    boolean afk = guild.getAfkChannel() != null && guild.getAfkChannel().getId().equals(channel.getId());
                    if (VoiceEligibility.isEligible(member.getUser().isBot(), humans, deaf, afk)) {
                        delta = (Math.max(0, now - s.xpCreditedUntil()) * XP_PER_MIN) / 60_000L;
                    }
                }
                VoiceXpBatch.Credit credit = new VoiceXpBatch.Credit(s.id(), guild.getId(), s.userId(), delta, now);
                credits.add(credit);
                members.add(member);
                creditForMember.add(credit);
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
> **Nota:** delta pode ser 0 mesmo elegível se `now - xpCreditedUntil < 6000ms` (menos de 0.1 min); no ritmo normal de 60s dá 10. `members`/`creditForMember` são auxiliares removíveis se o compilador reclamar de não-uso — manter só `credits`. **Simplificar:** remover as listas `members`/`creditForMember` (não usadas; o level-up relê o membro via `guild.getMemberById(r.userId())`).
- [ ] **Step 2:** Remover as listas auxiliares não usadas (`members`, `creditForMember`) deixadas acima — manter só `credits`:
```java
            List<VoiceXpBatch.Credit> credits = new ArrayList<>();
            for (VoiceSessionRepository.Open s : open) {
                Member member = guild.getMemberById(s.userId());
                VoiceChannel channel = guild.getVoiceChannelById(s.channelId());
                long delta = 0;
                if (member != null && channel != null) {
                    long humans = channel.getMembers().stream().filter(m -> !m.getUser().isBot()).count();
                    GuildVoiceState vs = member.getVoiceState();
                    boolean deaf = vs != null && vs.isDeafened();
                    boolean afk = guild.getAfkChannel() != null && guild.getAfkChannel().getId().equals(channel.getId());
                    if (VoiceEligibility.isEligible(member.getUser().isBot(), humans, deaf, afk)) {
                        delta = (Math.max(0, now - s.xpCreditedUntil()) * XP_PER_MIN) / 60_000L;
                    }
                }
                credits.add(new VoiceXpBatch.Credit(s.id(), guild.getId(), s.userId(), delta, now));
            }
```
- [ ] **Step 3: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 8: Registro no `BaseModule` + build + smoke

**Files:**
- Modify: `modules/base/BaseModule.java`

**Interfaces — Consumes:** o campo `leveling` (Plano 1).

- [ ] **Step 1:** No `register(...)`, após o registro do Leveling (Plano 1), registrar o listener de sessões:
```java
        // Leveling — voz (Plano 2): sessões persistidas para XP por tempo em call.
        registry.listener(new dev.davimf.basebot.modules.base.leveling.VoiceSessionListener(ctx));
```
- [ ] **Step 2:** No `onReady(...)`, agendar o ticker (60s) e a reconciliação (uma vez, com delay p/ o cache de voz):
```java
        // Leveling — voz: ticker de XP a cada 60s + reconciliação das sessões no boot.
        if (leveling != null) {
            dev.davimf.basebot.modules.base.leveling.VoiceXpTicker ticker =
                    new dev.davimf.basebot.modules.base.leveling.VoiceXpTicker(ctx, leveling);
            ctx.scheduler().repeating(ticker::tick, 60, 60, TimeUnit.SECONDS);
            ctx.scheduler().once(() ->
                    dev.davimf.basebot.modules.base.leveling.VoiceReconciler.run(ctx), 5, TimeUnit.SECONDS);
        }
```
> **Verificar:** `ctx.scheduler().repeating(Runnable, initialDelay, period, TimeUnit)` (mesma assinatura usada por `muteService::sweepExpired`) e a existência de `ctx.scheduler().once(Runnable, delay, TimeUnit)` — o AntiNuke usa `scheduler().once(...)` (`NukeAuditLookup`); confirmar a assinatura e ajustar se preciso (ex.: `schedule(...)`).
- [ ] **Step 3: Run** `./gradlew build` → BUILD SUCCESSFUL (todos os testes verdes: `VoiceSessionRepositoryTest`, `VoiceEligibilityTest`, `VoiceXpBatchTest`).
- [ ] **Step 4: Smoke (servidor de teste, `level:enabled`, com um alt):**
  - Você + o alt entram numa call → após ~1 min cada ganha ~10 XP (confirmar via `/rank`). Sozinho na call → **não** ganha. Ensurdecido → não ganha. No canal AFK → não ganha. Bot de música na call não conta como humano.
  - Trocar de canal → sessão fecha e reabre (sem duplicar). Sair → para de ganhar.
  - **Restart do bot com gente na call** → a reconciliação (5s após o ready) reabre as sessões; o XP volta a acumular sem creditar o tempo offline.
  - Subir de nível em call com o modo de notificação "canal atual" → chega na **DM**.

## Self-Review
- **Cobertura do spec (voz):** voice_sessions + índice parcial (T1); elegibilidade filtrando bots (T2); crédito transacional único por ciclo (T3); level-up fora da transação (T4/T7); evento unificado join/leave/move (T5); reconciliação no boot sem creditar offline (T6); ticker 60s ~10 XP/min (T7); registro + agendamento (T8). ✓
- **Transação única:** `VoiceXpBatch.apply` faz todo o I/O do ciclo num `BEGIN…COMMIT`; efeitos no Discord depois do commit. ✓
- **Consistência de tipos:** `VoiceSessionRepository.Open`, `VoiceXpBatch.Credit/Result`, `VoiceEligibility.isEligible`, `LevelingService.applyVoiceLevelUp`. ✓
- **Pontos a confirmar no build (inline):** tipos de canal do `GuildVoiceUpdateEvent`; `GuildVoiceState.isDeafened()`; assinaturas de `scheduler().repeating/once`.
