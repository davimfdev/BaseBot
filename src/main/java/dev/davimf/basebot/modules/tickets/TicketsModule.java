package dev.davimf.basebot.modules.tickets;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.modules.BotModule;
import dev.davimf.basebot.modules.ModuleRegistry;

/**
 * Module 2 — Ticket System with AES-encrypted transcripts (BOTSPECS §Module 2).
 *
 * <p>Creation flow ({@code /ticket painel} -&gt; StringSelectMenu -&gt; private channel),
 * the internal dashboard (Assumir, Criar Call, Membro, Notificar, Renomear, Fechar),
 * and the closure/transcript pipeline (render history -&gt; AES-encrypt -&gt; POST to
 * davimf.dev -&gt; closure embed to #log-tickets + creator DM -&gt; delete channels),
 * all driven by {@link TicketService}.
 */
public final class TicketsModule implements BotModule {

    @Override
    public String name() {
        return "Tickets";
    }

    @Override
    public void register(ModuleRegistry registry, BotContext ctx) {
        TicketService service = new TicketService(ctx);
        registry.command(new TicketPanelCommand());
        registry.component(new TicketComponentHandler(service));
    }
}
