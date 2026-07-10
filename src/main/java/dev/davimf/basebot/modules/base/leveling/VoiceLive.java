package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Junta o tempo salvo no SQLite com a janela ainda não creditada de quem está em call agora.
 *
 * <p>É a única classe desta feature que toca a JDA: a aritmética vive em {@link VoicePending},
 * {@link VoicePauseReason} e {@link VoiceRanking}, todas puras e testadas.
 *
 * <p>Enquanto a {@link VoiceGate} da guild estiver fechada, o pendente é zero: as sessões ainda
 * carregam a watermark de antes do último restart, e somá-la lançaria o período offline inteiro.
 */
public final class VoiceLive {

    /** {@code pauseReason} é {@code null} quando o tempo está subindo. */
    public record Snapshot(long savedMs, long pendingMs, String pauseReason, boolean inCall) {
        public long totalMs() {
            return savedMs + pendingMs;
        }
    }

    private VoiceLive() {}

    /** Pendente de cada membro em call agora. Vazio se a guild ainda não foi reconciliada. */
    public static Map<String, Long> pendingByUser(Guild guild, GuildConfig cfg, VoiceGate gate,
                                                  VoiceSessionRepository sessions, long now, long weekStart) {
        Map<String, Long> out = new HashMap<>();
        if (!gate.isReconciled(guild.getId())) {
            return out;
        }
        for (VoiceSessionRepository.Open s : sessions.openSessions(guild.getId())) {
            Member member = guild.getMemberById(s.userId());
            AudioChannel channel = guild.getChannelById(AudioChannel.class, s.channelId());
            if (member == null || channel == null) {
                continue;
            }
            VoiceStateSnapshot snap = VoiceSnapshots.of(member, channel, cfg, 0);
            long pending = VoicePending.pendingMs(s.timeCreditedUntil(), now, weekStart,
                    VoiceEligibility.timeEligible(snap));
            if (pending > 0) {
                out.put(s.userId(), pending);
            }
        }
        return out;
    }

    /** Salvo + pendente + motivo da pausa de um único membro. */
    public static Snapshot forUser(Guild guild, String userId, GuildConfig cfg, VoiceGate gate,
                                   VoiceSessionRepository sessions, VoiceTimeRepository times,
                                   long now, long weekStart) {
        long saved = times.msOf(guild.getId(), userId, weekStart);
        VoiceSessionRepository.Open open = sessions.openSession(guild.getId(), userId);
        if (open == null) {
            return new Snapshot(saved, 0, null, false);
        }
        Member member = guild.getMemberById(userId);
        AudioChannel channel = guild.getChannelById(AudioChannel.class, open.channelId());
        if (member == null || channel == null) {
            return new Snapshot(saved, 0, null, false);
        }
        if (!gate.isReconciled(guild.getId())) {
            return new Snapshot(saved, 0, "sincronizando", true);
        }
        VoiceStateSnapshot snap = VoiceSnapshots.of(member, channel, cfg, 0);
        String reason = VoicePauseReason.of(snap);
        long pending = VoicePending.pendingMs(open.timeCreditedUntil(), now, weekStart,
                VoiceEligibility.timeEligible(snap));
        return new Snapshot(saved, pending, reason, true);
    }

    /** Ranking da semana, já com o pendente somado, sem zeros e ordenado. */
    public static List<VoiceTimeRepository.Entry> ranking(Guild guild, GuildConfig cfg, VoiceGate gate,
                                                          VoiceSessionRepository sessions,
                                                          VoiceTimeRepository times,
                                                          long now, long weekStart) {
        return VoiceRanking.merge(times.allOfWeek(guild.getId(), weekStart),
                pendingByUser(guild, cfg, gate, sessions, now, weekStart));
    }
}
