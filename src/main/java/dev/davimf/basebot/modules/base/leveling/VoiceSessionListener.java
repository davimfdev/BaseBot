package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Abre/fecha sessões de voz a partir do evento unificado (join/leave/move). Requer
 * GUILD_VOICE_STATES.
 *
 * <p>Ao sair ou trocar de canal, a janela pendente é creditada ANTES de fechar a sessão, avaliada
 * com o <b>canal antigo</b> — escopo, AFK e contagem de humanos são de lá. O membro já saiu do
 * canal antigo quando o evento chega, então somamos 1 à contagem de humanos para reconstruir a
 * população durante a janela.
 */
public final class VoiceSessionListener extends ListenerAdapter {

    private final BotContext ctx;
    private final LevelingService leveling;
    private final VoiceSessionRepository sessions;
    private final VoiceGate gate;

    public VoiceSessionListener(BotContext ctx, LevelingService leveling, VoiceGate gate) {
        this.ctx = ctx;
        this.leveling = leveling;
        this.sessions = new VoiceSessionRepository(ctx.database().sqlite());
        this.gate = gate;
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

        if (left != null) {
            settleOnOldChannel(member, left, now);
            sessions.closeOpen(guildId, userId, now);
        }
        if (joined != null) {
            sessions.open(guildId, userId, joined.getId(), now);
        }
    }

    private void settleOnOldChannel(Member member, AudioChannel oldChannel, long now) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(member.getGuild().getId());
        // +1: o membro já não está mais em oldChannel.getMembers().
        VoiceStateSnapshot before = VoiceSnapshots.of(member, oldChannel, cfg, 1);
        VoiceSettler.settle(ctx, leveling, member.getGuild(), member, before, now, gate);
    }
}
