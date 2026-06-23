package dev.davimf.basebot;

import dev.davimf.basebot.config.BotConfig;
import dev.davimf.basebot.config.ConfigLoader;
import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.CommandManager;
import dev.davimf.basebot.core.component.ComponentRouter;
import dev.davimf.basebot.core.scheduler.TaskScheduler;
import dev.davimf.basebot.database.DatabaseManager;
import dev.davimf.basebot.modules.BotModule;
import dev.davimf.basebot.modules.ModuleRegistry;
import dev.davimf.basebot.modules.base.BaseModule;
import dev.davimf.basebot.modules.facs.FacsModule;
import dev.davimf.basebot.modules.sales.SalesModule;
import dev.davimf.basebot.modules.tickets.TicketsModule;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Application bootstrap: loads config, opens databases, wires modules, and connects to
 * the Discord gateway. Owns startup/shutdown ordering.
 *
 * <p>Wiring order matters: commands and component handlers must be registered with the
 * {@link CommandManager}/{@link ComponentRouter} <em>before</em> the JDA listener set is
 * built, and {@link BotContext#setJda} happens immediately after the gateway object
 * exists so {@code onReady} hooks see a live JDA.
 */
public final class BotApplication {

    private static final Logger log = LoggerFactory.getLogger(BotApplication.class);

    private final List<BotModule> modules = List.of(
            new BaseModule(),
            new TicketsModule(),
            new SalesModule(),
            new FacsModule()
    );

    public void start() throws InterruptedException {
        BotConfig config = ConfigLoader.load();
        log.info("Configuration loaded.");

        DatabaseManager database = new DatabaseManager(config);
        TaskScheduler scheduler = new TaskScheduler(2);
        BotContext context = new BotContext(config, database, scheduler);

        CommandManager commandManager = new CommandManager(context);
        ComponentRouter componentRouter = new ComponentRouter(context);
        ModuleRegistry registry = new ModuleRegistry(commandManager, componentRouter);

        for (BotModule module : modules) {
            log.info("Registering module: {}", module.name());
            module.register(registry, context);
        }
        log.info("{} commands across {} modules.", commandManager.size(), modules.size());

        JDA jda = JDABuilder.createDefault(config.discord().token())
                .enableIntents(
                        GatewayIntent.GUILD_MEMBERS,        // role events / hierarchy (privileged)
                        GatewayIntent.GUILD_MODERATION,     // bans/kicks logging
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT,       // transcripts / embed builder (privileged)
                        GatewayIntent.GUILD_VOICE_STATES,    // voice moderation + traffic logging
                        GatewayIntent.GUILD_EXPRESSIONS,     // /addemoji
                        GatewayIntent.DIRECT_MESSAGES)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .enableCache(CacheFlag.VOICE_STATE)
                .addEventListeners(commandManager, componentRouter)
                .addEventListeners(registry.eventListeners().toArray())
                .addEventListeners(new ReadinessCoordinator(context, modules))
                .build();

        context.setJda(jda);
        registerShutdownHook(database, scheduler, jda);

        jda.awaitReady();
        log.info("BaseBot connected as {}.", jda.getSelfUser().getAsTag());
    }

    private void registerShutdownHook(DatabaseManager database, TaskScheduler scheduler, JDA jda) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down BaseBot...");
            jda.shutdown();
            scheduler.close();
            database.close();
        }, "basebot-shutdown"));
    }

    /** Fans the {@link ReadyEvent} out to each module's {@code onReady} hook. */
    private static final class ReadinessCoordinator extends ListenerAdapter {
        private final BotContext context;
        private final List<BotModule> modules;

        ReadinessCoordinator(BotContext context, List<BotModule> modules) {
            this.context = context;
            this.modules = modules;
        }

        @Override
        public void onReady(ReadyEvent event) {
            for (BotModule module : modules) {
                try {
                    module.onReady(context);
                } catch (Exception e) {
                    log.error("Module {} onReady failed", module.name(), e);
                }
            }
        }
    }
}
