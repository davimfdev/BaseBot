package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.util.List;

/** Paginação do /topcall (namespace "vtime"). Recalcula o ranking ao vivo a cada clique. */
public final class VoiceTimeComponentHandler implements ComponentHandler {

    private final VoiceGate gate;

    public VoiceTimeComponentHandler(VoiceGate gate) { this.gate = gate; }

    @Override
    public String namespace() { return TopCallView.NS; }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"top".equals(id.action()) || event.getGuild() == null) {
            return;
        }
        String guildId = event.getGuild().getId();
        long now = System.currentTimeMillis();
        long week = VoiceWeek.weekStart(now);
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);

        List<VoiceTimeRepository.Entry> all = VoiceLive.ranking(event.getGuild(), cfg, gate,
                new VoiceSessionRepository(ctx.database().sqlite()),
                new VoiceTimeRepository(ctx.database().sqlite()), now, week);

        int pages = Math.max(1, (all.size() + TopCallView.PAGE - 1) / TopCallView.PAGE);
        int page = Math.min(Math.max(0, parse(id.arg(0))), pages - 1);
        int from = Math.min(page * TopCallView.PAGE, all.size());
        int to = Math.min(from + TopCallView.PAGE, all.size());

        event.editComponents(TopCallView.panel(EmbedColor.resolve(cfg),
                        all.subList(from, to), page, all.size()))
                .useComponentsV2().queue();
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }
}
