# Sorteios (giveaways) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (inline). Steps em checkbox. Depende de leveling (`voice_sessions`) e economy (já implementados).

**Goal:** Sorteios persistidos com painel de participação, requisitos configuráveis por sorteio (cargo, dias no servidor, horas em call, call em janela de horário), N ganhadores, prêmio texto + moedas, sorteio automático + encerrar/resortear (admin).

**Architecture:** Puros/testáveis (`VoiceWindow`, `GiveawayWindow`, `GiveawayDraw`, `GiveawayRequirements`) + adições ao `VoiceSessionRepository` + persistência (migração 030, `GiveawayRepository`) + `GiveawayService` (criar/participar/sortear/resortear/sweep) + view + comando + handler.

**Tech Stack:** Java 22, JDA 6.4.2 (Components V2), SQLite.

## Global Constraints

- **JDK 22.** **Sem commits.** Base ≠ facs. Admin gate = `MANAGE_SERVER`. Components V2 + `Emojis` + house style.
- **Migração 030** anexada a `SqliteMigrator.MIGRATIONS`.
- **Fuso fixo `America/Sao_Paulo`** para a janela. Requisitos são "E" (todos os ativos). Validados **na entrada**; no sorteio só descarta quem saiu.
- **`create` à prova de colisão** de id (gera 8 chars de UUID, retenta se já existir). **Reroll re-credita moedas com anúncio explícito.**

**Símbolos confirmados:** `Durations.parse(String)` → `OptionalLong` (ms); `EconomyService.wallets().addCash`; `EconomyConfig.enabled`; `member.getTimeJoined()` (`OffsetDateTime`); `ctx.scheduler().repeating`; `Replies.ephemeral`; teste SQLite `new SqliteManager(new BotConfig.Sqlite(path))`; `VoiceSessionRepository` (migração 028 já existe).

---

## Task 1: `VoiceSessionRepository` — `totalVoiceMs` + `sessionsOf` + teste

**Files:** Modify `modules/base/leveling/VoiceSessionRepository.java`; Test `test/.../leveling/VoiceSessionTotalsTest.java`.

**Produces:** `long totalVoiceMs(g,u)`; `List<long[]> sessionsOf(g,u)` (pares `[join, leaveOuAgora]`).

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

import static org.junit.jupiter.api.Assertions.*;

