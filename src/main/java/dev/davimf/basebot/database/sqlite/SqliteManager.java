// [OUTLINE START]
// Package: dev.davimf.basebot.database.sqlite
// 
// Class: SqliteManager
// 
// Constructors:
//   - `Constructor` : `public SqliteManager(BotConfig.Sqlite cfg)`
// 
// Methods:
//   - `Method` : `public Connection getConnection()`
// 
// Fields:
//   - `Field` : `private final HikariDataSource dataSource`
// [OUTLINE END]



package dev.davimf.basebot.database.sqlite;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.davimf.basebot.config.BotConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Manages the local SQLite database used for fast transactions: active tickets,
 * action logs and ephemeral state (BOTSPECS Module 2/4).
 *
 * <p>WAL journaling + a busy timeout are enabled on every connection so concurrent
 * readers don't block the single writer and short write contention retries instead
 * of throwing {@code SQLITE_BUSY}.
 */
public final class SqliteManager implements AutoCloseable {

    private final HikariDataSource dataSource;

    public SqliteManager(BotConfig.Sqlite cfg) {
        Path dbPath = Path.of(cfg.path());
        ensureParentDir(dbPath);

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite:" + dbPath);
        hc.setPoolName("basebot-sqlite");
        // SQLite is single-writer; a small pool avoids excessive lock contention while
        // still allowing concurrent WAL reads.
        hc.setMaximumPoolSize(4);
        hc.setConnectionInitSql(
                "PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL; "
                        + "PRAGMA foreign_keys=ON; PRAGMA busy_timeout=5000;");
        this.dataSource = new HikariDataSource(hc);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private static void ensureParentDir(Path dbPath) {
        Path parent = dbPath.toAbsolutePath().getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot create SQLite directory: " + parent, e);
            }
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
