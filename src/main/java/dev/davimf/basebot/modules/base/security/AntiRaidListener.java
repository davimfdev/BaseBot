package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.time.OffsetDateTime;

/** Detects a join surge and triggers the anti-raid lockdown. New accounts lower the threshold. */
public final class AntiRaidListener extends ListenerAdapter {

    private final BotContext ctx;
    private volatile JoinWindow window = new JoinWindow(10_000);
    private volatile long windowStamp = Long.MIN_VALUE;

    public AntiRaidListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!SecurityConfig.antiraid(cfg)) {
            return;
        }
        ensureWindow(cfg);
        int count = window.record(event.getGuild().getId(), System.currentTimeMillis());
        boolean youngAccount = event.getUser().getTimeCreated()
                .isAfter(OffsetDateTime.now().minusDays(SecurityConfig.raidMinAgeDays(cfg)));
        int threshold = Math.max(2, SecurityConfig.raidJoins(cfg) - (youngAccount ? 2 : 0));
        if (count >= threshold) {
            AntiRaidService.lockdown(ctx, event.getGuild());
        }
    }

    private void ensureWindow(GuildConfig cfg) {
        long stamp = SecurityConfig.raidWindowSeconds(cfg);
        if (stamp != windowStamp) {
            window = new JoinWindow(SecurityConfig.raidWindowSeconds(cfg) * 1000L);
            windowStamp = stamp;
        }
    }
}
