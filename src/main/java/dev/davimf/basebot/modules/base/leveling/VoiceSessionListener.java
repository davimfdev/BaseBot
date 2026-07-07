package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Abre/fecha sessões de voz a partir do evento unificado (join/leave/move). Requer GUILD_VOICE_STATES. */
public final class VoiceSessionListener extends ListenerAdapter {

    private final VoiceSessionRepository sessions;

    public VoiceSessionListener(BotContext ctx) {
        this.sessions = new VoiceSessionRepository(ctx.database().sqlite());
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        Member member = event.getMember();
        if (member.getUser().isBot()) {
            return;
        }
        String guildId = event.getGuild().getId();
        String userId = member.getId();
        long now = System.currentTimeMillis();
        AudioChannel joined = event.getChannelJoined();
        AudioChannel left = event.getChannelLeft();

        if (joined != null && left == null) {
            sessions.open(guildId, userId, joined.getId(), now);
        } else if (left != null && joined == null) {
            sessions.closeOpen(guildId, userId, now);
        } else if (left != null && joined != null) {
            sessions.closeOpen(guildId, userId, now);
            sessions.open(guildId, userId, joined.getId(), now);
        }
    }
}
