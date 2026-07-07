# Pix — Painel único, normalização e correção do BR Code · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (inline) ou subagent-driven-development. Steps em checkbox.

**Goal:** Corrigir o BR Code inválido (dobra ASCII), normalizar chaves por tipo, remover o parâmetro cidade e transformar `/pix` num comando único com painel efêmero (múltiplas chaves por vendedor: enviar / cadastrar / editar / remover).

**Architecture:** Duas frentes puras e testáveis (`PixKeyNormalizer`, dobra ASCII em `PixPayload`) + a troca do modelo de dados para N chaves por vendedor (`pix_keys` com `id`) + a UI de painel efêmero (`PixPanelView` + `PixComponentHandler` no namespace `pix`). O `/pix gerar`/`registrar` some; `/pix` abre o painel.

**Tech Stack:** Java 22, JDA 6.4.2 (Components V2), SQLite, ZXing (QR já existente).

## Global Constraints

- **JDK 22.** **Sem commits** (regra do projeto — cada task termina em `./gradlew build`/test, não em commit).
- **Migrações:** anexar todo `NNN_*.sql` a `SqliteMigrator.MIGRATIONS` (senão nunca aplica). Sem comentário inline após `;`; cada statement termina com `;` no fim da linha. Próximo nº: **026**.
- **Components V2** em toda mensagem do bot; usar `Panels`/`Replies`; emojis custom via `Emojis`; house style (`## título` + `Panels.divider()`).
- **Base do Pix continua por vendedor** (por `user_id`), agora com **múltiplas chaves**. Gate do cargo **vendedor** (`guild_config.roles["vendedor"]`) mantido.
- Cidade EMV fixa **`BRASIL`**. Normalização nunca salva valor inválido.

**Símbolos confirmados (código):**
- `ComponentHandler`: `onButton/onStringSelect/onEntitySelect/onModal(event, ComponentId id, BotContext ctx)` (defaults no-op). `ComponentId.of(ns, action, args...)`, `id.action()`, `id.arg(i)`.
- Editar msg efêmera in-place: `event.editComponents(container).useComponentsV2().queue()` (event é `IMessageEditCallback`: button/stringselect/modal servem).
- Abrir modal: `event.replyModal(modal).queue()`. Postar público a partir de modal: `event.replyComponents(container).useComponentsV2().addFiles(file).queue()`.
- `Replies.ephemeral(event, ctx, msg)` / `Replies.ephemeral(event, accentInt, msg)`.
- `Panels.container(accent, ContainerChildComponent...)`, `Panels.text(str)`, `Panels.divider()`.
- `StringSelectMenu.create(id).setPlaceholder(..).setMinValues(int).setMaxValues(int).addOptions(SelectOption...).build()`; `SelectOption.of(label, value).withDescription(..)`.
- `Modal.create(id, title).addComponents(Label.of("x", input.build())...).build()`; `TextInput.create(id, style).setPlaceholder(..).setRequired(bool).setMaxLength(int).setValue(..)`.
- `PixDispatch.render(PixKey key, long amountCents, int accent, String ownerId)` → `Rendered(container, file)`.

---

