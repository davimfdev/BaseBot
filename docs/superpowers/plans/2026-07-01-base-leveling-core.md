# Leveling — Plano 1 (Núcleo: XP por mensagem, níveis, cargos, rank/top/xp, setup)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (inline). Steps em checkbox.

**Goal:** Sistema de níveis por **mensagem**: XP com cooldown, curva MEE6, cargos por nível (com batching), notificação configurável de level-up, e comandos `/rank` `/top` `/xp` + `/setup → Nível`. (O XP por **voz** é o Plano 2.)

**Architecture:** Pacote `modules/base/leveling/`. Núcleo puro (`LevelFormula`, `LevelingConfig`, `LevelRewards`) + repos SQLite (`UserLevelRepository`, `LevelRewardRepository`) + `LevelingService` (concede XP, detecta level-up, aplica cargos numa única chamada e notifica) + `MessageXpListener` + UI (`RankView`/`RankData`, comandos, tela de setup).

**Tech Stack:** Java 22, JDA 6.4.2 (Components V2), SQLite.

## Global Constraints

- **JDK 22.** **Sem commits** (cada task termina em `./gradlew build`/test).
- **Base ≠ facs:** sem imports de facs; gate de admin = permissão do Discord `MANAGE_SERVER` (não `ManagerPermissions`).
- **Migrações:** anexar `NNN_*.sql` a `SqliteMigrator.MIGRATIONS`; sem comentário inline após `;`. Próximo nº: **027** (só `user_levels` + `level_rewards`; `voice_sessions` fica na 028/Plano 2).
- **Components V2** em toda mensagem; `Panels`/`Replies`/`Emojis`; house style.
- **Taxas fixas v1:** curva `5n²+50n+100`; mensagem **15–25 XP**, cooldown **60s**.
- **Cargos em cascata:** UMA chamada `guild.modifyMemberRoles(...)`, nunca `addRoleToMember` em loop. UMA notificação (nível final).

**Símbolos confirmados:**
- `GuildConfig`: `toggle(key,def)`, `setting(key)`, `channel(key)`, `role(key)`. `GuildConfigEdits.withToggle/withSetting/withChannel(c,key,val)`.
- `ctx.database().sqlite()`, `ctx.database().guildConfig().findOrEmpty(id)`, `ctx.scheduler()`.
- Teste SQLite: `new SqliteManager(new BotConfig.Sqlite(dir.resolve("test.db").toString()))` + `new SqliteMigrator(sqlite).migrate()`.
- `Replies.ephemeral(event,ctx,msg)`, `Replies.reply(...)`; `Panels.container(accent, kids...)`, `Panels.text`, `Panels.divider`; `EmbedColor.resolve(cfg)`; `ComponentId.of(ns,action,args...)`, `id.arg(i)`.
- Comando: `SlashCommand { String name(); SlashCommandData data(); void execute(SlashCommandInteractionEvent, BotContext); }`.

---

