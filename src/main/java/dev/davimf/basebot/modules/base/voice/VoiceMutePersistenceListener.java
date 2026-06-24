package dev.davimf.basebot.modules.base.voice;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildMuteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Enforces persistent call mutes (BOTSPECS Module 1): re-applies a still-active voice
 * mute both when a flagged member joins a voice channel and when someone (a moderator,
 * manually) un-mutes them before the timer is up.
 */
public final class VoiceMutePersistenceListener extends ListenerAdapter {

    private final BotContext ctx;

    public VoiceMutePersistenceListener(BotContext ctx) {
        this.ctx = ctx;
    }

    /** Joined/moved into a channel → re-mute if still under an active call mute. */
    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        if (event.getChannelJoined() == null) {
            return;
        }
        remuteIfActive(event.getGuild(), event.getMember());
    }

    /** Server mute changed → if they were just un-muted but still muted in the bot, re-mute. */
    @Override
    public void onGuildVoiceGuildMute(GuildVoiceGuildMuteEvent event) {
        if (event.isGuildMuted()) {
            return; // only react to un-mutes
        }
        remuteIfActive(event.getGuild(), event.getMember());
    }

    private void remuteIfActive(net.dv8tion.jda.api.entities.Guild guild, Member member) {
        if (ctx.database().mutes().isActive(guild.getId(), member.getId(),
                MuteRepository.VOICE, System.currentTimeMillis())) {
            guild.mute(member, true).reason("Mute de call ainda ativo").queue(ok -> {}, err -> {});
        }
    }
}
