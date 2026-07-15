package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

/** Runtime do /economia reparar (namespace "repair"). */
public final class RepararComponentHandler implements ComponentHandler {

    private final EquipmentService svc;

    public RepararComponentHandler(EquipmentService svc) { this.svc = svc; }

    @Override public String namespace() { return RepararView.NS; } // "repair"

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            return;
        }
        String g = event.getGuild().getId();
        String u = event.getMember().getId();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(g);
        int accent = EmbedColor.resolve(cfg);
        if ("fix".equals(id.action())) {
            String msg = svc.repair(g, u, parse(event.getValues().get(0)));
            event.editComponents(RepararView.result(accent, msg)).useComponentsV2().queue();
        }
    }

    private static long parse(String s) { try { return Long.parseLong(s); } catch (Exception e) { return -1; } }
}
