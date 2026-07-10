package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/** Paginação do /topcall (namespace "vtime"). */
public final class VoiceTimeComponentHandler implements ComponentHandler {

    @Override
    public String namespace() { return TopCallView.NS; }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"top".equals(id.action()) || event.getGuild() == null) {
            return;
        }
        int page = Math.max(0, parse(id.arg(0)));
        String guildId = event.getGuild().getId();
        long week = VoiceWeek.weekStart(System.currentTimeMillis());
        VoiceTimeRepository repo = new VoiceTimeRepository(ctx.database().sqlite());
        int total = repo.count(guildId, week);
        var entries = repo.topPage(guildId, week, TopCallView.PAGE, page * TopCallView.PAGE);
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
        event.editComponents(TopCallView.panel(accent, entries, page, total)).useComponentsV2().queue();
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }
}
