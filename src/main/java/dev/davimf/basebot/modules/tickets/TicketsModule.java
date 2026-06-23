package dev.davimf.basebot.modules.tickets;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.modules.BotModule;
import dev.davimf.basebot.modules.ModuleRegistry;

/**
 * Module 2 — Ticket System with AES-encrypted transcripts (BOTSPECS §Module 2).
 *
 * <p>Creation flow ({@code /ticket painel} -&gt; StringSelectMenu -&gt; private channel),
 * the internal dashboard (Assumir, Criar Call, Membro, Notificar, Renomear, Fechar),
 * and the closure/transcript pipeline. The transcript encryption + dashboard ingest is
 * implemented in {@link TicketService}; UI wiring is scaffolded.
 */
public final class TicketsModule implements BotModule {

    @Override
    public String name() {
        return "Tickets";
    }

    @Override
    public void register(ModuleRegistry registry, BotContext ctx) {
        TicketService service = new TicketService(ctx);
        registry.component(new TicketComponentHandler(service));

        // TODO(Module 2): /ticket painel command (StringSelectMenu), channel creation
        // with creator+staff ping, and the dashboard buttons routed to TicketService.
    }
}
