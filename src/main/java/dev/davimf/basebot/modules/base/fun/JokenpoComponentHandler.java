package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

public final class JokenpoComponentHandler implements ComponentHandler {
    private final JokenpoService service;

    public JokenpoComponentHandler(JokenpoService service) { this.service = service; }

    @Override public String namespace() { return JokenpoView.NS; }

    @Override public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"pick".equals(id.action())) {
            return;
        }
        try {
            service.onPick(event, JokenpoResult.Choice.valueOf(id.arg(0)));
        } catch (IllegalArgumentException ignored) {
            // escolha inválida
        }
    }
}
