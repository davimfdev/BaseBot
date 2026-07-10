package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/** Acerta as sessões de voz no boot: fecha órfãs, abre/reabre conforme quem está em call agora,
 *  e reancora as watermarks de quem continuou no mesmo canal. O período em que o bot esteve
 *  offline NUNCA é creditado retroativamente.
 *
 * <p>Ao terminar uma guild com sucesso, abre a {@link VoiceGate} PARA AQUELA GUILD — só então
 * ticker e settler passam a creditá-la. Se a reconciliação de uma guild lançar, a trava daquela
 * guild permanece fechada de propósito: NÃO usamos {@code finally} para marcar reconciliado,
 * porque uma reconciliação parcial/falha não deve liberar o crédito.
 *
 * <p>Cada guild é isolada em seu próprio {@code try/catch}: a falha de uma guild não impede as
 * outras de serem reconciliadas e liberadas normalmente. */
public final class VoiceReconciler {

    private static final Logger log = LoggerFactory.getLogger(VoiceReconciler.class);

    private VoiceReconciler() {}

    public static void run(BotContext ctx, VoiceGate gate) {
        if (ctx.jda() == null) {
            return;
        }
        VoiceSessionRepository repo = new VoiceSessionRepository(ctx.database().sqlite());
        long now = System.currentTimeMillis();
        for (Guild guild : ctx.jda().getGuilds()) {
            String guildId = guild.getId();
            try {
                reconcileGuild(repo, guild, guildId, now);
                // Só chega aqui se nada acima lançou: agora é seguro creditar esta guild.
                gate.markReconciled(guildId);
            } catch (Exception e) {
                log.error("Falha ao reconciliar sessões de voz da guild {}", guildId, e);
            }
        }
    }

    private static void reconcileGuild(VoiceSessionRepository repo, Guild guild, String guildId, long now) {
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
            } else {
                // Mesma sessão, mesmo canal: o bot pode ter ficado horas fora. Reancora as
                // watermarks para NÃO creditar o período offline no próximo tick.
                repo.reanchor(guildId, e.getKey(), now);
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
