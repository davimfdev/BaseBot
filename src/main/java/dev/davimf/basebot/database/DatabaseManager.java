package dev.davimf.basebot.database;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.postgres.GuildConfigRepository;
import dev.davimf.basebot.database.postgres.JdbcGuildConfigRepository;
import dev.davimf.basebot.database.postgres.PostgresPool;
import dev.davimf.basebot.database.sqlite.ActionLogRepository;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import dev.davimf.basebot.database.sqlite.TicketRepository;
import dev.davimf.basebot.modules.base.voice.VoiceMuteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single entry point to all persistence. Owns the Postgres pool (guild config source
 * of truth) and the SQLite manager (fast local state), exposes repositories, and runs
 * local migrations on startup.
 *
 * <p>Repositories are exposed via interfaces where the backing store may change
 * ({@link GuildConfigRepository} — Supabase today, Neon later) so feature modules
 * never depend on a concrete driver.
 */
public final class DatabaseManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private final PostgresPool postgres;
    private final SqliteManager sqlite;

    private final GuildConfigRepository guildConfig;
    private final TicketRepository tickets;
    private final ActionLogRepository actionLogs;
    private final VoiceMuteRepository voiceMutes;

    public DatabaseManager(BotConfig config) {
        log.info("Initializing databases...");
        this.postgres = new PostgresPool(config.postgres());
        this.sqlite = new SqliteManager(config.sqlite());

        // Local schema is bot-owned -> migrate automatically. Postgres schema is shared
        // with the dashboard, so it is applied manually (see resources/db/postgres).
        new SqliteMigrator(sqlite).migrate();

        this.guildConfig = new JdbcGuildConfigRepository(postgres);
        this.tickets = new TicketRepository(sqlite);
        this.actionLogs = new ActionLogRepository(sqlite);
        this.voiceMutes = new VoiceMuteRepository(sqlite);
        log.info("Databases ready.");
    }

    public GuildConfigRepository guildConfig() {
        return guildConfig;
    }

    public TicketRepository tickets() {
        return tickets;
    }

    public ActionLogRepository actionLogs() {
        return actionLogs;
    }

    public VoiceMuteRepository voiceMutes() {
        return voiceMutes;
    }

    public PostgresPool postgres() {
        return postgres;
    }

    public SqliteManager sqlite() {
        return sqlite;
    }

    @Override
    public void close() {
        log.info("Closing databases...");
        sqlite.close();
        postgres.close();
    }
}
