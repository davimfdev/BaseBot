// [OUTLINE START]
// Package: dev.davimf.basebot.core.command
// 
// Class: CommandManager
// 
// Constructors:
//   - `Constructor` : `public CommandManager(BotContext context)`
// 
// Methods:
//   - `Method` : `private static final Logger log = LoggerFactory. getLogger(CommandManager.class)`
//   - `Method` : `public CommandManager register(SlashCommand command)`
//   - `Method` : `public int size()`
// 
// Fields:
//   - `Field` : `private final Map<String, SlashCommand> commands`
//   - `Field` : `private final BotContext context`
// [OUTLINE END]



package dev.davimf.basebot.core.command;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registers {@link SlashCommand}s with Discord and dispatches incoming interactions.
 *
 * <p>Commands are registered <b>per guild</b> (so they appear instantly): on
 * {@code onReady} to every guild the bot is already in, and on {@link #onGuildJoin}
 * whenever the bot joins a new server. The one exception is the attachment-vault guild
 * ({@code vaultGuildId}), which is storage-only and never gets slash commands. Every
 * handler runs inside a try/catch so one failing command never tears down the listener.
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

        log.info("Reconciling {} commands across {} guild(s)...", commands.size(), jda.getGuilds().size());
        jda.getGuilds().forEach(this::registerTo);
    }

    /** Registers the commands in a guild as soon as the bot joins it. */
    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        registerTo(event.getGuild());
    }

    private void registerTo(Guild guild) {
        if (isVaultGuild(guild)) {
            log.info("Skipped vault guild {} ({}).", guild.getName(), guild.getId());
            return;
        }
        guild.retrieveCommands().queue(existing -> {
            Set<String> existingNames = existing.stream()
                    .map(net.dv8tion.jda.api.interactions.commands.Command::getName)
                    .collect(Collectors.toSet());
            Set<String> missing = missingCommandNames(existingNames, commands.keySet());
            if (missing.isEmpty()) {
                log.info("Guild {} already has all {} commands.", guild.getId(), commands.size());
                return;
            }
            log.info("Registering {} missing command(s) to guild {}.", missing.size(), guild.getId());
            for (String name : missing) {
                SlashCommand command = commands.get(name);
                guild.upsertCommand(command.data()).queue(
                        ok -> log.info("Registered /{} to guild {}.", name, guild.getId()),
                        err -> log.error("Failed to register /{} to guild {}", name, guild.getId(), err));
            }
        }, err -> log.error("Failed to retrieve commands from guild {}", guild.getId(), err));
    }

    static Set<String> missingCommandNames(Set<String> existing, Set<String> desired) {
        Set<String> missing = new LinkedHashSet<>(desired);
        missing.removeAll(existing);
        return missing;
    }

    /** True for the central attachment-vault guild, which never receives slash commands. */
    private boolean isVaultGuild(Guild guild) {
        return guild.getId().equals(context.config().discord().vaultGuildId());
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        SlashCommand command = commands.get(event.getName());
        if (command == null) {
            Replies.ephemeral(event, context, "Comando não reconhecido.");
            return;
        }
        try {
            command.execute(event, context);
        } catch (Exception e) {
            log.error("Command /{} failed", event.getName(), e);
            respondError(event);
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        SlashCommand command = commands.get(event.getName());
        if (command instanceof AutocompleteCommand ac) {
            try {
                ac.onAutocomplete(event, context);
            } catch (Exception e) {
                log.error("Autocomplete for /{} failed", event.getName(), e);
            }
        }
    }

    private void respondError(SlashCommandInteractionEvent event) {
        String msg = "Ocorreu um erro ao executar este comando.";
        if (event.isAcknowledged()) {
            Replies.hookEphemeral(event, context, msg);
        } else {
            Replies.ephemeral(event, context, msg);
        }
    }
}
