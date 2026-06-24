package dev.davimf.basebot.modules.tickets;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.TicketCategory;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;

import java.util.List;

/** Components V2 views for the ticket panel and the in-channel dashboard (BOTSPECS Module 2). */
public final class TicketView {

    public static final String NS = "ticket";

    private TicketView() {}

    /** Public panel members use to open a ticket — one category per select option. */
    public static Container panel(int accent, List<TicketCategory> categories) {
        StringSelectMenu.Builder menu = StringSelectMenu.create(ComponentId.of(NS, "open"))
                .setPlaceholder("Selecione uma categoria para abrir um ticket");
        for (TicketCategory c : categories) {
            String label = (c.emoji() != null && !c.emoji().isBlank() ? c.emoji() + " " : "") + c.name();
            menu.addOption(trim(label, 100), c.id(),
                    c.description() == null ? null : trim(c.description(), 100));
        }
        return Panels.container(accent,
                Panels.text("## 🎫 Central de Tickets\nSelecione uma categoria abaixo para abrir um ticket."),
                ActionRow.of(menu.build()));
    }

    /** In-channel dashboard posted as the first message of a new ticket. */
    public static Container dashboard(int accent, String ticketId, String headerMarkdown) {
        return Panels.container(accent,
                Panels.text(headerMarkdown),
                ActionRow.of(
                        Button.primary(ComponentId.of(NS, "assumir", ticketId), "Assumir"),
                        Button.secondary(ComponentId.of(NS, "call", ticketId), "Criar Call"),
                        Button.secondary(ComponentId.of(NS, "membro", ticketId), "Membro"),
                        Button.secondary(ComponentId.of(NS, "notificar", ticketId), "Notificar")),
                ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "renomear", ticketId), "Renomear"),
                        Button.danger(ComponentId.of(NS, "fechar", ticketId), "Fechar")));
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
