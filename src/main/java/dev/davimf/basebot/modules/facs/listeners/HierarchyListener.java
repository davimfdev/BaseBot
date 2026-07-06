// [OUTLINE START]
// Package: dev.davimf.basebot.modules.facs.listeners
// 
// Class: HierarchyListener
// 
// Constructors:
//   - `Constructor` : `public HierarchyListener(BotContext ctx, HierarchyService service)`
// 
// Fields:
//   - `Field` : `private final BotContext ctx`
//   - `Field` : `private final HierarchyService service`
// [OUTLINE END]



package dev.davimf.basebot.modules.facs.listeners;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.modules.facs.hierarchy.HierarchyService;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Keeps the {@code /hierarquia} panel in sync with role changes, debounced per guild.
 *
 * <p>BOTSPECS §1 / §Module 4: role-add/remove events can arrive in bursts (mass role
 * updates). Refreshing the public embed on each event would hit Discord rate limits, so
 * every event schedules a single trailing refresh via the shared 5-second
 * {@link dev.davimf.basebot.ratelimit.Debouncer}, keyed by guild id.
 */
public final class HierarchyListener extends ListenerAdapter {

    private final BotContext ctx;
    private final HierarchyService service;

    public HierarchyListener(BotContext ctx, HierarchyService service) {
        this.ctx = ctx;
        this.service = service;
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        scheduleRefresh(event.getGuild().getId());
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        scheduleRefresh(event.getGuild().getId());
    }

    private void scheduleRefresh(String guildId) {
        // Coalesce bursts of role changes into one panel edit per 5s (BOTSPECS §1 debounce).
        ctx.embedDebouncer().debounce("hierarchy:" + guildId, () -> service.refresh(guildId));
    }
}
