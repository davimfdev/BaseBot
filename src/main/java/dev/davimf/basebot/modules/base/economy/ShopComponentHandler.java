package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

/** Runtime da /loja: seleção → confirmação → compra (namespace "shop"). */
public final class ShopComponentHandler implements ComponentHandler {

    private final ShopService shop;

    public ShopComponentHandler(ShopService shop) { this.shop = shop; }

    @Override public String namespace() { return ShopView.NS; }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"pick".equals(id.action()) || event.getGuild() == null || event.getMember() == null) {
            return;
        }
        String guildId = event.getGuild().getId();
        long itemId = parseLong(event.getValues().get(0));
        ShopItem item = shop.items().find(guildId, itemId);
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        int accent = EmbedColor.resolve(cfg);
        if (item == null) {
            event.editComponents(ShopView.result(accent, "Item indisponível.")).useComponentsV2().queue();
            return;
        }
        long cash = new WalletRepository(ctx.database().sqlite()).get(guildId, event.getMember().getId()).cash();
        event.editComponents(ShopView.confirm(accent, item, cash, cfg)).useComponentsV2().queue();
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        int accent = EmbedColor.resolve(cfg);
        switch (id.action()) {
            case "cancel" -> event.editComponents(ShopView.result(accent, "Compra cancelada."))
                    .useComponentsV2().queue();
            case "buy" -> {
                long itemId = parseLong(id.arg(0));
                String msg = shop.buy(event.getGuild(), event.getMember(), itemId);
                event.editComponents(ShopView.result(accent, msg)).useComponentsV2().queue();
            }
            default -> { }
        }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return -1; }
    }
}
