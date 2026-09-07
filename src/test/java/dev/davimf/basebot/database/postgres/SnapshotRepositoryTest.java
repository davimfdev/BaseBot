package dev.davimf.basebot.database.postgres;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the diff-based snapshot writes at the SQL level (no real Postgres): the reconcile must
 * upsert with skip-unchanged + delete only absent rows in one committed transaction, never a
 * blanket DELETE, and roll back preserving the previous snapshot on failure. Follows the repo's
 * java.lang.reflect.Proxy convention for mocking JDBC (see JdbcGuildConfigOwnershipTest).
 */
class SnapshotRepositoryTest {

    private static final String INSTANCE = "00000000-0000-0000-0000-000000000001";

    private static final SnapshotRepository.ChannelRow CH1 =
            new SnapshotRepository.ChannelRow("c1", "geral", "TEXT", null, 0, true, true);
    private static final SnapshotRepository.ChannelRow CH2 =
            new SnapshotRepository.ChannelRow("c2", "voz", "VOICE", null, 1, true, false);
    private static final SnapshotRepository.RoleRow R1 =
            new SnapshotRepository.RoleRow("r1", "Admin", 5, false, true);

    // --- full reconcile ---

    @Test
    void reconcileChannelsUpsertsThenDeletesAbsentAndCommits() {
        Recorder rec = new Recorder(true, false);
        SnapshotRepository repo = new SnapshotRepository(rec::connection, INSTANCE);

        SnapshotRepository.SyncCounts counts = repo.reconcileChannels("g1", List.of(CH1, CH2));

        assertTrue(hasSql(rec, "ON CONFLICT (guild_id, channel_id)"), "usa upsert on-conflict");
        assertTrue(hasSql(rec, "IS DISTINCT FROM"), "pula linhas inalteradas");
        assertTrue(hasSql(rec, "channel_id <> ALL(?)"), "apaga só ausentes, parametrizado");
        assertFalse(hasExactSql(rec, "DELETE FROM guild_channels_snapshot WHERE guild_id = ?"),
                "nunca faz DELETE em massa da guild");
        assertTrue(rec.committed, "commit único ao final");
        assertFalse(rec.rolledBack, "sem rollback no caminho feliz");
        assertEquals(2, counts.total());
    }

    @Test
    void reconcileRolesUsesRoleUpsertAndScopedDelete() {
        Recorder rec = new Recorder(true, false);
        SnapshotRepository repo = new SnapshotRepository(rec::connection, INSTANCE);

        repo.reconcileRoles("g1", List.of(R1));

        assertTrue(hasSql(rec, "ON CONFLICT (guild_id, role_id)"));
        assertTrue(hasSql(rec, "role_id <> ALL(?)"));
        assertFalse(hasExactSql(rec, "DELETE FROM guild_roles_snapshot WHERE guild_id = ?"));
        assertTrue(rec.committed);
    }

    @Test
    void reconcileEmptyStillUsesScopedDeleteNotBlanket() {
        Recorder rec = new Recorder(true, false);
        SnapshotRepository repo = new SnapshotRepository(rec::connection, INSTANCE);

        repo.reconcileChannels("g1", List.of()); // guild ficou sem canais

        assertTrue(hasSql(rec, "channel_id <> ALL(?)"));
        assertFalse(hasExactSql(rec, "DELETE FROM guild_channels_snapshot WHERE guild_id = ?"));
        assertTrue(rec.committed);
    }

    @Test
    void reconcileByAnotherInstanceWritesNothing() {
        Recorder rec = new Recorder(false, false); // não é dono da guild
        SnapshotRepository repo = new SnapshotRepository(rec::connection, INSTANCE);

        SnapshotRepository.SyncCounts counts = repo.reconcileChannels("g1", List.of(CH1));

        assertFalse(hasSql(rec, "INSERT INTO guild_channels_snapshot"), "não escreve sem posse");
        assertFalse(hasSql(rec, "DELETE FROM"), "não apaga sem posse");
        assertTrue(rec.rolledBack);
        assertFalse(rec.committed);
        assertEquals(0, counts.written());
        assertEquals(0, counts.deleted());
    }

