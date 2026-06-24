package dev.davimf.basebot.modules.tickets;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.TicketCategory;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;

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
        return dashboard(accent, ticketId, headerMarkdown, null);
    }

    /**
     * Dashboard, optionally showing the staff member who assumed it. When assigned, the
     * "Assumir" button is disabled so it visibly reflects the claim (BOTSPECS Module 2).
     */
    public static Container dashboard(int accent, String ticketId, String headerMarkdown,
                                      String assignedStaffId) {
        String text = assignedStaffId == null ? headerMarkdown
                : headerMarkdown + "\n\n🙋 **Atendimento assumido por** <@" + assignedStaffId + ">";
        return Panels.container(accent,
                Panels.text(text),
                ActionRow.of(
                        Button.primary(ComponentId.of(NS, "assumir", ticketId), "Assumir")
                                .withDisabled(assignedStaffId != null),
                        Button.secondary(ComponentId.of(NS, "call", ticketId), "Criar Call"),
                        Button.secondary(ComponentId.of(NS, "membro", ticketId), "Membro"),
                        Button.secondary(ComponentId.of(NS, "notificar", ticketId), "Notificar")),
                ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "renomear", ticketId), "Renomear"),
                        Button.danger(ComponentId.of(NS, "fechar", ticketId), "Fechar")));
    }

    /** Closure-reason modal shown by the "Fechar" button before the transcript runs. */
    public static Modal closeReasonModal(String ticketId) {
        TextInput motivo = TextInput.create("motivo", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Motivo do fechamento (opcional)").setRequired(false).setMaxLength(400)
                .build();
        return Modal.create(ComponentId.of(NS, "closeform", ticketId), "Fechar ticket")
                .addComponents(Label.of("Motivo", motivo))
                .build();
    }

    /** DM sent by "Notificar": a link button that jumps back to the ticket channel. */
    public static Container notifyDm(int accent, String guildName, String channelUrl) {
        return Panels.container(accent,
                Panels.text("🔔 A equipe de **" + guildName + "** solicita sua atenção no seu ticket."),
                ActionRow.of(Button.link(channelUrl, "Ir para o canal")));
    }

    /** Ephemeral picker for the "Membro" action — add a user to the ticket channel. */
    public static Container addMemberPrompt(int accent, String ticketId) {
        EntitySelectMenu menu = EntitySelectMenu
                .create(ComponentId.of(NS, "addmember", ticketId), SelectTarget.USER)
                .setPlaceholder("Selecione o membro a adicionar")
                .setRequiredRange(1, 1)
                .build();
        return Panels.container(accent,
                Panels.text("Selecione o membro que deve ter acesso a este ticket."),
                ActionRow.of(menu));
    }

    /** Modal for the "Renomear" action. */
    public static Modal renameModal(String ticketId, String currentSuffix) {
        TextInput nome = TextInput.create("nome", TextInputStyle.SHORT)
                .setPlaceholder("Novo nome do canal").setRequired(true).setMaxLength(90)
                .setValue(currentSuffix).build();
        return Modal.create(ComponentId.of(NS, "renomeform", ticketId), "Renomear ticket")
                .addComponents(Label.of("Nome", nome))
                .build();
    }

    /** Closure embed (sent to #log-tickets and the creator's DM): link + one-time password. */
    public static Container closure(int accent, String channelName, String creatorId,
                                    String closerId, String reason, String url, String password) {
        String body = "## 🔒 Ticket fechado\n"
                + "**Canal:** " + channelName + "\n"
                + "**Aberto por:** <@" + creatorId + ">\n"
                + "**Fechado por:** <@" + closerId + ">\n"
                + "**Motivo:** " + (reason == null || reason.isBlank() ? "*não informado*" : reason) + "\n"
                + "**Senha (uso único):** `" + password + "`\n"
                + "-# A senha é necessária para abrir o transcript e não será exibida novamente.";
        return Panels.container(accent,
                Panels.text(body),
                ActionRow.of(Button.link(url, "Abrir transcript")));
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
