package dev.davimf.basebot.modules.base.utility;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

public final class EnqueteComponentHandler implements ComponentHandler {
    private final EnqueteService service;
    public EnqueteComponentHandler(EnqueteService service) { this.service = service; }

    @Override public String namespace() { return EnqueteView.NS; }

    @Override public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        switch (id.action()) {
            case "vote" -> {
                try {
                    service.vote(event, Integer.parseInt(id.arg(0)));
                } catch (NumberFormatException ignored) {
                    // índice inválido
                }
            }
            case "end" -> service.end(event);
            default -> { }
        }
    }
}
