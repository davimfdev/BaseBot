package dev.davimf.basebot.modules.tickets;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

/**
 * Routes the internal ticket dashboard buttons (BOTSPECS §Internal Ticket Dashboard):
 * Assumir Atendimento, Criar Call, Membro, Notificar, Renomear, Fechar.
 *
 * <p>Custom-id convention: {@code ticket:<action>:<ticketId>}. Wired into
 * {@link dev.davimf.basebot.core.component.ComponentRouter} under the {@code "ticket"}
 * namespace. Only the routing skeleton is present; each action is a TODO.
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
        switch (id.action()) {
            case "fechar"   -> service.closeTicket(event, id.arg(0));
            case "assumir"  -> notImplemented(event, "Assumir Atendimento");
            case "call"     -> notImplemented(event, "Criar Call");
            case "membro"   -> notImplemented(event, "Membro");
            case "notificar"-> notImplemented(event, "Notificar");
            case "renomear" -> notImplemented(event, "Renomear");
            default         -> notImplemented(event, id.action());
        }
    }

    private void notImplemented(ButtonInteractionEvent event, String action) {
        event.reply("Ação de ticket '" + action + "' ainda não implementada.")
                .setEphemeral(true).queue();
    }
}