## Task 1: `PixKeyNormalizer` (puro) + teste

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/sales/pix/PixKeyNormalizer.java`
- Test: `src/test/java/dev/davimf/basebot/modules/sales/pix/PixKeyNormalizerTest.java`

**Interfaces — Produces:** `PixKeyNormalizer.normalize(String tipo, String valor)` → `Result(boolean ok, String value, String error)`; consumido no `PixComponentHandler` (Task 4).

- [ ] **Step 1: Teste**
```java
package dev.davimf.basebot.modules.sales.pix;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PixKeyNormalizerTest {

    @Test
    void cpfStripsFormattingWhenValid() {
        PixKeyNormalizer.Result r = PixKeyNormalizer.normalize("CPF", "529.982.247-25");
        assertTrue(r.ok(), r.error());
        assertEquals("52998224725", r.value());
    }

    @Test
    void cpfRejectsBadCheckDigits() {
        assertFalse(PixKeyNormalizer.normalize("CPF", "111.111.111-11").ok());
        assertFalse(PixKeyNormalizer.normalize("CPF", "123.456.789-00").ok());
    }

    @Test
    void cnpjStripsFormattingWhenValid() {
        PixKeyNormalizer.Result r = PixKeyNormalizer.normalize("CNPJ", "11.222.333/0001-81");
        assertTrue(r.ok(), r.error());
        assertEquals("11222333000181", r.value());
    }

    @Test
    void cnpjRejectsBadCheckDigits() {
        assertFalse(PixKeyNormalizer.normalize("CNPJ", "11.222.333/0001-99").ok());
    }

    @Test
    void phoneNormalizesToE164() {
        assertEquals("+5562986089609", PixKeyNormalizer.normalize("PHONE", "62986089609").value());
        assertEquals("+5562986089609", PixKeyNormalizer.normalize("PHONE", "5562986089609").value());
        assertEquals("+5562986089609", PixKeyNormalizer.normalize("PHONE", "+55 (62) 98608-9609").value());
        assertEquals("+556232320000", PixKeyNormalizer.normalize("PHONE", "6232320000").value());
    }

    @Test
    void phoneRejectsTooShortOrLong() {
        assertFalse(PixKeyNormalizer.normalize("PHONE", "123").ok());
        assertFalse(PixKeyNormalizer.normalize("PHONE", "1234567890123456").ok());
    }

    @Test
    void emailLowercasesAndTrims() {
        PixKeyNormalizer.Result r = PixKeyNormalizer.normalize("EMAIL", "  Fulano@Example.COM ");
        assertTrue(r.ok(), r.error());
        assertEquals("fulano@example.com", r.value());
    }

    @Test
    void emailRejectsMalformed() {
        assertFalse(PixKeyNormalizer.normalize("EMAIL", "no-at-sign").ok());
    }

    @Test
    void randomAcceptsUuidLowercased() {
        PixKeyNormalizer.Result r = PixKeyNormalizer.normalize("RANDOM", "123E4567-E89B-12D3-A456-426614174000");
        assertTrue(r.ok(), r.error());
        assertEquals("123e4567-e89b-12d3-a456-426614174000", r.value());
    }

    @Test
    void randomRejectsNonUuid() {
        assertFalse(PixKeyNormalizer.normalize("RANDOM", "not-a-uuid").ok());
    }

    @Test
    void unknownTypeFails() {
        assertFalse(PixKeyNormalizer.normalize("XPTO", "abc").ok());
    }
}
```
- [ ] **Step 2: Run** `./gradlew test --tests "*.PixKeyNormalizerTest"` → FAIL (classe não existe).
- [ ] **Step 3: Implementar**
```java
package dev.davimf.basebot.modules.sales.pix;

import java.util.regex.Pattern;

/** Normaliza e valida o valor de uma chave Pix conforme o tipo. Puro/testável. */
public final class PixKeyNormalizer {

    private static final Pattern EMAIL =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern UUID =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private PixKeyNormalizer() {}

    /** Resultado da normalização: {@code ok=true} traz {@code value}; senão traz {@code error} (PT-BR). */
    public record Result(boolean ok, String value, String error) {
        static Result ok(String value) { return new Result(true, value, null); }
        static Result fail(String error) { return new Result(false, null, error); }
    }

    public static Result normalize(String tipo, String valor) {
        if (tipo == null) {
            return Result.fail("Tipo de chave ausente.");
        }
        String raw = valor == null ? "" : valor.trim();
        return switch (tipo) {
            case "CPF" -> cpf(raw);
            case "CNPJ" -> cnpj(raw);
            case "PHONE" -> phone(raw);
            case "EMAIL" -> email(raw);
            case "RANDOM" -> random(raw);
            default -> Result.fail("Tipo de chave desconhecido.");
        };
    }

    private static Result cpf(String raw) {
        String d = raw.replaceAll("\\D", "");
        if (d.length() != 11 || !validCpf(d)) {
            return Result.fail("CPF inválido. Confira os 11 dígitos.");
        }
        return Result.ok(d);
    }

    private static Result cnpj(String raw) {
        String d = raw.replaceAll("\\D", "");
        if (d.length() != 14 || !validCnpj(d)) {
            return Result.fail("CNPJ inválido. Confira os 14 dígitos.");
        }
        return Result.ok(d);
    }

    private static Result phone(String raw) {
        String d = raw.replaceAll("\\D", "");
        String full;
        if ((d.length() == 12 || d.length() == 13) && d.startsWith("55")) {
            full = d;
        } else if (d.length() == 10 || d.length() == 11) {
            full = "55" + d;
        } else {
            return Result.fail("Telefone inválido. Use DDD + número (ex.: 62986089609).");
        }
        if (full.length() != 12 && full.length() != 13) {
            return Result.fail("Telefone inválido. Use DDD + número (ex.: 62986089609).");
        }
        return Result.ok("+" + full);
    }

    private static Result email(String raw) {
        String v = raw.toLowerCase();
        if (!EMAIL.matcher(v).matches()) {
            return Result.fail("E-mail inválido.");
        }
        return Result.ok(v);
    }

    private static Result random(String raw) {
        String v = raw.toLowerCase();
        if (!UUID.matcher(v).matches()) {
            return Result.fail("Chave aleatória inválida (esperado formato UUID).");
        }
        return Result.ok(v);
    }

    private static boolean validCpf(String d) {
        if (d.chars().distinct().count() == 1) {
            return false;
        }
        int c1 = checkDigit(d, 9, 10);
        int c2 = checkDigit(d, 10, 11);
        return c1 == (d.charAt(9) - '0') && c2 == (d.charAt(10) - '0');
    }

