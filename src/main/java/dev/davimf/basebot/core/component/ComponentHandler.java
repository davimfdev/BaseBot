// [OUTLINE START]
// Package: dev.davimf.basebot.core.component
// 
// Interface: ComponentHandler
// 
// Methods:
//   - `Method` : `package-private String namespace()`
// [OUTLINE END]



package dev.davimf.basebot.core.component;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

/**
 * Handles interactions (buttons, select menus, modals) for one namespace.
 *
 * <p>BOTSPECS mandates Components V2 everywhere, so interactions are the backbone of
 * the UI. Custom IDs follow {@code namespace:action:arg1:arg2}; {@link ComponentRouter}
 * dispatches by the {@code namespace} segment to the matching handler. Default methods
 * are no-ops so a handler only overrides the interaction types it actually uses.
 */
public interface ComponentHandler {

    /** The custom-id namespace this handler owns (e.g. {@code "ticket"}, {@code "budget"}). */
    String namespace();

    default void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {}

    default void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {}

    default void onEntitySelect(EntitySelectInteractionEvent event, ComponentId id, BotContext ctx) {}

    default void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {}
}
