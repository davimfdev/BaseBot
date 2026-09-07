package dev.davimf.basebot.modules.tickets;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.TicketCategory;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.emoji.Emoji;
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
                Panels.text("## " + Emojis.of(Emojis.TICKET, "🎫") + " Central de Atendimento"),
                Panels.divider(),
                Panels.text("> Selecione abaixo a categoria que melhor descreve o seu atendimento. "
                        + "Um canal privado será aberto somente para você e a equipe."),
                ActionRow.of(menu.build()),
                Panels.text("-# " + Emojis.of(Emojis.LOCK, "🔒") + " Apenas você e a equipe responsável terão acesso ao canal."));
    }

    /** In-channel dashboard posted as the first message of a new ticket. */
    public static Container dashboard(int accent, String ticketId, String headerMarkdown) {
        return dashboard(accent, ticketId, headerMarkdown, null);
    }

    /**
     * Dashboard. Before the ticket is assumed it stays clean — only Assumir + Fechar.
     * Once a staff member assumes it, the Assumir button is removed entirely and the full
     * action set (Criar Call, Membro, Notificar, Renomear) appears, with the header showing
     * who is handling it. The header (including the reason) is always preserved.
     */
    public static Container dashboard(int accent, String ticketId, String headerMarkdown,
                                      String assignedStaffId) {
        if (assignedStaffId == null) {
            return Panels.container(accent,
                    Panels.text(headerMarkdown),
                    Panels.divider(),
                    Panels.text("-# " + Emojis.of(Emojis.LOADING, "⏳") + " Aguardando um atendente assumir o seu ticket."),
                    ActionRow.of(
                            Button.primary(ComponentId.of(NS, "assumir", ticketId), "Assumir atendimento")
                                    .withEmoji(Emojis.button(Emojis.RECRUIT)),
                            Button.danger(ComponentId.of(NS, "fechar", ticketId), "Fechar")
                                    .withEmoji(Emojis.button(Emojis.LOCK))));
        }
        return Panels.container(accent,
                Panels.text(headerMarkdown),
                Panels.divider(),
                Panels.text("" + Emojis.of(Emojis.RECRUIT, "🙋") + " **Responsável** · <@" + assignedStaffId + ">"),
                Panels.divider(),
                ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "call", ticketId), "Criar call")
                                .withEmoji(Emojis.button(Emojis.VOLUME)),
                        Button.secondary(ComponentId.of(NS, "membro", ticketId), "Adicionar membro")
                                .withEmoji(Emojis.button(Emojis.MEMBER)),
                        Button.secondary(ComponentId.of(NS, "notificar", ticketId), "Notificar autor")
                                .withEmoji(Emojis.button(Emojis.BELL))),
                ActionRow.of(
                        Button.secondary(ComponentId.of(NS, "renomear", ticketId), "Renomear")
                                .withEmoji(Emojis.button(Emojis.EDIT)),
                        Button.danger(ComponentId.of(NS, "fechar", ticketId), "Fechar")
                                .withEmoji(Emojis.button(Emojis.LOCK))));
    }

    /** Modal asking the member why they are opening the ticket (shown before creation). */
    public static Modal openReasonModal(String categoryId) {
        TextInput motivo = TextInput.create("motivo", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Descreva o motivo do seu atendimento").setRequired(true).setMaxLength(500)
                .build();
        return Modal.create(ComponentId.of(NS, "openform", categoryId), "Abrir ticket")
                .addComponents(Label.of("Motivo", motivo))
                .build();
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
                Panels.text("## " + Emojis.of(Emojis.BELL, "🔔") + " Atenção solicitada"),
                Panels.divider(),
                Panels.text("> A equipe de **" + guildName + "** solicita a sua presença no seu ticket."),
                ActionRow.of(Button.link(channelUrl, "Ir para o canal").withEmoji(Emojis.button(Emojis.ARROW))));
    }

    /** Ephemeral picker for the "Membro" action — add a user to the ticket channel. */
    public static Container addMemberPrompt(int accent, String ticketId) {
        EntitySelectMenu menu = EntitySelectMenu
                .create(ComponentId.of(NS, "addmember", ticketId), SelectTarget.USER)
                .setPlaceholder("Selecione o membro a adicionar")
                .setRequiredRange(1, 1)
                .build();
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.MEMBER, "👤") + " Adicionar membro"),
                Panels.divider(),
                Panels.text("> Selecione quem deve passar a ter acesso a este atendimento."),
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
        String details = "**Canal** · `" + channelName + "`\n"
                + "" + Emojis.of(Emojis.MEMBER, "👤") + " **Aberto por** · <@" + creatorId + ">\n"
                + "" + Emojis.of(Emojis.SHIELD, "🛡️") + " **Fechado por** · <@" + closerId + ">\n"
                + "" + Emojis.of(Emojis.NOTE, "📝") + " **Motivo** · " + (reason == null || reason.isBlank() ? "*não informado*" : reason);
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.LOCK, "🔒") + " Ticket fechado"),
                Panels.divider(),
                Panels.text(details),
                Panels.divider(),
                Panels.text("" + Emojis.of(Emojis.KEY, "🔑") + " **Senha (uso único)** · `" + password + "`\n"
                        + "-# A senha é necessária para abrir o transcript e não será exibida novamente."),
                ActionRow.of(Button.link(url, "Abrir transcript").withEmoji(Emojis.button(Emojis.NOTE))));
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