## Task 1: `LevelFormula` (puro) + teste

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/leveling/LevelFormula.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/leveling/LevelFormulaTest.java`

**Interfaces — Produces:** `xpForLevel(int)`, `totalXpForLevel(int)`, `levelForXp(long)`, `progress(long)` → `Progress(int level, long into, long needed)`.

- [ ] **Step 1: Teste**
```java
package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LevelFormulaTest {

    @Test
    void xpForLevelUsesMee6Curve() {
        assertEquals(100, LevelFormula.xpForLevel(0));
        assertEquals(155, LevelFormula.xpForLevel(1));
        assertEquals(220, LevelFormula.xpForLevel(2));
    }

    @Test
    void totalXpAccumulates() {
        assertEquals(0, LevelFormula.totalXpForLevel(0));
        assertEquals(100, LevelFormula.totalXpForLevel(1));
        assertEquals(255, LevelFormula.totalXpForLevel(2));
        assertEquals(475, LevelFormula.totalXpForLevel(3));
    }

    @Test
    void levelForXpFindsHighestReached() {
        assertEquals(0, LevelFormula.levelForXp(0));
        assertEquals(0, LevelFormula.levelForXp(99));
        assertEquals(1, LevelFormula.levelForXp(100));
        assertEquals(1, LevelFormula.levelForXp(254));
        assertEquals(2, LevelFormula.levelForXp(255));
    }

    @Test
    void progressWithinLevel() {
        LevelFormula.Progress p = LevelFormula.progress(150);
        assertEquals(1, p.level());
        assertEquals(50, p.into());
        assertEquals(155, p.needed());
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.LevelFormulaTest"` → FAIL.
- [ ] **Step 3: Implementar**
```java
package dev.davimf.basebot.modules.base.leveling;

/** Curva de XP estilo MEE6. Puro/testável. XP p/ subir do nível n: 5n² + 50n + 100. */
public final class LevelFormula {

    private LevelFormula() {}

    public record Progress(int level, long into, long needed) {}

    public static long xpForLevel(int level) {
        long n = level;
        return 5 * n * n + 50 * n + 100;
    }

    public static long totalXpForLevel(int level) {
        long sum = 0;
        for (int n = 0; n < level; n++) {
            sum += xpForLevel(n);
        }
        return sum;
    }

    public static int levelForXp(long totalXp) {
        int level = 0;
        long acc = 0;
        while (acc + xpForLevel(level) <= totalXp) {
            acc += xpForLevel(level);
            level++;
        }
        return level;
    }

    public static Progress progress(long totalXp) {
        int level = levelForXp(totalXp);
        long base = totalXpForLevel(level);
        return new Progress(level, totalXp - base, xpForLevel(level));
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.LevelFormulaTest"` → PASS.

---

## Task 2: `LevelingConfig` (leitor puro) + teste

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/base/leveling/LevelingConfig.java`
- Test: `src/test/java/dev/davimf/basebot/modules/base/leveling/LevelingConfigTest.java`

**Interfaces — Produces:** `KEY_*`, `enabled/notifyMode/notifyChannelId/ignoredChannels`.

- [ ] **Step 1: Teste**
```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LevelingConfigTest {
    private static GuildConfig cfg(Map<String, String> settings, Map<String, Boolean> toggles, Map<String, String> channels) {
        return new GuildConfig("g1", null, null, channels, Map.of(), toggles, List.of(), settings);
    }

    @Test
    void defaults() {
        GuildConfig c = cfg(Map.of(), Map.of(), Map.of());
        assertFalse(LevelingConfig.enabled(c));
        assertEquals("current", LevelingConfig.notifyMode(c));
        assertNull(LevelingConfig.notifyChannelId(c));
        assertTrue(LevelingConfig.ignoredChannels(c).isEmpty());
    }

    @Test
    void readsValues() {
        GuildConfig c = cfg(Map.of(LevelingConfig.KEY_NOTIFY, "dm", LevelingConfig.KEY_IGNORED, "1, 2 ,3"),
                Map.of(LevelingConfig.KEY_ENABLED, true), Map.of(LevelingConfig.KEY_NOTIFY_CHANNEL, "999"));
        assertTrue(LevelingConfig.enabled(c));
        assertEquals("dm", LevelingConfig.notifyMode(c));
        assertEquals("999", LevelingConfig.notifyChannelId(c));
        assertEquals(java.util.Set.of("1", "2", "3"), LevelingConfig.ignoredChannels(c));
    }
}
```
> Confirmar a ordem dos parâmetros do construtor `GuildConfig` (ver `WelcomeConfigTest`/`LevelingConfigTest` do projeto — `(id, ?, ?, channels, roles, toggles, ?, settings)`); ajustar o helper `cfg(...)` conforme a assinatura real.
- [ ] **Step 2: Run** `./gradlew test --tests "*.LevelingConfigTest"` → FAIL.
- [ ] **Step 3: Implementar**
```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.model.GuildConfig;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Leitor puro da config de leveling (prefixo {@code level:}). */
public final class LevelingConfig {

    public static final String KEY_ENABLED = "level:enabled";
    public static final String KEY_NOTIFY = "level:notify";
    public static final String KEY_IGNORED = "level:ignored-channels";
    /** Chave em guild_config.channels do canal fixo de level-up. */
    public static final String KEY_NOTIFY_CHANNEL = "level-notify";

    private LevelingConfig() {}

    public static boolean enabled(GuildConfig cfg) { return cfg.toggle(KEY_ENABLED, false); }

    /** {@code current} (default), {@code channel}, {@code dm} ou {@code off}. */
    public static String notifyMode(GuildConfig cfg) {
        String v = cfg.setting(KEY_NOTIFY);
        return v == null || v.isBlank() ? "current" : v.trim();
    }

    public static String notifyChannelId(GuildConfig cfg) { return cfg.channel(KEY_NOTIFY_CHANNEL); }

    public static Set<String> ignoredChannels(GuildConfig cfg) {
        String raw = cfg.setting(KEY_IGNORED);
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.LevelingConfigTest"` → PASS.

---

## Task 3: Migração 027 + `UserLevelRepository` + `LevelRewardRepository` + testes

**Files:**
- Create: `src/main/resources/db/sqlite/027_leveling.sql`, `modules/base/leveling/UserLevelRepository.java`, `modules/base/leveling/LevelRewardRepository.java`
- Modify: `database/sqlite/SqliteMigrator.java`
- Test: `test/.../leveling/UserLevelRepositoryTest.java`, `test/.../leveling/LevelRewardRepositoryTest.java`

**Interfaces — Produces:** `UserLevelRepository{ long addXp(g,u,delta) /*→novo total*/, long xp(g,u), long lastMessageTs(g,u), void setLastMessageTs(g,u,ts), void setXp(g,u,xp), List<Entry> topPage(g,limit,offset), int rank(g,u), int count(g) }` com `record Entry(String userId, long xp)`; `LevelRewardRepository{ void put(g,level,roleId), void remove(g,level), java.util.Map<Integer,String> all(g) }`.

- [ ] **Step 1: Migração** `027_leveling.sql`
```sql
CREATE TABLE IF NOT EXISTS user_levels (
    guild_id        TEXT NOT NULL,
    user_id         TEXT NOT NULL,
    xp              INTEGER NOT NULL DEFAULT 0,
    last_message_ts INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, user_id)
);
CREATE TABLE IF NOT EXISTS level_rewards (
    guild_id TEXT NOT NULL,
    level    INTEGER NOT NULL,
    role_id  TEXT NOT NULL,
    PRIMARY KEY (guild_id, level)
);
CREATE INDEX IF NOT EXISTS idx_user_levels_top ON user_levels (guild_id, xp DESC);
```
- [ ] **Step 2:** Anexar a `SqliteMigrator.MIGRATIONS` (após `026_pix_keys_multi.sql`):
```java
            "/db/sqlite/026_pix_keys_multi.sql",
            "/db/sqlite/027_leveling.sql"
```
- [ ] **Step 3: Teste** `UserLevelRepositoryTest.java`
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

class UserLevelRepositoryTest {
    private SqliteManager sqlite;
    private UserLevelRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new UserLevelRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void addXpAccumulatesAndReturnsNewTotal() {
        assertEquals(20, repo.addXp("g1", "u1", 20));
        assertEquals(50, repo.addXp("g1", "u1", 30));
        assertEquals(50, repo.xp("g1", "u1"));
    }

    @Test
    void lastMessageTsRoundtrips() {
        repo.setLastMessageTs("g1", "u1", 1234L);
        assertEquals(1234L, repo.lastMessageTs("g1", "u1"));
    }

    @Test
    void setXpOverwrites() {
        repo.addXp("g1", "u1", 500);
        repo.setXp("g1", "u1", 10);
        assertEquals(10, repo.xp("g1", "u1"));
    }

    @Test
    void topAndRankOrderByXpDesc() {
        repo.addXp("g1", "a", 100);
        repo.addXp("g1", "b", 300);
        repo.addXp("g1", "c", 200);
        List<UserLevelRepository.Entry> top = repo.topPage("g1", 10, 0);
        assertEquals("b", top.get(0).userId());
        assertEquals("c", top.get(1).userId());
        assertEquals(1, repo.rank("g1", "b"));
        assertEquals(3, repo.rank("g1", "a"));
        assertEquals(3, repo.count("g1"));
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.UserLevelRepositoryTest"` → FAIL.
- [ ] **Step 5: Implementar** `UserLevelRepository.java`
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

/** SQLite store para XP por usuário (migração 027). */
public final class UserLevelRepository {

    public record Entry(String userId, long xp) {}

    private final SqliteManager sqlite;

    public UserLevelRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    /** Soma {@code delta} ao XP (upsert) e retorna o novo total. */
    public long addXp(String guildId, String userId, long delta) {
        String sql = "INSERT INTO user_levels (guild_id, user_id, xp) VALUES (?,?,?) "
                + "ON CONFLICT (guild_id, user_id) DO UPDATE SET xp = xp + excluded.xp";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setLong(3, delta);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("addXp " + guildId + "/" + userId, e);
        }
        return xp(guildId, userId);
    }

    public long xp(String guildId, String userId) {
        return longQuery("SELECT xp FROM user_levels WHERE guild_id=? AND user_id=?", guildId, userId);
    }

    public long lastMessageTs(String guildId, String userId) {
        return longQuery("SELECT last_message_ts FROM user_levels WHERE guild_id=? AND user_id=?", guildId, userId);
    }

    public void setLastMessageTs(String guildId, String userId, long ts) {
        String sql = "INSERT INTO user_levels (guild_id, user_id, last_message_ts) VALUES (?,?,?) "
                + "ON CONFLICT (guild_id, user_id) DO UPDATE SET last_message_ts = excluded.last_message_ts";
        exec(sql, ps -> { ps.setString(1, guildId); ps.setString(2, userId); ps.setLong(3, ts); });
    }

    public void setXp(String guildId, String userId, long xp) {
        String sql = "INSERT INTO user_levels (guild_id, user_id, xp) VALUES (?,?,?) "
                + "ON CONFLICT (guild_id, user_id) DO UPDATE SET xp = excluded.xp";
        exec(sql, ps -> { ps.setString(1, guildId); ps.setString(2, userId); ps.setLong(3, Math.max(0, xp)); });
    }

    public List<Entry> topPage(String guildId, int limit, int offset) {
        List<Entry> out = new ArrayList<>();
        String sql = "SELECT user_id, xp FROM user_levels WHERE guild_id=? ORDER BY xp DESC, user_id LIMIT ? OFFSET ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Entry(rs.getString("user_id"), rs.getLong("xp")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("topPage " + guildId, e);
        }
    }

    /** Posição 1-based do usuário no ranking por XP (0 se sem registro). */
    public int rank(String guildId, String userId) {
        long myXp = xp(guildId, userId);
        if (myXp <= 0 && count(guildId) == 0) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM user_levels WHERE guild_id=? AND xp > ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setLong(2, myXp);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) + 1 : 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("rank " + guildId + "/" + userId, e);
        }
    }

    public int count(String guildId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM user_levels WHERE guild_id=?")) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("count " + guildId, e);
        }
    }

    private long longQuery(String sql, String guildId, String userId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RepositoryException("query " + guildId + "/" + userId, e);
        }
    }

    private interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private void exec(String sql, Binder binder) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("exec", e);
        }
    }
}
```
- [ ] **Step 6: Run** `./gradlew test --tests "*.UserLevelRepositoryTest"` → PASS.
- [ ] **Step 7: Teste** `LevelRewardRepositoryTest.java`
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LevelRewardRepositoryTest {
    private SqliteManager sqlite;
    private LevelRewardRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new LevelRewardRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void putListRemove() {
        repo.put("g1", 5, "role5");
        repo.put("g1", 10, "role10");
        repo.put("g1", 5, "role5b");
        Map<Integer, String> all = repo.all("g1");
        assertEquals("role5b", all.get(5));
        assertEquals("role10", all.get(10));
        repo.remove("g1", 5);
        assertFalse(repo.all("g1").containsKey(5));
    }
}
```
- [ ] **Step 8: Run** `./gradlew test --tests "*.LevelRewardRepositoryTest"` → FAIL.
- [ ] **Step 9: Implementar** `LevelRewardRepository.java`
```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** SQLite store para cargos por nível (migração 027). */
public final class LevelRewardRepository {

    private final SqliteManager sqlite;

    public LevelRewardRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    public void put(String guildId, int level, String roleId) {
        String sql = "INSERT INTO level_rewards (guild_id, level, role_id) VALUES (?,?,?) "
                + "ON CONFLICT (guild_id, level) DO UPDATE SET role_id = excluded.role_id";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setInt(2, level);
            ps.setString(3, roleId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("put reward " + guildId + "/" + level, e);
        }
    }

    public void remove(String guildId, int level) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM level_rewards WHERE guild_id=? AND level=?")) {
            ps.setString(1, guildId);
            ps.setInt(2, level);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("remove reward " + guildId + "/" + level, e);
        }
    }

    /** Mapa nível→roleId ordenado por nível. */
    public Map<Integer, String> all(String guildId) {
        Map<Integer, String> out = new LinkedHashMap<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT level, role_id FROM level_rewards WHERE guild_id=? ORDER BY level")) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getInt("level"), rs.getString("role_id"));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("all rewards " + guildId, e);
        }
    }
}
```
- [ ] **Step 10: Run** `./gradlew test --tests "*.LevelRewardRepositoryTest"` → PASS.

