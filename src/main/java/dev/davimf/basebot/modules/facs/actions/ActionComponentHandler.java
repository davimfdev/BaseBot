package dev.davimf.basebot.modules.facs.actions;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;

/** Routes the {@code /painel-acoes} action panel interactions (BOTSPECS Module 4). */
public final class ActionComponentHandler implements ComponentHandler {

    private final ActionService service;

    public ActionComponentHandler(ActionService service) {
        this.service = service;
    }

    @Override
    public String namespace() {
        return ActionView.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        String actionId = id.arg(0);
        switch (id.action()) {
            case "join" -> service.join(event, actionId);
            case "leave" -> service.leave(event, actionId);
            case "align" -> service.align(event, actionId);
            case "config" -> service.config(event, actionId);
            case "victory" -> service.result(event, actionId, "VICTORY", "🏆 Vitória");
            case "defeat" -> service.result(event, actionId, "DEFEAT", "💀 Derrota");
            case "close" -> service.result(event, actionId, "CLOSED", "🔒 Encerrada");
            default -> { /* not ours */ }
        }
    }

    @Override
    public void onEntitySelect(EntitySelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if ("backfill".equals(id.action())) {
            service.backfill(event, id.arg(0));
        }
    }

    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        if ("create".equals(id.action())) {
            service.create(event);
        }
    }
}
