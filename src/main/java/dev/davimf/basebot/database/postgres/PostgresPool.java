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
        // Accept either a jdbc: URL (with separate user/password) or a Neon/libpq
        // connection string with credentials embedded; explicit user/password win.
        PostgresUrl.Parsed url = PostgresUrl.normalize(cfg.url(), cfg.username(), cfg.password());

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(url.jdbcUrl());
        if (url.user() != null) {
            hc.setUsername(url.user());
        }
        if (url.password() != null) {
            hc.setPassword(url.password());
        }
        hc.setMaximumPoolSize(cfg.maxPoolSize());
        hc.setPoolName("basebot-postgres");
        // Neon's serverless compute suspends after a few minutes idle and closes every
        // connection. Letting the pool drain to zero idle connections means there are no
        // stale connections left for the housekeeper to find (and warn about) on suspend;
        // new ones are opened on demand. maxLifetime stays comfortably under Neon's window.
        // Keepalive is disabled (0): a pool that drains to zero has nothing to keep alive, and a
        // periodic keepalive probe would defeat the whole point by pinging Neon and blocking the
        // autosuspend we want. Connections are validated on borrow instead.
        hc.setConnectionTimeout(10_000);
        hc.setMinimumIdle(0);
        hc.setIdleTimeout(60_000);
        hc.setKeepaliveTime(0);
        hc.setMaxLifetime(240_000);
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
