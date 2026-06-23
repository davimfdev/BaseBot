package dev.davimf.basebot.core.command;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
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
 * <p>During development a {@code devGuildId} can be set so commands appear instantly in
 * one guild; otherwise they register globally (up to ~1h propagation). Every handler
 * runs inside a try/catch so one failing command never tears down the listener.
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

    /** Pushes all command definitions to Discord once the session is ready. */
    @Override
    public void onReady(ReadyEvent event) {
        JDA jda = event.getJDA();
        SlashCommandData[] data = commands.values().stream()
                .map(SlashCommand::data)
                .toArray(SlashCommandData[]::new);

        String devGuildId = context.config().discord().devGuildId();
        if (context.config().discord().hasDevGuild()) {
            Guild guild = jda.getGuildById(devGuildId);
            if (guild == null) {
                log.warn("devGuildId {} not found; falling back to global registration.", devGuildId);
                jda.updateCommands().addCommands(data).queue(
                        ok -> log.info("Registered {} global commands.", data.length));
            } else {
                guild.updateCommands().addCommands(data).queue(
                        ok -> log.info("Registered {} commands to dev guild {}.", data.length, devGuildId));
            }
        } else {
            jda.updateCommands().addCommands(data).queue(
                    ok -> log.info("Registered {} global commands.", data.length));
        }
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
