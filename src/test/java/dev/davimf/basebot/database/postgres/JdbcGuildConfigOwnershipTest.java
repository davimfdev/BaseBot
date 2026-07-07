package dev.davimf.basebot.database.postgres;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcGuildConfigOwnershipTest {

    @Test
    void atomicClaimAndSaveFailureIsNeverSilentlyIgnored() {
        List<String> preparedSql = new ArrayList<>();
        Connection connection = connection(preparedSql, false);
        JdbcGuildConfigRepository repository = new JdbcGuildConfigRepository(() -> connection, "00000000-0000-0000-0000-000000000001");

        GuildConfig config = new GuildConfig("guild-1", null, null, Map.of(), Map.of(), Map.of(), List.of(), Map.of());
        assertThrows(RepositoryException.class, () -> repository.save(config));

        assertEquals(1, preparedSql.size());
        assertTrue(preparedSql.getFirst().contains("WITH claimed AS"));
        assertTrue(preparedSql.getFirst().contains("INSERT INTO guild_config"));
    }

    @Test
    void claimAndConfigSaveUseOneAtomicStatement() {
        List<String> preparedSql = new ArrayList<>();
        Connection connection = connection(preparedSql, true);
        JdbcGuildConfigRepository repository = new JdbcGuildConfigRepository(() -> connection,
                "00000000-0000-0000-0000-000000000001");

        repository.save(new GuildConfig("guild-1", null, null,
                Map.of("log-mensagens", "channel-1"), Map.of(), Map.of(), List.of(), Map.of()));

        assertEquals(1, preparedSql.size());
        String sql = preparedSql.getFirst();
        assertTrue(sql.contains("WITH claimed AS"));
        assertTrue(sql.contains("bot_instance_id = EXCLUDED.bot_instance_id"));
        assertTrue(sql.contains("INSERT INTO guild_config"));
    }

    @Test
    void claimsPreviouslyUnregisteredGuildBeforeSavingConfig() {
        List<String> preparedSql = new ArrayList<>();
        Connection connection = connection(preparedSql, true);
        JdbcGuildConfigRepository repository = new JdbcGuildConfigRepository(() -> connection,
                "00000000-0000-0000-0000-000000000001");

        repository.save(new GuildConfig("new-guild", null, null,
                Map.of("log-mensagens", "channel-1"), Map.of(), Map.of(), List.of(), Map.of()));

        assertTrue(preparedSql.get(0).contains("INSERT INTO bot_guilds"));
        assertTrue(preparedSql.stream().anyMatch(sql -> sql.contains("INSERT INTO guild_config")));
    }

    private static Connection connection(List<String> preparedSql, boolean ownsGuild) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            if (method.getName().equals("prepareStatement")) {
                String sql = String.valueOf(args[0]);
                preparedSql.add(sql);
                return preparedStatement(ownsGuild);
            }
            if (method.getName().equals("close")) return null;
            return defaultValue(method.getReturnType());
        });
    }

    private static PreparedStatement preparedStatement(boolean ownsGuild) {
        return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
            if (method.getName().equals("executeQuery")) return resultSet(ownsGuild);
            if (method.getName().equals("executeUpdate")) return ownsGuild ? 1 : 0;
            if (method.getName().equals("close")) return null;
            return defaultValue(method.getReturnType());
        });
    }

    private static ResultSet resultSet(boolean ownsGuild) {
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
            if (method.getName().equals("next")) return ownsGuild;
            if (method.getName().equals("close")) return null;
            return defaultValue(method.getReturnType());
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class || type == short.class || type == byte.class || type == long.class) return 0;
        if (type == float.class || type == double.class) return 0d;
        return null;
    }
}
