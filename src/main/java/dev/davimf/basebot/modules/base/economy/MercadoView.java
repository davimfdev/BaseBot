package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Equip;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;

import java.util.List;

/** Painéis do mercado: vitrine de categorias → itens do slot → resultado da compra. Namespace "equip". */
public final class MercadoView {
    public static final String NS = "equip";
    private MercadoView() {}

    static String slotLabel(Slot slot) {
        return switch (slot) {
            case MINING -> "Mineração";
            case COOKING -> "Culinária";
            case DELIVERY -> "Entrega";
            case WEAPON -> "Arma";
        };
    }

    /** Vitrine inicial: um seletor de categoria (slot). */
    public static Container vitrine(int accent, GuildConfig cfg) {
        String body = "## " + Emojis.of(Emojis.SALES, "🛒") + " Mercado\n"
                + "Escolha uma categoria de equipamento para ver os itens à venda.";
        StringSelectMenu select = StringSelectMenu.create(ComponentId.of(NS, "mslot"))
                .setPlaceholder("Escolha uma categoria…")
                .addOption(slotLabel(Slot.MINING), Slot.MINING.name())
                .addOption(slotLabel(Slot.COOKING), Slot.COOKING.name())
                .addOption(slotLabel(Slot.DELIVERY), Slot.DELIVERY.name())
                .addOption(slotLabel(Slot.WEAPON), Slot.WEAPON.name())
                .build();
        return Panels.container(accent,
                Panels.text(body),
                Panels.divider(),
                ActionRow.of(select));
    }

    /** Itens à venda de um slot específico. */
    public static Container slotList(int accent, Slot slot, GuildConfig cfg) {
        String header = "## " + Emojis.of(Emojis.SALES, "🛒") + " Mercado · " + slotLabel(slot);
        List<Equip> items = EquipmentCatalog.ofSlot(slot);
        if (items.isEmpty()) {
            return Panels.container(accent, Panels.text(header), Panels.divider(),
                    Panels.text("Nenhum item disponível nessa categoria."));
        }
        List<Equip> capped = items.size() > 25 ? items.subList(0, 25) : items;
        StringBuilder body = new StringBuilder(header).append("\n");
        StringSelectMenu.Builder select = StringSelectMenu.create(ComponentId.of(NS, "buy"))
                .setPlaceholder("Escolha um item para comprar…");
        for (Equip e : capped) {
            body.append("\n**").append(e.name()).append("** · ")
                    .append(EconomyFormat.format(e.price(), cfg))
                    .append("\n-# ").append(EquipmentService.label(e));
            select.addOption(trim(e.name(), 100), e.key(),
                    trim(EconomyFormat.format(e.price(), cfg) + " · " + e.maxUsos() + " usos", 100));
        }
        return Panels.container(accent,
                Panels.text(body.toString()),
                Panels.divider(),
                ActionRow.of(select.build()));
    }

    /** Resultado (texto simples) de uma ação no mercado (ex.: mensagem da compra). */
    public static Container result(int accent, String message) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.SALES, "🛒") + " Mercado"),
                Panels.divider(),
                Panels.text(message));
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