class VoiceSessionTotalsTest {
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
    void totalSumsClosedSessions() {
        long now = System.currentTimeMillis();
        repo.open("g1", "u1", "c1", now - 3_600_000L); // sessão aberta ~1h
        // fecha e reabre para simular duas sessões: usa closeOpen
        repo.closeOpen("g1", "u1", now - 1_800_000L);  // fechou: durou 30min
        repo.open("g1", "u1", "c1", now - 600_000L);   // aberta ~10min
        long total = repo.totalVoiceMs("g1", "u1");
        assertTrue(total >= 1_800_000L + 600_000L - 5_000L, "≈ 40min, foi " + total);
        assertEquals(2, repo.sessionsOf("g1", "u1").size());
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.VoiceSessionTotalsTest"` → FAIL.
- [ ] **Step 3: Implementar** — adicionar ao `VoiceSessionRepository`:
```java
    /** Tempo total em call (soma das sessões; abertas contam até agora). */
    public long totalVoiceMs(String guildId, String userId) {
        long now = System.currentTimeMillis();
        long total = 0;
        try (java.sql.Connection c = sqlite.getConnection();
             java.sql.PreparedStatement ps = c.prepareStatement(
                     "SELECT join_time, leave_time FROM voice_sessions WHERE guild_id=? AND user_id=?")) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long join = rs.getLong("join_time");
                    long leave = rs.getObject("leave_time") == null ? now : rs.getLong("leave_time");
                    if (leave > join) {
                        total += leave - join;
                    }
                }
            }
            return total;
        } catch (java.sql.SQLException e) {
            throw new dev.davimf.basebot.database.postgres.RepositoryException("total voice " + guildId + "/" + userId, e);
        }
    }

    /** Pares [join, leave] (leave = agora se aberta) de todas as sessões do usuário. */
    public java.util.List<long[]> sessionsOf(String guildId, String userId) {
        long now = System.currentTimeMillis();
        java.util.List<long[]> out = new java.util.ArrayList<>();
        try (java.sql.Connection c = sqlite.getConnection();
             java.sql.PreparedStatement ps = c.prepareStatement(
                     "SELECT join_time, leave_time FROM voice_sessions WHERE guild_id=? AND user_id=?")) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long join = rs.getLong("join_time");
                    long leave = rs.getObject("leave_time") == null ? now : rs.getLong("leave_time");
                    out.add(new long[]{join, leave});
                }
            }
            return out;
        } catch (java.sql.SQLException e) {
            throw new dev.davimf.basebot.database.postgres.RepositoryException("sessionsOf " + guildId + "/" + userId, e);
        }
    }
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.VoiceSessionTotalsTest"` → PASS.

---

## Task 2: `VoiceWindow` (puro) + teste

**Files:** Create `modules/base/giveaway/VoiceWindow.java`, `test/.../giveaway/VoiceWindowTest.java`.

**Produces:** `boolean overlapsDailyWindow(long joinMs, long leaveMs, int startHour, int endHour, ZoneId zone)`.

- [ ] **Step 1: Teste**
```java
package dev.davimf.basebot.modules.base.giveaway;

import org.junit.jupiter.api.Test;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import static org.junit.jupiter.api.Assertions.*;

class VoiceWindowTest {
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    private long at(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, SP).toInstant().toEpochMilli();
    }

    @Test
    void sessionInsideWindowOverlaps() {
        assertTrue(VoiceWindow.overlapsDailyWindow(at(2026, 6, 1, 20, 30), at(2026, 6, 1, 21, 0), 20, 23, SP));
    }

    @Test
    void sessionBeforeWindowDoesNotOverlap() {
        assertFalse(VoiceWindow.overlapsDailyWindow(at(2026, 6, 1, 18, 0), at(2026, 6, 1, 19, 0), 20, 23, SP));
    }

    @Test
    void sessionCrossingIntoWindowOverlaps() {
        assertTrue(VoiceWindow.overlapsDailyWindow(at(2026, 6, 1, 19, 0), at(2026, 6, 1, 20, 30), 20, 23, SP));
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.VoiceWindowTest"` → FAIL.
- [ ] **Step 3: Implementar**
```java
package dev.davimf.basebot.modules.base.giveaway;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Verifica se uma sessão de voz intersecta a faixa diária [startHour, endHour) no fuso dado. Puro. */
public final class VoiceWindow {

    private VoiceWindow() {}

    public static boolean overlapsDailyWindow(long joinMs, long leaveMs, int startHour, int endHour, ZoneId zone) {
        if (leaveMs <= joinMs || startHour >= endHour) {
            return false;
        }
        LocalDate first = java.time.Instant.ofEpochMilli(joinMs).atZone(zone).toLocalDate();
        LocalDate last = java.time.Instant.ofEpochMilli(leaveMs).atZone(zone).toLocalDate();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            long wStart = ZonedDateTime.of(d, java.time.LocalTime.of(startHour, 0), zone).toInstant().toEpochMilli();
            long wEnd = endHour >= 24
                    ? ZonedDateTime.of(d.plusDays(1), java.time.LocalTime.MIDNIGHT, zone).toInstant().toEpochMilli()
                    : ZonedDateTime.of(d, java.time.LocalTime.of(endHour, 0), zone).toInstant().toEpochMilli();
            if (joinMs < wEnd && leaveMs > wStart) {
                return true;
            }
        }
        return false;
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.VoiceWindowTest"` → PASS.

---

## Task 3: `GiveawayWindow.parse` + `GiveawayDraw.pick` (puros) + testes

**Files:** Create `modules/base/giveaway/GiveawayWindow.java`, `GiveawayDraw.java`; Test `GiveawayWindowParseTest`, `GiveawayDrawTest`.

- [ ] **Step 1: Testes**
```java
package dev.davimf.basebot.modules.base.giveaway;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GiveawayWindowParseTest {
    @Test
    void parsesValid() {
        assertArrayEquals(new int[]{20, 23}, GiveawayWindow.parse("20-23"));
        assertArrayEquals(new int[]{0, 24}, GiveawayWindow.parse(" 0 - 24 "));
    }

    @Test
    void rejectsInvalid() {
        assertNull(GiveawayWindow.parse("abc"));
        assertNull(GiveawayWindow.parse("23-20"));
        assertNull(GiveawayWindow.parse("25-30"));
        assertNull(GiveawayWindow.parse(null));
    }
}
```
```java
package dev.davimf.basebot.modules.base.giveaway;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class GiveawayDrawTest {
    @Test
    void picksDistinctWinners() {
        List<String> w = GiveawayDraw.pick(List.of("a", "b", "c", "d"), 2, new Random(1));
        assertEquals(2, w.size());
        assertEquals(2, w.stream().distinct().count());
        assertTrue(List.of("a", "b", "c", "d").containsAll(w));
    }

    @Test
    void capsAtEntrantCount() {
        assertEquals(3, GiveawayDraw.pick(List.of("a", "b", "c"), 10, new Random(1)).size());
        assertTrue(GiveawayDraw.pick(List.of(), 3, new Random(1)).isEmpty());
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.GiveawayWindowParseTest" --tests "*.GiveawayDrawTest"` → FAIL.
- [ ] **Step 3: Implementar**
```java
package dev.davimf.basebot.modules.base.giveaway;

/** Parse da janela de horário "HH-HH". Puro. */
public final class GiveawayWindow {
    private GiveawayWindow() {}

    public static int[] parse(String s) {
        if (s == null) {
            return null;
        }
        String[] parts = s.trim().split("-");
        if (parts.length != 2) {
            return null;
        }
        try {
            int a = Integer.parseInt(parts[0].trim());
            int b = Integer.parseInt(parts[1].trim());
            if (a < 0 || b > 24 || a >= b) {
                return null;
            }
            return new int[]{a, b};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```
```java
package dev.davimf.basebot.modules.base.giveaway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Sorteia N ganhadores distintos. Puro (Random injetável). */
public final class GiveawayDraw {
    private GiveawayDraw() {}

    public static List<String> pick(List<String> entrants, int n, Random r) {
        List<String> copy = new ArrayList<>(entrants);
        Collections.shuffle(copy, r);
        return new ArrayList<>(copy.subList(0, Math.min(Math.max(0, n), copy.size())));
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.GiveawayWindowParseTest" --tests "*.GiveawayDrawTest"` → PASS.

---

## Task 4: Migração 030 + `Giveaway` (record) + `GiveawayRepository` + teste

**Files:** Create `resources/db/sqlite/030_giveaways.sql`, `modules/base/giveaway/Giveaway.java`, `GiveawayRepository.java`; Modify `SqliteMigrator.java`; Test `GiveawayRepositoryTest`.

**Produces:** `Giveaway` (record); repo `create/find/setMessageId/setEnded/addEntry/entries/entryCount/dueActive`.

- [ ] **Step 1: Migração** `030_giveaways.sql`
```sql
CREATE TABLE IF NOT EXISTS giveaways (
    id                  TEXT PRIMARY KEY,
    guild_id            TEXT NOT NULL,
    channel_id          TEXT NOT NULL,
    message_id          TEXT,
    prize               TEXT NOT NULL,
    coin_reward         INTEGER NOT NULL DEFAULT 0,
    winners             INTEGER NOT NULL DEFAULT 1,
    ends_at             INTEGER NOT NULL,
    ended               INTEGER NOT NULL DEFAULT 0,
    req_role_id         TEXT,
    req_min_days        INTEGER NOT NULL DEFAULT 0,
    req_min_voice_hours INTEGER NOT NULL DEFAULT 0,
    req_window_start    INTEGER NOT NULL DEFAULT -1,
    req_window_end      INTEGER NOT NULL DEFAULT -1
);
CREATE TABLE IF NOT EXISTS giveaway_entries (
    giveaway_id TEXT NOT NULL,
    user_id     TEXT NOT NULL,
    PRIMARY KEY (giveaway_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_giveaways_active ON giveaways (ended, ends_at);
```
- [ ] **Step 2:** Anexar a `SqliteMigrator.MIGRATIONS` (após `029_economy.sql`): `"/db/sqlite/030_giveaways.sql"`.
- [ ] **Step 3:** `Giveaway.java`
```java
package dev.davimf.basebot.modules.base.giveaway;

/** Um sorteio persistido (migração 030). */
public record Giveaway(String id, String guildId, String channelId, String messageId, String prize,
                       long coinReward, int winners, long endsAt, boolean ended,
                       String reqRoleId, int reqMinDays, int reqMinVoiceHours,
                       int reqWindowStart, int reqWindowEnd) {

    public boolean hasWindow() { return reqWindowStart >= 0 && reqWindowEnd >= 0; }
}
```
- [ ] **Step 4: Teste** `GiveawayRepositoryTest.java`
```java
package dev.davimf.basebot.modules.base.giveaway;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GiveawayRepositoryTest {
    private SqliteManager sqlite;
    private GiveawayRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new GiveawayRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    private Giveaway draft(long endsAt) {
        return new Giveaway(null, "g1", "c1", null, "Nitro", 100, 2, endsAt, false,
                "role1", 3, 5, 20, 23);
    }

    @Test
    void createFindRoundtrips() {
        String id = repo.create(draft(9999L));
        Giveaway g = repo.find(id).orElseThrow();
        assertEquals("Nitro", g.prize());
        assertEquals(2, g.winners());
        assertEquals(100, g.coinReward());
        assertTrue(g.hasWindow());
        assertEquals(8, id.length());
    }

    @Test
    void entriesAreIdempotent() {
        String id = repo.create(draft(9999L));
        assertTrue(repo.addEntry(id, "u1"));
        assertFalse(repo.addEntry(id, "u1"));
        assertTrue(repo.addEntry(id, "u2"));
        assertEquals(2, repo.entryCount(id));
        assertEquals(2, repo.entries(id).size());
    }

    @Test
    void dueActiveAndSetEnded() {
        String id = repo.create(draft(1000L));
        repo.setMessageId(id, "m1");
        assertEquals(1, repo.dueActive(2000L).size());
        repo.setEnded(id);
        assertTrue(repo.dueActive(2000L).isEmpty());
        assertEquals("m1", repo.find(id).orElseThrow().messageId());
    }
}
```
- [ ] **Step 5: Run** `./gradlew test --tests "*.GiveawayRepositoryTest"` → FAIL.
- [ ] **Step 6: Implementar** `GiveawayRepository.java`
```java
package dev.davimf.basebot.modules.base.giveaway;

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

/** SQLite store dos sorteios (migração 030). */
public final class GiveawayRepository {

    private final SqliteManager sqlite;

    public GiveawayRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    /** Cria o sorteio com um id gerado; retenta se colidir. Retorna o id. */
    public String create(Giveaway g) {
        String sql = "INSERT INTO giveaways (id, guild_id, channel_id, prize, coin_reward, winners, ends_at, "
                + "req_role_id, req_min_days, req_min_voice_hours, req_window_start, req_window_end) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        for (int attempt = 0; attempt < 5; attempt++) {
            String id = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (find(id).isPresent()) {
                continue;
            }
            try (Connection c = sqlite.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, id);
                ps.setString(2, g.guildId());
                ps.setString(3, g.channelId());
                ps.setString(4, g.prize());
                ps.setLong(5, g.coinReward());
                ps.setInt(6, g.winners());
                ps.setLong(7, g.endsAt());
                ps.setString(8, g.reqRoleId());
                ps.setInt(9, g.reqMinDays());
                ps.setInt(10, g.reqMinVoiceHours());
                ps.setInt(11, g.reqWindowStart());
                ps.setInt(12, g.reqWindowEnd());
                ps.executeUpdate();
                return id;
            } catch (SQLException e) {
                if (attempt == 4) {
                    throw new RepositoryException("create giveaway " + g.guildId(), e);
                }
            }
        }
        throw new RepositoryException("create giveaway: não gerou id único", null);
    }

    public Optional<Giveaway> find(String id) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM giveaways WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("find giveaway " + id, e);
        }
    }

    public void setMessageId(String id, String messageId) {
        exec("UPDATE giveaways SET message_id=? WHERE id=?", ps -> { ps.setString(1, messageId); ps.setString(2, id); });
    }

    public void setEnded(String id) {
        exec("UPDATE giveaways SET ended=1 WHERE id=?", ps -> ps.setString(1, id));
    }

    /** true se a entrada é nova (INSERT OR IGNORE). */
    public boolean addEntry(String giveawayId, String userId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR IGNORE INTO giveaway_entries (giveaway_id, user_id) VALUES (?,?)")) {
            ps.setString(1, giveawayId);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("add entry " + giveawayId, e);
        }
    }

    public List<String> entries(String giveawayId) {
        List<String> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT user_id FROM giveaway_entries WHERE giveaway_id=?")) {
            ps.setString(1, giveawayId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("entries " + giveawayId, e);
        }
    }

    public int entryCount(String giveawayId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM giveaway_entries WHERE giveaway_id=?")) {
            ps.setString(1, giveawayId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("entryCount " + giveawayId, e);
        }
    }

    public List<Giveaway> dueActive(long now) {
        List<Giveaway> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM giveaways WHERE ended=0 AND ends_at<=?")) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("dueActive", e);
        }
    }

    private static Giveaway map(ResultSet rs) throws SQLException {
        return new Giveaway(rs.getString("id"), rs.getString("guild_id"), rs.getString("channel_id"),
                rs.getString("message_id"), rs.getString("prize"), rs.getLong("coin_reward"),
                rs.getInt("winners"), rs.getLong("ends_at"), rs.getInt("ended") == 1,
                rs.getString("req_role_id"), rs.getInt("req_min_days"), rs.getInt("req_min_voice_hours"),
                rs.getInt("req_window_start"), rs.getInt("req_window_end"));
    }

    private interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private void exec(String sql, Binder b) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            b.bind(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("exec giveaway", e);
        }
    }
}
```
- [ ] **Step 7: Run** `./gradlew test --tests "*.GiveawayRepositoryTest"` → PASS.

---

## Task 5: `GiveawayRequirements` (puro) + teste

**Files:** Create `modules/base/giveaway/GiveawayRequirements.java`, `test/.../giveaway/GiveawayRequirementsTest.java`.

**Produces:** `String firstUnmet(Giveaway g, boolean hasRole, long joinedEpochMs, long totalVoiceMs, boolean windowOk, long now)` — `null` se ok, senão o motivo.

- [ ] **Step 1: Teste**
```java
package dev.davimf.basebot.modules.base.giveaway;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GiveawayRequirementsTest {
    private Giveaway g(String role, int days, int hours, int ws, int we) {
        return new Giveaway("id", "g", "c", null, "p", 0, 1, 0, false, role, days, hours, ws, we);
    }

    @Test
    void allInactivePasses() {
        assertNull(GiveawayRequirements.firstUnmet(g(null, 0, 0, -1, -1), false, 0, 0, false, 1000));
    }

    @Test
    void roleUnmet() {
        assertNotNull(GiveawayRequirements.firstUnmet(g("r1", 0, 0, -1, -1), false, 0, 0, false, 1000));
        assertNull(GiveawayRequirements.firstUnmet(g("r1", 0, 0, -1, -1), true, 0, 0, false, 1000));
    }

    @Test
    void daysUnmet() {
        long now = 10L * 86_400_000L;
        assertNotNull(GiveawayRequirements.firstUnmet(g(null, 5, 0, -1, -1), true, now - 2L * 86_400_000L, 0, false, now));
        assertNull(GiveawayRequirements.firstUnmet(g(null, 5, 0, -1, -1), true, now - 6L * 86_400_000L, 0, false, now));
    }

    @Test
    void voiceHoursUnmet() {
        assertNotNull(GiveawayRequirements.firstUnmet(g(null, 0, 5, -1, -1), true, 0, 4L * 3_600_000L, false, 1000));
        assertNull(GiveawayRequirements.firstUnmet(g(null, 0, 5, -1, -1), true, 0, 5L * 3_600_000L, false, 1000));
    }

    @Test
    void windowUnmet() {
        assertNotNull(GiveawayRequirements.firstUnmet(g(null, 0, 0, 20, 23), true, 0, 0, false, 1000));
        assertNull(GiveawayRequirements.firstUnmet(g(null, 0, 0, 20, 23), true, 0, 0, true, 1000));
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.GiveawayRequirementsTest"` → FAIL.
- [ ] **Step 3: Implementar**
```java
package dev.davimf.basebot.modules.base.giveaway;

/** Elegibilidade de participação: devolve o 1º requisito ativo não cumprido, ou null se ok. Puro. */
public final class GiveawayRequirements {

    private GiveawayRequirements() {}

    public static String firstUnmet(Giveaway g, boolean hasRole, long joinedEpochMs, long totalVoiceMs,
                                    boolean windowOk, long now) {
        if (g.reqRoleId() != null && !g.reqRoleId().isBlank() && !hasRole) {
            return "Você precisa do cargo <@&" + g.reqRoleId() + "> para participar.";
        }
        if (g.reqMinDays() > 0 && (now - joinedEpochMs) < g.reqMinDays() * 86_400_000L) {
            return "Você precisa estar há pelo menos **" + g.reqMinDays() + " dia(s)** no servidor.";
        }
        if (g.reqMinVoiceHours() > 0 && totalVoiceMs < g.reqMinVoiceHours() * 3_600_000L) {
            return "Você precisa de pelo menos **" + g.reqMinVoiceHours() + "h** em call.";
        }
        if (g.hasWindow() && !windowOk) {
            return "Você precisa ter ficado em call entre **" + g.reqWindowStart() + "h e " + g.reqWindowEnd() + "h**.";
        }
        return null;
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.GiveawayRequirementsTest"` → PASS.

---

## Task 6: `GiveawayView`

**Files:** Create `modules/base/giveaway/GiveawayView.java`.

**Produces:** `NS = "gwy"`; `panel(accent, Giveaway, entryCount)`; `ended(accent, Giveaway, List<String> winnerMentions)`.

- [ ] **Step 1: Implementar**
```java
package dev.davimf.basebot.modules.base.giveaway;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;

import java.util.ArrayList;
import java.util.List;

/** Render dos painéis de sorteio (namespace "gwy"). */
public final class GiveawayView {

    public static final String NS = "gwy";

    private GiveawayView() {}

    public static Container panel(int accent, Giveaway g, int entryCount) {
        StringBuilder sb = new StringBuilder("## " + Emojis.of(Emojis.GIFT, "🎉") + " Sorteio: " + g.prize() + "\n");
        sb.append(Emojis.of(Emojis.CLOCK, "⏰")).append(" Encerra <t:").append(g.endsAt() / 1000).append(":R>\n");
        sb.append(Emojis.of(Emojis.MEMBERS, "👥")).append(" Participantes: `").append(entryCount).append("`\n");
        sb.append(Emojis.of(Emojis.TROPHY, "🏆")).append(" Ganhadores: `").append(g.winners()).append("`");
        if (g.coinReward() > 0) {
            sb.append(" · ").append(Emojis.of(Emojis.MONEY, "💰")).append(" `").append(g.coinReward()).append("` moedas");
        }
        List<String> reqs = requirements(g);
        if (!reqs.isEmpty()) {
            sb.append("\n---\n").append(Emojis.of(Emojis.INFO, "ℹ️")).append(" **Requisitos:**\n");
            for (String r : reqs) {
                sb.append("• ").append(r).append("\n");
            }
        }
        sb.append("\n-# id: `").append(g.id()).append("`");
        return Panels.container(accent,
                Panels.text(sb.toString()),
                Panels.divider(),
                ActionRow.of(Button.success(ComponentId.of(NS, "enter", g.id()), "Participar")
                        .withEmoji(Emojis.button(Emojis.GIFT))));
    }

    public static Container ended(int accent, Giveaway g, List<String> winnerMentions) {
        String winners = winnerMentions.isEmpty() ? "*sem ganhadores*" : String.join(", ", winnerMentions);
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.TROPHY, "🏆") + " Sorteio encerrado: " + g.prize()),
                Panels.divider(),
                Panels.text(Emojis.of(Emojis.TROPHY, "🏆") + " **Ganhador(es):** " + winners
                        + (g.coinReward() > 0 ? "\n" + Emojis.of(Emojis.MONEY, "💰") + " **+" + g.coinReward()
                        + "** moedas para cada um." : "")));
    }

    private static List<String> requirements(Giveaway g) {
        List<String> r = new ArrayList<>();
        if (g.reqRoleId() != null && !g.reqRoleId().isBlank()) {
            r.add("Ter o cargo <@&" + g.reqRoleId() + ">");
        }
        if (g.reqMinDays() > 0) {
            r.add("Estar há " + g.reqMinDays() + "+ dia(s) no servidor");
        }
        if (g.reqMinVoiceHours() > 0) {
            r.add("Ter " + g.reqMinVoiceHours() + "h+ em call");
        }
        if (g.hasWindow()) {
            r.add("Ter ficado em call entre " + g.reqWindowStart() + "h e " + g.reqWindowEnd() + "h");
        }
        return r;
    }
}
```
> **Verificar:** `Emojis.INFO`/`MEMBERS`/`CLOCK`/`GIFT`/`TROPHY`/`MONEY` (existem). `ChannelLog`/house style: `---` vira divisória só no `ChannelLog`; aqui é texto normal — trocar o `---` por uma nova linha simples se ficar estranho (é só um painel V2, sem auto-split).
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 7: `GiveawayService`

**Files:** Create `modules/base/giveaway/GiveawayService.java`.

**Consumes:** repos + economy + `VoiceSessionRepository` + puros. **Produces:** `create(...)`, `enter(ButtonInteractionEvent, id)`, `endNow(id)`, `reroll(id)`, `sweep()`.

- [ ] **Step 1: Implementar**
```java
package dev.davimf.basebot.modules.base.giveaway;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.EconomyService;
import dev.davimf.basebot.modules.base.leveling.VoiceSessionRepository;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Coordenador dos sorteios: criar, participar, sortear, resortear e sweep de encerramento. */
public final class GiveawayService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final BotContext ctx;
    private final EconomyService economy;
    private final GiveawayRepository repo;
    private final VoiceSessionRepository voice;

    public GiveawayService(BotContext ctx, EconomyService economy) {
        this.ctx = ctx;
        this.economy = economy;
        this.repo = new GiveawayRepository(ctx.database().sqlite());
        this.voice = new VoiceSessionRepository(ctx.database().sqlite());
    }

    private GuildConfig cfg(Guild g) { return ctx.database().guildConfig().findOrEmpty(g.getId()); }

    public GiveawayRepository repo() { return repo; }

    // --- criação ---------------------------------------------------------------

    public String create(Guild guild, TextChannel channel, String prize, long durationMs, int winners,
                         long coins, String roleId, int minDays, int minHours, int[] window) {
        long endsAt = System.currentTimeMillis() + durationMs;
        Giveaway draft = new Giveaway(null, guild.getId(), channel.getId(), null, prize, Math.max(0, coins),
                Math.max(1, winners), endsAt, false, roleId, Math.max(0, minDays), Math.max(0, minHours),
                window == null ? -1 : window[0], window == null ? -1 : window[1]);
        String id = repo.create(draft);
        Giveaway g = repo.find(id).orElseThrow();
        int accent = EmbedColor.resolve(cfg(guild));
        channel.sendMessageComponents(GiveawayView.panel(accent, g, 0)).useComponentsV2()
                .queue(sent -> repo.setMessageId(id, sent.getId()), err -> { });
        return id;
    }

    // --- participação ----------------------------------------------------------

    public void enter(ButtonInteractionEvent event, String giveawayId) {
        if (event.getGuild() == null || event.getMember() == null) {
            return;
        }
        Optional<Giveaway> maybe = repo.find(giveawayId);
        if (maybe.isEmpty() || maybe.get().ended()) {
            Replies.ephemeral(event, ctx, "Esse sorteio já encerrou.");
            return;
        }
        Giveaway g = maybe.get();
        Guild guild = event.getGuild();
        Member m = event.getMember();
        boolean hasRole = g.reqRoleId() == null || g.reqRoleId().isBlank()
                || m.getRoles().stream().anyMatch(r -> r.getId().equals(g.reqRoleId()));
        long joined = m.getTimeJoined().toInstant().toEpochMilli();
        long totalVoice = g.reqMinVoiceHours() > 0 ? voice.totalVoiceMs(guild.getId(), m.getId()) : 0;
        boolean windowOk = !g.hasWindow() || windowOk(guild.getId(), m.getId(), g);
        String reason = GiveawayRequirements.firstUnmet(g, hasRole, joined, totalVoice, windowOk,
                System.currentTimeMillis());
        if (reason != null) {
            Replies.ephemeral(event, ctx, reason);
            return;
        }
        boolean added = repo.addEntry(giveawayId, m.getId());
        Replies.ephemeral(event, ctx, added ? "Inscrito no sorteio! Boa sorte." : "Você já está participando.");
        if (added) {
            refreshPanel(guild, g);
        }
    }

    private boolean windowOk(String guildId, String userId, Giveaway g) {
        for (long[] s : voice.sessionsOf(guildId, userId)) {
            if (VoiceWindow.overlapsDailyWindow(s[0], s[1], g.reqWindowStart(), g.reqWindowEnd(), ZONE)) {
                return true;
            }
        }
        return false;
    }

    private void refreshPanel(Guild guild, Giveaway g) {
        if (g.messageId() == null) {
            return;
        }
        TextChannel ch = guild.getTextChannelById(g.channelId());
        if (ch != null) {
            ch.editMessageComponentsById(g.messageId(),
                            GiveawayView.panel(EmbedColor.resolve(cfg(guild)), g, repo.entryCount(g.id())))
                    .useComponentsV2().queue(ok -> { }, err -> { });
        }
    }

    // --- sorteio / sweep / reroll ----------------------------------------------

    public void sweep() {
        if (ctx.jda() == null) {
            return;
        }
        for (Giveaway g : repo.dueActive(System.currentTimeMillis())) {
            draw(g, false);
        }
    }

    public String endNow(String id) {
        Optional<Giveaway> g = repo.find(id);
        if (g.isEmpty() || g.get().ended()) {
            return "Sorteio não encontrado ou já encerrado.";
        }
        draw(g.get(), false);
        return "Sorteio encerrado.";
    }

    public String reroll(String id) {
        Optional<Giveaway> g = repo.find(id);
        if (g.isEmpty() || !g.get().ended()) {
            return "Sorteio não encontrado ou ainda em andamento.";
        }
        draw(g.get(), true);
        return "Novo sorteio realizado.";
    }

    private void draw(Giveaway g, boolean reroll) {
        Guild guild = ctx.jda().getGuildById(g.guildId());
        if (guild == null) {
            repo.setEnded(g.id());
            return;
        }
        List<String> valid = new ArrayList<>();
        for (String uid : repo.entries(g.id())) {
            if (guild.getMemberById(uid) != null) {
                valid.add(uid);
            }
        }
        List<String> winners = GiveawayDraw.pick(valid, g.winners(), new Random());
        if (g.coinReward() > 0 && EconomyConfig.enabled(cfg(guild))) {
            for (String w : winners) {
                economy.wallets().addCash(guild.getId(), w, g.coinReward());
            }
        }
        if (!reroll) {
            repo.setEnded(g.id());
        }
        announce(guild, g, winners, reroll);
    }

    private void announce(Guild guild, Giveaway g, List<String> winners, boolean reroll) {
        TextChannel ch = guild.getTextChannelById(g.channelId());
        if (ch == null) {
            return;
        }
        int accent = EmbedColor.resolve(cfg(guild));
        List<String> mentions = winners.stream().map(w -> "<@" + w + ">").toList();
        if (!reroll && g.messageId() != null) {
            ch.editMessageComponentsById(g.messageId(), GiveawayView.ended(accent, g, mentions))
                    .useComponentsV2().queue(ok -> { }, err -> { });
        }
        String head = reroll ? "🔁 Novo ganhador sorteado! Prêmio entregue." : "🎉 Sorteio encerrado!";
        String body = mentions.isEmpty() ? "Não houve participantes válidos." : "Parabéns " + String.join(", ", mentions)
                + "! Vocês ganharam **" + g.prize() + "**"
                + (g.coinReward() > 0 && EconomyConfig.enabled(cfg(guild)) ? " + **" + g.coinReward() + "** moedas" : "") + ".";
        ch.sendMessageComponents(dev.davimf.basebot.core.component.Panels.container(accent,
                        dev.davimf.basebot.core.component.Panels.text("## " + head),
                        dev.davimf.basebot.core.component.Panels.divider(),
                        dev.davimf.basebot.core.component.Panels.text(body)))
                .useComponentsV2()
                .setAllowedMentions(java.util.List.of(net.dv8tion.jda.api.entities.Message.MentionType.USER))
                .queue(ok -> { }, err -> { });
    }
}
```
> **Nota:** `enter` recomputa `windowOk` só se o sorteio tem janela; `totalVoice` só se há requisito de horas (evita queries à toa). `member.getTimeJoined()` exige `GUILD_MEMBERS` (habilitado). Reroll **não** re-marca `ended` (já estava) e credita de novo com anúncio explícito.
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 8: `/sorteio` (comando)

**Files:** Create `modules/base/commands/SorteioCommand.java`.

**Consumes:** `GiveawayService`, `Durations`, `GiveawayWindow`. Gate `MANAGE_SERVER`.

- [ ] **Step 1: Implementar**
```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.giveaway.GiveawayService;
import dev.davimf.basebot.modules.base.giveaway.GiveawayWindow;
import dev.davimf.basebot.util.Durations;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.OptionalLong;

/** /sorteio criar|encerrar|resortear (admin). */
public final class SorteioCommand implements SlashCommand {

    private final GiveawayService service;

    public SorteioCommand(GiveawayService service) { this.service = service; }

    @Override public String name() { return "sorteio"; }

    @Override public SlashCommandData data() {
        SubcommandData criar = new SubcommandData("criar", "Cria um sorteio")
                .addOptions(new OptionData(OptionType.STRING, "premio", "O que será sorteado", true))
                .addOptions(new OptionData(OptionType.STRING, "duracao", "Duração (ex.: 2h, 1d, 30m)", true))
                .addOptions(new OptionData(OptionType.INTEGER, "ganhadores", "Quantos ganhadores (default 1)", false))
                .addOptions(new OptionData(OptionType.INTEGER, "moedas", "Moedas por ganhador (opcional)", false))
                .addOptions(new OptionData(OptionType.ROLE, "cargo", "Requisito: ter este cargo", false))
                .addOptions(new OptionData(OptionType.INTEGER, "dias_servidor", "Requisito: dias no servidor", false))
                .addOptions(new OptionData(OptionType.INTEGER, "horas_call", "Requisito: horas em call", false))
                .addOptions(new OptionData(OptionType.STRING, "janela", "Requisito: call na faixa (ex.: 20-23)", false));
        SubcommandData encerrar = new SubcommandData("encerrar", "Encerra um sorteio agora")
                .addOptions(new OptionData(OptionType.STRING, "id", "ID do sorteio", true));
        SubcommandData resortear = new SubcommandData("resortear", "Sorteia novos ganhadores")
                .addOptions(new OptionData(OptionType.STRING, "id", "ID do sorteio", true));
        return Commands.slash("sorteio", "Sorteios do servidor.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addSubcommands(criar, encerrar, resortear);
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        switch (event.getSubcommandName() == null ? "" : event.getSubcommandName()) {
            case "criar" -> criar(event, ctx);
            case "encerrar" -> Replies.ephemeral(event, ctx, service.endNow(strOpt(event, "id")));
            case "resortear" -> Replies.ephemeral(event, ctx, service.reroll(strOpt(event, "id")));
            default -> Replies.ephemeral(event, ctx, "Subcomando inválido.");
        }
    }

    private void criar(SlashCommandInteractionEvent event, BotContext ctx) {
        if (!(event.getChannel() instanceof TextChannel channel)) {
            Replies.ephemeral(event, ctx, "Use num canal de texto.");
            return;
        }
        OptionalLong dur = Durations.parse(strOpt(event, "duracao"));
        if (dur.isEmpty()) {
            Replies.ephemeral(event, ctx, "Duração inválida. Ex.: `2h`, `1d`, `30m`.");
            return;
        }
        String janelaRaw = strOpt(event, "janela");
        int[] window = null;
        if (janelaRaw != null && !janelaRaw.isBlank()) {
            window = GiveawayWindow.parse(janelaRaw);
            if (window == null) {
                Replies.ephemeral(event, ctx, "Janela inválida. Ex.: `20-23` (0 a 24).");
                return;
            }
        }
        Role cargo = event.getOption("cargo", OptionMapping::getAsRole);
        int ganhadores = (int) longOpt(event, "ganhadores", 1);
        long moedas = longOpt(event, "moedas", 0);
        int dias = (int) longOpt(event, "dias_servidor", 0);
        int horas = (int) longOpt(event, "horas_call", 0);
        String id = service.create(event.getGuild(), channel, strOpt(event, "premio"), dur.getAsLong(),
                ganhadores, moedas, cargo == null ? null : cargo.getId(), dias, horas, window);
        Replies.ephemeral(event, ctx, "Sorteio criado! ID: `" + id + "`.");
    }

    private static String strOpt(SlashCommandInteractionEvent event, String name) {
        OptionMapping o = event.getOption(name);
        return o == null ? null : o.getAsString();
    }

    private static long longOpt(SlashCommandInteractionEvent event, String name, long def) {
        OptionMapping o = event.getOption(name);
        return o == null ? def : o.getAsLong();
    }
}
```
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 9: `GiveawayComponentHandler`

**Files:** Create `modules/base/giveaway/GiveawayComponentHandler.java`.

- [ ] **Step 1: Implementar**
```java
package dev.davimf.basebot.modules.base.giveaway;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/** Botão "Participar" dos sorteios (namespace "gwy"). */
public final class GiveawayComponentHandler implements ComponentHandler {

    private final GiveawayService service;

    public GiveawayComponentHandler(GiveawayService service) { this.service = service; }

    @Override
    public String namespace() { return GiveawayView.NS; }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if ("enter".equals(id.action())) {
            service.enter(event, id.arg(0));
        }
    }
}
```
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 10: Registro no `BaseModule` + sweep + build + smoke

**Files:** Modify `modules/base/BaseModule.java`.

- [ ] **Step 1:** Campo:
```java
    private dev.davimf.basebot.modules.base.giveaway.GiveawayService giveaways;
```
- [ ] **Step 2:** No `register(...)`, após a economia (usa a variável `economy`):
```java
        // Sorteios (Base) — usa voice_sessions + economia.
        this.giveaways = new dev.davimf.basebot.modules.base.giveaway.GiveawayService(ctx, economy);
        registry.command(new dev.davimf.basebot.modules.base.commands.SorteioCommand(giveaways));
        registry.component(new dev.davimf.basebot.modules.base.giveaway.GiveawayComponentHandler(giveaways));
```
- [ ] **Step 3:** No `onReady(...)`:
```java
        // Sorteios: encerra os vencidos a cada 30s (à prova de restart).
        if (giveaways != null) {
            ctx.scheduler().repeating(giveaways::sweep, 30, 30, TimeUnit.SECONDS);
        }
```
- [ ] **Step 4: Run** `./gradlew build` → BUILD SUCCESSFUL (todos os testes verdes).
- [ ] **Step 5: Smoke (servidor de teste):**
  - `/sorteio criar premio:"Nitro" duracao:2m ganhadores:1` → painel com botão Participar; clicar → "inscrito", contagem sobe.
  - Requisitos: criar com `cargo`/`dias_servidor`/`horas_call`/`janela:20-23` e tentar participar sem cumprir → recusa com o motivo; cumprindo → inscreve.
  - Esperar 2 min → sweep sorteia, anuncia ganhador, credita moedas (se houver), edita o painel.
  - `/sorteio resortear id:<id>` → "🔁 Novo ganhador sorteado! Prêmio entregue." + credita de novo. `/sorteio encerrar id:<id>` antes do tempo → encerra na hora.

## Self-Review
- **Cobertura do spec:** voice totals (T1); janela (T2); parse+draw (T3); persistência+id colisão (T4); requisitos (T5); painel (T6); serviço criar/participar/sortear/reroll/sweep (T7); comando (T8); botão (T9); registro+sweep (T10). ✓
- **Reroll re-credita c/ anúncio explícito** (T7 `announce` head "🔁 Novo ganhador…"). **Validação na entrada; descarta quem saiu no sorteio** (T7 `draw`). **Fuso fixo** (`ZONE` em T7). ✓
- **Consistência:** `Giveaway(...)` (14 campos), `GiveawayRepository.create/find/addEntry/entries/entryCount/dueActive/setEnded/setMessageId`, `GiveawayRequirements.firstUnmet`, `GiveawayDraw.pick`, `VoiceWindow.overlapsDailyWindow`, `GiveawayWindow.parse`, `VoiceSessionRepository.totalVoiceMs/sessionsOf`. ✓
- **Pontos a confirmar no build (inline):** `Emojis.INFO/MEMBERS`; `event.getOption("cargo", OptionMapping::getAsRole)`; `member.getTimeJoined()`; `editMessageComponentsById`.
