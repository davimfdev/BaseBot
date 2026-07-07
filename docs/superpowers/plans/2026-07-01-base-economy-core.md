# Economia — Plano 1 (Núcleo: carteira/banco, ganhos, roubo, transferência, ranking, admin, setup)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (inline). Steps em checkbox.

**Goal:** Economia por-usuário separada da facção: saldos carteira/banco, ganhos (`/daily /trabalhar /crime`), roubo (`/roubar`), transferência (`/pagar`), banco (`/depositar /sacar`), `/saldo`, `/rico`, `/eco` (admin) e `/setup → Economia`. (Loja = Plano 2.)

**Architecture:** Pacote `modules/base/economy/`. Puro/testável: `EconomyConfig`, `EconomyFormat`, `EconomyDefaults`, `CrimeOutcome`, `RobOutcome`. Persistência **atômica** (`WalletRepository`, `CooldownRepository`, migração 029). `EconomyService` orquestra e devolve mensagens prontas. Comandos finos + tela de setup.

**Tech Stack:** Java 22, JDA 6.4.2 (Components V2), SQLite.

## Global Constraints

- **JDK 22.** **Sem commits** (cada task termina em `./gradlew build`/test).
- **Base ≠ facs:** sem imports de facs; admin gate = `MANAGE_SERVER`. Economia por-usuário é **separada** do tesouro de facção (`fac_finance`).
- **Migração 029** anexada a `SqliteMigrator.MIGRATIONS`; sem comentário inline após `;`.
- **⚠️ Dedução atômica:** nunca `SELECT`+`UPDATE`; o lock vai no `UPDATE … WHERE … AND cash >= ?`, checando `executeUpdate()==0`. Transferência = transação de 2 etapas (debita pagador atômico → credita recebedor com upsert).
- **Anti-poluição:** `/saldo` e `/rico` **efêmeros**; ações usam `Replies.reply` (temporário). Cooldown via `<t:{epoch}:R>`.
- **Moeda:** inteiro; nome/emoji configuráveis (default 🪙 "moedas"). Configurável no v1: moeda + daily + trabalhar; crime/roubo = `EconomyDefaults`.

**Símbolos confirmados:** `GuildConfig.toggle/setting`, `GuildConfigEdits.withToggle/withSetting`; `Replies.ephemeral/reply(event,ctx,msg)`; `Panels.container/text/divider`; `ComponentId.of/arg`; `EmbedColor.resolve`; `Emojis.of/MONEY`; teste SQLite `new SqliteManager(new BotConfig.Sqlite(path))`; comando `SlashCommand{name,data,execute}`; setup helpers `moduleNav/addNav/hub option/edit/config/value`.

---

## Task 1: `EconomyConfig` (leitor puro) + teste

**Files:** Create `modules/base/economy/EconomyConfig.java`, `test/.../economy/EconomyConfigTest.java`.

**Produces:** `enabled`, `currencyName`, `currencyEmoji`, `daily`, `workMin`, `workMax`, `workCooldownSeconds` + `KEY_*`.

