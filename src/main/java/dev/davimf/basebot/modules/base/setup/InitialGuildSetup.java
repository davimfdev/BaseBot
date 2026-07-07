package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.database.postgres.GuildConfigRepository;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Consumer;

/** Creates the initial log-channel configuration for guilds not yet present in guild_config. */
public final class InitialGuildSetup {

    private static final Logger log = LoggerFactory.getLogger(InitialGuildSetup.class);

    private InitialGuildSetup() {}

    static boolean shouldSetup(Optional<GuildConfig> config) {
        return config.isEmpty();
    }

    public static void run(BotContext ctx) {
        if (ctx.jda() == null) return;
        for (Guild guild : ctx.jda().getGuilds()) {
            runGuild(guild, ctx);
        }
    }

    public static boolean runGuild(Guild guild, BotContext ctx) {
        if (isVaultGuild(guild.getId(), ctx.config().discord().vaultGuildId())) {
            log.info("Initial setup skipped for vault guild {}", guild.getId());
            return false;
        }
        try {
            boolean ran = runGuild(guild, ctx.database().guildConfig(), g -> QuickLogSetup.run(g, ctx));
            if (ran) log.info("Initial setup completed for guild {}", guild.getId());
            else log.info("Initial setup skipped for guild {}: guild_config already exists", guild.getId());
            return ran;
        } catch (RuntimeException e) {
            log.error("Initial setup failed for guild {}", guild.getId(), e);
            return false;
        }
    }

    static boolean runGuild(Guild guild, GuildConfigRepository configs, Consumer<Guild> setup) {
        if (!shouldSetup(configs.find(guild.getId()))) return false;
        setup.accept(guild);
        return true;
    }

    static boolean isVaultGuild(String guildId, String vaultGuildId) {
        return vaultGuildId != null && !vaultGuildId.isBlank() && vaultGuildId.equals(guildId);
    }
}
