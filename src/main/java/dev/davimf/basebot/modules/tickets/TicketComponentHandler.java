package dev.davimf.basebot.modules.tickets;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
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

    /** Panel select: a member chose a category → open a ticket. */
    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"open".equals(id.action()) || event.getGuild() == null) {
            return;
        }
        String categoryId = event.getValues().get(0);
        event.deferReply(true).queue();
        ctx.database().ticketCategories().find(categoryId).ifPresentOrElse(
                cat -> service.openTicket(event, cat),
                () -> event.getHook().sendMessage("Essa categoria não existe mais.").queue());
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        String ticketId = id.arg(0);
        switch (id.action()) {
            case "fechar"    -> service.closeTicket(event, ticketId);
            case "assumir"   -> service.assume(event, ticketId);
            case "call"      -> service.createCall(event, ticketId);
            case "membro"    -> service.promptAddMember(event, ticketId);
            case "notificar" -> service.notifyCreator(event, ticketId);
            case "renomear"  -> service.promptRename(event, ticketId);
            default          -> event.reply("Ação de ticket desconhecida.").setEphemeral(true).queue();
        }
    }

    /** Member picker from the "Membro" button. */
    @Override
    public void onEntitySelect(EntitySelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if ("addmember".equals(id.action())) {
            service.addMember(event, id.arg(0));
        }
    }

    /** Rename modal submit. */
    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        if ("renomeform".equals(id.action())) {
            service.rename(event, id.arg(0));
        }
    }
}
