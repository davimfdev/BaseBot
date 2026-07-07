package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.util.HashMap;
import java.util.Map;

/** Acerta as sessões de voz no boot: fecha órfãs, abre/reabre conforme quem está em call agora. */
public final class VoiceReconciler {

    private VoiceReconciler() {}

    public static void run(BotContext ctx) {
        if (ctx.jda() == null) {
            return;
        }
        VoiceSessionRepository repo = new VoiceSessionRepository(ctx.database().sqlite());
        long now = System.currentTimeMillis();
        for (Guild guild : ctx.jda().getGuilds()) {
            String guildId = guild.getId();
            // userId -> canal da sessão aberta que o banco conhece
            Map<String, VoiceSessionRepository.Open> dbOpen = new HashMap<>();
            for (VoiceSessionRepository.Open o : repo.openSessions(guildId)) {
                dbOpen.put(o.userId(), o);
            }
            // quem está em call agora (canal atual por usuário)
            Map<String, String> current = new HashMap<>();
            guild.getVoiceChannels().forEach(vc -> {
                for (Member m : vc.getMembers()) {
                    if (!m.getUser().isBot()) {
                        current.put(m.getId(), vc.getId());
                    }
                }
            });
            // abre/reabre para quem está em call
            for (Map.Entry<String, String> e : current.entrySet()) {
                VoiceSessionRepository.Open open = dbOpen.get(e.getKey());
                if (open == null) {
                    repo.open(guildId, e.getKey(), e.getValue(), now);
                } else if (!open.channelId().equals(e.getValue())) {
                    repo.closeOpen(guildId, e.getKey(), now);
                    repo.open(guildId, e.getKey(), e.getValue(), now);
                }
            }
            // fecha órfãs: banco achava aberto mas não está mais em call
            for (String userId : dbOpen.keySet()) {
                if (!current.containsKey(userId)) {
                    repo.closeOpen(guildId, userId, now);
                }
            }
        }
    }
}
