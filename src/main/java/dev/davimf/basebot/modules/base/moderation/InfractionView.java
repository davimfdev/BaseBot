package dev.davimf.basebot.modules.base.moderation;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Durations;
import dev.davimf.basebot.util.Paginator;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;

import java.util.ArrayList;
import java.util.List;

/** Components V2 panels for the moderation case system: history, case detail, revoke picker. */
public final class InfractionView {

    public static final String NS = "inf";
    public static final int PAGE_SIZE = 6;

    private InfractionView() {}

    /** Paginated history of a user's cases (newest first). */
    public static Container history(int accent, String userId, List<Infraction> cases, int pageIndex) {
        int pages = Math.max(1, Paginator.pageCount(cases.size(), PAGE_SIZE));
        int page = Math.max(0, Math.min(pageIndex, pages - 1));

        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + Emojis.of(Emojis.SHIELD, "🛡️") + " Infrações · <@" + userId + "> `" + cases.size() + "`"));
        kids.add(Panels.divider());
        if (cases.isEmpty()) {
            kids.add(Panels.text("-# *Nenhuma infração registrada.*"));
            return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
        }
        StringBuilder body = new StringBuilder();
        for (Infraction inf : Paginator.page(cases, page, PAGE_SIZE)) {
            if (body.length() > 0) {
                body.append('\n');
            }
            body.append(line(inf));
        }
        kids.add(Panels.text(body.toString()));
        kids.add(Panels.text("-# Página " + (page + 1) + "/" + pages
                + " · use `/caso <número>` para detalhes"));
        kids.add(ActionRow.of(
                Button.secondary(ComponentId.of(NS, "histpage", userId, String.valueOf(page - 1)), "◀")
                        .withDisabled(page <= 0),
                Button.secondary(ComponentId.of(NS, "histpage", userId, String.valueOf(page + 1)), "▶")
                        .withDisabled(page >= pages - 1)));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    /** Detail of one case, with a Revogar button when still active. */
    public static Container caseDetail(int accent, Infraction inf) {
        InfractionType type = inf.kind();
        String head = type == null ? "Caso" : type.emoji() + " " + type.label();
        long createdSecs = inf.createdAt() / 1000;
        StringBuilder body = new StringBuilder()
                .append("" + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · <@").append(inf.userId()).append("> · `").append(inf.userId()).append("`\n")
                .append("" + Emojis.of(Emojis.SHIELD, "🛡️") + " **Moderador** · ")
                .append("system".equals(inf.modId()) ? "`automático`" : "<@" + inf.modId() + ">").append('\n')
                .append("" + Emojis.of(Emojis.NOTE, "📝") + " **Motivo** · ").append(inf.reason() == null ? "*não informado*" : inf.reason()).append('\n')
                .append("" + Emojis.of(Emojis.CLOCK, "🕒") + " **Quando** · <t:").append(createdSecs).append(":F>");
        if (inf.durationMs() != null) {
            body.append("\n" + Emojis.of(Emojis.HOURGLASS, "⏳") + " **Duração** · `").append(Durations.format(inf.durationMs())).append('`');
        }
        if (inf.expiresAt() != null) {
            body.append("\n" + Emojis.of(Emojis.HOURGLASS, "⌛") + " **Expira** · <t:").append(inf.expiresAt() / 1000).append(":R>");
        }
        body.append("\n" + Emojis.of(Emojis.PIN, "📌") + " **Estado** · ").append(inf.active() ? "" + Emojis.of(Emojis.ONLINE, "🟢") + " ativo" : "" + Emojis.of(Emojis.DOT, "⚪") + " inativo");

        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + head + " · Caso #" + inf.caseNumber()));
        kids.add(Panels.divider());
        kids.add(Panels.text(body.toString()));
        if (inf.active()) {
            kids.add(ActionRow.of(Button.danger(
                    ComponentId.of(NS, "revoke", String.valueOf(inf.caseNumber())), "Revogar")));
        }
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    /** A select of a member's active cases to revoke. */
    public static Container revokePicker(int accent, String userId, List<Infraction> active) {
        List<ContainerChildComponent> kids = new ArrayList<>();
        kids.add(Panels.text("## " + Emojis.of(Emojis.RECYCLE, "♻️") + " Revogar infração"));
        kids.add(Panels.divider());
        kids.add(Panels.text("> Selecione qual infração de <@" + userId + "> deseja revogar."));
        if (active.isEmpty()) {
            kids.add(Panels.text("-# *Nenhuma infração ativa para revogar.*"));
            return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
        }
        StringSelectMenu.Builder menu = StringSelectMenu.create(ComponentId.of(NS, "revokepick", userId))
                .setPlaceholder("Escolha a infração a revogar");
        for (Infraction inf : active.stream().limit(25).toList()) {
            InfractionType t = inf.kind();
            String label = "Caso #" + inf.caseNumber() + " · " + (t == null ? inf.type() : t.label());
            String desc = inf.reason() == null ? null : trim(inf.reason(), 100);
            menu.addOption(trim(label, 100), String.valueOf(inf.caseNumber()), desc);
        }
        kids.add(ActionRow.of(menu.build()));
        return Panels.container(accent, kids.toArray(new ContainerChildComponent[0]));
    }

    private static String line(Infraction inf) {
        InfractionType t = inf.kind();
        String reason = inf.reason() == null ? "*sem motivo*" : trim(inf.reason(), 80);
        String state = inf.active() ? "" : " · " + Emojis.of(Emojis.DOT, "⚪") + "";
        return "`#" + inf.caseNumber() + "` " + (t == null ? "" : t.emoji() + " ") + "**"
                + (t == null ? inf.type() : t.label()) + "** · " + reason + state
                + "\n-# por " + ("system".equals(inf.modId()) ? "`automático`" : "<@" + inf.modId() + ">")
                + " · <t:" + (inf.createdAt() / 1000) + ":R>";
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
