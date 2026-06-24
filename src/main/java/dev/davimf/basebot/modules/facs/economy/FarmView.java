package dev.davimf.basebot.modules.facs.economy;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;

/** Views for the {@code /farm} submission approval flow (BOTSPECS Module 4). */
public final class FarmView {

    public static final String NS = "farm";

    private FarmView() {}

    /** Pending submission with approve/reject buttons carrying the pending id. */
    public static Container request(int accent, String pendingId, String farmerId, String item, long qty) {
        return Panels.container(accent,
                Panels.text(body(farmerId, item, qty)),
                ActionRow.of(
                        Button.success(ComponentId.of(NS, "approve", pendingId), "✅ Aprovar"),
                        Button.danger(ComponentId.of(NS, "reject", pendingId), "❌ Recusar")));
    }

    public static Container resolved(int accent, String farmerId, String item, long qty, String statusLine) {
        return Panels.container(accent, Panels.text(body(farmerId, item, qty) + "\n" + statusLine));
    }

    private static String body(String farmerId, String item, long qty) {
        return "## 🌾 Entrega de Farm\n"
                + "**Membro:** <@" + farmerId + ">\n"
                + "**Material:** " + item + "\n"
                + "**Quantidade:** " + qty;
    }
}
