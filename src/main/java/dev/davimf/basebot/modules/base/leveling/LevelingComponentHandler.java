package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/** Paginação do /top (namespace "lvl"). */
public final class LevelingComponentHandler implements ComponentHandler {

    private final LevelingService leveling;

    public LevelingComponentHandler(LevelingService leveling) { this.leveling = leveling; }

    @Override
    public String namespace() { return TopView.NS; }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"top".equals(id.action()) || event.getGuild() == null) {
            return;
        }
        int page = Math.max(0, parse(id.arg(0)));
        String guildId = event.getGuild().getId();
        int total = leveling.users().count(guildId);
        var entries = leveling.users().topPage(guildId, TopView.PAGE, page * TopView.PAGE);
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
        event.editComponents(TopView.panel(accent, entries, page, total)).useComponentsV2().queue();
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }
}
