package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

public final class QuizComponentHandler implements ComponentHandler {
    private final QuizPlayService service;

    public QuizComponentHandler(QuizPlayService service) { this.service = service; }

    @Override public String namespace() { return QuizPlayView.NS; }

    @Override public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"ans".equals(id.action())) {
            return;
        }
        try {
            service.onAnswer(event, Integer.parseInt(id.arg(0)));
        } catch (NumberFormatException ignored) {
            // índice inválido
        }
    }
}
