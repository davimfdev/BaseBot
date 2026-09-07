package dev.davimf.basebot.modules.tickets;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

/**
 * Routes the internal ticket dashboard interactions (BOTSPECS §Internal Ticket Dashboard):
 * Assumir Atendimento, Criar Call, Membro, Notificar, Renomear, Fechar.
 *
 * <p>Custom-id convention: {@code ticket:<action>:<ticketId>}. Wired into
 * {@link dev.davimf.basebot.core.component.ComponentRouter} under the {@code "ticket"}
 * namespace. Each action delegates to {@link TicketService}.
 */
public final class TicketComponentHandler implements ComponentHandler {

    private final TicketService service;

    public TicketComponentHandler(TicketService service) {
        this.service = service;
    }

    @Override
    public String namespace() {
        return "ticket";
    }

    /** Panel select: a member chose a category → ask the reason via a modal. */
    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"open".equals(id.action()) || event.getGuild() == null) {
            return;
        }
        String categoryId = event.getValues().get(0);
        if (ctx.database().ticketCategories().find(categoryId).isEmpty()) {
            Replies.ephemeral(event, ctx, "Essa categoria não existe mais.");
            return;
        }
        event.replyModal(TicketView.openReasonModal(categoryId)).queue();
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        String ticketId = id.arg(0);
        switch (id.action()) {
            case "fechar"    -> service.promptClose(event, ticketId);
            case "assumir"   -> service.assume(event, ticketId);
            case "call"      -> service.createCall(event, ticketId);
            case "membro"    -> service.promptAddMember(event, ticketId);
            case "notificar" -> service.notifyCreator(event, ticketId);
            case "renomear"  -> service.promptRename(event, ticketId);
            default          -> Replies.ephemeral(event, ctx, "Ação de ticket desconhecida.");
        }
    }

    /** Member picker from the "Membro" button. */
    @Override
    public void onEntitySelect(EntitySelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if ("addmember".equals(id.action())) {
            service.addMember(event, id.arg(0));
        }
    }

    /** Open-reason, rename + close modal submits. */
    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        switch (id.action()) {
            case "openform" -> openTicket(event, id.arg(0), ctx);
            case "renomeform" -> service.rename(event, id.arg(0));
            case "closeform" -> service.closeTicket(event, id.arg(0));
            default -> { /* not ours */ }
        }
    }

    private void openTicket(ModalInteractionEvent event, String categoryId, BotContext ctx) {
        if (event.getGuild() == null) {
            return;
        }
        String reason = event.getValue("motivo") == null ? "" : event.getValue("motivo").getAsString();
        event.deferReply(true).queue();
        ctx.database().ticketCategories().find(categoryId).ifPresentOrElse(
                cat -> service.openTicket(event, cat, reason),
                () -> Replies.hook(event, ctx, "Essa categoria não existe mais."));
    }
}
