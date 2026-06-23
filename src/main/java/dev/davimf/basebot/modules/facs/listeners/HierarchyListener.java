package dev.davimf.basebot.modules.facs.listeners;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the {@code /hierarquia} panel in sync with role changes, debounced per guild.
 *
 * <p>BOTSPECS §1 / §Module 4: role-add/remove events can arrive in bursts (mass role
 * updates). Refreshing the public embed on each event would hit Discord rate limits, so
 * every event schedules a single trailing refresh via the shared 5-second
 * {@link dev.davimf.basebot.ratelimit.Debouncer}, keyed by guild id.
 */
public final class HierarchyListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(HierarchyListener.class);

    private final BotContext ctx;

    public HierarchyListener(BotContext ctx) {
        this.ctx = ctx;
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
        ctx.embedDebouncer().debounce("hierarchy:" + guildId, () -> refreshPanel(guildId));
    }

    private void refreshPanel(String guildId) {
        // TODO(Module 4): rebuild and edit the hierarchy embed for this guild using the
        // configured panel message id (from guild_config). Runs at most once per 5s.
        log.debug("Hierarchy panel refresh fired for guild {}", guildId);
    }
}