---

## Task 4: `LevelRewards` (puro) + `RankData` + `LevelingService`

**Files:**
- Create: `modules/base/leveling/LevelRewards.java`, `modules/base/leveling/RankData.java`, `modules/base/leveling/LevelingService.java`
- Test: `test/.../leveling/LevelRewardsTest.java`

**Interfaces — Consumes:** `LevelFormula`, `LevelingConfig`, repos (Task 3). **Produces:** `LevelRewards.rolesForCrossedLevels(Map<Integer,String>, int from, int to)` → `List<String>`; `RankData(int level, long xpTotal, long into, long needed, int rank)`; `LevelingService{ void awardMessage(Guild, Member, MessageChannel), void award(Guild, Member, long, MessageChannel), RankData rank(String,String), UserLevelRepository users(), LevelRewardRepository rewards() }`.

- [ ] **Step 1: Teste** `LevelRewardsTest.java`
```java
package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LevelRewardsTest {
    @Test
    void collectsRolesForEveryLevelCrossed() {
        Map<Integer, String> rewards = Map.of(1, "r1", 3, "r3", 5, "r5");
        assertEquals(List.of("r1", "r3"), LevelRewards.rolesForCrossedLevels(rewards, 0, 4));
    }

    @Test
    void inclusiveOfDestinationLevel() {
        Map<Integer, String> rewards = Map.of(5, "r5");
        assertEquals(List.of("r5"), LevelRewards.rolesForCrossedLevels(rewards, 4, 5));
    }

    @Test
    void emptyWhenNoRewardInRange() {
        assertEquals(List.of(), LevelRewards.rolesForCrossedLevels(Map.of(10, "r10"), 0, 4));
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.LevelRewardsTest"` → FAIL.
- [ ] **Step 3: Implementar** `LevelRewards.java`
```java
package dev.davimf.basebot.modules.base.leveling;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Coleta os cargos de recompensa para todos os níveis cruzados num level-up (puro). */
public final class LevelRewards {

    private LevelRewards() {}

    /** Cargos de {@code rewards} para os níveis {@code (from, to]} (destino incluído). */
    public static List<String> rolesForCrossedLevels(Map<Integer, String> rewards, int from, int to) {
        List<String> out = new ArrayList<>();
        for (int level = from + 1; level <= to; level++) {
            String roleId = rewards.get(level);
            if (roleId != null && !roleId.isBlank()) {
                out.add(roleId);
            }
        }
        return out;
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.LevelRewardsTest"` → PASS.
- [ ] **Step 5: Implementar** `RankData.java`
```java
package dev.davimf.basebot.modules.base.leveling;

/** Dados de rank de um usuário — contrato reusável pelo painel V2 e por um renderer de imagem futuro. */
public record RankData(int level, long xpTotal, long into, long needed, int rank) {}
```
- [ ] **Step 6: Implementar** `LevelingService.java`
```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Núcleo do leveling: concede XP, detecta level-up, aplica cargos (1 chamada) e notifica. */
public final class LevelingService {

    private final BotContext ctx;
    private final UserLevelRepository users;
    private final LevelRewardRepository rewards;

    public LevelingService(BotContext ctx) {
        this.ctx = ctx;
        this.users = new UserLevelRepository(ctx.database().sqlite());
        this.rewards = new LevelRewardRepository(ctx.database().sqlite());
    }

    public UserLevelRepository users() { return users; }
    public LevelRewardRepository rewards() { return rewards; }

    /** Concede 15–25 XP de mensagem, com o canal atual como contexto de notificação. */
    public void awardMessage(Guild guild, Member member, MessageChannel current) {
        award(guild, member, ThreadLocalRandom.current().nextInt(15, 26), current);
    }

    /** Soma XP e, se houve level-up, aplica cargos (1 chamada) e notifica. */
    public void award(Guild guild, Member member, long amount, MessageChannel current) {
        if (amount <= 0) {
            return;
        }
        long novo = users.addXp(guild.getId(), member.getId(), amount);
        int antes = LevelFormula.levelForXp(novo - amount);
        int agora = LevelFormula.levelForXp(novo);
        if (agora > antes) {
            onLevelUp(guild, member, antes, agora, current);
        }
    }

    public RankData rank(String guildId, String userId) {
        long xp = users.xp(guildId, userId);
        LevelFormula.Progress p = LevelFormula.progress(xp);
        return new RankData(p.level(), xp, p.into(), p.needed(), users.rank(guildId, userId));
    }

    private void onLevelUp(Guild guild, Member member, int antes, int agora, MessageChannel current) {
        Map<Integer, String> map = rewards.all(guild.getId());
        List<String> roleIds = LevelRewards.rolesForCrossedLevels(map, antes, agora);
        List<Role> toAdd = new ArrayList<>();
        for (String id : roleIds) {
            Role r = guild.getRoleById(id);
            if (r != null && guild.getSelfMember().canInteract(r) && !member.getRoles().contains(r)) {
                toAdd.add(r);
            }
        }
        if (!toAdd.isEmpty()) {
            guild.modifyMemberRoles(member, toAdd, List.of()).reason("Recompensa de nível " + agora)
                    .queue(ok -> {}, err -> {});
        }
        notify(guild, member, agora, toAdd, current);
    }

    private void notify(Guild guild, Member member, int level, List<Role> gained, MessageChannel current) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        String mode = LevelingConfig.notifyMode(cfg);
        if ("off".equals(mode)) {
            return;
        }
        int accent = EmbedColor.resolve(cfg);
        String text = Emojis.of(Emojis.TROPHY, "🎉") + " " + member.getAsMention()
                + " subiu para o **nível " + level + "**!"
                + (gained.isEmpty() ? "" : "\n" + Emojis.of(Emojis.ROLES, "🏷️") + " Novo cargo: "
                        + gained.stream().map(Role::getAsMention).reduce((a, b) -> a + " " + b).orElse(""));
        var panel = Panels.container(accent, Panels.text(text));
        switch (mode) {
            case "dm" -> member.getUser().openPrivateChannel().queue(
                    pc -> pc.sendMessageComponents(panel).useComponentsV2().queue(ok -> {}, err -> {}),
                    err -> { /* CANNOT_SEND_TO_USER: engole */ });
            case "channel" -> {
                String chId = LevelingConfig.notifyChannelId(cfg);
                var ch = chId == null ? null : guild.getTextChannelById(chId);
                if (ch != null) {
                    ch.sendMessageComponents(panel).useComponentsV2()
                            .setAllowedMentions(List.of(net.dv8tion.jda.api.entities.Message.MentionType.USER))
                            .queue(ok -> {}, err -> {});
                }
            }
            default -> { // "current"
                if (current != null) {
                    current.sendMessageComponents(panel).useComponentsV2()
                            .setAllowedMentions(List.of(net.dv8tion.jda.api.entities.Message.MentionType.USER))
                            .queue(ok -> {}, err -> {});
                } else { // level-up de voz sem canal atual → DM
                    member.getUser().openPrivateChannel().queue(
                            pc -> pc.sendMessageComponents(panel).useComponentsV2().queue(ok -> {}, err -> {}),
                            err -> { });
                }
            }
        }
    }
}
```
> **Verificar no build:** `Emojis.TROPHY`/`Emojis.ROLES` (existem: `trophy`, `roles`); `MessageChannel` import (`net.dv8tion.jda.api.entities.channel.middleman.MessageChannel`); `guild.modifyMemberRoles(member, add, remove)` retorna `AuditableRestAction` (tem `.reason(...)`).
- [ ] **Step 7: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 5: `MessageXpListener`

