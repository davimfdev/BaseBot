package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/** Paginação do /rico (namespace "eco"). */
public final class EconomyComponentHandler implements ComponentHandler {

    private final EconomyService eco;

    public EconomyComponentHandler(EconomyService eco) { this.eco = eco; }

    @Override
    public String namespace() { return RicoView.NS; }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"top".equals(id.action()) || event.getGuild() == null) {
            return;
        }
        int page = Math.max(0, parse(id.arg(0)));
        String guildId = event.getGuild().getId();
        int total = eco.wallets().count(guildId);
        var entries = eco.wallets().topPage(guildId, RicoView.PAGE, page * RicoView.PAGE);
        var cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        event.editComponents(RicoView.panel(EmbedColor.resolve(cfg), entries, page, total, cfg))
                .useComponentsV2().queue();
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }
}
