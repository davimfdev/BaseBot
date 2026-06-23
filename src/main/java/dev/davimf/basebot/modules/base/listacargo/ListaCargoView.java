package dev.davimf.basebot.modules.base.listacargo;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.util.Paginator;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;

import java.util.List;

/** Builds the {@code /listacargo} paginated embed + prev/next navigation row. */
public final class ListaCargoView {

    public static final String NS = "listacargo";
    public static final int PAGE_SIZE = 20;

    private ListaCargoView() {}

    public static MessageEmbed embed(Role role, List<Member> members, int pageIndex) {
        int pages = Paginator.pageCount(members.size(), PAGE_SIZE);
        List<Member> slice = Paginator.page(members, pageIndex, PAGE_SIZE);
        StringBuilder sb = new StringBuilder();
        if (slice.isEmpty()) {
            sb.append("*Nenhum membro com este cargo.*");
        } else {
            for (Member m : slice) {
                sb.append("• ").append(m.getAsMention()).append('\n');
            }
        }
        return new EmbedBuilder()
                .setTitle("Membros de " + role.getName() + " (" + members.size() + ")")
                .setColor(role.getColorRaw())
                .setDescription(sb.toString())
                .setFooter("Página " + (pageIndex + 1) + "/" + pages)
                .build();
    }

    public static ActionRow navRow(String roleId, int pageIndex, int pageCount) {
        Button prev = Button.secondary(ComponentId.of(NS, "nav", roleId, String.valueOf(pageIndex - 1)), "◀")
                .withDisabled(pageIndex <= 0);
        Button next = Button.secondary(ComponentId.of(NS, "nav", roleId, String.valueOf(pageIndex + 1)), "▶")
                .withDisabled(pageIndex >= pageCount - 1);
        return ActionRow.of(prev, next);
    }
}
