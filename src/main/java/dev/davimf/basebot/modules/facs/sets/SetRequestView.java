package dev.davimf.basebot.modules.facs.sets;

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
        String body = "## 🎖️ Solicitação de Set\n"
                + "**Membro:** <@" + requesterId + ">\n"
                + "**Cargo pedido:** <@&" + roleId + ">\n"
                + "**Motivo:** " + (reason == null || reason.isBlank() ? "*não informado*" : reason)
                + "\n-# A aprovação exige um cargo superior na hierarquia.";
        return Panels.container(accent,
                Panels.text(body),
                ActionRow.of(
                        Button.success(ComponentId.of(NS, "approve", requesterId, roleId), "✅ Aprovar"),
                        Button.danger(ComponentId.of(NS, "reject", requesterId, roleId), "❌ Recusar")));
    }

    /** The resolved (button-less) request after approval/rejection. */
    public static Container resolved(int accent, String requesterId, String roleId, String statusLine) {
        return Panels.container(accent, Panels.text("## 🎖️ Solicitação de Set\n"
                + "**Membro:** <@" + requesterId + ">\n"
                + "**Cargo:** <@&" + roleId + ">\n" + statusLine));
    }
}
