// [OUTLINE START]
// Package: dev.davimf.basebot.modules.facs.sets
// 
// Class: SetRequestView
// 
// Constructors:
//   - `Constructor` : `private SetRequestView()`
// 
// Methods:
//   - `Method` : `public static Container request(int accent, String requesterId, String roleId, String reason)`
//   - `Method` : `public static Container resolved(int accent, String requesterId, String roleId, String statusLine)`
// 
// Fields:
//   - `Field` : `public static final String NS`
// [OUTLINE END]



package dev.davimf.basebot.modules.facs.sets;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;

/** Views for the {@code /solicitar-cargo} (Set) request pipeline (BOTSPECS Module 4). */
public final class SetRequestView {

    public static final String NS = "set";

    private SetRequestView() {}

    /** The pending request posted to #log-sets with approve/reject buttons. */
    public static Container request(int accent, String requesterId, String roleId, String reason) {
        String body = "" + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · <@" + requesterId + ">\n"
                + "" + Emojis.of(Emojis.ROLES, "🎭") + " **Cargo pedido** · <@&" + roleId + ">\n"
                + "" + Emojis.of(Emojis.NOTE, "📝") + " **Motivo** · " + (reason == null || reason.isBlank() ? "*não informado*" : reason);
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.RANK, "🎖️") + " Solicitação de Set"),
                Panels.divider(),
                Panels.text(body),
                Panels.text("-# A aprovação exige um cargo superior na hierarquia."),
                ActionRow.of(
                        Button.success(ComponentId.of(NS, "approve", requesterId, roleId), "Aprovar").withEmoji(Emojis.button(Emojis.CHECK_YES)),
                        Button.danger(ComponentId.of(NS, "reject", requesterId, roleId), "Recusar").withEmoji(Emojis.button(Emojis.CHECK_NO))));
    }

    /** The resolved (button-less) request after approval/rejection. */
    public static Container resolved(int accent, String requesterId, String roleId, String statusLine) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.RANK, "🎖️") + " Solicitação de Set"),
                Panels.divider(),
                Panels.text("" + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · <@" + requesterId + ">\n"
                        + "" + Emojis.of(Emojis.ROLES, "🎭") + " **Cargo** · <@&" + roleId + ">"),
                Panels.divider(),
                Panels.text(statusLine));
    }
}
