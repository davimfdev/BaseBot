package dev.davimf.basebot.modules.base.voice;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Re-applies persistent call mutes (BOTSPECS Module 1): when a flagged member joins a
 * voice channel, server-mute them again so the mute survives reconnects.
 */
public final class VoiceMutePersistenceListener extends ListenerAdapter {

    private final BotContext ctx;

    public VoiceMutePersistenceListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        if (event.getChannelJoined() == null) {
            return; // only act when joining/moving into a channel
        }
        Member member = event.getMember();
        String guildId = event.getGuild().getId();
        if (ctx.database().voiceMutes().isMuted(guildId, member.getId())) {
            event.getGuild().mute(member, true).reason("Mute de call persistente").queue(ok -> {}, err -> {});
        }
    }
}
