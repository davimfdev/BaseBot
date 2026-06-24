package dev.davimf.basebot.modules.facs;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.List;

/**
 * Posts a Components V2 log entry to one of the Facs log channels configured in
 * {@code /setup → Logs} (BOTSPECS §Module 4 integration). Mentions are suppressed so log
 * entries never ping; a missing/invalid channel is silently skipped.
 */
public final class FacsLog {

    private FacsLog() {}

    public static void post(BotContext ctx, String guildId, String logKey, String markdown) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        String channelId = cfg.channel(logKey);
        if (channelId == null || ctx.jda() == null) {
            return;
        }
        TextChannel channel = ctx.jda().getTextChannelById(channelId);
        if (channel == null) {
            return;
        }
        channel.sendMessageComponents(Panels.container(EmbedColor.resolve(cfg), Panels.text(markdown)))
                .useComponentsV2()
                .setAllowedMentions(List.of())
                .queue(ok -> {}, err -> {});
    }
}
