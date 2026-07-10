package dev.davimf.basebot.database.sqlite;

import dev.davimf.basebot.config.BotConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Regressao para o backfill feito por 039_voice_time.sql:
 *
 *   ALTER TABLE voice_sessions ADD COLUMN time_credited_until INTEGER NOT NULL DEFAULT 0;
 *   UPDATE voice_sessions SET time_credited_until = xp_credited_until WHERE time_credited_until = 0;
 *
 * O ticker de tempo de voz calcula a janela [time_credited_until, now]. Se uma sessao
 * OPEN pre-existente ficasse com time_credited_until = 0, a janela cobriria desde o
 * epoch Unix ate agora, creditando decadas de tempo de call fictício. Este teste prova
 * que o UPDATE de backfill copia xp_credited_until para as linhas zeradas e nao mexe
 * nas linhas que ja tinham um watermark de tempo definido.
 *
 * <p>O SQL do backfill NAO e duplicado aqui: e lido diretamente de 039_voice_time.sql no
 * classpath, para que uma mudanca na migration quebre este teste em vez de deixa-lo testando
 * um UPDATE que ja nao existe mais.
 */
class VoiceTimeBackfillTest {

    private static final String MIGRATION_RESOURCE = "/db/sqlite/039_voice_time.sql";

    private static final String BACKFILL_SQL = loadBackfillSqlFromMigration();

    private SqliteManager sqlite;

    private static String loadBackfillSqlFromMigration() {
        String migrationSql;
        try (InputStream in = VoiceTimeBackfillTest.class.getResourceAsStream(MIGRATION_RESOURCE)) {
            if (in == null) {
                throw new AssertionError("Migration nao encontrada no classpath: " + MIGRATION_RESOURCE);
            }
            migrationSql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Falha ao ler " + MIGRATION_RESOURCE, e);
        }

        return Arrays.stream(migrationSql.split(";"))
                .map(String::trim)
                .filter(stmt -> stmt.regionMatches(true, 0, "UPDATE voice_sessions", 0, "UPDATE voice_sessions".length()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Nenhum UPDATE voice_sessions encontrado em " + MIGRATION_RESOURCE
                                + " — a migration mudou? Atualize este teste."));
    }

    @BeforeEach
    void setUp(@TempDir Path dir) {
        sqlite = new SqliteManager(new BotConfig.Sqlite(dir.resolve("t.db").toString()));
        new SqliteMigrator(sqlite).migrate();
    }

    @AfterEach
    void tearDown() { sqlite.close(); }

    @Test
    void backfillCopiesXpWatermarkOntoPreExistingRows() throws Exception {
        long xpCreditedUntil = 1_700_000_000_000L;

        try (Connection c = sqlite.getConnection()) {
            long id = insertVoiceSession(c, "g1", "u1", xpCreditedUntil);
            // Simula o estado pre-migration: coluna nova recem-criada com o default 0.
            setTimeCreditedUntil(c, id, 0);

            try (PreparedStatement ps = c.prepareStatement(BACKFILL_SQL)) {
                ps.executeUpdate();
            }

            long timeCreditedUntil = readTimeCreditedUntil(c, id);
            assertEquals(xpCreditedUntil, timeCreditedUntil,
                    "Backfill deve copiar xp_credited_until para time_credited_until");
            assertNotEquals(0L, timeCreditedUntil,
                    "Linha pre-existente nao pode ficar com time_credited_until = 0 apos o backfill");
        }
    }

    @Test
    void backfillDoesNotClobberRowsThatAlreadyHaveATimeWatermark() throws Exception {
        try (Connection c = sqlite.getConnection()) {
            long id = insertVoiceSession(c, "g1", "u2", 1_700_000_000_000L);
            setTimeCreditedUntil(c, id, 12345);

            try (PreparedStatement ps = c.prepareStatement(BACKFILL_SQL)) {
                ps.executeUpdate();
            }

            assertEquals(12345L, readTimeCreditedUntil(c, id),
                    "Backfill nao deve sobrescrever linhas que ja tem time_credited_until != 0");
        }
    }

    private long insertVoiceSession(Connection c, String guildId, String userId, long xpCreditedUntil) throws Exception {
        String sql = "INSERT INTO voice_sessions (guild_id, user_id, channel_id, join_time, leave_time, xp_credited_until) "
                + "VALUES (?, ?, 'ch1', 1700000000000, NULL, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setLong(3, xpCreditedUntil);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private void setTimeCreditedUntil(Connection c, long id, long value) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE voice_sessions SET time_credited_until = ? WHERE id = ?")) {
            ps.setLong(1, value);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private long readTimeCreditedUntil(Connection c, long id) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT time_credited_until FROM voice_sessions WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