- [ ] **Step 1: Teste**
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EconomyConfigTest {
    private static GuildConfig cfg(Map<String, String> settings, Map<String, Boolean> toggles) {
        return new GuildConfig("g1", null, null, Map.of(), Map.of(), toggles, List.of(), settings);
    }

    @Test
    void defaults() {
        GuildConfig c = cfg(Map.of(), Map.of());
        assertFalse(EconomyConfig.enabled(c));
        assertEquals("moedas", EconomyConfig.currencyName(c));
        assertEquals(500, EconomyConfig.daily(c));
        assertEquals(50, EconomyConfig.workMin(c));
        assertEquals(250, EconomyConfig.workMax(c));
        assertEquals(3600, EconomyConfig.workCooldownSeconds(c));
    }

    @Test
    void readsValues() {
        GuildConfig c = cfg(Map.of(EconomyConfig.KEY_CURRENCY_NAME, "dols", EconomyConfig.KEY_DAILY, "1000",
                EconomyConfig.KEY_WORK_MIN, "10", EconomyConfig.KEY_WORK_MAX, "20",
                EconomyConfig.KEY_WORK_COOLDOWN, "120"), Map.of(EconomyConfig.KEY_ENABLED, true));
        assertTrue(EconomyConfig.enabled(c));
        assertEquals("dols", EconomyConfig.currencyName(c));
        assertEquals(1000, EconomyConfig.daily(c));
        assertEquals(10, EconomyConfig.workMin(c));
        assertEquals(20, EconomyConfig.workMax(c));
        assertEquals(120, EconomyConfig.workCooldownSeconds(c));
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.EconomyConfigTest"` → FAIL.
- [ ] **Step 3: Implementar**
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.Emojis;

/** Leitor puro da config de economia (prefixo {@code eco:}). */
public final class EconomyConfig {

    public static final String KEY_ENABLED = "eco:enabled";
    public static final String KEY_CURRENCY_NAME = "eco:currency-name";
    public static final String KEY_CURRENCY_EMOJI = "eco:currency-emoji";
    public static final String KEY_DAILY = "eco:daily";
    public static final String KEY_WORK_MIN = "eco:work-min";
    public static final String KEY_WORK_MAX = "eco:work-max";
    public static final String KEY_WORK_COOLDOWN = "eco:work-cooldown";

    private EconomyConfig() {}

    public static boolean enabled(GuildConfig cfg) { return cfg.toggle(KEY_ENABLED, false); }

    public static String currencyName(GuildConfig cfg) {
        String v = cfg.setting(KEY_CURRENCY_NAME);
        return v == null || v.isBlank() ? "moedas" : v.trim();
    }

    public static String currencyEmoji(GuildConfig cfg) {
        String v = cfg.setting(KEY_CURRENCY_EMOJI);
        return v == null || v.isBlank() ? Emojis.of(Emojis.MONEY, "🪙") : v.trim();
    }

    public static long daily(GuildConfig cfg) { return longOr(cfg.setting(KEY_DAILY), 500); }
    public static long workMin(GuildConfig cfg) { return longOr(cfg.setting(KEY_WORK_MIN), 50); }
    public static long workMax(GuildConfig cfg) { return longOr(cfg.setting(KEY_WORK_MAX), 250); }
    public static long workCooldownSeconds(GuildConfig cfg) { return longOr(cfg.setting(KEY_WORK_COOLDOWN), 3600); }

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
- [ ] **Step 4: Run** `./gradlew test --tests "*.EconomyConfigTest"` → PASS.

---

## Task 2: `EconomyFormat` (puro) + teste

**Files:** Create `modules/base/economy/EconomyFormat.java`, `test/.../economy/EconomyFormatTest.java`.

**Produces:** `format(long, GuildConfig)` → `{emoji} {1.234}`; `formatNamed(long, GuildConfig)` → `{emoji} {1.234} {nome}`.

- [ ] **Step 1: Teste**
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EconomyFormatTest {
    private static GuildConfig cfg(Map<String, String> settings) {
        return new GuildConfig("g1", null, null, Map.of(), Map.of(), Map.of(), List.of(), settings);
    }

    @Test
    void groupsThousandsBr() {
        GuildConfig c = cfg(Map.of(EconomyConfig.KEY_CURRENCY_EMOJI, "$"));
        assertEquals("$ 1.234", EconomyFormat.format(1234, c));
        assertEquals("$ 0", EconomyFormat.format(0, c));
    }

    @Test
    void namedAppendsCurrencyName() {
        GuildConfig c = cfg(Map.of(EconomyConfig.KEY_CURRENCY_EMOJI, "$", EconomyConfig.KEY_CURRENCY_NAME, "dols"));
        assertEquals("$ 1.234 dols", EconomyFormat.formatNamed(1234, c));
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.EconomyFormatTest"` → FAIL.
- [ ] **Step 3: Implementar**
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.model.GuildConfig;

/** Formata quantias da economia: emoji + número agrupado (BR) + nome opcional. Puro. */
public final class EconomyFormat {

    private EconomyFormat() {}

    public static String format(long amount, GuildConfig cfg) {
        return EconomyConfig.currencyEmoji(cfg) + " " + grouped(amount);
    }

    public static String formatNamed(long amount, GuildConfig cfg) {
        return format(amount, cfg) + " " + EconomyConfig.currencyName(cfg);
    }

    private static String grouped(long amount) {
        boolean neg = amount < 0;
        String digits = String.format("%,d", Math.abs(amount)).replace(',', '.');
        return (neg ? "-" : "") + digits;
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.EconomyFormatTest"` → PASS.

---

## Task 3: Migração 029 + `WalletRepository` + `CooldownRepository` + testes

**Files:** Create `resources/db/sqlite/029_economy.sql`, `modules/base/economy/WalletRepository.java`, `modules/base/economy/CooldownRepository.java`; Modify `SqliteMigrator.java`; Test `WalletRepositoryTest`, `CooldownRepositoryTest`.

**Produces:** `WalletRepository` (`record Wallet(long cash, long bank)`, `record Entry(String userId, long total)`; `get/addCash/addBank/setCash/setBank/tryDebitCash/deposit/withdraw/transfer/topPage/rank/count`); `CooldownRepository` (`lastTs/stamp`).

- [ ] **Step 1: Migração** `029_economy.sql`
```sql
CREATE TABLE IF NOT EXISTS user_wallets (
    guild_id TEXT NOT NULL,
    user_id  TEXT NOT NULL,
    cash     INTEGER NOT NULL DEFAULT 0,
    bank     INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, user_id)
);
CREATE TABLE IF NOT EXISTS eco_cooldowns (
    guild_id TEXT NOT NULL,
    user_id  TEXT NOT NULL,
    action   TEXT NOT NULL,
    last_ts  INTEGER NOT NULL,
    PRIMARY KEY (guild_id, user_id, action)
);
```
- [ ] **Step 2:** Anexar a `SqliteMigrator.MIGRATIONS` (após `028_voice_sessions.sql`): `"/db/sqlite/029_economy.sql"`.
- [ ] **Step 3: Teste** `WalletRepositoryTest.java`
```java
package dev.davimf.basebot.modules.base.economy;

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

class WalletRepositoryTest {
    private SqliteManager sqlite;
    private WalletRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new WalletRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void addCashUpsertsAndGet() {
        repo.addCash("g1", "u1", 100);
        assertEquals(100, repo.get("g1", "u1").cash());
        assertEquals(0, repo.get("g1", "u1").bank());
    }

    @Test
    void depositMovesCashToBankAtomically() {
        repo.addCash("g1", "u1", 100);
        assertTrue(repo.deposit("g1", "u1", 60));
        assertEquals(40, repo.get("g1", "u1").cash());
        assertEquals(60, repo.get("g1", "u1").bank());
        assertFalse(repo.deposit("g1", "u1", 999));
        assertEquals(40, repo.get("g1", "u1").cash());
    }

    @Test
    void withdrawMovesBankToCashAtomically() {
        repo.addCash("g1", "u1", 100);
        repo.deposit("g1", "u1", 100);
        assertTrue(repo.withdraw("g1", "u1", 30));
        assertEquals(30, repo.get("g1", "u1").cash());
        assertEquals(70, repo.get("g1", "u1").bank());
        assertFalse(repo.withdraw("g1", "u1", 999));
    }

    @Test
    void transferDebitsPayerAndCreatesRecipientRow() {
        repo.addCash("g1", "payer", 100);
        assertTrue(repo.transfer("g1", "payer", "newbie", 40));
        assertEquals(60, repo.get("g1", "payer").cash());
        assertEquals(40, repo.get("g1", "newbie").cash());
    }

    @Test
    void transferFailsWithoutDebitWhenInsufficient() {
        repo.addCash("g1", "payer", 10);
        assertFalse(repo.transfer("g1", "payer", "other", 40));
        assertEquals(10, repo.get("g1", "payer").cash());
        assertEquals(0, repo.get("g1", "other").cash());
    }

    @Test
    void tryDebitCashFloorsAtBalance() {
        repo.addCash("g1", "u1", 30);
        assertFalse(repo.tryDebitCash("g1", "u1", 40));
        assertEquals(30, repo.get("g1", "u1").cash());
        assertTrue(repo.tryDebitCash("g1", "u1", 20));
        assertEquals(10, repo.get("g1", "u1").cash());
    }

    @Test
    void topAndRankByTotal() {
        repo.addCash("g1", "a", 100);
        repo.addCash("g1", "b", 50); repo.addBank("g1", "b", 300);
        repo.addCash("g1", "c", 200);
        List<WalletRepository.Entry> top = repo.topPage("g1", 10, 0);
        assertEquals("b", top.get(0).userId());
        assertEquals(350, top.get(0).total());
        assertEquals("c", top.get(1).userId());
        assertEquals(1, repo.rank("g1", "b"));
        assertEquals(3, repo.count("g1"));
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.WalletRepositoryTest"` → FAIL.
- [ ] **Step 5: Implementar** `WalletRepository.java`
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** SQLite store da economia por-usuário (migração 029). Deduções atômicas via WHERE (anti double-spend). */
public final class WalletRepository {

    public record Wallet(long cash, long bank) {}
    public record Entry(String userId, long total) {}

    private final SqliteManager sqlite;

    public WalletRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    public Wallet get(String g, String u) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT cash, bank FROM user_wallets WHERE guild_id=? AND user_id=?")) {
            ps.setString(1, g);
            ps.setString(2, u);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Wallet(rs.getLong(1), rs.getLong(2)) : new Wallet(0, 0);
            }
        } catch (SQLException e) {
            throw new RepositoryException("get wallet " + g + "/" + u, e);
        }
    }

    /** Credita a carteira (upsert). Para injetar dinheiro (daily/work/crime-win/admin). */
    public void addCash(String g, String u, long delta) {
        upsert(g, u, "cash", delta);
    }

    public void addBank(String g, String u, long delta) {
        upsert(g, u, "bank", delta);
    }

    private void upsert(String g, String u, String col, long delta) {
        String sql = "INSERT INTO user_wallets (guild_id, user_id, " + col + ") VALUES (?,?,?) "
                + "ON CONFLICT (guild_id, user_id) DO UPDATE SET " + col + " = " + col + " + excluded." + col;
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, g);
            ps.setString(2, u);
            ps.setLong(3, delta);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("add " + col + " " + g + "/" + u, e);
        }
    }

    public void setCash(String g, String u, long value) { set(g, u, "cash", Math.max(0, value)); }
    public void setBank(String g, String u, long value) { set(g, u, "bank", Math.max(0, value)); }

    private void set(String g, String u, String col, long value) {
        String sql = "INSERT INTO user_wallets (guild_id, user_id, " + col + ") VALUES (?,?,?) "
                + "ON CONFLICT (guild_id, user_id) DO UPDATE SET " + col + " = excluded." + col;
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, g);
            ps.setString(2, u);
            ps.setLong(3, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("set " + col + " " + g + "/" + u, e);
        }
    }

    /** Debita a carteira só se houver saldo (atômico). */
    public boolean tryDebitCash(String g, String u, long amount) {
        if (amount <= 0) {
            return true;
        }
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE user_wallets SET cash = cash - ? WHERE guild_id=? AND user_id=? AND cash >= ?")) {
            ps.setLong(1, amount);
            ps.setString(2, g);
            ps.setString(3, u);
            ps.setLong(4, amount);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("debit cash " + g + "/" + u, e);
        }
    }

    public boolean deposit(String g, String u, long amount) { return move(g, u, "cash", "bank", amount); }
    public boolean withdraw(String g, String u, long amount) { return move(g, u, "bank", "cash", amount); }

    private boolean move(String g, String u, String from, String to, long amount) {
        if (amount <= 0) {
            return false;
        }
        String sql = "UPDATE user_wallets SET " + from + " = " + from + " - ?, " + to + " = " + to + " + ? "
                + "WHERE guild_id=? AND user_id=? AND " + from + " >= ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, amount);
            ps.setLong(2, amount);
            ps.setString(3, g);
            ps.setString(4, u);
            ps.setLong(5, amount);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("move " + from + "->" + to + " " + g + "/" + u, e);
        }
    }

    /** Transfere carteira→carteira: debita o pagador (atômico) e credita o recebedor (upsert), numa transação. */
    public boolean transfer(String g, String fromU, String toU, long amount) {
        if (amount <= 0) {
            return false;
        }
        try (Connection c = sqlite.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                int debited;
                try (PreparedStatement deb = c.prepareStatement(
                        "UPDATE user_wallets SET cash = cash - ? WHERE guild_id=? AND user_id=? AND cash >= ?")) {
                    deb.setLong(1, amount);
                    deb.setString(2, g);
                    deb.setString(3, fromU);
                    deb.setLong(4, amount);
                    debited = deb.executeUpdate();
                }
                if (debited == 0) {
                    c.rollback();
                    return false;
                }
                try (PreparedStatement cred = c.prepareStatement(
                        "INSERT INTO user_wallets (guild_id, user_id, cash) VALUES (?,?,?) "
                        + "ON CONFLICT (guild_id, user_id) DO UPDATE SET cash = cash + excluded.cash")) {
                    cred.setString(1, g);
                    cred.setString(2, toU);
                    cred.setLong(3, amount);
                    cred.executeUpdate();
                }
                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            throw new RepositoryException("transfer " + g + " " + fromU + "->" + toU, e);
        }
    }

    public List<Entry> topPage(String g, int limit, int offset) {
        List<Entry> out = new ArrayList<>();
        String sql = "SELECT user_id, (cash + bank) AS total FROM user_wallets WHERE guild_id=? "
                + "ORDER BY total DESC, user_id LIMIT ? OFFSET ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, g);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Entry(rs.getString("user_id"), rs.getLong("total")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("topPage " + g, e);
        }
    }

    public int rank(String g, String u) {
        Wallet w = get(g, u);
        long total = w.cash() + w.bank();
        if (total <= 0 && count(g) == 0) {
            return 0;
        }
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM user_wallets WHERE guild_id=? AND (cash + bank) > ?")) {
            ps.setString(1, g);
            ps.setLong(2, total);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) + 1 : 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("rank " + g + "/" + u, e);
        }
    }

    public int count(String g) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM user_wallets WHERE guild_id=?")) {
            ps.setString(1, g);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("count " + g, e);
        }
    }
}
```
- [ ] **Step 6: Run** `./gradlew test --tests "*.WalletRepositoryTest"` → PASS.
- [ ] **Step 7: Teste** `CooldownRepositoryTest.java`
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CooldownRepositoryTest {
    private SqliteManager sqlite;
    private CooldownRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new CooldownRepository(sqlite);
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void stampThenReadRoundtrips() {
        assertEquals(0, repo.lastTs("g1", "u1", "daily"));
        repo.stamp("g1", "u1", "daily", 5000L);
        assertEquals(5000L, repo.lastTs("g1", "u1", "daily"));
        repo.stamp("g1", "u1", "daily", 9000L);
        assertEquals(9000L, repo.lastTs("g1", "u1", "daily"));
        assertEquals(0, repo.lastTs("g1", "u1", "work"));
    }
}
```
- [ ] **Step 8: Run** `./gradlew test --tests "*.CooldownRepositoryTest"` → FAIL.
- [ ] **Step 9: Implementar** `CooldownRepository.java`
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Cooldowns por (guild,user,action) para /daily /trabalhar /crime /roubar (migração 029). */
public final class CooldownRepository {