    private static int checkDigit(String d, int len, int startWeight) {
        int sum = 0;
        for (int i = 0; i < len; i++) {
            sum += (d.charAt(i) - '0') * (startWeight - i);
        }
        int mod = sum % 11;
        return mod < 2 ? 0 : 11 - mod;
    }

    private static boolean validCnpj(String d) {
        if (d.chars().distinct().count() == 1) {
            return false;
        }
        int[] w1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] w2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int c1 = cnpjDigit(d, w1);
        int c2 = cnpjDigit(d, w2);
        return c1 == (d.charAt(12) - '0') && c2 == (d.charAt(13) - '0');
    }

    private static int cnpjDigit(String d, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (d.charAt(i) - '0') * weights[i];
        }
        int mod = sum % 11;
        return mod < 2 ? 0 : 11 - mod;
    }
}
```
- [ ] **Step 4: Run** `./gradlew test --tests "*.PixKeyNormalizerTest"` → PASS.

---

## Task 2: `PixPayload` — dobra ASCII (corrige o BR Code inválido) + cidade padrão `BRASIL`

**Files:**
- Modify: `src/main/java/dev/davimf/basebot/modules/sales/pix/PixPayload.java`
- Test: `src/test/java/dev/davimf/basebot/modules/sales/pix/PixPayloadTest.java`

**Interfaces — Produces:** `toBrCode()` passa a emitir payload **somente ASCII**; comprimentos TLV coerentes em bytes. API do builder inalterada.

- [ ] **Step 1: Teste (adicionar ao `PixPayloadTest`)**
```java
    @Test
    void foldsAccentsToAsciiSoBrCodeIsByteSafe() {
        String code = PixPayload.builder()
                .key("+5562986089609")
                .merchantName("José da Silva")
                .merchantCity("Goiânia")
                .build().toBrCode();
        assertTrue(code.chars().allMatch(c -> c < 128), "BR Code deve ser ASCII puro");
        assertTrue(code.contains("JOSE DA SILVA") || code.contains("Jose da Silva")
                || code.contains("Jose Da Silva") || code.toUpperCase().contains("JOSE DA SILVA"),
                "nome sem acento no payload");
        assertFalse(code.contains("Goiânia"), "cidade não pode ter acento");
        // CRC recomputável sobre os bytes do corpo.
        String body = code.substring(0, code.length() - 4);
        assertEquals(Crc16.hex4(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                code.substring(code.length() - 4));
    }
```
> **Nota:** a dobra remove só os acentos, não altera caixa (mantém "Jose da Silva"). O `assertTrue` acima cobre a variante real ("Jose da Silva"); mantê-lo tolerante evita fragilidade. Ajuste para `assertTrue(code.contains("Jose da Silva"))` após ver o output se preferir exatidão.
- [ ] **Step 2: Run** `./gradlew test --tests "*.PixPayloadTest"` → FAIL (contém acento / não-ASCII).
- [ ] **Step 3: Implementar** — em `PixPayload.java`:

Trocar o método `clip(...)` por uma dobra ASCII e usá-la no construtor:
```java
    private PixPayload(Builder b) {
        if (b.key == null || b.key.isBlank()) {
            throw new IllegalArgumentException("Pix key is required");
        }
        this.key = b.key.trim();
        this.merchantName = ascii(b.merchantName, 25);
        this.merchantCity = ascii(b.merchantCity, 15);
        this.amount = b.amount;
        this.txid = b.txid;
        this.description = b.description;
    }
```
```java
    /**
     * Recorta e dobra o texto para ASCII puro: remove acentos (NFD + marcas de combinação) e
     * qualquer caractere não-ASCII restante. Garante que o comprimento em caracteres do campo
     * TLV coincida com o comprimento em bytes — do contrário o app pagador desalinha o parse e
     * o Pix fica inválido (ex.: "Goiânia" tem 7 chars mas 8 bytes).
     */
    private static String ascii(String s, int max) {
        String v = (s == null) ? "" : s.trim();
        v = java.text.Normalizer.normalize(v, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^\\x20-\\x7E]", "");
        return v.length() > max ? v.substring(0, max) : v;
    }
```
E no `Builder`, cidade padrão `BRASIL`:
```java
        private String merchantCity = "BRASIL";
```
> Remover o antigo método `clip(...)` (substituído por `ascii`).
- [ ] **Step 4: Run** `./gradlew test --tests "*.PixPayloadTest"` → PASS (todos, inclusive os existentes).

---

## Task 3: Modelo de dados multi-chave — migração 026 + `PixKey` + `PixKeyRepository` + consumidores triviais

**Files:**
- Create: `src/main/resources/db/sqlite/026_pix_keys_multi.sql`
- Modify: `src/main/java/dev/davimf/basebot/database/sqlite/SqliteMigrator.java`
- Modify: `src/main/java/dev/davimf/basebot/database/model/PixKey.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/sales/pix/PixKeyRepository.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/sales/pix/PixDispatch.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/sales/budget/BudgetService.java:178`
- Modify: `src/main/java/dev/davimf/basebot/modules/sales/pix/PixCommand.java` (ponte mínima para compilar)
- Test: `src/test/java/dev/davimf/basebot/modules/sales/pix/PixKeyRepositoryTest.java` (reescrever)

**Interfaces — Produces:** `PixKey(long id, String guildId, String userId, String keyType, String keyValue, String merchantName)`; repo `insert/list/find/update/delete/findDefault`; consumidos em Task 4.

- [ ] **Step 1: Migração** `026_pix_keys_multi.sql`
```sql
CREATE TABLE pix_keys_new (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id      TEXT NOT NULL,
    user_id       TEXT NOT NULL,
    key_type      TEXT NOT NULL,
    key_value     TEXT NOT NULL,
    merchant_name TEXT NOT NULL,
    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
);
INSERT INTO pix_keys_new (guild_id, user_id, key_type, key_value, merchant_name)
    SELECT guild_id, user_id, key_type, key_value, merchant_name FROM pix_keys;
DROP TABLE pix_keys;
ALTER TABLE pix_keys_new RENAME TO pix_keys;
CREATE INDEX idx_pix_keys_user ON pix_keys (guild_id, user_id);
```
- [ ] **Step 2:** Anexar a `SqliteMigrator.MIGRATIONS` (após `025_self_roles.sql`, com vírgula antes):
```java
            "/db/sqlite/025_self_roles.sql",
            "/db/sqlite/026_pix_keys_multi.sql"
```
- [ ] **Step 3:** `PixKey.java` — record novo (sem cidade, com id):
```java
package dev.davimf.basebot.database.model;

/** A seller's Pix key: multiple per guild+user (BOTSPECS Module 3). City is no longer stored. */
public record PixKey(
        long id,
        String guildId,
        String userId,
        String keyType,
        String keyValue,
        String merchantName
) {}
```
- [ ] **Step 4: Teste** `PixKeyRepositoryTest.java` (reescrever inteiro):
```java
package dev.davimf.basebot.modules.sales.pix;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.model.PixKey;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PixKeyRepositoryTest {

    private SqliteManager sqlite;
    private PixKeyRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("test.db").toString()));
        new SqliteMigrator(sqlite).migrate();
        repo = new PixKeyRepository(sqlite);
    }

    @AfterEach
    void tearDown() {
        sqlite.close();
    }

    private PixKey unsaved(String guild, String user, String type, String value, String name) {
        return new PixKey(0, guild, user, type, value, name);
    }

    @Test
    void insertThenListReturnsAllUserKeys() {
        repo.insert(unsaved("g1", "u1", "EMAIL", "a@b.com", "Loja X"));
        repo.insert(unsaved("g1", "u1", "PHONE", "+5562986089609", "Loja X"));
        List<PixKey> keys = repo.list("g1", "u1");
        assertEquals(2, keys.size());
        assertEquals("a@b.com", keys.get(0).keyValue());
        assertEquals("+5562986089609", keys.get(1).keyValue());
    }

    @Test
    void findReturnsById() {
        long id = repo.insert(unsaved("g1", "u1", "EMAIL", "a@b.com", "Loja X"));
        PixKey k = repo.find(id).orElseThrow();
        assertEquals("a@b.com", k.keyValue());
        assertEquals("Loja X", k.merchantName());
    }

    @Test
    void updateChangesValueAndName() {
        long id = repo.insert(unsaved("g1", "u1", "EMAIL", "old@b.com", "Old"));
        repo.update(id, "new@b.com", "New");
        PixKey k = repo.find(id).orElseThrow();
        assertEquals("new@b.com", k.keyValue());
        assertEquals("New", k.merchantName());
        assertEquals("EMAIL", k.keyType());
    }

    @Test
    void deleteRemovesRow() {
        long id = repo.insert(unsaved("g1", "u1", "EMAIL", "a@b.com", "Loja X"));
        repo.delete(id);
        assertTrue(repo.find(id).isEmpty());
        assertTrue(repo.list("g1", "u1").isEmpty());
    }

    @Test
    void findDefaultReturnsFirstByInsertionOrder() {
        repo.insert(unsaved("g1", "u1", "EMAIL", "first@b.com", "A"));
        repo.insert(unsaved("g1", "u1", "PHONE", "+5562986089609", "B"));
        assertEquals("first@b.com", repo.findDefault("g1", "u1").orElseThrow().keyValue());
    }

    @Test
    void keysAreGuildAndUserScoped() {
        repo.insert(unsaved("g1", "u1", "EMAIL", "u1@b.com", "L1"));
        repo.insert(unsaved("g2", "u1", "EMAIL", "other@b.com", "L2"));
        assertEquals(1, repo.list("g1", "u1").size());
        assertTrue(repo.list("g1", "u2").isEmpty());
    }
}
```
- [ ] **Step 5: Run** `./gradlew test --tests "*.PixKeyRepositoryTest"` → FAIL (API antiga).
- [ ] **Step 6:** `PixKeyRepository.java` — reescrever:
```java
package dev.davimf.basebot.modules.sales.pix;

