package dev.davimf.basebot.modules.facs.economy;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;

import java.util.List;

/** Views for the {@code /farm} submission approval flow (BOTSPECS Module 4). */
public final class FarmView {

    public static final String NS = "farm";

    private FarmView() {}

    /** Pending submission with approve/reject buttons carrying the pending id. */
    public static Container request(int accent, String pendingId, String farmerId, String item, long qty) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.FARM, "🌾") + " Entrega de Farm"),
                Panels.divider(),
                Panels.text(details(farmerId, item, qty)),
                ActionRow.of(
                        Button.success(ComponentId.of(NS, "approve", pendingId), "Aprovar").withEmoji(Emojis.button(Emojis.CHECK_YES)),
                        Button.danger(ComponentId.of(NS, "reject", pendingId), "Recusar").withEmoji(Emojis.button(Emojis.CHECK_NO))));
    }

    public static Container resolved(int accent, String farmerId, String item, long qty, String statusLine) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.FARM, "🌾") + " Entrega de Farm"),
                Panels.divider(),
                Panels.text(details(farmerId, item, qty)),
                Panels.divider(),
                Panels.text(statusLine));
    }

    /** Ephemeral item picker shown by {@code /farm}. {@code items} must be non-empty. */
    public static Container pickItem(int accent, List<String> items) {
        StringSelectMenu.Builder menu = StringSelectMenu.create(ComponentId.of(NS, "pickitem"))
                .setPlaceholder("Escolha o material entregue");
        for (String i : items) {
            menu.addOption(i, i);
        }
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.FARM, "🌾") + " Entregar Farm"),
                Panels.divider(),
                Panels.text("> Escolha o material; em seguida informe a quantidade."),
                ActionRow.of(menu.build()));
    }

    /** Modal asking only the quantity; the chosen item is carried in the custom id. */
    public static Modal quantityModal(String item) {
        TextInput qty = TextInput.create("quantidade", TextInputStyle.SHORT)
                .setPlaceholder("Quantidade entregue").setRequired(true).setMaxLength(7).build();
        return Modal.create(ComponentId.of(NS, "qtyform", item), "Entregar material")
                .addComponents(Label.of("Quantidade de " + trimLabel(item), qty))
                .build();
    }

    private static String trimLabel(String item) {
        return item.length() > 30 ? item.substring(0, 30) : item;
    }

    private static String details(String farmerId, String item, long qty) {
        return "" + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · <@" + farmerId + ">\n"
                + "" + Emojis.of(Emojis.PRODUCT, "📦") + " **Material** · `" + item + "`\n"
                + "" + Emojis.of(Emojis.HASH, "🔢") + " **Quantidade** · `" + qty + "`";
    }
}
