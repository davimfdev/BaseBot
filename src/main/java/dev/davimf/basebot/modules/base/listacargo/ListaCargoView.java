// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.listacargo
// 
// Class: ListaCargoView
// 
// Constructors:
//   - `Constructor` : `private ListaCargoView()`
// 
// Methods:
//   - `Method` : `public static Container container(int accent, Role role, List<Member> members, int pageIndex)`
//   - `Method` : `public static ActionRow navRow(String roleId, int pageIndex, int pageCount)`
// 
// Fields:
//   - `Field` : `public static final String NS`
//   - `Field` : `public static final int PAGE_SIZE`
// [OUTLINE END]



package dev.davimf.basebot.modules.base.listacargo;

import dev.davimf.basebot.util.Emojis;

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

    public static Container container(int accent, Role role, List<Member> members, int pageIndex) {
        int pages = Paginator.pageCount(members.size(), PAGE_SIZE);
        List<Member> slice = Paginator.page(members, pageIndex, PAGE_SIZE);

        StringBuilder body = new StringBuilder();
        if (slice.isEmpty()) {
            body.append("-# *Nenhum membro com este cargo.*");
        } else {
            int start = pageIndex * PAGE_SIZE;
            for (int i = 0; i < slice.size(); i++) {
                body.append(start + i + 1).append(". ").append(slice.get(i).getAsMention()).append('\n');
            }
            body.append("\n-# Página ").append(pageIndex + 1).append('/').append(pages);
        }

        // Tint with the role's own colour, falling back to the guild accent when it has none.
        int color = role.getColors().isDefault() ? accent : role.getColors().getPrimaryRaw();
        return Panels.container(color,
                Panels.text("## " + Emojis.of(Emojis.MEMBERS, "👥") + " " + role.getName() + " `" + members.size() + "`"),
                Panels.divider(),
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
