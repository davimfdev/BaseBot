package dev.davimf.basebot.modules.sales.budget;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

/** Routes the {@code /orçamento} builder + approval interactions (BOTSPECS Module 3). */
public final class BudgetComponentHandler implements ComponentHandler {

    private final BudgetService service;

    public BudgetComponentHandler(BudgetService service) {
        this.service = service;
    }

    @Override
    public String namespace() {
        return BudgetView.NS;
    }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        switch (id.action()) {
            case "pickcat" -> service.pickCategory(event, id.arg(0), event.getValues().get(0));
            case "pickprod" -> service.pickProduct(event, id.arg(0), event.getValues().get(0));
            default -> { /* not ours */ }
        }
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        switch (id.action()) {
            case "back" -> service.back(event, id.arg(0));
            case "send" -> service.send(event, id.arg(0));
            case "cancel" -> service.cancel(event, id.arg(0));
            case "approve" -> service.approve(event, id.arg(0));
            case "reject" -> service.reject(event, id.arg(0));
            default -> { /* not ours */ }
        }
    }

    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        if ("qty".equals(id.action())) {
            service.addItem(event, id.arg(0), id.arg(1));
        }
    }
}