**Files:**
- Create: `modules/base/leveling/MessageXpListener.java`

**Interfaces — Consumes:** `LevelingService`, `LevelingConfig`. Intent `GUILD_MESSAGES` (já habilitado — `MessageLoggingListener` usa).

- [ ] **Step 1: Implementar**
```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** XP por mensagem: 15–25 XP com cooldown de 60s por usuário (só quando level:enabled). */
public final class MessageXpListener extends ListenerAdapter {

    private static final long COOLDOWN_MS = 60_000L;

    private final BotContext ctx;
    private final LevelingService leveling;

    public MessageXpListener(BotContext ctx, LevelingService leveling) {
        this.ctx = ctx;
        this.leveling = leveling;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }
        Member member = event.getMember();
        if (member == null) {
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!LevelingConfig.enabled(cfg)
                || LevelingConfig.ignoredChannels(cfg).contains(event.getChannel().getId())) {
            return;
        }
        String guildId = event.getGuild().getId();
        String userId = member.getId();
        long now = System.currentTimeMillis();
        if (now - leveling.users().lastMessageTs(guildId, userId) < COOLDOWN_MS) {
            return;
        }
        leveling.users().setLastMessageTs(guildId, userId, now);
        leveling.awardMessage(event.getGuild(), member, event.getChannel());
    }
}
```
> **Verificar:** `event.getChannel()` é um `MessageChannelUnion` (subtipo de `MessageChannel`) — compatível com `awardMessage(..., MessageChannel)`. `event.isWebhookMessage()` existe.
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 6: `RankData` → `RankView` + `/rank`

