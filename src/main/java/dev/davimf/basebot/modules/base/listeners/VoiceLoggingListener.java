package dev.davimf.basebot.modules.base.listeners;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.AuditLookup;
import dev.davimf.basebot.util.ChannelLog;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildDeafenEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildMuteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceStreamEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceVideoEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Logs de voz em log-voz: tráfego, movido à força (substitui o log de mudança normal),
 *  server mute/deafen, stream e câmera. */
public final class VoiceLoggingListener extends ListenerAdapter {

    private final BotContext ctx;

    public VoiceLoggingListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        AudioChannel left = event.getChannelLeft();
        AudioChannel joined = event.getChannelJoined();
        String member = event.getMember().getAsMention();
        String guildId = event.getGuild().getId();
        if (left == null && joined != null) {
            ChannelLog.post(ctx, guildId, "log-voz",
                    "## " + Emojis.of(Emojis.VOLUME, "🔊") + " Entrou em call\n" + member + " entrou em " + joined.getAsMention());
        } else if (left != null && joined == null) {
            ChannelLog.post(ctx, guildId, "log-voz",
                    "## " + Emojis.of(Emojis.MUTE, "🔇") + " Saiu da call\n" + member + " saiu de " + left.getAsMention());
        } else if (left != null) {
            String trajeto = left.getAsMention() + " → " + joined.getAsMention();
            AuditLookup.lookup(event.getGuild(), event.getMember().getId(), ActionType.MEMBER_VOICE_MOVE,
                    actor -> ChannelLog.post(ctx, guildId, "log-voz",
                            "## " + Emojis.of(Emojis.ARROW_MOVE, "↪️") + " Movido à força\n---\n"
                                    + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + member
                                    + "\n**Trajeto** · " + trajeto + "\n---\n"
                                    + Emojis.of(Emojis.SHIELD, "🛡️") + " **Responsável** · " + actor.moderatorMention()),
                    () -> ChannelLog.post(ctx, guildId, "log-voz",
                            "## " + Emojis.of(Emojis.ARROW_MOVE, "🔁") + " Mudou de call\n" + member + " · " + trajeto));
        }
    }

    @Override
    public void onGuildVoiceGuildMute(GuildVoiceGuildMuteEvent event) {
        String head = event.isGuildMuted()
                ? "## " + Emojis.of(Emojis.MUTE, "🔇") + " Silenciado no servidor"
                : "## " + Emojis.of(Emojis.VOLUME_LOW, "🔈") + " Dessilenciado no servidor";
        AuditLookup.lookup(event.getGuild(), event.getMember().getId(), ActionType.MEMBER_UPDATE, actor ->
                ChannelLog.post(ctx, event.getGuild().getId(), "log-voz",
                        head + "\n---\n" + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + event.getMember().getAsMention()
                                + "\n---\n" + Emojis.of(Emojis.SHIELD, "🛡️") + " **Responsável** · " + actor.moderatorMention()));
    }

    @Override
    public void onGuildVoiceGuildDeafen(GuildVoiceGuildDeafenEvent event) {
        String head = event.isGuildDeafened()
                ? "## " + Emojis.of(Emojis.BELL_OFF, "🔕") + " Ensurdecido no servidor"
                : "## " + Emojis.of(Emojis.BELL, "🔔") + " Desensurdecido no servidor";
        AuditLookup.lookup(event.getGuild(), event.getMember().getId(), ActionType.MEMBER_UPDATE, actor ->
                ChannelLog.post(ctx, event.getGuild().getId(), "log-voz",
                        head + "\n---\n" + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + event.getMember().getAsMention()
                                + "\n---\n" + Emojis.of(Emojis.SHIELD, "🛡️") + " **Responsável** · " + actor.moderatorMention()));
    }

    @Override
    public void onGuildVoiceStream(GuildVoiceStreamEvent event) {
        String head = event.isStream()
                ? "## " + Emojis.of(Emojis.STREAM, "📡") + " Transmissão ligada"
                : "## " + Emojis.of(Emojis.OFFLINE, "📴") + " Transmissão desligada";
        ChannelLog.post(ctx, event.getGuild().getId(), "log-voz",
                head + "\n" + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + event.getMember().getAsMention());
    }

    @Override
    public void onGuildVoiceVideo(GuildVoiceVideoEvent event) {
        String head = event.isSendingVideo()
                ? "## " + Emojis.of(Emojis.CAMERA, "📷") + " Câmera ligada"
                : "## " + Emojis.of(Emojis.CAMERA, "📷") + " Câmera desligada";
        ChannelLog.post(ctx, event.getGuild().getId(), "log-voz",
                head + "\n" + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + event.getMember().getAsMention());
    }
}
