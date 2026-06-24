package dev.davimf.basebot.modules.base.embed;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;

/** Routes the {@code /embed} and {@code /editembed} modal submits (BOTSPECS Module 1). */
public final class EmbedComponentHandler implements ComponentHandler {

    private final EmbedService service;

    public EmbedComponentHandler(EmbedService service) {
        this.service = service;
    }

    @Override
    public String namespace() {
        return EmbedView.NS;
    }

    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        switch (id.action()) {
            case "send" -> service.send(event);
            case "editform" -> service.edit(event, id.arg(0));
            default -> { /* not ours */ }
        }
    }
}
