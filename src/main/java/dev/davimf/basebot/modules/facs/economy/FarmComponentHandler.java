package dev.davimf.basebot.modules.facs.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/** Routes the {@code /farm} approve/reject buttons (BOTSPECS Module 4). */
public final class FarmComponentHandler implements ComponentHandler {

    private final FarmService service;

    public FarmComponentHandler(FarmService service) {
        this.service = service;
    }

    @Override
    public String namespace() {
        return FarmView.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        switch (id.action()) {
            case "approve" -> service.approve(event, id.arg(0));
            case "reject" -> service.reject(event, id.arg(0));
            default -> { /* not ours */ }
        }
    }
}
