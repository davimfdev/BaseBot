package dev.davimf.basebot.core.command;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * A single slash command. Implementations declare their JDA command data and handle
 * execution. Registered with {@link CommandManager}, which routes by {@link #name()}.
 */
public interface SlashCommand {

    /** The command name (must equal {@code data().getName()}); used for routing. */
    String name();

    /** JDA command definition (options, permissions, subcommands). */
    SlashCommandData data();

    /** Handles an invocation. The {@link BotContext} exposes all shared services. */
    void execute(SlashCommandInteractionEvent event, BotContext ctx);
}