**Files:**
- Create: `modules/base/leveling/RankView.java`, `modules/base/commands/RankCommand.java`

**Interfaces — Consumes:** `RankData`, `LevelingService`. **Produces:** `RankView.panel(accent, Member, RankData)`; comando `/rank`.

- [ ] **Step 1: Implementar** `RankView.java`
```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Member;

/** Cartão de rank em Components V2 (consome o mesmo RankData de um renderer de imagem futuro). */
public final class RankView {

    private static final int BAR = 12;

    private RankView() {}

    public static Container panel(int accent, Member member, RankData d) {
        long into = d.into();
        long needed = d.needed();
        int filled = needed <= 0 ? BAR : (int) Math.min(BAR, (into * BAR) / needed);
        String bar = "`[" + "█".repeat(filled) + "░".repeat(BAR - filled) + "]`";
        String rank = d.rank() > 0 ? "#" + d.rank() : "—";
        String body = "## " + Emojis.of(Emojis.RANK, "📊") + " Rank de " + member.getEffectiveName() + "\n"
                + Emojis.of(Emojis.STAR, "⭐") + " **Nível** · `" + d.level() + "`\n"
                + Emojis.of(Emojis.GROWTH, "📈") + " **Posição** · `" + rank + "`\n"
                + Emojis.of(Emojis.STATS, "🔢") + " **XP** · `" + into + "/" + needed + "` (total `" + d.xpTotal() + "`)\n"
                + bar;
        return Panels.container(accent, Panels.text(body));
    }
}
```
> **Verificar:** `Emojis.RANK`/`STAR`/`GROWTH`/`STATS` (existem: `rank`, `star`, `growth`, `stats`).
- [ ] **Step 2: Implementar** `RankCommand.java`
```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.leveling.LevelingService;
import dev.davimf.basebot.modules.base.leveling.RankData;
import dev.davimf.basebot.modules.base.leveling.RankView;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /rank — mostra o nível/XP do usuário (ou de outro membro). */
public final class RankCommand implements SlashCommand {

    private final LevelingService leveling;

    public RankCommand(LevelingService leveling) { this.leveling = leveling; }

    @Override
    public String name() { return "rank"; }

    @Override
    public SlashCommandData data() {
        return Commands.slash("rank", "Mostra seu nível e XP (ou de outro membro).")
                .addOption(OptionType.USER, "usuario", "Membro (opcional)", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        Member target = event.getOption("usuario", event.getMember(), OptionMapping::getAsMember);
        if (target == null) {
            Replies.ephemeral(event, ctx, "Esse usuário não está no servidor.");
            return;
        }
        RankData d = leveling.rank(event.getGuild().getId(), target.getId());
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));
        event.replyComponents(RankView.panel(accent, target, d)).useComponentsV2().queue();
    }
}
```
> **Verificar:** overload `event.getOption(name, fallback, resolver)` existe no JDA; se não, usar `OptionMapping m = event.getOption("usuario"); Member target = m == null ? event.getMember() : m.getAsMember();`.
- [ ] **Step 3: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 7: `/top` + `LevelingComponentHandler` (paginação)

