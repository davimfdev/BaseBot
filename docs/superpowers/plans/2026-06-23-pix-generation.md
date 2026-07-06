<!-- [OUTLINE START]
Markdown Document: Pix Code Generation Implementation Plan
[OUTLINE END] -->



# Pix Code Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Module 3 (Sales/Vendas) Pix payment-code generation feature end-to-end: a `/pix` command that registers a seller's Pix key per role and generates a valid, scannable Brazilian BR Code (EMV) payload, QR image, and copy-paste block, with an owner-restricted confirmation button.

**Architecture:** A pure-logic core (CRC16-CCITT, EMV TLV payload builder) with no Discord/DB dependencies and full unit-test coverage, wrapped by a ZXing QR renderer, a SQLite key-store repository, and a thin JDA command/component layer that reuses the existing BaseBot framework (`SlashCommand`, `ComponentHandler`, `BotContext`).

**Tech Stack:** Java 22, JDA 6.4.2 (Components V2), ZXing (core + javase), SQLite via the existing `SqliteManager`, JUnit 5.

## Global Constraints

- JDK 22; JDA `6.4.2`; Discord UI strictly Components V2 (buttons/select menus/modals). (copied from BOTSPECS)
- All configuration is **Guild-specific** — Pix keys are scoped by `(guild_id, role_id)`; never global state. (BOTSPECS Golden Rule)
- Pix BR Code conforms to EMV®QRCPS: GUI `br.gov.bcb.pix`, currency `986` (BRL), country `BR`, CRC16-CCITT (poly `0x1021`, init `0xFFFF`). (BOTSPECS Module 3 + BCB BR Code spec)
- Merchant name/city are treated as ASCII (Pix length fields count bytes); name ≤ 25 chars, city ≤ 15 chars.
- TDD: failing test first; frequent commits; DRY; YAGNI.

---

### Task 1: CRC16-CCITT (FALSE) primitive

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/sales/pix/Crc16.java`
- Test: `src/test/java/dev/davimf/basebot/modules/sales/pix/Crc16Test.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `Crc16.ccittFalse(byte[]) -> int` (0..0xFFFF); `Crc16.hex4(byte[]) -> String` (4-char uppercase hex).

- [ ] **Step 1: Write the failing test**

```java
package dev.davimf.basebot.modules.sales.pix;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Crc16Test {

    @Test
    void matchesCanonicalCheckValue() {
        // CRC-16/CCITT-FALSE check value for ASCII "123456789" is 0x29B1 (universal vector).
        int crc = Crc16.ccittFalse("123456789".getBytes(StandardCharsets.US_ASCII));
        assertEquals(0x29B1, crc);
    }

    @Test
    void hex4IsUppercaseZeroPadded() {
        assertEquals("29B1", Crc16.hex4("123456789".getBytes(StandardCharsets.US_ASCII)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.sales.pix.Crc16Test"`
Expected: FAIL — `Crc16` does not exist / cannot find symbol.

- [ ] **Step 3: Write minimal implementation**

```java
package dev.davimf.basebot.modules.sales.pix;

/**
 * CRC-16/CCITT-FALSE (polynomial 0x1021, initial value 0xFFFF, no reflection,
 * xor-out 0x0000) — the checksum required at the end of a Pix BR Code (tag 63).
 */
public final class Crc16 {

    private Crc16() {}

    public static int ccittFalse(byte[] data) {
        int crc = 0xFFFF;
        for (byte b : data) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ 0x1021;
                } else {
                    crc <<= 1;
                }
                crc &= 0xFFFF;
            }
        }
        return crc & 0xFFFF;
    }

    public static String hex4(byte[] data) {
        return String.format("%04X", ccittFalse(data));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.sales.pix.Crc16Test"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/sales/pix/Crc16.java \
        src/test/java/dev/davimf/basebot/modules/sales/pix/Crc16Test.java
git commit -m "feat(pix): add CRC-16/CCITT-FALSE primitive for BR Code"
```

---

