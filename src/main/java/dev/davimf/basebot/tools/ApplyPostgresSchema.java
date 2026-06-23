package dev.davimf.basebot.tools;

import dev.davimf.basebot.config.DotEnv;
import dev.davimf.basebot.database.postgres.PostgresMigrator;
import dev.davimf.basebot.database.postgres.PostgresUrl;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * One-off tool to create/update the guild-config Postgres schema using the connection
 * string from {@code .env} (POSTGRES_URL, optionally POSTGRES_USER/POSTGRES_PASSWORD).
 *
 * <p>Run from the project root so {@code .env} is found:
 * <pre>java -cp build/libs/basebot.jar dev.davimf.basebot.tools.ApplyPostgresSchema</pre>
 * Idempotent — safe to re-run. Only the bot's guild-config DB is touched (the ticket
 * transcript DB is a separate Neon database owned by the dashboard).
 */
public final class ApplyPostgresSchema {

    private ApplyPostgresSchema() {}

    public static void main(String[] args) throws Exception {
        DotEnv env = DotEnv.load(Path.of(".env"));
        String rawUrl = env.get("POSTGRES_URL");
        if (rawUrl == null || rawUrl.isBlank()) {
            System.err.println("POSTGRES_URL não configurada (defina no .env).");
            System.exit(1);
            return;
        }
        PostgresUrl.Parsed pg = PostgresUrl.normalize(rawUrl, env.get("POSTGRES_USER"), env.get("POSTGRES_PASSWORD"));
        // jdbcUrl carries no credentials (they are passed separately), so this is safe to print.
        System.out.println("Conectando a: " + pg.jdbcUrl());

        try (Connection c = DriverManager.getConnection(pg.jdbcUrl(), pg.user(), pg.password())) {
            List<String> applied = PostgresMigrator.migrate(c);
            if (applied.isEmpty()) {
                System.out.println("Nenhuma migração nova — o schema já está atualizado.");
            } else {
                applied.forEach(m -> System.out.println("Aplicada: " + m));
            }
            verify(c, "guild_config");
            verify(c, "schema_migrations");
            printColumns(c, "guild_config");
        }
        System.out.println("Concluído com sucesso.");
    }

    private static void printColumns(Connection c, String table) throws Exception {
        System.out.println("Colunas de " + table + ":");
        String sql = "SELECT column_name, data_type FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name = ? ORDER BY ordinal_position";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println("  - " + rs.getString(1) + " (" + rs.getString(2) + ")");
                }
            }
        }
    }

    private static void verify(Connection c, String table) throws Exception {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT to_regclass('public." + table + "') IS NOT NULL AS ok")) {
            rs.next();
            System.out.println("Tabela " + table + " existe: " + rs.getBoolean("ok"));
        }
    }
}
