package dev.davimf.basebot.core;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.core.scheduler.TaskScheduler;
import dev.davimf.basebot.crypto.TicketCrypto;
import dev.davimf.basebot.database.DatabaseManager;
import dev.davimf.basebot.integration.TicketIngestClient;
import dev.davimf.basebot.ratelimit.Debouncer;
import net.dv8tion.jda.api.JDA;

/**
 * Shared service container handed to every command, component handler and listener.
 *
 * <p>Holds the long-lived singletons so feature modules never construct their own
 * database pools, schedulers or HTTP clients. {@link JDA} is wired in once the
 * gateway is built (it does not exist when the context is first created).
 */
public final class BotContext {

    private final BotConfig config;
    private final DatabaseManager database;
    private final TaskScheduler scheduler;
    private final Debouncer embedDebouncer;
    private final TicketCrypto ticketCrypto;
    private final TicketIngestClient ticketIngest;

    private volatile JDA jda;

    public BotContext(BotConfig config,
                      DatabaseManager database,
                      TaskScheduler scheduler) {
        this.config = config;
        this.database = database;
        this.scheduler = scheduler;
        this.embedDebouncer = new Debouncer(scheduler.executor(), config.rateLimit().embedDebounceMillis());
        this.ticketCrypto = new TicketCrypto(config.tickets().pbkdf2Iterations());
        this.ticketIngest = new TicketIngestClient(config.tickets());
    }

    public BotConfig config() {
        return config;
    }

    public DatabaseManager database() {
        return database;
    }

    public TaskScheduler scheduler() {
        return scheduler;
    }

    /** Debouncer pre-configured with the embed-refresh window (Hierarchy panel, etc.). */
    public Debouncer embedDebouncer() {
        return embedDebouncer;
    }

    public TicketCrypto ticketCrypto() {
        return ticketCrypto;
    }

    public TicketIngestClient ticketIngest() {
        return ticketIngest;
    }

    public JDA jda() {
        return jda;
    }

    /** Set exactly once after the gateway is built. */
    public void setJda(JDA jda) {
        this.jda = jda;
    }
}
