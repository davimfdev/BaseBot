package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Equip;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.Row;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.List;

/** Painel do /economia reparar: seleção de item quase quebrado → reparo. Namespace "repair". */
public final class RepararView {
    public static final String NS = "repair";
    private RepararView() {}

    /** Lista só itens reparáveis (já filtrados pelo comando). */
    public static Container panel(int accent, List<Row> repairable, GuildConfig cfg) {
        String header = "## " + Emojis.of(Emojis.WRENCH, "🔧") + " Reparar";
        if (repairable.isEmpty()) {
            return Panels.container(accent, Panels.text(header), Panels.divider(),
                    Panels.text("Você não tem itens quase quebrados para reparar. "
                            + "O reparo só vale quando o item está no fim da durabilidade."));
        }
        List<Row> capped = repairable.size() > 25 ? repairable.subList(0, 25) : repairable;
        StringBuilder body = new StringBuilder(header).append("\n");
        StringSelectMenu.Builder select = StringSelectMenu.create(ComponentId.of(NS, "fix"))
                .setPlaceholder("Escolha um item para reparar…");
        for (Row row : capped) {
            Equip e = EquipmentCatalog.byKey(row.itemKey());
            String name = e != null ? e.name() : row.itemKey();
            int max = e != null ? e.maxUsos() : 0;
            long price = e != null ? RepairPolicy.cost(e.price()) : 0;
            body.append("\n**").append(name).append("** · ").append(row.usosLeft()).append('/').append(max)
                    .append(" usos · reparos ").append(row.repairs()).append("/").append(RepairPolicy.MAX_REPAIRS)
                    .append("\n-# custa ").append(EconomyFormat.format(price, cfg))
                    .append(" · restaura ").append(e != null ? RepairPolicy.restoredUsos(max) : 0).append(" usos");
            String desc = EconomyFormat.formatPlain(price, cfg) + " · " + row.usosLeft() + "/" + max + " usos";
            select.addOptions(option("#" + row.id() + " " + name, String.valueOf(row.id()),
                    desc.length() > 100 ? desc.substring(0, 99) + "…" : desc));
        }
        return Panels.container(accent, Panels.text(body.toString()), Panels.divider(), ActionRow.of(select.build()));
    }

    public static Container result(int accent, String message) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.WRENCH, "🔧") + " Reparar"),
                Panels.divider(),
                Panels.text(message));
    }

    private static SelectOption option(String label, String value, String description) {
        SelectOption o = SelectOption.of(label.length() > 100 ? label.substring(0, 99) + "…" : label, value)
                .withDescription(description);
        Emoji e = Emojis.button(Emojis.WRENCH);
        return e != null ? o.withEmoji(e) : o;
    }
}
