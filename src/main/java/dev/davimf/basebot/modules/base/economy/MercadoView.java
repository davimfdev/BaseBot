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
            case TECH -> "Tecnologia";
            case FARM -> "Fazenda";
            case FISHING -> "Pescaria";
            case EXPEDITION -> "Expedição";
            case BUSINESS -> "Negócios";
        };
    }

    /** Nome do emoji custom que melhor representa o slot (usado via {@code withEmoji}). */
    static String slotEmoji(Slot slot) {
        return switch (slot) {
            case MINING -> Emojis.GEM;
            case WEAPON -> Emojis.WEAPON;
            case FARM -> Emojis.SPROUT;
            case EXPEDITION -> Emojis.COMPASS;
            case BUSINESS -> Emojis.MONEY;
            case TECH -> Emojis.GEAR;
            case COOKING, DELIVERY, FISHING -> Emojis.PRODUCT;
        };
    }

    /** Vitrine inicial: um seletor de categoria (slot). */
    public static Container vitrine(int accent, GuildConfig cfg) {
        String body = "## " + Emojis.of(Emojis.SALES, "🛒") + " Mercado\n"
                + "Escolha uma categoria de equipamento para ver os itens à venda.";
        StringSelectMenu.Builder builder = StringSelectMenu.create(ComponentId.of(NS, "mslot"))
                .setPlaceholder("Escolha uma categoria…");
        for (Slot slot : Slot.values()) {
            builder.addOptions(option(slotLabel(slot), slot.name(), null, slotEmoji(slot)));
        }
        StringSelectMenu select = builder.build();
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
            select.addOptions(option(trim(e.name(), 100), e.key(),
                    trim(EconomyFormat.formatPlain(e.price(), cfg) + " · " + e.maxUsos() + " usos", 100),
                    slotEmoji(slot)));
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

    /** Opção de select com emoji custom via {@code withEmoji} (renderiza, ao contrário do texto);
     *  sem ícone se o emoji ainda não subiu. {@code description} pode ser {@code null}. */
    private static SelectOption option(String label, String value, String description, String emojiName) {
        SelectOption o = SelectOption.of(label, value);
        if (description != null) {
            o = o.withDescription(description);
        }
        Emoji e = Emojis.button(emojiName);
        return e != null ? o.withEmoji(e) : o;
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
