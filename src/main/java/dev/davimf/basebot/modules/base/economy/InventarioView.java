package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Equip;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Painel do inventário: itens agrupados por slot, controle de equipar opera sempre por
 * {@code rowId} (nunca por {@code item_key}, já que o mesmo item pode ter várias instâncias).
 * Namespace "equip".
 */
public final class InventarioView {
    private InventarioView() {}

    public static Container panel(int accent, List<InventoryRepository.Row> rows, GuildConfig cfg) {
        String header = "## " + Emojis.of(Emojis.EDIT, "🎒") + " Inventário";
        if (rows.isEmpty()) {
            return Panels.container(accent, Panels.text(header), Panels.divider(),
                    Panels.text("Você não tem nenhum equipamento. Confira o `/economia mercado`."));
        }
        Map<Slot, List<InventoryRepository.Row>> bySlot = new EnumMap<>(Slot.class);
        for (InventoryRepository.Row row : rows) {
            bySlot.computeIfAbsent(row.slot(), s -> new ArrayList<>()).add(row);
        }
        StringBuilder body = new StringBuilder(header).append("\n");
        StringSelectMenu.Builder select = StringSelectMenu.create(ComponentId.of(MercadoView.NS, "equip"))
                .setPlaceholder("Escolha um item para equipar…");
        int options = 0;
        for (Slot slot : Slot.values()) {
            List<InventoryRepository.Row> items = bySlot.get(slot);
            if (items == null || items.isEmpty()) {
                continue;
            }
            body.append("\n**").append(MercadoView.slotLabel(slot)).append("**\n");
            for (InventoryRepository.Row row : items) {
                Equip e = EquipmentCatalog.byKey(row.itemKey());
                String name = e != null ? e.name() : row.itemKey();
                boolean equipped = row.equipped();
                body.append(equipped ? Emojis.of(Emojis.CHECK_YES, "✅") + " " : "- ")
                        .append("#").append(row.id()).append(" · ").append(name)
                        .append(" · ").append(row.usosLeft()).append(" usos")
                        .append(equipped ? " (equipado)" : "").append("\n");
                if (options < 25) {
                    String label = trim("Equipar #" + row.id() + " — " + name + " — " + row.usosLeft() + " usos", 100);
                    select.addOptions(option(label, String.valueOf(row.id()), MercadoView.slotEmoji(slot)));
                    options++;
                }
            }
        }
        if (options == 0) {
            return Panels.container(accent, Panels.text(body.toString()));
        }
        return Panels.container(accent,
                Panels.text(body.toString()),
                Panels.divider(),
                ActionRow.of(select.build()));
    }

    /** Resultado (texto simples) de uma ação no inventário (ex.: mensagem de equipar). */
    public static Container result(int accent, String message) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.EDIT, "🎒") + " Inventário"),
                Panels.divider(),
                Panels.text(message));
    }

    /** Opção de select com emoji custom via {@code withEmoji}; sem ícone se ainda não subiu. */
    private static SelectOption option(String label, String value, String emojiName) {
        SelectOption o = SelectOption.of(label, value);
        Emoji e = Emojis.button(emojiName);
        return e != null ? o.withEmoji(e) : o;
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
