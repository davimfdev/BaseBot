package dev.davimf.basebot.modules.facs.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;

/** Routes the {@code /farm} flow: item picker (select) → quantity modal → submit, plus the
 *  manager approve/reject buttons (BOTSPECS Module 4). */
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

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if ("pickitem".equals(id.action()) && !event.getValues().isEmpty()) {
            event.replyModal(FarmView.quantityModal(event.getValues().get(0))).queue();
        }
    }

    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"qtyform".equals(id.action()) || event.getGuild() == null) {
            return;
        }
        String item = id.arg(0);
        ModalMapping qtyMap = event.getValue("quantidade");
        long qty;
        try {
            qty = Long.parseLong(qtyMap == null ? "" : qtyMap.getAsString().trim());
        } catch (NumberFormatException e) {
            Replies.ephemeral(event, ctx, "Quantidade inválida — informe um número.");
            return;
        }
        if (qty <= 0 || item == null || item.isBlank()) {
            Replies.ephemeral(event, ctx, "Quantidade ou item inválidos.");
            return;
        }
        service.submit(event, item, qty);
    }
}
