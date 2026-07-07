package dev.davimf.basebot.database.sqlite;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.modules.base.security.VerificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FullMigrationSmokeTest {

    @Test
    void fullChainAppliesOnFreshDb(@TempDir Path dir) {
        SqliteManager sqlite = new SqliteManager(
                new BotConfig.Sqlite(dir.resolve("smoke.db").toString()));
        try {
            assertDoesNotThrow(
                    () -> new SqliteMigrator(sqlite).migrate(),
                    "A cadeia 001..038 deve aplicar sem erro em um DB SQLite limpo");

            // 038_verification.sql precisa ter criado a tabela usada pelo repositório.
            VerificationRepository repo = new VerificationRepository(sqlite);
            assertFalse(repo.isVerified("g1", "u1"),
                    "Consulta na tabela de verificação deve funcionar após migrar");
        } finally {
            sqlite.close();
        }
    }
}
