package dev.davimf.basebot.modules.tickets;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

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

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        switch (id.action()) {
            case "assumir"  -> notImplemented(event, "Assumir Atendimento");
            case "call"     -> notImplemented(event, "Criar Call");
            case "membro"   -> notImplemented(event, "Membro");
            case "notificar"-> notImplemented(event, "Notificar");
            case "renomear" -> notImplemented(event, "Renomear");
            case "fechar"   -> notImplemented(event, "Fechar");
            default         -> notImplemented(event, id.action());
        }
    }

    private void notImplemented(ButtonInteractionEvent event, String action) {
        event.reply("Ação de ticket '" + action + "' ainda não implementada.")
                .setEphemeral(true).queue();
    }
}
