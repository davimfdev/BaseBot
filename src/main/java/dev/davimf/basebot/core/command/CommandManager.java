package dev.davimf.basebot.core.command;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers {@link SlashCommand}s with Discord and dispatches incoming interactions.
 *
 * <p>Commands are registered <b>per guild</b> (so they appear instantly): on
 * {@code onReady} to every guild the bot is already in, and on {@link #onGuildJoin}
 * whenever the bot joins a new server. A {@code devGuildId} (one or more, comma-separated)
 * restricts registration to those guilds during development. Every handler runs inside a
 * try/catch so one failing command never tears down the listener.
 */
public final class CommandManager extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(CommandManager.class);

    private final Map<String, SlashCommand> commands = new LinkedHashMap<>();
    private final BotContext context;

    public CommandManager(BotContext context) {
        this.context = context;
    }

    /** Registers a command instance. Call during module wiring, before JDA is built. */
    public CommandManager register(SlashCommand command) {
        if (commands.putIfAbsent(command.name(), command) != null) {
            throw new IllegalStateException("Duplicate command name: " + command.name());
        }
        return this;
    }

    public int size() {
        return commands.size();
    }

    /** Registers commands to every guild the bot is already in once the session is ready. */
    @Override
    public void onReady(ReadyEvent event) {
        JDA jda = event.getJDA();

        if (context.config().discord().hasDevGuild()) {
            for (String id : context.config().discord().devGuildIds()) {
                Guild guild = jda.getGuildById(id);
                if (guild != null) {
                    registerTo(guild);
                } else {
                    log.warn("devGuildId {} not found — is the bot a member of that guild?", id);
                }
            }
            return;
        }
        log.info("Registering {} commands to {} guild(s)...", commands.size(), jda.getGuilds().size());
        jda.getGuilds().forEach(this::registerTo);
    }

    /** Registers the commands in a guild as soon as the bot joins it. */
    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        // In dev mode only the listed dev guild(s) are managed; ignore other joins.
        if (context.config().discord().hasDevGuild()
                && !context.config().discord().devGuildIds().contains(event.getGuild().getId())) {
            return;
        }
        registerTo(event.getGuild());
    }

    /** Pushes all command definitions to a single guild. */
    private void registerTo(Guild guild) {
        SlashCommandData[] data = commands.values().stream()
                .map(SlashCommand::data)
                .toArray(SlashCommandData[]::new);
        guild.updateCommands().addCommands(data).queue(
                ok -> log.info("Registered {} commands to guild {} ({}).",
                        data.length, guild.getName(), guild.getId()),
                err -> log.error("Failed to register commands to guild {}", guild.getId(), err));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        SlashCommand command = commands.get(event.getName());
        if (command == null) {
            event.reply("Comando não reconhecido.").setEphemeral(true).queue();
            return;
        }
        try {
            command.execute(event, context);
        } catch (Exception e) {
            log.error("Command /{} failed", event.getName(), e);
            respondError(event);
        }
    }

    private void respondError(SlashCommandInteractionEvent event) {
        String msg = "Ocorreu um erro ao executar este comando.";
        if (event.isAcknowledged()) {
            event.getHook().sendMessage(msg).setEphemeral(true).queue();
        } else {
            event.reply(msg).setEphemeral(true).queue();
        }
    }
}
