// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.listacargo
// 
// Class: ListaCargoComponentHandler
// 
// Methods:
//   - `Method` : `public String namespace()`
//   - `Method` : `private int parsePage(String raw)`
// [OUTLINE END]



package dev.davimf.basebot.modules.base.listacargo;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Paginator;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.util.List;

/** Prev/next navigation for the {@code /listacargo} embed (stateless: re-reads members). */
public final class ListaCargoComponentHandler implements ComponentHandler {

    @Override
    public String namespace() {
        return ListaCargoView.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"nav".equals(id.action()) || event.getGuild() == null) {
            return;
        }
        Role role = event.getGuild().getRoleById(id.arg(0));
        if (role == null) {
            Replies.ephemeral(event, ctx, "Cargo não encontrado.");
            return;
        }
        int requested = parsePage(id.arg(1));
        List<Member> members = event.getGuild().getMembersWithRoles(role);
        int pages = Paginator.pageCount(members.size(), ListaCargoView.PAGE_SIZE);
        int page = Math.max(0, Math.min(requested, pages - 1));
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));
        event.editComponents(ListaCargoView.container(accent, role, members, page))
                .useComponentsV2()
                .setAllowedMentions(java.util.Collections.emptyList()) // don't ping listed members
                .queue();
    }

    private int parsePage(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