import dev.davimf.basebot.database.model.PixKey;
import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQLite store for Pix keys: multiple per guild+user (BOTSPECS Module 3). */
public final class PixKeyRepository {

    private final SqliteManager sqlite;

    public PixKeyRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public long insert(PixKey k) {
        String sql = "INSERT INTO pix_keys (guild_id, user_id, key_type, key_value, merchant_name) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, k.guildId());
            ps.setString(2, k.userId());
            ps.setString(3, k.keyType());
            ps.setString(4, k.keyValue());
            ps.setString(5, k.merchantName());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new RepositoryException("insert pix key " + k.guildId() + "/" + k.userId(), e);
        }
    }

    public void update(long id, String keyValue, String merchantName) {
        String sql = "UPDATE pix_keys SET key_value = ?, merchant_name = ? WHERE id = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, keyValue);
            ps.setString(2, merchantName);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("update pix key " + id, e);
        }
    }

    public void delete(long id) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM pix_keys WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("delete pix key " + id, e);
        }
    }

    public Optional<PixKey> find(long id) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM pix_keys WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("find pix key " + id, e);
        }
    }

    public List<PixKey> list(String guildId, String userId) {
        List<PixKey> out = new ArrayList<>();
        String sql = "SELECT * FROM pix_keys WHERE guild_id = ? AND user_id = ? ORDER BY id";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list pix keys " + guildId + "/" + userId, e);
        }
    }

    public Optional<PixKey> findDefault(String guildId, String userId) {
        String sql = "SELECT * FROM pix_keys WHERE guild_id = ? AND user_id = ? ORDER BY id LIMIT 1";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("find default pix key " + guildId + "/" + userId, e);
        }
    }

    private static PixKey map(ResultSet rs) throws SQLException {
        return new PixKey(
                rs.getLong("id"),
                rs.getString("guild_id"),
                rs.getString("user_id"),
                rs.getString("key_type"),
                rs.getString("key_value"),
                rs.getString("merchant_name"));
    }
}
```
- [ ] **Step 7:** `PixDispatch.java` — remover a linha da cidade (usa o default `BRASIL` do builder). No método `render`, o builder fica:
```java
        PixPayload.Builder builder = PixPayload.builder()
                .key(key.keyValue())
                .merchantName(key.merchantName());
