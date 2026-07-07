package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** XP por mensagem: 15–25 XP com cooldown de 60s por usuário (só quando level:enabled). */
public final class MessageXpListener extends ListenerAdapter {

    private static final long COOLDOWN_MS = 60_000L;

    private final BotContext ctx;
    private final LevelingService leveling;

    public MessageXpListener(BotContext ctx, LevelingService leveling) {
        this.ctx = ctx;
        this.leveling = leveling;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }
        Member member = event.getMember();
        if (member == null) {
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!LevelingConfig.enabled(cfg)
                || LevelingConfig.ignoredChannels(cfg).contains(event.getChannel().getId())) {
            return;
        }
        String guildId = event.getGuild().getId();
        String userId = member.getId();
        long now = System.currentTimeMillis();
        if (now - leveling.users().lastMessageTs(guildId, userId) < COOLDOWN_MS) {
            return;
        }
        leveling.users().setLastMessageTs(guildId, userId, now);
        leveling.awardMessage(event.getGuild(), member, event.getChannel());
    }
}
