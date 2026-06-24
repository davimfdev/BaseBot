package dev.davimf.basebot.modules.base.message;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

/** Routes all {@code /mensagem} builder interactions (BOTSPECS Module 1). */
public final class MessageBuilderComponentHandler implements ComponentHandler {

    private final MessageBuilderService service;

    public MessageBuilderComponentHandler(MessageBuilderService service) {
        this.service = service;
    }

    @Override
    public String namespace() {
        return MessageBuilderView.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null) {
            return;
        }
        service.onButton(event, id.action(), id.arg(0));
    }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null) {
            return;
        }
        service.onStringSelect(event, id.action());
    }

    @Override
    public void onEntitySelect(EntitySelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null) {
            return;
        }
        service.onEntitySelect(event, id.action());
    }

    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null) {
            return;
        }
        service.onModal(event, id.action(), id.arg(0));
    }
}
