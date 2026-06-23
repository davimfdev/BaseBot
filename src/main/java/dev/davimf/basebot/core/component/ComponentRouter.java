package dev.davimf.basebot.core.component;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Single JDA listener that dispatches all component & modal interactions to the
 * {@link ComponentHandler} registered for their custom-id namespace.
 *
 * <p>Routing all Components V2 traffic through one place keeps custom-id parsing and
 * error handling uniform across modules.
 */
public final class ComponentRouter extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ComponentRouter.class);

    private final Map<String, ComponentHandler> handlers = new HashMap<>();
    private final BotContext context;

    public ComponentRouter(BotContext context) {
        this.context = context;
    }

    public ComponentRouter register(ComponentHandler handler) {
        if (handlers.putIfAbsent(handler.namespace(), handler) != null) {
            throw new IllegalStateException("Duplicate component namespace: " + handler.namespace());
        }
        return this;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        dispatch(event, event.getComponentId(), (h, id) -> h.onButton(event, id, context));
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        dispatch(event, event.getComponentId(), (h, id) -> h.onStringSelect(event, id, context));
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        dispatch(event, event.getComponentId(), (h, id) -> h.onEntitySelect(event, id, context));
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        dispatch(event, event.getModalId(), (h, id) -> h.onModal(event, id, context));
    }

    private void dispatch(IReplyCallback event, String rawId, Invoker invoker) {
        ComponentId id = ComponentId.parse(rawId);
        ComponentHandler handler = handlers.get(id.namespace());
        if (handler == null) {
            log.debug("No handler for component namespace '{}' (id={})", id.namespace(), rawId);
            return;
        }
        try {
            invoker.invoke(handler, id);
        } catch (Exception e) {
            log.error("Component handler '{}' failed for id {}", id.namespace(), rawId, e);
            if (!event.isAcknowledged()) {
                event.reply("Ocorreu um erro ao processar esta interação.").setEphemeral(true).queue();
            }
        }
    }

    @FunctionalInterface
    private interface Invoker {
        void invoke(ComponentHandler handler, ComponentId id);
    }
}
