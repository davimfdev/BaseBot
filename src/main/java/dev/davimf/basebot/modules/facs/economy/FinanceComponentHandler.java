// [OUTLINE START]
// Package: dev.davimf.basebot.modules.facs.economy
// 
// Class: FinanceComponentHandler
// 
// Constructors:
//   - `Constructor` : `public FinanceComponentHandler(FinanceService service)`
// 
// Methods:
//   - `Method` : `public String namespace()`
// 
// Fields:
//   - `Field` : `private final FinanceService service`
// [OUTLINE END]



package dev.davimf.basebot.modules.facs.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/** Routes the {@code /painel-financeiro} buttons + modals (BOTSPECS Module 4). */
public final class FinanceComponentHandler implements ComponentHandler {

    private final FinanceService service;

    public FinanceComponentHandler(FinanceService service) {
        this.service = service;
    }

    @Override
    public String namespace() {
        return FinanceView.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        service.onButton(event, id.action());
    }

    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        service.onModal(event, id.action());
    }
}
