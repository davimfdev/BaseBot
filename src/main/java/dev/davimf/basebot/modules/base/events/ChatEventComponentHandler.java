package dev.davimf.basebot.modules.base.events;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/** Botões de QUIZ/GRAB (namespace "chatevt"). */
public final class ChatEventComponentHandler implements ComponentHandler {

    private final ChatEventService service;

    public ChatEventComponentHandler(ChatEventService service) {
        this.service = service;
    }

    @Override
    public String namespace() {
        return ChatEventView.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        service.resolveButton(event, id);
    }
}
