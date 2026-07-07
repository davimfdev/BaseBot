package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.Durations;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;

import java.util.List;

/** Painéis da loja (vitrine efêmera + confirmação + resultado). Namespace "shop". */
public final class ShopView {
    public static final String NS = "shop";
    private ShopView() {}

    private static String typeLabel(ShopItem it) {
        return switch (it.type()) {
            case ROLE_PERM -> "cargo permanente";
            case ROLE_TEMP -> "cargo por " + Durations.format(it.durationS() * 1000L);
            case CUSTOM -> "item";
        };
    }

    private static String stockLabel(ShopItem it) {
        if (!it.limited()) {
            return "";
        }
        return it.soldOut() ? " · esgotado" : " · " + it.remaining() + " restantes";
    }

    public static Container vitrine(int accent, List<ShopItem> all, GuildConfig cfg) {
        if (all.isEmpty()) {
            return Panels.container(accent,
                    Panels.text("## " + Emojis.of(Emojis.SALES, "🛒") + " Loja"),
                    Panels.divider(),
                    Panels.text("A loja está vazia."));
        }
        List<ShopItem> items = all.size() > 25 ? all.subList(0, 25) : all;
        StringBuilder body = new StringBuilder("## " + Emojis.of(Emojis.SALES, "🛒") + " Loja\n");
        StringSelectMenu.Builder select = StringSelectMenu.create(ComponentId.of(NS, "pick"))
                .setPlaceholder("Escolha um item para comprar…");
        int buyable = 0;
        for (ShopItem it : items) {
            body.append("\n**").append(it.name()).append("** · ")
                    .append(EconomyFormat.format(it.price(), cfg)).append(" · ")
                    .append(typeLabel(it)).append(stockLabel(it));
            if (it.description() != null && !it.description().isBlank()) {
                body.append("\n-# ").append(it.description());
            }
            if (!it.soldOut()) {
                select.addOption(trim(it.name(), 100),
                        String.valueOf(it.id()),
                        trim(EconomyFormat.format(it.price(), cfg) + " · " + typeLabel(it), 100));
                buyable++;
            }
        }
        if (buyable == 0) {
            return Panels.container(accent, Panels.text(body.toString()), Panels.divider(),
                    Panels.text("-# Tudo esgotado no momento."));
        }
        return Panels.container(accent,
                Panels.text(body.toString()),
                Panels.divider(),
                ActionRow.of(select.build()));
    }

    public static Container confirm(int accent, ShopItem it, long cash, GuildConfig cfg) {
        String body = "## " + Emojis.of(Emojis.SALES, "🛒") + " Confirmar compra\n"
                + "**" + it.name() + "** · " + typeLabel(it) + "\n"
                + (it.description() == null || it.description().isBlank() ? "" : "-# " + it.description() + "\n")
                + Emojis.of(Emojis.CASH, "💵") + " Preço · " + EconomyFormat.formatNamed(it.price(), cfg) + "\n"
                + Emojis.of(Emojis.GEM, "💠") + " Sua carteira · " + EconomyFormat.format(cash, cfg);
        return Panels.container(accent,
                Panels.text(body),
                Panels.divider(),
                ActionRow.of(
                        Button.success(ComponentId.of(NS, "buy", String.valueOf(it.id())), "Comprar")
                                .withEmoji(Emojis.button(Emojis.CASH)),
                        Button.secondary(ComponentId.of(NS, "cancel"), "Cancelar")));
    }

    public static Container result(int accent, String message) {
        return Panels.container(accent,
                Panels.text("## " + Emojis.of(Emojis.SALES, "🛒") + " Loja"),
                Panels.divider(),
                Panels.text(message));
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
