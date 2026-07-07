package dev.davimf.basebot.modules.base.giveaway;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/** Botão "Participar" dos sorteios (namespace "gwy"). */
public final class GiveawayComponentHandler implements ComponentHandler {

    private final GiveawayService service;

    public GiveawayComponentHandler(GiveawayService service) { this.service = service; }

    @Override
    public String namespace() { return GiveawayView.NS; }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if ("enter".equals(id.action())) {
            service.enter(event, id.arg(0));
        }
    }
}
