package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.moderation.ModerationService;
import dev.davimf.basebot.util.ChannelLog;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.automod.AutoModExecutionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Reacts to a native AutoMod block: checks exemptions, counts the violation in a sliding
 * window, and on the configured threshold applies a warn (which escalates via Infractions).
 * Always posts a summary to the modlog. The Discord side already blocked the message.
 */
public final class AutoModExecutionListener extends ListenerAdapter {

    private final BotContext ctx;
    private final ModerationService moderation;
    private volatile ViolationWindow window = new ViolationWindow(1, 0);
    private volatile long windowStamp = Long.MIN_VALUE;

    public AutoModExecutionListener(BotContext ctx, ModerationService moderation) {
        this.ctx = ctx;
        this.moderation = moderation;
    }

    @Override
    public void onAutoModExecution(AutoModExecutionEvent event) {
        Guild guild = event.getGuild();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        if (!SecurityConfig.automod(cfg) || !SecurityConfig.automodWarn(cfg)) {
            return;
        }
        long userId = event.getUserIdLong();
        String channelId = event.getChannel() == null ? null : event.getChannel().getId();
        String matched = firstNonBlank(event.getMatchedKeyword(), event.getMatchedContent(), "—");

        guild.retrieveMemberById(userId).queue(member -> {
            List<String> roleIds = member.getRoles().stream().map(Role::getId).collect(Collectors.toList());
            if (SecurityConfig.isExempt(cfg, roleIds, channelId)) {
                return;
            }
            ensureWindow(cfg);
            boolean warnNow = window.record(String.valueOf(userId), System.currentTimeMillis());
            ChannelLog.post(ctx, guild.getId(), ModerationService.MODLOG_KEY,
                    "## " + Emojis.of(Emojis.SHIELD, "🛡️") + " AutoMod bloqueou\n---\n"
                            + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + member.getAsMention()
                            + "\n**Tipo** · `" + event.getTriggerType() + "`"
                            + "\n**Casou** · `" + trim(matched) + "`"
                            + (warnNow ? "\n---\n" + Emojis.of(Emojis.WARN, "⚠️") + " **Warn aplicado** (limite atingido)" : ""));
            if (warnNow) {
                moderation.warn(guild, member, "system", "AutoMod: " + event.getTriggerType());
            }
        }, err -> { });
    }

    private void ensureWindow(GuildConfig cfg) {
        long stamp = (long) SecurityConfig.warnPer(cfg) * 1_000_000L + SecurityConfig.windowSeconds(cfg);
        if (stamp != windowStamp) {
            window = new ViolationWindow(SecurityConfig.warnPer(cfg), SecurityConfig.windowSeconds(cfg) * 1000L);
            windowStamp = stamp;
        }
    }

    private static String firstNonBlank(String a, String b, String fallback) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return fallback;
    }

    private static String trim(String s) {
        return s.length() > 100 ? s.substring(0, 100) + "…" : s;
    }
}
