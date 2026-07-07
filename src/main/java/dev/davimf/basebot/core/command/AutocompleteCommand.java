package dev.davimf.basebot.core.command;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;

/**
 * Optional capability for a {@link SlashCommand} whose options use Discord autocomplete.
 * {@link CommandManager} routes autocomplete interactions to commands implementing this.
 */
public interface AutocompleteCommand {

    /** Suggests choices for the focused option. Must reply (e.g. {@code replyChoiceStrings(...)}). */
    void onAutocomplete(CommandAutoCompleteInteractionEvent event, BotContext ctx);
}
