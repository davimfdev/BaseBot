package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

/** Constrói um {@link VoiceStateSnapshot} a partir do estado vivo da JDA. */
public final class VoiceSnapshots {

    private VoiceSnapshots() {}

    /**
     * @param humanBonus soma à contagem de humanos do canal. Vale 1 ao fechar a janela de um move
     *                   ou leave, quando o membro já saiu do canal antigo e portanto não aparece
     *                   mais em {@code channel.getMembers()}.
     */
    public static VoiceStateSnapshot of(Member member, AudioChannel channel, GuildConfig cfg, long humanBonus) {
        Guild guild = channel.getGuild();
        long humans = channel.getMembers().stream().filter(m -> !m.getUser().isBot()).count() + humanBonus;
        GuildVoiceState vs = member.getVoiceState();
        String afkId = guild.getAfkChannel() == null ? null : guild.getAfkChannel().getId();
        return new VoiceStateSnapshot(
                member.getUser().isBot(),
                humans,
                vs != null && vs.isSelfMuted(),
                vs != null && vs.isSelfDeafened(),
                vs != null && vs.isGuildDeafened(),
                afkId != null && afkId.equals(channel.getId()),
                VoiceScope.inScope(channel, cfg));
    }
}
