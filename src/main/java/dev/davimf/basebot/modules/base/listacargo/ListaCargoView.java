package dev.davimf.basebot.modules.base.listacargo;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.Paginator;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.List;

/** Builds the {@code /listacargo} list as a Components V2 container (text + nav inside). */
public final class ListaCargoView {

    public static final String NS = "listacargo";
    public static final int PAGE_SIZE = 20;

    private ListaCargoView() {}

    public static Container container(Role role, List<Member> members, int pageIndex) {
        int pages = Paginator.pageCount(members.size(), PAGE_SIZE);
        List<Member> slice = Paginator.page(members, pageIndex, PAGE_SIZE);

        StringBuilder body = new StringBuilder("## Membros de " + role.getName()
                + " (" + members.size() + ")\n");
        if (slice.isEmpty()) {
            body.append("*Nenhum membro com este cargo.*");
        } else {
            for (Member m : slice) {
                body.append("• ").append(m.getAsMention()).append('\n');
            }
        }
        body.append("\n-# Página ").append(pageIndex + 1).append('/').append(pages);

        int accent = role.getColorRaw() == 0 ? Panels.BLURPLE : role.getColorRaw();
        return Panels.container(accent,
                Panels.text(body.toString()),
                navRow(role.getId(), pageIndex, pages));
    }

    public static ActionRow navRow(String roleId, int pageIndex, int pageCount) {
        Button prev = Button.secondary(ComponentId.of(NS, "nav", roleId, String.valueOf(pageIndex - 1)), "◀")
                .withDisabled(pageIndex <= 0);
        Button next = Button.secondary(ComponentId.of(NS, "nav", roleId, String.valueOf(pageIndex + 1)), "▶")
                .withDisabled(pageIndex >= pageCount - 1);
        return ActionRow.of(prev, next);
    }
}