**Files:**
- Create: `modules/base/commands/TopCommand.java`, `modules/base/leveling/LevelingComponentHandler.java`, `modules/base/leveling/TopView.java`

**Interfaces — Consumes:** `UserLevelRepository` (via `LevelingService.users()`), `LevelFormula`. **Produces:** `/top` + namespace `lvl` (ação `top` com arg página).

- [ ] **Step 1: Implementar** `TopView.java`
```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;

import java.util.ArrayList;
import java.util.List;

/** Leaderboard paginado (10/página). */
public final class TopView {

    public static final String NS = "lvl";
    public static final int PAGE = 10;

    private TopView() {}

    public static Container panel(int accent, List<UserLevelRepository.Entry> entries, int page, int total) {
        int pages = Math.max(1, (total + PAGE - 1) / PAGE);
        StringBuilder sb = new StringBuilder("## " + Emojis.of(Emojis.TROPHY, "🏆") + " Ranking de nível\n");
        if (entries.isEmpty()) {
            sb.append("-# Ninguém pontuou ainda.");
        } else {
            int base = page * PAGE;
            for (int i = 0; i < entries.size(); i++) {
                UserLevelRepository.Entry e = entries.get(i);
                int lvl = LevelFormula.levelForXp(e.xp());
                sb.append("\n`").append(base + i + 1).append(".` <@").append(e.userId())
                        .append("> · nível `").append(lvl).append("` · `").append(e.xp()).append(" XP`");
            }
        }
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text(sb.toString()));
        kids.add(Panels.divider());
        kids.add(Panels.text("-# Página " + (page + 1) + "/" + pages));
        if (pages > 1) {
            kids.add(ActionRow.of(
                    Button.secondary(ComponentId.of(NS, "top", String.valueOf(page - 1)), "◀")
                            .withDisabled(page <= 0),
                    Button.secondary(ComponentId.of(NS, "top", String.valueOf(page + 1)), "▶")
                            .withDisabled(page >= pages - 1)));
        }
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }
}
```
- [ ] **Step 2: Implementar** `TopCommand.java`
```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.leveling.LevelingService;
import dev.davimf.basebot.modules.base.leveling.TopView;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /top — ranking de nível do servidor (paginado). */
public final class TopCommand implements SlashCommand {

    private final LevelingService leveling;

    public TopCommand(LevelingService leveling) { this.leveling = leveling; }

    @Override
    public String name() { return "top"; }

    @Override
    public SlashCommandData data() {
        return Commands.slash("top", "Ranking de nível do servidor.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        String guildId = event.getGuild().getId();
        int total = leveling.users().count(guildId);
        var entries = leveling.users().topPage(guildId, TopView.PAGE, 0);
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
        event.replyComponents(TopView.panel(accent, entries, 0, total)).useComponentsV2().queue();
    }
}
```
- [ ] **Step 3: Implementar** `LevelingComponentHandler.java`
```java
package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/** Paginação do /top (namespace "lvl"). */
public final class LevelingComponentHandler implements ComponentHandler {

    private final LevelingService leveling;

    public LevelingComponentHandler(LevelingService leveling) { this.leveling = leveling; }

    @Override
    public String namespace() { return TopView.NS; }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"top".equals(id.action()) || event.getGuild() == null) {
            return;
        }
        int page = Math.max(0, parse(id.arg(0)));
        String guildId = event.getGuild().getId();
        int total = leveling.users().count(guildId);
        var entries = leveling.users().topPage(guildId, TopView.PAGE, page * TopView.PAGE);
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
        event.editComponents(TopView.panel(accent, entries, page, total)).useComponentsV2().queue();
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }
}
```
- [ ] **Step 4: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 8: `/xp` (admin)