    private final SqliteManager sqlite;

    public CooldownRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    public long lastTs(String g, String u, String action) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT last_ts FROM eco_cooldowns WHERE guild_id=? AND user_id=? AND action=?")) {
            ps.setString(1, g);
            ps.setString(2, u);
            ps.setString(3, action);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RepositoryException("cooldown get " + g + "/" + u + "/" + action, e);
        }
    }

    public void stamp(String g, String u, String action, long ts) {
        String sql = "INSERT INTO eco_cooldowns (guild_id, user_id, action, last_ts) VALUES (?,?,?,?) "
                + "ON CONFLICT (guild_id, user_id, action) DO UPDATE SET last_ts = excluded.last_ts";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, g);
            ps.setString(2, u);
            ps.setString(3, action);
            ps.setLong(4, ts);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("cooldown stamp " + g + "/" + u + "/" + action, e);
        }
    }
}
```
- [ ] **Step 10: Run** `./gradlew test --tests "*.CooldownRepositoryTest"` → PASS.

---

## Task 4: `EconomyDefaults` + `CrimeOutcome` + `RobOutcome` (puros) + testes

**Files:** Create `modules/base/economy/EconomyDefaults.java`, `CrimeOutcome.java`, `RobOutcome.java`; Test `CrimeOutcomeTest`, `RobOutcomeTest`.

**Produces:** constantes v1; `CrimeOutcome.resolve(int rollPct, int successPct, long win, long fine)` → `record CrimeOutcome(boolean success, long delta)`; `RobOutcome.resolve(int rollPct, int successPct, long targetCash, int stealPct, long fine)` → `record RobOutcome(boolean success, long stolen, long fine)`.

- [ ] **Step 1: Testes**
```java
package dev.davimf.basebot.modules.base.economy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrimeOutcomeTest {
    @Test
    void successWhenRollBelowChance() {
        CrimeOutcome o = CrimeOutcome.resolve(10, 50, 300, 100);
        assertTrue(o.success());
        assertEquals(300, o.delta());
    }

    @Test
    void failureAppliesFineAsNegative() {
        CrimeOutcome o = CrimeOutcome.resolve(80, 50, 300, 100);
        assertFalse(o.success());
        assertEquals(-100, o.delta());
    }
}
```
```java
package dev.davimf.basebot.modules.base.economy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RobOutcomeTest {
    @Test
    void successStealsPercentOfTargetCash() {
        RobOutcome o = RobOutcome.resolve(10, 40, 1000, 20, 100);
        assertTrue(o.success());
        assertEquals(200, o.stolen());
        assertEquals(0, o.fine());
    }

    @Test
    void successStealsAtLeastOneWhenTargetTiny() {
        RobOutcome o = RobOutcome.resolve(0, 40, 3, 20, 100);
        assertTrue(o.success());
        assertEquals(1, o.stolen());
    }

    @Test
    void failureAppliesFineNoSteal() {
        RobOutcome o = RobOutcome.resolve(90, 40, 1000, 20, 100);
        assertFalse(o.success());
        assertEquals(0, o.stolen());
        assertEquals(100, o.fine());
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.CrimeOutcomeTest" --tests "*.RobOutcomeTest"` → FAIL.
- [ ] **Step 3: Implementar**
```java
package dev.davimf.basebot.modules.base.economy;

/** Constantes v1 da economia (crime/roubo fixos; defaults de config em EconomyConfig). */
public final class EconomyDefaults {
    private EconomyDefaults() {}

    public static final long DAILY_COOLDOWN_S = 86_400;

    public static final long CRIME_COOLDOWN_S = 3_600;
    public static final int CRIME_SUCCESS_PCT = 50;
    public static final long CRIME_WIN_MIN = 100, CRIME_WIN_MAX = 500;
    public static final long CRIME_FINE_MIN = 50, CRIME_FINE_MAX = 250;

    public static final long ROB_COOLDOWN_S = 7_200;
    public static final int ROB_SUCCESS_PCT = 40;
    public static final int ROB_STEAL_MIN_PCT = 10, ROB_STEAL_MAX_PCT = 30;
    public static final long ROB_FINE_MIN = 50, ROB_FINE_MAX = 200;
    public static final long ROB_TARGET_MIN_CASH = 100;
}
```
```java
package dev.davimf.basebot.modules.base.economy;

/** Resultado puro de /crime dado um roll (0–99). {@code delta} soma na carteira (negativo = multa). */
public record CrimeOutcome(boolean success, long delta) {
    public static CrimeOutcome resolve(int rollPct, int successPct, long win, long fine) {
        return rollPct < successPct ? new CrimeOutcome(true, win) : new CrimeOutcome(false, -fine);
    }
}
```
```java
package dev.davimf.basebot.modules.base.economy;

/** Resultado puro de /roubar dado um roll (0–99). Sucesso rouba % da carteira do alvo (mín. 1). */
public record RobOutcome(boolean success, long stolen, long fine) {
    public static RobOutcome resolve(int rollPct, int successPct, long targetCash, int stealPct, long fine) {
        if (rollPct < successPct) {
            long stolen = Math.max(1, targetCash * stealPct / 100);
            return new RobOutcome(true, stolen, 0);
        }
        return new RobOutcome(false, 0, fine);
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.CrimeOutcomeTest" --tests "*.RobOutcomeTest"` → PASS.

---

## Task 5: `EconomyService`

**Files:** Create `modules/base/economy/EconomyService.java`.

**Consumes:** repos + config + resolvers. **Produces:** métodos que devolvem `String` (mensagem pronta, formatada com a moeda), usados pelos comandos. Assinaturas: `daily/work/crime(Guild,Member)`, `rob(Guild,Member actor,Member target)`, `pay(Guild,Member actor,Member target,long)`, `deposit/withdraw(Guild,Member,long)`, `WalletRepository wallets()`.

- [ ] **Step 1: Implementar**
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.util.concurrent.ThreadLocalRandom;

/** Núcleo da economia: ganhos, roubo, transferência, banco. Devolve mensagens já formatadas. */
public final class EconomyService {

    private static final String[] WORK_MSGS = {
            "Você trabalhou como entregador", "Você fez um bico", "Você lavou carros",
            "Você vendeu doces", "Você fez um freela"
    };

    private final BotContext ctx;
    private final WalletRepository wallets;
    private final CooldownRepository cooldowns;

    public EconomyService(BotContext ctx) {
        this.ctx = ctx;
        this.wallets = new WalletRepository(ctx.database().sqlite());
        this.cooldowns = new CooldownRepository(ctx.database().sqlite());
    }

    public WalletRepository wallets() { return wallets; }

    private GuildConfig cfg(Guild g) { return ctx.database().guildConfig().findOrEmpty(g.getId()); }

    private String onCooldown(Guild g, String u, String action, long cooldownS) {
        long readyAt = cooldowns.lastTs(g.getId(), u, action) + cooldownS * 1000L;
        if (System.currentTimeMillis() < readyAt) {
            return "Aguarde — disponível novamente <t:" + (readyAt / 1000) + ":R>.";
        }
        return null;
    }

    public String daily(Guild g, Member m) {
        String cd = onCooldown(g, m.getId(), "daily", EconomyDefaults.DAILY_COOLDOWN_S);
        if (cd != null) {
            return cd;
        }
        long amount = EconomyConfig.daily(cfg(g));
        wallets.addCash(g.getId(), m.getId(), amount);
        cooldowns.stamp(g.getId(), m.getId(), "daily", System.currentTimeMillis());
        return "Recompensa diária: **+" + EconomyFormat.formatNamed(amount, cfg(g)) + "** na carteira.";
    }

    public String work(Guild g, Member m) {
        GuildConfig cfg = cfg(g);
        String cd = onCooldown(g, m.getId(), "work", EconomyConfig.workCooldownSeconds(cfg));
        if (cd != null) {
            return cd;
        }
        long min = Math.min(EconomyConfig.workMin(cfg), EconomyConfig.workMax(cfg));
        long max = Math.max(EconomyConfig.workMin(cfg), EconomyConfig.workMax(cfg));
        long amount = min + (max > min ? ThreadLocalRandom.current().nextLong(max - min + 1) : 0);
        wallets.addCash(g.getId(), m.getId(), amount);
        cooldowns.stamp(g.getId(), m.getId(), "work", System.currentTimeMillis());
        String flavor = WORK_MSGS[ThreadLocalRandom.current().nextInt(WORK_MSGS.length)];
        return flavor + " e ganhou **+" + EconomyFormat.formatNamed(amount, cfg) + "**.";
    }

    public String crime(Guild g, Member m) {
        String cd = onCooldown(g, m.getId(), "crime", EconomyDefaults.CRIME_COOLDOWN_S);
        if (cd != null) {
            return cd;
        }
        cooldowns.stamp(g.getId(), m.getId(), "crime", System.currentTimeMillis());
        long win = rand(EconomyDefaults.CRIME_WIN_MIN, EconomyDefaults.CRIME_WIN_MAX);
        long fine = rand(EconomyDefaults.CRIME_FINE_MIN, EconomyDefaults.CRIME_FINE_MAX);
        CrimeOutcome o = CrimeOutcome.resolve(ThreadLocalRandom.current().nextInt(100),
                EconomyDefaults.CRIME_SUCCESS_PCT, win, fine);
        if (o.success()) {
            wallets.addCash(g.getId(), m.getId(), o.delta());
            return "🔫 Crime bem-sucedido! **+" + EconomyFormat.formatNamed(o.delta(), cfg(g)) + "**.";
        }
        wallets.tryDebitCash(g.getId(), m.getId(), fine); // piso: só debita o que houver
        return "🚔 Você foi pego! Multa de **" + EconomyFormat.formatNamed(fine, cfg(g)) + "**.";
    }

    public String rob(Guild g, Member actor, Member target) {
        if (target.getUser().isBot() || target.getId().equals(actor.getId())) {
            return "Alvo inválido.";
        }
        String cd = onCooldown(g, actor.getId(), "rob", EconomyDefaults.ROB_COOLDOWN_S);
        if (cd != null) {
            return cd;
        }
        long targetCash = wallets.get(g.getId(), target.getId()).cash();
        if (targetCash < EconomyDefaults.ROB_TARGET_MIN_CASH) {
            return target.getEffectiveName() + " não tem dinheiro suficiente na carteira pra roubar.";
        }
        cooldowns.stamp(g.getId(), actor.getId(), "rob", System.currentTimeMillis());
        int stealPct = (int) rand(EconomyDefaults.ROB_STEAL_MIN_PCT, EconomyDefaults.ROB_STEAL_MAX_PCT);
        long fine = rand(EconomyDefaults.ROB_FINE_MIN, EconomyDefaults.ROB_FINE_MAX);
        RobOutcome o = RobOutcome.resolve(ThreadLocalRandom.current().nextInt(100),
                EconomyDefaults.ROB_SUCCESS_PCT, targetCash, stealPct, fine);
        if (o.success()) {
            // transfere só se o alvo ainda tiver o valor (atômico); senão trata como falha
            if (wallets.transfer(g.getId(), target.getId(), actor.getId(), o.stolen())) {
                return "🕵️ Você roubou **" + EconomyFormat.formatNamed(o.stolen(), cfg(g)) + "** de "
                        + target.getAsMention() + "!";
            }
            return "O roubo falhou — o alvo não tinha o valor.";
        }
        wallets.tryDebitCash(g.getId(), actor.getId(), o.fine());
        return "🚔 Roubo fracassado! Você pagou **" + EconomyFormat.formatNamed(o.fine(), cfg(g)) + "** de multa.";
    }

    public String pay(Guild g, Member actor, Member target, long amount) {
        if (amount <= 0) {
            return "Informe um valor positivo.";
        }
        if (target.getUser().isBot() || target.getId().equals(actor.getId())) {
            return "Destinatário inválido.";
        }
        if (wallets.transfer(g.getId(), actor.getId(), target.getId(), amount)) {
            return "Você pagou **" + EconomyFormat.formatNamed(amount, cfg(g)) + "** para " + target.getAsMention() + ".";
        }
        return "Saldo insuficiente na carteira.";
    }

    public String deposit(Guild g, Member m, long amount) {
        long cash = wallets.get(g.getId(), m.getId()).cash();
        long amt = amount <= 0 ? cash : Math.min(amount, cash);
        if (amt <= 0 || !wallets.deposit(g.getId(), m.getId(), amt)) {
            return "Nada para depositar.";
        }
        return "Depositado **" + EconomyFormat.formatNamed(amt, cfg(g)) + "** no banco.";
    }

    public String withdraw(Guild g, Member m, long amount) {
        long bank = wallets.get(g.getId(), m.getId()).bank();
        long amt = amount <= 0 ? bank : Math.min(amount, bank);
        if (amt <= 0 || !wallets.withdraw(g.getId(), m.getId(), amt)) {
            return "Nada para sacar.";
        }
        return "Sacado **" + EconomyFormat.formatNamed(amt, cfg(g)) + "** do banco.";
    }

    private static long rand(long min, long max) {
        return max <= min ? min : min + ThreadLocalRandom.current().nextLong(max - min + 1);
    }
}
```
> **Nota:** `daily`/`work` carimbam o cooldown **após** o crédito; `crime`/`rob` carimbam **antes** de rolar (o cooldown vale mesmo em falha). `-1` como valor no comando = "tudo" para depositar/sacar (a UI passa `0` p/ "tudo" — aqui `amount<=0` já cobre).
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 6: `/saldo` + `/daily` + `/trabalhar` + `/crime`

**Files:** Create `SaldoView.java` (em economy) + comandos `SaldoCommand`, `DailyCommand`, `TrabalharCommand`, `CrimeCommand` (em `modules/base/commands`).

**Consumes:** `EconomyService`, `EconomyConfig`, `EconomyFormat`.

- [ ] **Step 1:** `SaldoView.java`
```java
package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Member;

public final class SaldoView {
    private SaldoView() {}

    public static Container panel(int accent, Member member, WalletRepository.Wallet w, int rank, GuildConfig cfg) {
        long total = w.cash() + w.bank();
        String pos = rank > 0 ? "#" + rank : "—";
        String body = "## " + Emojis.of(Emojis.MONEY, "💰") + " Saldo de " + member.getEffectiveName() + "\n"
                + Emojis.of(Emojis.CASH, "💵") + " **Carteira** · " + EconomyFormat.format(w.cash(), cfg) + "\n"
                + Emojis.of(Emojis.BANK, "🏦") + " **Banco** · " + EconomyFormat.format(w.bank(), cfg) + "\n"
                + Emojis.of(Emojis.GEM, "💠") + " **Total** · " + EconomyFormat.formatNamed(total, cfg) + "\n"
                + Emojis.of(Emojis.GROWTH, "📈") + " **Posição** · `" + pos + "`";
        return Panels.container(accent, Panels.text(body));
    }
}
```
> **Verificar:** `Emojis.BANK` (existe: `bank.png`). Se faltar, usar `MONEY`.
- [ ] **Step 2:** `SaldoCommand` (efêmero)
```java
package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.EconomyService;
import dev.davimf.basebot.modules.base.economy.SaldoView;
import dev.davimf.basebot.modules.base.economy.WalletRepository;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class SaldoCommand implements SlashCommand {
    private final EconomyService eco;
    public SaldoCommand(EconomyService eco) { this.eco = eco; }

    @Override public String name() { return "saldo"; }

    @Override public SlashCommandData data() {
        return Commands.slash("saldo", "Mostra seu saldo (carteira e banco).")
                .addOption(OptionType.USER, "usuario", "Membro (opcional)", false);
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor."); return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!EconomyConfig.enabled(cfg)) { Replies.ephemeral(event, ctx, "Economia desativada neste servidor."); return; }
        OptionMapping opt = event.getOption("usuario");
        Member target = opt == null ? event.getMember() : opt.getAsMember();
        if (target == null) { Replies.ephemeral(event, ctx, "Esse usuário não está no servidor."); return; }
        WalletRepository.Wallet w = eco.wallets().get(event.getGuild().getId(), target.getId());
        int rank = eco.wallets().rank(event.getGuild().getId(), target.getId());
        event.replyComponents(SaldoView.panel(EmbedColor.resolve(cfg), target, w, rank, cfg))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
```
- [ ] **Step 3:** `DailyCommand`, `TrabalharCommand`, `CrimeCommand` — mesma forma (checam guild + `EconomyConfig.enabled`, chamam o serviço, respondem com `Replies.reply` temporário):
```java
// DailyCommand: name "daily", data "Recompensa diária.", execute →
//   Replies.reply(event, ctx, eco.daily(event.getGuild(), event.getMember()));
// TrabalharCommand: name "trabalhar" → eco.work(...)
// CrimeCommand: name "crime" → eco.crime(...)
```
Escrever os três arquivos completos seguindo o molde do `SaldoCommand` (sem a opção `usuario`; corpo: gate guild+enabled → `Replies.reply(event, ctx, eco.XXX(event.getGuild(), event.getMember()));`).
- [ ] **Step 4: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 7: `/roubar` + `/pagar` + `/depositar` + `/sacar`

**Files:** Create `RoubarCommand`, `PagarCommand`, `DepositarCommand`, `SacarCommand` (em `modules/base/commands`).

- [ ] **Step 1:** `RoubarCommand` (`usuario` obrigatório) e `PagarCommand` (`usuario` + `quantia` obrigatórios):
```java
// RoubarCommand: name "roubar", option USER "usuario" (true). execute →
//   Member alvo = event.getOption("usuario").getAsMember(); if null → erro;
//   Replies.reply(event, ctx, eco.rob(event.getGuild(), event.getMember(), alvo));
// PagarCommand: name "pagar", option USER "usuario" (true) + INTEGER "quantia" (true). execute →
//   Member alvo = ...; long qtd = event.getOption("quantia").getAsLong();
//   Replies.reply(event, ctx, eco.pay(event.getGuild(), event.getMember(), alvo, qtd));
```
- [ ] **Step 2:** `DepositarCommand` / `SacarCommand` (`quantia` opcional; ausente/0 = tudo):
```java
// DepositarCommand: name "depositar", option INTEGER "quantia" (false). execute →
//   OptionMapping q = event.getOption("quantia"); long amt = q == null ? 0 : q.getAsLong();
//   Replies.reply(event, ctx, eco.deposit(event.getGuild(), event.getMember(), amt));
// SacarCommand: name "sacar" → eco.withdraw(...)
```
> Todos: gate `event.getGuild()!=null && event.getMember()!=null` + `EconomyConfig.enabled(cfg)` antes de chamar o serviço. Escrever os quatro arquivos completos no molde do `SaldoCommand`.
- [ ] **Step 3: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 8: `/rico` + `RicoView` + `EconomyComponentHandler` (paginação)

**Files:** Create `RicoView.java`, `EconomyComponentHandler.java` (em economy), `RicoCommand.java` (em commands).

**Produces:** `RicoView.NS = "eco"`, ação `top` com arg página; leaderboard 10/página, **efêmero**.

- [ ] **Step 1:** `RicoView.java` — espelho do `TopView` do leveling (título 🏆 "Ranking de riqueza", linhas `#n <@id> · {total formatado}`, botões `eco:top:<page>` com `withDisabled`, `PAGE=10`). Usa `EconomyFormat.format(entry.total(), cfg)`.
- [ ] **Step 2:** `RicoCommand` — efêmero: lê `count` + `topPage(guildId, 10, 0)`, responde `RicoView.panel(...)` com `.setEphemeral(true)`.
- [ ] **Step 3:** `EconomyComponentHandler` — namespace `"eco"`, `onButton` ação `top`: relê a página e `event.editComponents(...).useComponentsV2().queue()` (espelho do `LevelingComponentHandler`). Recebe `EconomyService` no construtor (usa `eco.wallets()`).
> Escrever os três seguindo exatamente o padrão de `TopView`/`TopCommand`/`LevelingComponentHandler` já implementados no leveling, trocando XP por total formatado e o namespace para `eco`.
- [ ] **Step 4: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 9: `/eco` (admin)

**Files:** Create `EcoCommand.java` (em commands).

- [ ] **Step 1:** `EcoCommand` — `MANAGE_SERVER`, subcomandos `add|remove|set|reset`, opções `usuario` (true) + `quantia` (true, exceto reset) + destino `carteira|banco` (opcional, default carteira):
```java
// data(): Commands.slash("eco",...).setDefaultPermissions(enabledFor(MANAGE_SERVER))
//   add/remove/set: usuario + quantia (INTEGER) + option STRING "destino" choices carteira/banco (false)
//   reset: usuario
// execute(): resolve user; destino (default "carteira"); long qtd;
//   add    → destino banco ? wallets.addBank(+qtd) : wallets.addCash(+qtd)
//   remove → destino banco ? addBank(-qtd) (clamp via set) : tryDebitCash(qtd) (ou set max(0,cash-qtd))
//   set    → destino banco ? setBank(qtd) : setCash(qtd)
//   reset  → setCash(0) + setBank(0)
//   Replies.reply(...) confirmando.
```
> Para `remove` sem ir negativo: usar `setCash(g,u, Math.max(0, get().cash()-qtd))` / idem banco (simples e seguro). Escrever o arquivo completo.
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 10: `/setup → Economia`

**Files:** Modify `SetupView.java` (tela `economyScreen` + `economyCurrencyModal` + `economyValuesModal`), `SetupComponentHandler.java` (nav + toggle + modais), `SetupView.hub` + `moduleNav` (entrada "Economia").

- [ ] **Step 1:** `SetupView` — importar `EconomyConfig`; `economyScreen(GuildConfig cfg)`: overview (ligado?, moeda, daily, trabalhar min-max/cooldown) + botões `ecotoggle` (liga/desliga `EconomyConfig.KEY_ENABLED`), `ecocurrency` (abre `economyCurrencyModal`), `ecovalues` (abre `economyValuesModal`), `nav hub` Voltar, `moduleNav("economia")`. Modais: `economyform-currency` (campos `nome`, `emoji`) e `economyform-values` (campos `daily`, `work_min`, `work_max`, `work_cooldown`), pré-preenchidos.
- [ ] **Step 2:** `SetupView.moduleNav` — `addNav(menu, current, "Economia", "economia");`. `SetupView.hub` — `.addOption("Economia", "economia", "Carteira, banco, ganhos, roubo e ranking")`.
- [ ] **Step 3:** `SetupComponentHandler` — nav button `case "economia" -> SetupView.economyScreen(config(ctx, guildId));`; `onStringSelect "section"` idem; `onButton`: `ecotoggle` (withToggle KEY_ENABLED, re-render), `ecocurrency`/`ecovalues` (replyModal); `onModal`: `economyform-currency` → `withSetting(KEY_CURRENCY_NAME/EMOJI)`; `economyform-values` → `withSetting(KEY_DAILY/WORK_MIN/WORK_MAX/WORK_COOLDOWN)` (validar inteiros, ignorar campo inválido), salvar + re-render.
> Seguir os moldes de `welctoggle`/`welcedit`/`welcomeform`/`saveWelcome` já existentes.
- [ ] **Step 4: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 11: Registro no `BaseModule` + build + smoke

**Files:** Modify `modules/base/BaseModule.java`.

- [ ] **Step 1:** No `register(...)`:
```java
        // Economia por-usuário (Base) — separada do tesouro de facção.
        dev.davimf.basebot.modules.base.economy.EconomyService economy =
                new dev.davimf.basebot.modules.base.economy.EconomyService(ctx);
        registry.command(new dev.davimf.basebot.modules.base.commands.SaldoCommand(economy));
        registry.command(new dev.davimf.basebot.modules.base.commands.DailyCommand(economy));
        registry.command(new dev.davimf.basebot.modules.base.commands.TrabalharCommand(economy));
        registry.command(new dev.davimf.basebot.modules.base.commands.CrimeCommand(economy));
        registry.command(new dev.davimf.basebot.modules.base.commands.RoubarCommand(economy));
        registry.command(new dev.davimf.basebot.modules.base.commands.PagarCommand(economy));
        registry.command(new dev.davimf.basebot.modules.base.commands.DepositarCommand(economy));
        registry.command(new dev.davimf.basebot.modules.base.commands.SacarCommand(economy));
        registry.command(new dev.davimf.basebot.modules.base.commands.RicoCommand(economy));
        registry.command(new dev.davimf.basebot.modules.base.commands.EcoCommand(economy));
        registry.component(new dev.davimf.basebot.modules.base.economy.EconomyComponentHandler(economy));
```
- [ ] **Step 2: Run** `./gradlew build` → BUILD SUCCESSFUL (todos os testes verdes).
- [ ] **Step 3: Smoke (servidor de teste, `eco:enabled`):**
  - `/daily`, `/trabalhar` (repetir → cooldown com `<t:…:R>`), `/crime` (ganha/perde), `/saldo` (efêmero).
  - `/depositar tudo` → carteira 0, banco cheio; `/roubar @alt` → só pega da carteira (banco protegido); `/sacar 50`.
  - `/pagar @alt 100` (recebedor novo cria linha); tentar pagar mais que tem → recusa sem debitar.
  - `/rico` (efêmero, paginado); `/eco add @alt 1000 banco` (admin).

## Self-Review
- **Cobertura do spec:** moeda/format (T2), config (T1), carteira/banco atômicos + cooldowns (T3), crime/roubo puros (T4), serviço (T5), comandos de ganho/leitura (T6), roubo/pagar/banco (T7), ranking (T8), admin (T9), setup (T10), registro (T11). Loja = Plano 2. ✓
- **Anti double-spend:** `tryDebitCash`/`deposit`/`withdraw`/`transfer` usam `WHERE … >= ?` e `executeUpdate()==0`; `transfer` em transação com upsert do recebedor. ✓
- **Consistência de tipos:** `Wallet(cash,bank)`, `Entry(userId,total)`, `CrimeOutcome(success,delta)`, `RobOutcome(success,stolen,fine)`, `EconomyService.daily/work/crime/rob/pay/deposit/withdraw/wallets`. ✓
- **Pontos a confirmar no build (inline):** `Emojis.BANK`; overloads de `event.getOption`; molde exato de `TopView`/`LevelingComponentHandler` p/ `RicoView`/`EconomyComponentHandler`.