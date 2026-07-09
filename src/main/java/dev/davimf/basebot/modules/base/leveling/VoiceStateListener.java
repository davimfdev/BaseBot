package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildDeafenEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildMuteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceSelfDeafenEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceSelfMuteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.function.UnaryOperator;

/**
 * Credita a janela pendente sempre que o estado de voz muda, usando o estado ANTERIOR ao evento.
 *
 * <p>Cada evento carrega o valor NOVO da flag que mudou; o valor anterior é a negação disso, e as
 * demais flags vêm do {@code GuildVoiceState} atual. Não invertemos o estado vivo nem o agregado
 * {@code isDeafened()} — ver {@link VoiceStateSnapshot}.
 *
 * <p>{@code GuildVoiceGuildMuteEvent} não muda elegibilidade (server-mute não pausa nada), mas
 * passa pelo mesmo caminho: o crédito é idêntico ao que seria com o estado atual, e manter o
 * padrão evita um buraco se a regra mudar.
 */
public final class VoiceStateListener extends ListenerAdapter {

    private final BotContext ctx;
    private final LevelingService leveling;

    public VoiceStateListener(BotContext ctx, LevelingService leveling) {
        this.ctx = ctx;
        this.leveling = leveling;
    }

    @Override
    public void onGuildVoiceSelfMute(GuildVoiceSelfMuteEvent event) {
        settle(event.getMember(), s -> s.withSelfMuted(!event.isSelfMuted()));
    }

    @Override
    public void onGuildVoiceSelfDeafen(GuildVoiceSelfDeafenEvent event) {
        settle(event.getMember(), s -> s.withSelfDeafened(!event.isSelfDeafened()));
    }

    @Override
    public void onGuildVoiceGuildDeafen(GuildVoiceGuildDeafenEvent event) {
        settle(event.getMember(), s -> s.withGuildDeafened(!event.isGuildDeafened()));
    }

    @Override
    public void onGuildVoiceGuildMute(GuildVoiceGuildMuteEvent event) {
        settle(event.getMember(), UnaryOperator.identity());
    }

    private void settle(Member member, UnaryOperator<VoiceStateSnapshot> toPrevious) {
        if (member.getUser().isBot() || member.getVoiceState() == null) {
            return;
        }
        AudioChannel channel = member.getVoiceState().getChannel();
        if (channel == null) {
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(member.getGuild().getId());
        VoiceStateSnapshot current = VoiceSnapshots.of(member, channel, cfg, 0);
        VoiceSettler.settle(ctx, leveling, member.getGuild(), member,
                toPrevious.apply(current), System.currentTimeMillis());
    }
}
