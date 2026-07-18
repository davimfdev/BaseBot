// [OUTLINE START]
// Package: dev.davimf.basebot.database
// 
// Class: DatabaseManager
// 
// Constructors:
//   - `Constructor` : `public DatabaseManager(BotConfig config)`
// 
// Methods:
//   - `Method` : `private static final Logger log = LoggerFactory. getLogger(DatabaseManager.class)`
//   - `Method` : `public GuildConfigRepository guildConfig()`
//   - `Method` : `public TicketRepository tickets()`
//   - `Method` : `public ActionLogRepository actionLogs()`
//   - `Method` : `public MuteRepository mutes()`
//   - `Method` : `public TicketCategoryRepository ticketCategories()`
//   - `Method` : `public ActionTypeRepository actionTypes()`
//   - `Method` : `public PostgresPool postgres()`
//   - `Method` : `public SqliteManager sqlite()`
// 
// Fields:
//   - `Field` : `private final PostgresPool postgres`
//   - `Field` : `private final SqliteManager sqlite`
//   - `Field` : `private final GuildConfigRepository guildConfig`
//   - `Field` : `private final TicketRepository tickets`
//   - `Field` : `private final ActionLogRepository actionLogs`
//   - `Field` : `private final MuteRepository mutes`
//   - `Field` : `private final TicketCategoryRepository ticketCategories`
//   - `Field` : `private final ActionTypeRepository actionTypes`
// [OUTLINE END]



package dev.davimf.basebot.database;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.database.postgres.CachingGuildConfigRepository;
import dev.davimf.basebot.database.postgres.GuildConfigRepository;
import dev.davimf.basebot.database.postgres.JdbcGuildConfigRepository;
import dev.davimf.basebot.database.postgres.PostgresPool;
import dev.davimf.basebot.database.sqlite.ActionLogRepository;
import dev.davimf.basebot.database.postgres.ActionTypeRepository;
import dev.davimf.basebot.database.sqlite.MessageArchiveRepository;
import dev.davimf.basebot.database.sqlite.LocalInstanceRepository;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.database.sqlite.SqliteMigrator;
import dev.davimf.basebot.database.sqlite.TicketRepository;
import dev.davimf.basebot.database.postgres.VerificationQuestionRepository;
import dev.davimf.basebot.database.postgres.VipPlanRepository;
import dev.davimf.basebot.database.postgres.JdbcVipPlanRepository;
import dev.davimf.basebot.database.postgres.VipGrantRepository;
import dev.davimf.basebot.database.postgres.JdbcVipGrantRepository;
import dev.davimf.basebot.modules.base.security.VerificationRepository;
import dev.davimf.basebot.modules.base.voice.MuteRepository;
import dev.davimf.basebot.modules.tickets.TicketCategoryRepository;
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
    private final MuteRepository mutes;
    private final TicketCategoryRepository ticketCategories;
    private final ActionTypeRepository actionTypes;
    private final MessageArchiveRepository messageArchive;
    private final VerificationRepository verification;
    private final VerificationQuestionRepository verificationQuestions;
    private final VipPlanRepository vipPlans;
    private final VipGrantRepository vipGrants;

    public DatabaseManager(BotConfig config) {
        log.info("Initializing databases...");
        this.postgres = new PostgresPool(config.postgres());
        this.sqlite = new SqliteManager(config.sqlite());

        // Local schema is bot-owned -> migrate automatically. Postgres schema is shared
        // with the dashboard, so it is applied manually (see resources/db/postgres).
        new SqliteMigrator(sqlite).migrate();

        String configuredInstanceId = config.instance().instanceId();
        String botInstanceId = configuredInstanceId != null && !configuredInstanceId.isBlank()
                ? configuredInstanceId
                : new LocalInstanceRepository(sqlite).getOrCreate();
        this.guildConfig = new CachingGuildConfigRepository(new JdbcGuildConfigRepository(postgres, botInstanceId));
        this.tickets = new TicketRepository(sqlite);
        this.actionLogs = new ActionLogRepository(sqlite);
        this.mutes = new MuteRepository(sqlite);
        this.ticketCategories = new TicketCategoryRepository(postgres);
        this.actionTypes = new ActionTypeRepository(postgres);
        this.messageArchive = new MessageArchiveRepository(sqlite);
        this.verification = new VerificationRepository(sqlite);
        this.verificationQuestions = new VerificationQuestionRepository(postgres);
        this.vipPlans = new JdbcVipPlanRepository(postgres);
        this.vipGrants = new JdbcVipGrantRepository(postgres);
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

    public MuteRepository mutes() {
        return mutes;
    }

    public TicketCategoryRepository ticketCategories() {
        return ticketCategories;
    }

    public ActionTypeRepository actionTypes() {
        return actionTypes;
    }

    public MessageArchiveRepository messageArchive() {
        return messageArchive;
    }

    public VerificationRepository verification() {
        return verification;
    }

    public VerificationQuestionRepository verificationQuestions() {
        return verificationQuestions;
    }

    public VipPlanRepository vipPlans() {
        return vipPlans;
    }

    public VipGrantRepository vipGrants() {
        return vipGrants;
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