### Task 2: EMV BR Code payload builder

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/sales/pix/PixPayload.java`
- Test: `src/test/java/dev/davimf/basebot/modules/sales/pix/PixPayloadTest.java`

**Interfaces:**
- Consumes: `Crc16.hex4(byte[])` from Task 1.
- Produces:
  - `PixPayload.emv(String id, String value) -> String` (package-private TLV: id + 2-digit length + value).
  - `PixPayload.builder()` → `Builder` with `.key(String)`, `.merchantName(String)`, `.merchantCity(String)`, `.amount(BigDecimal /*nullable*/)`, `.txid(String /*nullable*/)`, `.description(String /*nullable*/)`, `.build() -> PixPayload`.
  - `PixPayload.toBrCode() -> String` (full payload incl. CRC).

- [ ] **Step 1: Write the failing test**

```java
package dev.davimf.basebot.modules.sales.pix;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class PixPayloadTest {

    @Test
    void emvEncodesIdLengthValue() {
        assertEquals("000201", PixPayload.emv("00", "01"));
        assertEquals("0014br.gov.bcb.pix", PixPayload.emv("00", "br.gov.bcb.pix"));
    }

    @Test
    void buildsValidStaticBrCode() {
        String code = PixPayload.builder()
                .key("fulano@example.com")
                .merchantName("Fulano de Tal")
                .merchantCity("BRASILIA")
                .txid("***")
                .build()
                .toBrCode();

        assertTrue(code.startsWith("000201"), "payload format indicator");
        assertTrue(code.contains("0014br.gov.bcb.pix"), "pix GUI");
        assertTrue(code.contains("5303986"), "currency BRL 986");
        assertTrue(code.contains("5802BR"), "country BR");
        assertTrue(code.matches(".*6304[0-9A-F]{4}$"), "ends with CRC tag + 4 hex");

        // CRC consistency: recomputing CRC over the body must equal the trailing 4 chars.
        String body = code.substring(0, code.length() - 4);
        String trailer = code.substring(code.length() - 4);
        assertEquals(Crc16.hex4(body.getBytes(StandardCharsets.UTF_8)), trailer);
    }

    @Test
    void includesAmountWithTwoDecimalsWhenPresent() {
        String code = PixPayload.builder()
                .key("k").merchantName("M").merchantCity("C")
                .amount(new BigDecimal("10.5"))
                .build().toBrCode();
        assertTrue(code.contains("540510.50"), "amount tag 54, len 05, value 10.50");
    }

    @Test
    void defaultsTxidToTripleStarWhenBlank() {
        String code = PixPayload.builder()
                .key("k").merchantName("M").merchantCity("C")
                .build().toBrCode();
        assertTrue(code.contains("62070503***"), "additional data field with ref '***'");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.sales.pix.PixPayloadTest"`
Expected: FAIL — `PixPayload` cannot be resolved.

- [ ] **Step 3: Write minimal implementation**

```java
package dev.davimf.basebot.modules.sales.pix;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

/**
 * Builds a Pix "BR Code" (EMV®QRCPS) static payload string. Fields are encoded as
 * TLV (id + 2-digit length + value); tag 63 holds the CRC-16 over everything that
 * precedes it (including the "6304" prefix). See BOTSPECS Module 3.
 */
public final class PixPayload {

    private final String key;
    private final String merchantName;
    private final String merchantCity;
    private final BigDecimal amount;     // nullable -> omitted (open amount)
    private final String txid;           // nullable/blank -> "***"
    private final String description;    // nullable -> omitted

    private PixPayload(Builder b) {
        if (b.key == null || b.key.isBlank()) {
            throw new IllegalArgumentException("Pix key is required");
        }
        this.key = b.key.trim();
        this.merchantName = clip(b.merchantName, 25);
        this.merchantCity = clip(b.merchantCity, 15);
        this.amount = b.amount;
        this.txid = b.txid;
        this.description = b.description;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** TLV encode: id + 2-digit length + value. */
    static String emv(String id, String value) {
        return id + String.format("%02d", value.length()) + value;
    }

    public String toBrCode() {
        String merchantAccount = emv("00", "br.gov.bcb.pix") + emv("01", key);
        if (description != null && !description.isBlank()) {
            merchantAccount += emv("02", description.trim());
        }

        StringBuilder sb = new StringBuilder();
        sb.append(emv("00", "01"));               // Payload Format Indicator
        sb.append(emv("26", merchantAccount));    // Merchant Account Information (Pix)
        sb.append(emv("52", "0000"));             // Merchant Category Code
        sb.append(emv("53", "986"));              // Transaction Currency (BRL)
        if (amount != null) {
            sb.append(emv("54", amount.setScale(2, RoundingMode.HALF_UP).toPlainString()));
        }
        sb.append(emv("58", "BR"));               // Country Code
        sb.append(emv("59", merchantName));       // Merchant Name
        sb.append(emv("60", merchantCity));       // Merchant City
        String ref = (txid == null || txid.isBlank()) ? "***" : txid.trim();
        sb.append(emv("62", emv("05", ref)));     // Additional Data Field (reference label)
        sb.append("6304");                         // CRC tag id + length
        sb.append(Crc16.hex4(sb.toString().getBytes(StandardCharsets.UTF_8)));
        return sb.toString();
    }

    private static String clip(String s, int max) {
        String v = (s == null) ? "" : s.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    public static final class Builder {
        private String key;
        private String merchantName = "PIX";
        private String merchantCity = "SAO PAULO";
        private BigDecimal amount;
        private String txid;
        private String description;

        public Builder key(String v) { this.key = v; return this; }
        public Builder merchantName(String v) { this.merchantName = v; return this; }
        public Builder merchantCity(String v) { this.merchantCity = v; return this; }
        public Builder amount(BigDecimal v) { this.amount = v; return this; }
        public Builder txid(String v) { this.txid = v; return this; }
        public Builder description(String v) { this.description = v; return this; }

        public PixPayload build() { return new PixPayload(this); }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.sales.pix.PixPayloadTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/sales/pix/PixPayload.java \
        src/test/java/dev/davimf/basebot/modules/sales/pix/PixPayloadTest.java
git commit -m "feat(pix): add EMV BR Code payload builder"
```

---

### Task 3: QR code PNG renderer (ZXing)

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/sales/pix/PixQrCode.java`
- Test: `src/test/java/dev/davimf/basebot/modules/sales/pix/PixQrCodeTest.java`

**Interfaces:**
- Consumes: `PixPayload.toBrCode()` output (any String).
- Produces: `PixQrCode.pngBytes(String content, int size) -> byte[]` (PNG image bytes).

- [ ] **Step 1: Write the failing test**

```java
package dev.davimf.basebot.modules.sales.pix;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PixQrCodeTest {

    @Test
    void generatedQrDecodesBackToContent() throws Exception {
        String content = "00020126...PIX-TEST-PAYLOAD...6304ABCD";
        byte[] png = PixQrCode.pngBytes(content, 300);

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertTrue(img.getWidth() >= 300 && img.getHeight() >= 300, "image sized");

        Result decoded = new MultiFormatReader().decode(
                new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(img))));
        assertEquals(content, decoded.getText());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.sales.pix.PixQrCodeTest"`
Expected: FAIL — `PixQrCode` cannot be resolved.

- [ ] **Step 3: Write minimal implementation**

```java
package dev.davimf.basebot.modules.sales.pix;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Map;

/** Renders a Pix BR Code string as a PNG QR image using ZXing. */
public final class PixQrCode {

    private PixQrCode() {}

    public static byte[] pngBytes(String content, int size) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);
        try {
            BitMatrix matrix = new QRCodeWriter()
                    .encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render Pix QR code", e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.sales.pix.PixQrCodeTest"`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/sales/pix/PixQrCode.java \
        src/test/java/dev/davimf/basebot/modules/sales/pix/PixQrCodeTest.java
git commit -m "feat(pix): render BR Code as PNG QR via ZXing"
```

---

### Task 4: Pix key store (SQLite, per guild+role)

**Files:**
- Create: `src/main/resources/db/sqlite/002_pix_keys.sql`
- Create: `src/main/java/dev/davimf/basebot/database/model/PixKey.java`
- Create: `src/main/java/dev/davimf/basebot/modules/sales/pix/PixKeyRepository.java`
- Modify: `src/main/java/dev/davimf/basebot/database/sqlite/SqliteMigrator.java` (append the new migration to `MIGRATIONS`)
- Test: `src/test/java/dev/davimf/basebot/modules/sales/pix/PixKeyRepositoryTest.java`

**Interfaces:**
- Consumes: `SqliteManager`, `SqliteMigrator` (existing); `BotConfig.Sqlite` record.
- Produces:
  - `PixKey(String guildId, String roleId, String keyType, String keyValue, String merchantName, String merchantCity)` (record).
  - `PixKeyRepository(SqliteManager)`; `.upsert(PixKey)`; `.findByRole(String guildId, String roleId) -> Optional<PixKey>`.

- [ ] **Step 1: Write the migration + model + the failing test**

`src/main/resources/db/sqlite/002_pix_keys.sql`:

```sql
-- Module 3: Pix keys registered per seller (identified by Discord role). Guild-scoped.
CREATE TABLE IF NOT EXISTS pix_keys (
    guild_id      TEXT NOT NULL,
    role_id       TEXT NOT NULL,
    key_type      TEXT NOT NULL,   -- CPF | CNPJ | EMAIL | PHONE | RANDOM
    key_value     TEXT NOT NULL,
    merchant_name TEXT NOT NULL,
    merchant_city TEXT NOT NULL,
    PRIMARY KEY (guild_id, role_id)
);
```

`src/main/java/dev/davimf/basebot/database/model/PixKey.java`:

```java
package dev.davimf.basebot.database.model;

/** A seller's Pix key, scoped to a guild + role (BOTSPECS Module 3). */
public record PixKey(
        String guildId,
        String roleId,
        String keyType,
        String keyValue,
        String merchantName,
        String merchantCity
) {}
```

`src/test/java/dev/davimf/basebot/modules/sales/pix/PixKeyRepositoryTest.java`:

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
import java.util.Optional;

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

    @Test
    void upsertThenFindByRoleReturnsKey() {
        repo.upsert(new PixKey("g1", "r1", "EMAIL", "a@b.com", "Loja X", "SAO PAULO"));

        Optional<PixKey> found = repo.findByRole("g1", "r1");
        assertTrue(found.isPresent());
        assertEquals("a@b.com", found.get().keyValue());
        assertEquals("Loja X", found.get().merchantName());
    }

    @Test
    void upsertReplacesExistingRow() {
        repo.upsert(new PixKey("g1", "r1", "EMAIL", "old@b.com", "Loja X", "SAO PAULO"));
        repo.upsert(new PixKey("g1", "r1", "RANDOM", "new-key", "Loja Y", "RIO"));

        PixKey k = repo.findByRole("g1", "r1").orElseThrow();
        assertEquals("RANDOM", k.keyType());
        assertEquals("new-key", k.keyValue());
        assertEquals("Loja Y", k.merchantName());
    }

    @Test
    void findByRoleIsGuildScoped() {
        repo.upsert(new PixKey("g1", "r1", "EMAIL", "a@b.com", "Loja X", "SAO PAULO"));
        assertTrue(repo.findByRole("g2", "r1").isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.sales.pix.PixKeyRepositoryTest"`
Expected: FAIL — `PixKeyRepository` cannot be resolved.

- [ ] **Step 3: Add the migration to the runner and implement the repository**

In `SqliteMigrator.java`, change the `MIGRATIONS` list to include the new file:

```java
    private static final List<String> MIGRATIONS = List.of(
            "/db/sqlite/001_init.sql",
            "/db/sqlite/002_pix_keys.sql"
    );
```

`src/main/java/dev/davimf/basebot/modules/sales/pix/PixKeyRepository.java`:

```java
package dev.davimf.basebot.modules.sales.pix;

import dev.davimf.basebot.database.model.PixKey;
import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** SQLite store for seller Pix keys, scoped per guild + role (BOTSPECS Module 3). */
public final class PixKeyRepository {

    private final SqliteManager sqlite;

    public PixKeyRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public void upsert(PixKey k) {
        String sql = """
                INSERT INTO pix_keys
                    (guild_id, role_id, key_type, key_value, merchant_name, merchant_city)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (guild_id, role_id) DO UPDATE SET
                    key_type      = excluded.key_type,
                    key_value     = excluded.key_value,
                    merchant_name = excluded.merchant_name,
                    merchant_city = excluded.merchant_city
                """;
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, k.guildId());
            ps.setString(2, k.roleId());
            ps.setString(3, k.keyType());
            ps.setString(4, k.keyValue());
            ps.setString(5, k.merchantName());
            ps.setString(6, k.merchantCity());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("upsert pix key " + k.guildId() + "/" + k.roleId(), e);
        }
    }

    public Optional<PixKey> findByRole(String guildId, String roleId) {
        String sql = "SELECT * FROM pix_keys WHERE guild_id = ? AND role_id = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new PixKey(
                        rs.getString("guild_id"),
                        rs.getString("role_id"),
                        rs.getString("key_type"),
                        rs.getString("key_value"),
                        rs.getString("merchant_name"),
                        rs.getString("merchant_city")
                ));
            }
        } catch (SQLException e) {
            throw new RepositoryException("find pix key " + guildId + "/" + roleId, e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.sales.pix.PixKeyRepositoryTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/sqlite/002_pix_keys.sql \
        src/main/java/dev/davimf/basebot/database/model/PixKey.java \
        src/main/java/dev/davimf/basebot/modules/sales/pix/PixKeyRepository.java \
        src/main/java/dev/davimf/basebot/database/sqlite/SqliteMigrator.java \
        src/test/java/dev/davimf/basebot/modules/sales/pix/PixKeyRepositoryTest.java
git commit -m "feat(pix): add per-guild/role Pix key SQLite store"
```

---

### Task 5: `/pix` command + owner-restricted confirmation (JDA wiring)

**Files:**
- Create: `src/main/java/dev/davimf/basebot/modules/sales/pix/PixOwnership.java`
- Create: `src/main/java/dev/davimf/basebot/modules/sales/pix/PixCommand.java`
- Create: `src/main/java/dev/davimf/basebot/modules/sales/pix/PixComponentHandler.java`
- Modify: `src/main/java/dev/davimf/basebot/modules/sales/SalesModule.java` (register command + handler)
- Test: `src/test/java/dev/davimf/basebot/modules/sales/pix/PixOwnershipTest.java`

**Interfaces:**
- Consumes: `PixPayload`, `PixQrCode`, `PixKeyRepository`, `PixKey`; `SlashCommand`, `ComponentHandler`, `ComponentId`, `BotContext`, `SqliteManager` (via `ctx.database().sqlite()`).
- Produces:
  - `PixOwnership.isOwner(String clickerId, String ownerId) -> boolean` (security rule).
  - `PixCommand implements SlashCommand` (name `"pix"`, subcommands `registrar`, `gerar`).
  - `PixComponentHandler implements ComponentHandler` (namespace `"pix"`, button action `confirmar` with arg0 = owner user id).

- [ ] **Step 1: Write the failing test**

```java
package dev.davimf.basebot.modules.sales.pix;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PixOwnershipTest {

    @Test
    void ownerMayConfirm() {
        assertTrue(PixOwnership.isOwner("123", "123"));
    }

    @Test
    void nonOwnerMayNotConfirm() {
        assertFalse(PixOwnership.isOwner("999", "123"));
        assertFalse(PixOwnership.isOwner(null, "123"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.sales.pix.PixOwnershipTest"`
Expected: FAIL — `PixOwnership` cannot be resolved.

- [ ] **Step 3: Implement ownership, command, handler, and register them**

`src/main/java/dev/davimf/basebot/modules/sales/pix/PixOwnership.java`:

```java
package dev.davimf.basebot.modules.sales.pix;

/**
 * The Pix confirmation button is restricted strictly to the Pix owner — the member who
 * generated the charge (BOTSPECS Module 3, "Validation").
 */
public final class PixOwnership {

    private PixOwnership() {}

    public static boolean isOwner(String clickerId, String ownerId) {
        return clickerId != null && clickerId.equals(ownerId);
    }
}
```

`src/main/java/dev/davimf/basebot/modules/sales/pix/PixCommand.java`:

```java
package dev.davimf.basebot.modules.sales.pix;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.PixKey;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Module 3 Pix command: {@code /pix registrar} stores a seller's key for a role;
 * {@code /pix gerar} generates a BR Code (copy-paste + QR) with an owner-restricted
 * confirmation button.
 */
public final class PixCommand implements SlashCommand {

    private final PixKeyRepository keys;

    public PixCommand(PixKeyRepository keys) {
        this.keys = keys;
    }

    @Override
    public String name() {
        return "pix";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("pix", "Gerenciar e gerar cobranças Pix.")
                .addSubcommands(
                        new SubcommandData("registrar", "Registra a chave Pix de um cargo (vendedor).")
                                .addOption(OptionType.ROLE, "cargo", "Cargo do vendedor", true)
                                .addOptions(new OptionData(OptionType.STRING, "tipo", "Tipo da chave", true)
                                        .addChoice("CPF", "CPF").addChoice("CNPJ", "CNPJ")
                                        .addChoice("E-mail", "EMAIL").addChoice("Telefone", "PHONE")
                                        .addChoice("Aleatória", "RANDOM"))
                                .addOption(OptionType.STRING, "chave", "Valor da chave Pix", true)
                                .addOption(OptionType.STRING, "nome", "Nome do recebedor (máx 25)", true)
                                .addOption(OptionType.STRING, "cidade", "Cidade do recebedor (máx 15)", true),
                        new SubcommandData("gerar", "Gera uma cobrança Pix para um cargo.")
                                .addOption(OptionType.ROLE, "cargo", "Cargo do vendedor", true)
                                .addOption(OptionType.NUMBER, "valor", "Valor (opcional)", false)
                );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        String sub = event.getSubcommandName();
        if ("registrar".equals(sub)) {
            registrar(event);
        } else if ("gerar".equals(sub)) {
            gerar(event);
        } else {
            event.reply("Subcomando inválido.").setEphemeral(true).queue();
        }
    }

    private void registrar(SlashCommandInteractionEvent event) {
        Role role = event.getOption("cargo", OptionMapping::getAsRole);
        String tipo = event.getOption("tipo", OptionMapping::getAsString);
        String chave = event.getOption("chave", OptionMapping::getAsString);
        String nome = event.getOption("nome", OptionMapping::getAsString);
        String cidade = event.getOption("cidade", OptionMapping::getAsString);

        keys.upsert(new PixKey(event.getGuild().getId(), role.getId(), tipo, chave, nome, cidade));
        event.reply("Chave Pix registrada para o cargo " + role.getAsMention() + ".")
                .setEphemeral(true).queue();
    }

    private void gerar(SlashCommandInteractionEvent event) {
        Role role = event.getOption("cargo", OptionMapping::getAsRole);
        Optional<PixKey> maybe = keys.findByRole(event.getGuild().getId(), role.getId());
        if (maybe.isEmpty()) {
            event.reply("Nenhuma chave Pix registrada para " + role.getAsMention()
                    + ". Use /pix registrar primeiro.").setEphemeral(true).queue();
            return;
        }
        PixKey key = maybe.get();

        Double valor = event.getOption("valor", OptionMapping::getAsDouble);
        PixPayload.Builder builder = PixPayload.builder()
                .key(key.keyValue())
                .merchantName(key.merchantName())
                .merchantCity(key.merchantCity());
        if (valor != null && valor > 0) {
            builder.amount(BigDecimal.valueOf(valor));
        }
        String brCode = builder.build().toBrCode();
        byte[] qrPng = PixQrCode.pngBytes(brCode, 360);

        String ownerId = event.getUser().getId();
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Cobrança Pix")
                .setDescription("**Pix Copia e Cola:**\n```" + brCode + "```")
                .setImage("attachment://pix.png")
                .setColor(0x00B894);
        if (valor != null && valor > 0) {
            embed.addField("Valor", "R$ " + String.format("%.2f", valor), true);
        }

        Button confirm = Button.success(
                ComponentId.of("pix", "confirmar", ownerId), "Confirmar Pagamento");

        event.replyEmbeds(embed.build())
                .addFiles(FileUpload.fromData(qrPng, "pix.png"))
                .addActionRow(confirm)
                .queue();
    }
}
```

`src/main/java/dev/davimf/basebot/modules/sales/pix/PixComponentHandler.java`:

```java
package dev.davimf.basebot.modules.sales.pix;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/** Handles the Pix confirmation button, restricted to the Pix owner (BOTSPECS Module 3). */
public final class PixComponentHandler implements ComponentHandler {

    @Override
    public String namespace() {
        return "pix";
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"confirmar".equals(id.action())) {
            return;
        }
        String ownerId = id.arg(0);
        if (!PixOwnership.isOwner(event.getUser().getId(), ownerId)) {
            event.reply("Apenas o dono do Pix pode confirmar este pagamento.")
                    .setEphemeral(true).queue();
            return;
        }
        event.reply("Pagamento confirmado pelo dono do Pix.").queue();
    }
}
```

In `SalesModule.java`, replace the body of `register(...)` with the wiring (keep the class/imports otherwise intact):

```java
    @Override
    public void register(ModuleRegistry registry, BotContext ctx) {
        PixKeyRepository pixKeys = new PixKeyRepository(ctx.database().sqlite());
        registry.command(new dev.davimf.basebot.modules.sales.pix.PixCommand(pixKeys));
        registry.component(new dev.davimf.basebot.modules.sales.pix.PixComponentHandler());

        // TODO(Module 3): /orçamento (selector + approval embed + auto-dispatch Pix),
        // /tabela (paginated catalog). Budget expiry uses ctx.scheduler() to auto-cancel
        // rows past `expires_at` in the `budgets` table.
    }
```

Add the import near the other imports in `SalesModule.java`:

```java
import dev.davimf.basebot.modules.sales.pix.PixKeyRepository;
```

- [ ] **Step 4: Run the focused test, then the full build**

Run: `./gradlew test --tests "dev.davimf.basebot.modules.sales.pix.PixOwnershipTest"`
Expected: PASS (2 tests).

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (compiles, all Pix tests + the existing crypto test pass, `basebot.jar` produced).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/davimf/basebot/modules/sales/pix/PixOwnership.java \
        src/main/java/dev/davimf/basebot/modules/sales/pix/PixCommand.java \
        src/main/java/dev/davimf/basebot/modules/sales/pix/PixComponentHandler.java \
        src/main/java/dev/davimf/basebot/modules/sales/SalesModule.java \
        src/test/java/dev/davimf/basebot/modules/sales/pix/PixOwnershipTest.java
git commit -m "feat(pix): wire /pix command and owner-restricted confirmation"
```

---

## Self-Review

**Spec coverage (Module 3 — Pix & Payments):**
- "Registration: /pix configures keys per seller (identified by Role)" → Task 4 (store) + Task 5 (`/pix registrar`). ✓
- "Generation: ZXing to generate Pix Payload (BR Code/EMV), QR Code Image, and Copy-Paste block" → Task 1+2 (EMV payload + CRC), Task 3 (QR PNG), Task 5 (`/pix gerar` embed with copy-paste + QR). ✓
- "Validation: Confirmation button restricted strictly to the Pix owner" → Task 5 (`PixOwnership` + `PixComponentHandler`). ✓
- Out of scope for this plan (separate future plans): Budgets/Orçamentos, Product Catalog/Tabela. Noted as TODOs in `SalesModule`.

**Placeholder scan:** No TBD/TODO-in-code/"add error handling" placeholders; every code step is complete. (The `SalesModule` TODO comments are intentional scope markers for *other* features, not gaps in this plan.)

**Type consistency:** `PixPayload.builder()` / `.toBrCode()`, `PixQrCode.pngBytes(String,int)`, `PixKeyRepository.upsert/findByRole`, `PixKey` 6-arg record, `PixOwnership.isOwner(String,String)`, `ComponentId.of("pix","confirmar",ownerId)` / `id.arg(0)` are used identically across the tasks that define and consume them. ✓
