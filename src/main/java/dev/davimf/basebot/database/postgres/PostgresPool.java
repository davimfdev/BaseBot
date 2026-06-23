package dev.davimf.basebot.database.postgres;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.davimf.basebot.config.BotConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * HikariCP-backed connection pool for the PostgreSQL source-of-truth (guild config).
 *
 * <p>Deliberately a thin wrapper over a plain JDBC pool. The bot never speaks the
 * Supabase REST API; it connects to Postgres directly, so the planned Supabase -&gt;
 * Neon migration is purely a change of JDBC URL/credentials in config — no code here
 * needs to change.
 */
public final class PostgresPool implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final String schema;

    public PostgresPool(BotConfig.Postgres cfg) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(cfg.url());
        hc.setUsername(cfg.username());
        hc.setPassword(cfg.password());
        hc.setMaximumPoolSize(cfg.maxPoolSize());
        hc.setPoolName("basebot-postgres");
        // Neon's serverless proxy benefits from validation + a bounded connection lifetime.
        hc.setConnectionTimeout(10_000);
        hc.setKeepaliveTime(30_000);
        hc.setMaxLifetime(300_000);
        if (cfg.schema() != null && !cfg.schema().isBlank()) {
            hc.setSchema(cfg.schema());
        }
        this.schema = cfg.schema();
        this.dataSource = new HikariDataSource(hc);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public String schema() {
        return schema;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