    @Test
    void reconcileRollsBackAndPreservesSnapshotOnBatchFailure() {
        Recorder rec = new Recorder(true, true); // executeBatch lança
        SnapshotRepository repo = new SnapshotRepository(rec::connection, INSTANCE);

        assertThrows(RepositoryException.class, () -> repo.reconcileChannels("g1", List.of(CH1, CH2)));

        assertTrue(rec.rolledBack, "falha parcial faz rollback");
        assertFalse(rec.committed, "nada é commitado em falha");
        // Nenhum delete-em-massa foi sequer preparado antes do upsert, então o snapshot anterior
        // permanece intacto.
        assertFalse(hasExactSql(rec, "DELETE FROM guild_channels_snapshot WHERE guild_id = ?"));
    }

    // --- single-entity incremental ---

    @Test
    void upsertChannelTouchesOnlyThatChannelWhenOwner() {
        Recorder rec = new Recorder(true, false);
        SnapshotRepository repo = new SnapshotRepository(rec::connection, INSTANCE);

        repo.upsertChannel("g1", CH1);

        assertTrue(hasSql(rec, "ON CONFLICT (guild_id, channel_id)"));
        assertFalse(hasSql(rec, "DELETE FROM"), "upsert de 1 canal não apaga nada");
    }

    @Test
    void upsertChannelByAnotherInstanceIsNoop() {
        Recorder rec = new Recorder(false, false);
        SnapshotRepository repo = new SnapshotRepository(rec::connection, INSTANCE);

        repo.upsertChannel("g1", CH1);

        assertFalse(hasSql(rec, "INSERT INTO guild_channels_snapshot"));
    }

    @Test
    void deleteChannelIsScopedByGuildAndChannel() {
        Recorder rec = new Recorder(true, false);
        SnapshotRepository repo = new SnapshotRepository(rec::connection, INSTANCE);

        repo.deleteChannel("g1", "c1");

        assertTrue(hasExactSql(rec, "DELETE FROM guild_channels_snapshot WHERE guild_id = ? AND channel_id = ?"));
    }

    @Test
    void deleteRoleIsScopedByGuildAndRole() {
        Recorder rec = new Recorder(true, false);
        SnapshotRepository repo = new SnapshotRepository(rec::connection, INSTANCE);

        repo.deleteRole("g1", "r1");

        assertTrue(hasExactSql(rec, "DELETE FROM guild_roles_snapshot WHERE guild_id = ? AND role_id = ?"));
    }

    private static boolean hasSql(Recorder rec, String needle) {
        return rec.preparedSql.stream().anyMatch(s -> s.contains(needle));
    }

    private static boolean hasExactSql(Recorder rec, String exact) {
        return rec.preparedSql.stream().map(s -> s.replaceAll("\\s+", " ").trim())
                .anyMatch(s -> s.equals(exact));
    }

    /** Records prepared SQL + commit/rollback; ownership and batch-failure are configurable. */
    private static final class Recorder {
        final List<String> preparedSql = new ArrayList<>();
        final boolean ownsGuild;
        final boolean failBatch;
        boolean committed;
        boolean rolledBack;

        Recorder(boolean ownsGuild, boolean failBatch) {
            this.ownsGuild = ownsGuild;
            this.failBatch = failBatch;
        }

        Connection connection() {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "prepareStatement":
                                String sql = String.valueOf(args[0]);
                                preparedSql.add(sql);
                                return preparedStatement(sql);
                            case "getAutoCommit": return true;
                            case "commit": committed = true; return null;
                            case "rollback": rolledBack = true; return null;
                            case "createArrayOf": return array();
                            case "setAutoCommit":
                            case "close": return null;
                            default: return defaultValue(method.getReturnType());
                        }
                    });
        }

        private PreparedStatement preparedStatement(String sql) {
            boolean isUpsert = sql.contains("ON CONFLICT");
            return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "executeQuery": return resultSet(); // ownership probe
                            case "executeBatch":
                                if (failBatch && isUpsert) throw new SQLException("boom");
                                return new int[]{1, 1};
                            case "executeUpdate": return 1;
                            case "close":
                            case "addBatch": return null;
                            default: return defaultValue(method.getReturnType());
                        }
                    });
        }

        private ResultSet resultSet() {
            return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
                        if (method.getName().equals("next")) return ownsGuild;
                        if (method.getName().equals("close")) return null;
                        return defaultValue(method.getReturnType());
                    });
        }

        private static Array array() {
            return (Array) Proxy.newProxyInstance(Array.class.getClassLoader(),
                    new Class<?>[]{Array.class}, (proxy, method, args) -> null);
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class || type == short.class || type == byte.class || type == long.class) return 0;
        if (type == float.class || type == double.class) return 0d;
        return null;
    }
}