```
> (removida a chamada `.merchantCity(key.merchantCity())`).
- [ ] **Step 8:** `BudgetService.java:178` — trocar `findByUser` por `findDefault`:
```java
        Optional<PixKey> key = pixKeys.findDefault(b.guildId(), b.sellerId());
```
- [ ] **Step 9:** `PixCommand.java` — ponte mínima para compilar (o painel completo vem na Task 4). Substituir o corpo por um `/pix` sem subcomandos que, por ora, gera com a chave padrão:
```java
    @Override
    public SlashCommandData data() {
        return Commands.slash("pix", "Painel de chaves e cobranças Pix.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        String sellerRoleId = cfg.role(SELLER_ROLE_KEY);
        if (sellerRoleId == null || sellerRoleId.isBlank()) {
            Replies.ephemeral(event, ctx,
                    "Cargo de vendedor não configurado. Defina em /setup → Cargos → Vendedor (Pix).");
            return;
        }
        boolean isSeller = event.getMember().getRoles().stream()
                .anyMatch(r -> r.getId().equals(sellerRoleId));
        if (!isSeller) {
            Replies.ephemeral(event, ctx,
                    "Apenas membros com o cargo de vendedor (<@&" + sellerRoleId + ">) podem usar o /pix.");
            return;
        }
        Replies.ephemeral(event, ctx, "Painel Pix em construção.");
    }
```
> Remover imports agora não usados (`OptionData`, `OptionType`, `SubcommandData`, `PixKey`, `PixDispatch`, `Optional`, `EmbedColor`) para compilar limpo. O `PixKeyRepository keys` fica no construtor (usado na Task 4).
- [ ] **Step 10: Run** `./gradlew build` → BUILD SUCCESSFUL (todos os testes verdes; `/pix` compila com a ponte).

---

## Task 4: Painel efêmero — `PixPanelView` + `PixCommand` + `PixComponentHandler`

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/sales/pix/PixPanelView.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/sales/pix/PixCommand.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/sales/pix/PixComponentHandler.java`

**Interfaces — Consumes:** `PixKeyRepository` (Task 3), `PixKeyNormalizer` (Task 1), `PixDispatch.render` (existente). **Produces:** UX completa no namespace `pix`.

- [ ] **Step 1:** `PixPanelView.java` — telas (root, gerenciar, tipo-novo) + rótulo mascarado:
```java
package dev.davimf.basebot.modules.sales.pix;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.PixKey;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;

import java.util.ArrayList;
import java.util.List;

/** Render das telas efêmeras do painel Pix (namespace "pix"). */
public final class PixPanelView {

    public static final String NS = "pix";

    private PixPanelView() {}

    /** Painel raiz: enviar cobrança ou gerenciar chaves. */
    public static Container root(int accent, int keyCount) {
        String body = "## " + Emojis.of(Emojis.GEM, "💠") + " Pix\n"
                + "-# Você tem `" + keyCount + "` chave(s) cadastrada(s).";
        return Panels.container(accent,
                Panels.text(body),
                Panels.divider(),
                ActionRow.of(
                        Button.success(ComponentId.of(NS, "send"), "Enviar cobrança")
                                .withEmoji(Emojis.button(Emojis.CASH)),
                        Button.secondary(ComponentId.of(NS, "manage"), "Gerenciar chaves")
                                .withEmoji(Emojis.button(Emojis.KEY))));
    }

    /** Tela de gerenciamento: lista de chaves + editar/remover + cadastrar nova. */
    public static Container manage(int accent, List<PixKey> keys) {
        StringBuilder sb = new StringBuilder("## " + Emojis.of(Emojis.KEY, "🔑") + " Suas chaves Pix\n");
        List<ActionRow> rows = new ArrayList<>();
        if (keys.isEmpty()) {
            sb.append("-# Nenhuma chave ainda. Cadastre a primeira.");
        } else {
            for (PixKey k : keys) {
                sb.append("\n• `").append(k.keyType()).append("` ").append(mask(k.keyValue()))
                        .append(" · ").append(k.merchantName());
                rows.add(ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "edit", String.valueOf(k.id())),
                                "Editar: " + mask(k.keyValue())).withEmoji(Emojis.button(Emojis.EDIT)),
                        Button.danger(ComponentId.of(NS, "del", String.valueOf(k.id())), "Remover")));
                if (rows.size() >= 4) {
                    break;
                }
            }
        }
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text(sb.toString()));
        kids.add(Panels.divider());
        kids.addAll(rows);
        kids.add(ActionRow.of(
                Button.primary(ComponentId.of(NS, "new"), "Cadastrar nova")
                        .withEmoji(Emojis.button(Emojis.PLUS)),
                Button.secondary(ComponentId.of(NS, "root"), "◀ Voltar")));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    /** Select do tipo da nova chave. */
    public static Container newType(int accent) {
        StringSelectMenu menu = StringSelectMenu.create(ComponentId.of(NS, "typenew"))
                .setPlaceholder("Tipo da nova chave…")
                .addOptions(
                        SelectOption.of("CPF", "CPF"),
                        SelectOption.of("CNPJ", "CNPJ"),
                        SelectOption.of("E-mail", "EMAIL"),
                        SelectOption.of("Telefone", "PHONE"),
                        SelectOption.of("Aleatória", "RANDOM"))
                .build();
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.PLUS, "➕") + " Nova chave"),
                Panels.divider(),
                Panels.text("-# Escolha o tipo; em seguida informe a chave e o nome do recebedor."),
                ActionRow.of(menu),
                ActionRow.of(Button.secondary(ComponentId.of(NS, "manage"), "◀ Voltar")));
    }

    /** Select de qual chave enviar (valor da opção = id da chave). */
    public static Container sendPicker(int accent, List<PixKey> keys) {
        StringSelectMenu.Builder menu = StringSelectMenu.create(ComponentId.of(NS, "sendkey"))
                .setPlaceholder("Escolha a chave para a cobrança…")
                .setMinValues(1).setMaxValues(1);
        for (PixKey k : keys) {
            menu.addOptions(SelectOption.of(k.keyType() + " · " + mask(k.keyValue()), String.valueOf(k.id()))
                    .withDescription(k.merchantName()));
        }
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.CASH, "💵") + " Enviar cobrança"),
                Panels.divider(),
                Panels.text("-# Selecione a chave; depois você pode informar um valor (opcional)."),
                ActionRow.of(menu.build()),
                ActionRow.of(Button.secondary(ComponentId.of(NS, "root"), "◀ Voltar")));
    }

    static String mask(String value) {
        if (value == null || value.length() <= 4) {
            return value == null ? "" : value;
        }
        return "…" + value.substring(value.length() - 4);
    }
}
```
> **Verificar no build:** constantes usadas em `Emojis` (`GEM`, `CASH`, `KEY`, `EDIT`, `PLUS`). Se `KEY`/`PLUS` não existirem, usar as que existirem (ver `Emojis.java`) — há `KEY` (key.png) e `PLUS` (plus.png) no manifest.
- [ ] **Step 2:** `PixCommand.java` — abrir o painel raiz (substitui a ponte da Task 3). O `execute` final:
```java
    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        String sellerRoleId = cfg.role(SELLER_ROLE_KEY);
        if (sellerRoleId == null || sellerRoleId.isBlank()) {
            Replies.ephemeral(event, ctx,
                    "Cargo de vendedor não configurado. Defina em /setup → Cargos → Vendedor (Pix).");
            return;
        }
        boolean isSeller = event.getMember().getRoles().stream()
                .anyMatch(r -> r.getId().equals(sellerRoleId));
        if (!isSeller) {
            Replies.ephemeral(event, ctx,
                    "Apenas membros com o cargo de vendedor (<@&" + sellerRoleId + ">) podem usar o /pix.");
            return;
        }
        int count = keys.list(event.getGuild().getId(), event.getUser().getId()).size();
        event.replyComponents(PixPanelView.root(EmbedColor.resolve(cfg), count))
                .useComponentsV2().setEphemeral(true).queue();
    }
```
> Reimportar `EmbedColor` (`dev.davimf.basebot.util.EmbedColor`). O construtor `PixCommand(PixKeyRepository keys)` e o campo `keys` permanecem.
- [ ] **Step 3:** `PixComponentHandler.java` — roteamento completo. Substituir a classe:
```java
package dev.davimf.basebot.modules.sales.pix;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.PixKey;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.util.List;
import java.util.Optional;

/** Painel Pix (namespace "pix"): enviar cobrança + CRUD de chaves. */
public final class PixComponentHandler implements ComponentHandler {

    @Override
    public String namespace() {
        return "pix";
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        String guildId = event.getGuild() == null ? "0" : event.getGuild().getId();
        String userId = event.getUser().getId();
        PixKeyRepository repo = repo(ctx);
        int accent = accent(ctx, guildId);
        switch (id.action()) {
            case "confirmar" -> {
                if (!PixOwnership.isOwner(userId, id.arg(0))) {
                    Replies.ephemeral(event, ctx, "Apenas o dono do Pix pode confirmar este pagamento.");
                    return;
                }
                Replies.reply(event, ctx, "Pagamento confirmado pelo dono do Pix.");
            }
            case "root" -> edit(event, PixPanelView.root(accent, repo.list(guildId, userId).size()));
            case "manage" -> edit(event, PixPanelView.manage(accent, repo.list(guildId, userId)));
            case "send" -> {
                List<PixKey> keys = repo.list(guildId, userId);
                if (keys.isEmpty()) {
                    Replies.ephemeral(event, ctx, "Cadastre uma chave antes de enviar uma cobrança.");
                    return;
                }
                edit(event, PixPanelView.sendPicker(accent, keys));
            }
            case "new" -> edit(event, PixPanelView.newType(accent));
            case "edit" -> {
                Optional<PixKey> k = repo.find(Long.parseLong(id.arg(0)));
                if (k.isEmpty()) {
                    Replies.ephemeral(event, ctx, "Chave não encontrada.");
                    return;
                }
                event.replyModal(keyModal("editform", id.arg(0), k.get().keyValue(), k.get().merchantName())).queue();
            }
            case "del" -> {
                repo.delete(Long.parseLong(id.arg(0)));
                edit(event, PixPanelView.manage(accent, repo.list(guildId, userId)));
            }
            default -> { }
        }
    }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        switch (id.action()) {
            case "typenew" -> {
                String tipo = event.getValues().get(0);
                event.replyModal(keyModal("newform", tipo, "", "")).queue();
            }
            case "sendkey" -> {
                String keyId = event.getValues().get(0);
                TextInput valor = TextInput.create("valor", TextInputStyle.SHORT)
                        .setPlaceholder("Valor em R$ (opcional, ex.: 49.90)").setRequired(false).setMaxLength(15)
                        .build();
                event.replyModal(Modal.create(ComponentId.of("pix", "sendform", keyId), "Enviar cobrança")
                        .addComponents(Label.of("Valor", valor)).build()).queue();
            }
            default -> { }
        }
    }

    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        String guildId = event.getGuild() == null ? "0" : event.getGuild().getId();
        String userId = event.getUser().getId();
        PixKeyRepository repo = repo(ctx);
        int accent = accent(ctx, guildId);
        switch (id.action()) {
            case "newform" -> {
                String tipo = id.arg(0);
                PixKeyNormalizer.Result r = PixKeyNormalizer.normalize(tipo, value(event, "chave"));
                if (!r.ok()) {
                    Replies.ephemeral(event, ctx, r.error());
                    return;
                }
                repo.insert(new PixKey(0, guildId, userId, tipo, r.value(), name(event)));
                edit(event, PixPanelView.manage(accent, repo.list(guildId, userId)));
            }
            case "editform" -> {
                Optional<PixKey> existing = repo.find(Long.parseLong(id.arg(0)));
                if (existing.isEmpty()) {
                    Replies.ephemeral(event, ctx, "Chave não encontrada.");
                    return;
                }
                PixKeyNormalizer.Result r = PixKeyNormalizer.normalize(existing.get().keyType(), value(event, "chave"));
                if (!r.ok()) {
                    Replies.ephemeral(event, ctx, r.error());
                    return;
                }
                repo.update(existing.get().id(), r.value(), name(event));
                edit(event, PixPanelView.manage(accent, repo.list(guildId, userId)));
            }
            case "sendform" -> {
                Optional<PixKey> key = repo.find(Long.parseLong(id.arg(0)));
                if (key.isEmpty()) {
                    Replies.ephemeral(event, ctx, "Chave não encontrada.");
                    return;
                }
                long cents = parseCents(value(event, "valor"));
                PixDispatch.Rendered pix = PixDispatch.render(key.get(), cents, accent, userId);
                event.replyComponents(pix.container()).useComponentsV2().addFiles(pix.file()).queue();
            }
            default -> { }
        }
    }

    private static Modal keyModal(String action, String arg, String chaveValue, String nomeValue) {
        TextInput chave = TextInput.create("chave", TextInputStyle.SHORT)
                .setPlaceholder("Valor da chave (será normalizado)").setRequired(true).setMaxLength(80)
                .setValue(chaveValue == null || chaveValue.isBlank() ? null : chaveValue).build();
        TextInput nome = TextInput.create("nome", TextInputStyle.SHORT)
                .setPlaceholder("Nome do recebedor (máx 25)").setRequired(true).setMaxLength(25)
                .setValue(nomeValue == null || nomeValue.isBlank() ? null : nomeValue).build();
        return Modal.create(ComponentId.of("pix", action, arg), "Chave Pix")
                .addComponents(Label.of("Chave", chave), Label.of("Nome do recebedor", nome))
                .build();
    }

    private static long parseCents(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            double v = Double.parseDouble(raw.trim().replace(',', '.'));
            return v > 0 ? Math.round(v * 100) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String value(ModalInteractionEvent event, String id) {
        var mapping = event.getValue(id);
        return mapping == null ? "" : mapping.getAsString();
    }

    private static String name(ModalInteractionEvent event) {
        String n = value(event, "nome").trim();
        return n.isBlank() ? "PIX" : n;
    }

    private void edit(IMessageEditCallback event, Container screen) {
        event.editComponents(screen).useComponentsV2().queue();
    }

    private static PixKeyRepository repo(BotContext ctx) {
        return new PixKeyRepository(ctx.database().sqlite());
    }

    private static int accent(BotContext ctx, String guildId) {
        return EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
    }
}
```
> **Verificar no build:** pacotes de `Label`/`TextInput`/`TextInputStyle`/`Modal` (conferir com o uso em `SetupView` — mesmo projeto/JDA). `event.getValue(id)` em `ModalInteractionEvent` retorna `ModalMapping` (`getAsString()`). `IMessageEditCallback` é implementado por button/stringselect/modal events.
- [ ] **Step 4: Run** `./gradlew build` → BUILD SUCCESSFUL.

---

## Task 5: Build completo + smoke manual

- [ ] **Step 1: Run** `./gradlew build` → BUILD SUCCESSFUL (todos os testes: `PixKeyNormalizerTest`, `PixPayloadTest`, `PixKeyRepositoryTest` + suíte).
- [ ] **Step 2: Smoke (servidor de teste, com cargo vendedor):**
  - `/pix` → painel efêmero. **Gerenciar → Cadastrar nova**: tipo Telefone, chave `62986089609` → salva como `+5562986089609`. CPF `529.982.247-25` → `52998224725`. Chave inválida → erro efêmero, nada salvo.
  - **Enviar cobrança** → escolher chave → informar valor (ou vazio) → cobrança **pública** no canal com QR válido. **Escanear/pagar num app real de banco** (nome sem acento no payload) → deve validar.
  - **Editar** uma chave (valor/nome) e **Remover** outra → lista atualiza.
  - Orçamento (`/orçamento`) com o vendedor tendo ≥1 chave → aprovação dispara o Pix com a **chave padrão** (primeira).

## Self-Review
- **Cobertura do spec:** bug ASCII (T2); cidade `BRASIL` (T2/T3); normalização CPF/CNPJ/PHONE/EMAIL/RANDOM (T1); `/pix` painel único enviar/gerenciar (T4); múltiplas chaves (T3/T4); migração 026 + model + repo (T3); orçamento usa `findDefault` (T3); cobrança pública (T4); editar só valor/nome (T4). ✓
- **Consistência de tipos:** `PixKeyNormalizer.Result(ok/value/error)` (T1) usado em T4; `PixKey(long id,…)` (T3) em T3/T4; repo `insert(→long)/list/find/update/delete/findDefault` (T3) em T4; `PixPanelView.root/manage/newType/sendPicker` (T4) no handler/command; `PixDispatch.render(key, cents, accent, ownerId)` inalterado. ✓
- **Sem placeholders:** todo passo traz código real; smoke exige teste em app de banco (validação final do bug). ✓
- **Pontos a confirmar no build (anotados inline):** nomes de constantes em `Emojis`; pacotes de `Label/TextInput/Modal`; `ModalMapping.getAsString`. Se algum divergir, ajustar pelo uso equivalente em `SetupView`/`SetupComponentHandler`.