**Files:**
- Create: `modules/base/commands/XpCommand.java`

**Interfaces — Consumes:** `LevelingService`. Gate `MANAGE_SERVER`.

- [ ] **Step 1: Implementar**
```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.leveling.LevelingService;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

/** /xp (admin) — add/remove/set/reset do XP de um usuário. */
public final class XpCommand implements SlashCommand {

    private final LevelingService leveling;

    public XpCommand(LevelingService leveling) { this.leveling = leveling; }

    @Override
    public String name() { return "xp"; }

    @Override
    public SlashCommandData data() {
        OptionData user = new OptionData(OptionType.USER, "usuario", "Membro", true);
        OptionData qtd = new OptionData(OptionType.INTEGER, "quantidade", "Quantidade de XP", true);
        return Commands.slash("xp", "Gerencia o XP de um membro (admin).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addSubcommands(
                        new SubcommandData("add", "Adiciona XP").addOptions(user, qtd),
                        new SubcommandData("remove", "Remove XP").addOptions(user, qtd),
                        new SubcommandData("set", "Define o XP").addOptions(user, qtd),
                        new SubcommandData("reset", "Zera o XP")
                                .addOptions(new OptionData(OptionType.USER, "usuario", "Membro", true)));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        var member = event.getOption("usuario", OptionMapping::getAsMember);
        User user = event.getOption("usuario", OptionMapping::getAsUser);
        if (user == null) {
            Replies.ephemeral(event, ctx, "Usuário inválido.");
            return;
        }
        String guildId = event.getGuild().getId();
        String userId = user.getId();
        String sub = event.getSubcommandName();
        long qtd = "reset".equals(sub) ? 0 : event.getOption("quantidade", 0L, OptionMapping::getAsLong);

        switch (sub == null ? "" : sub) {
            case "add" -> {
                if (member != null) {
                    leveling.award(event.getGuild(), member, qtd, null);
                } else {
                    leveling.users().addXp(guildId, userId, qtd);
                }
                Replies.reply(event, ctx, "Adicionado `" + qtd + "` XP a <@" + userId + ">.");
            }
            case "remove" -> {
                long novo = Math.max(0, leveling.users().xp(guildId, userId) - qtd);
                leveling.users().setXp(guildId, userId, novo);
                Replies.reply(event, ctx, "Removido `" + qtd + "` XP de <@" + userId + ">.");
            }
            case "set" -> {
                leveling.users().setXp(guildId, userId, qtd);
                Replies.reply(event, ctx, "XP de <@" + userId + "> definido para `" + qtd + "`.");
            }
            case "reset" -> {
                leveling.users().setXp(guildId, userId, 0);
                Replies.reply(event, ctx, "XP de <@" + userId + "> zerado.");
            }
            default -> Replies.ephemeral(event, ctx, "Subcomando inválido.");
        }
    }
}
```
> Import `OptionData` (`net.dv8tion.jda.api.interactions.commands.build.OptionData`). `event.getOption(name, default, resolver)` para `getAsLong` — se o overload não existir, ler `OptionMapping m = event.getOption("quantidade"); long qtd = m==null?0:m.getAsLong();`. **Nota de design:** `add` com um membro presente passa pelo `award` (dispara level-up/cargos/notificação em cascata — batching já garantido); `remove/set/reset` ajustam o total direto (sem remover cargos já ganhos — coerente com o stacking).
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 9: `/setup → Nível`

**Files:**
- Modify: `modules/base/setup/SetupView.java` (tela `levelingScreen` + `levelRewardModal`)
- Modify: `modules/base/setup/SetupComponentHandler.java` (nav + botões + selects + modal)

**Interfaces — Consumes:** `LevelingConfig`, `LevelRewardRepository`, helpers existentes `channelSelect`, `moduleNav`, `edit`, `config`, `saveChannel`, `firstChannelId`, `value`.

- [ ] **Step 1:** `SetupView` — importar `LevelingConfig`; adicionar `levelingScreen(GuildConfig cfg, java.util.Map<Integer,String> rewards)` e `levelRewardModal()` (padrão das outras telas: `## título` + divider + overview + selects + botões + `moduleNav("nivel")`). Toggle `nivel-toggle` (liga/desliga), select de modo de notificação (`StringSelectMenu` `nivel-notify` com opções current/channel/dm/off), `channelSelect("nivel-notifychan", LevelingConfig.KEY_NOTIFY_CHANNEL, ...)`, `channelSelect` multi para ignorados (`nivel-ignored`, usar `setRequiredRange(0, 25)`), botão `nivel-reward` (abre modal nível+cargo), e a lista de cargos-por-nível no corpo. O modal `nivel-rewardform` tem os campos `nivel` (SHORT) e `cargo` (SHORT — id ou menção). Botão de remover recompensa `nivel-rewarddel:<level>`.
- [ ] **Step 2:** `SetupView.moduleNav` — adicionar `addNav(menu, current, "Nível", "nivel");` (e replicar no `hub()` se ele não reusar `moduleNav`).
- [ ] **Step 3:** `SetupComponentHandler` — construir o repo `new LevelRewardRepository(ctx.database().sqlite())`; no switch de nav: `case "nivel" -> edit(event, SetupView.levelingScreen(config(ctx, guildId), rewards.all(guildId)));`. Botões: `niveltoggle` (inverte `LevelingConfig.KEY_ENABLED` via `GuildConfigEdits.withToggle`, re-render), `nivel-reward` (abre `levelRewardModal`), `nivel-rewarddel` (`rewards.remove(guildId, parseInt(id.arg(0)))`, re-render). Selects: `nivel-notify` (StringSelect → `withSetting(KEY_NOTIFY, value)`), `nivel-notifychan`/`nivel-ignored` (entity-select de canal → salva `KEY_NOTIFY_CHANNEL`/CSV de `KEY_IGNORED`). Modal `nivel-rewardform`: parse `nivel` (int) + `cargo` (extrair id da menção `<@&id>` ou id cru) → `rewards.put(guildId, level, roleId)`, re-render.
> **Padrões a seguir:** copiar a mecânica de `secedit`/`welctoggle`/`welcomechan` já existentes no handler (mesma forma de `edit`, `config`, `saveChannel`, `value`, `withToggle/withSetting`). Para o CSV de ignorados, ler os canais do entity-select (`event.getMentions().getChannels()`) e juntar por vírgula em `KEY_IGNORED`.
- [ ] **Step 4: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 10: Registro no `BaseModule` + build + smoke

**Files:**
- Modify: `modules/base/BaseModule.java`

- [ ] **Step 1:** No `register(...)`, criar o serviço e registrar comandos/listener/handler:
```java
        // Leveling (Base) — XP por mensagem, níveis, cargos e ranking.
        dev.davimf.basebot.modules.base.leveling.LevelingService leveling =
                new dev.davimf.basebot.modules.base.leveling.LevelingService(ctx);
        registry.listener(new dev.davimf.basebot.modules.base.leveling.MessageXpListener(ctx, leveling));
        registry.command(new dev.davimf.basebot.modules.base.commands.RankCommand(leveling));
        registry.command(new dev.davimf.basebot.modules.base.commands.TopCommand(leveling));
        registry.command(new dev.davimf.basebot.modules.base.commands.XpCommand(leveling));
        registry.component(new dev.davimf.basebot.modules.base.leveling.LevelingComponentHandler(leveling));
```
> Guardar `leveling` num campo se o Plano 2 (voz) precisar reusar a mesma instância no `onReady`.
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL (todos os testes verdes).
- [ ] **Step 3: Smoke (servidor de teste, `level:enabled` ligado em /setup → Nível):**
  - Mandar mensagens → ganha XP (respeitando 60s de cooldown); `/rank` mostra nível/XP/posição; `/top` pagina.
  - `/setup → Nível`: definir cargo no nível 2; `/xp add usuario:@você quantidade:300` → sobe de nível, ganha o cargo, notifica conforme o modo.
  - `/xp add ... quantidade:100000` → cascata: **um** conjunto de cargos aplicado numa chamada, **uma** notificação.
  - Trocar modo de notificação (canal/DM/off) e conferir o roteamento; canal ignorado não dá XP.

## Self-Review
- **Cobertura do spec (núcleo):** curva/fórmula (T1); config (T2); tabelas+repos (T3); level-up + cargos batched + notificação (T4); XP mensagem c/ cooldown + ignorados (T5); /rank c/ RankData (T6); /top paginado (T7); /xp admin (T8); /setup Nível (T9); registro (T10). Voz = Plano 2. ✓
- **Batching de cargos:** `onLevelUp` coleta via `LevelRewards.rolesForCrossedLevels` e chama `modifyMemberRoles` UMA vez. ✓
- **Consistência de tipos:** `LevelFormula.Progress`, `RankData(level,xpTotal,into,needed,rank)`, repos (`addXp→long`, `Entry(userId,xp)`, `all→Map`), `LevelingService.awardMessage/award/rank/users/rewards`, `TopView.NS/PAGE`. ✓
- **Pontos a confirmar no build (inline):** constantes `Emojis`; overloads `event.getOption(name,default,resolver)`; construtor de `GuildConfig` no teste de config; `MessageChannelUnion`↔`MessageChannel`.
